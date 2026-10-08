import argparse
import json
import math
from pathlib import Path

REFERENCE_REVISION = '7a389bf04a5a81a5d1a84c2c36fcf1beb5bafe13'
CHANNELS = ('actors', 'attacks', 'damage', 'deaths', 'spawns', 'rewards', 'projectiles', 'circuits')
CONTEXT = ('scenario_sha256', 'rules_sha256', 'map_sha256', 'seed')


class TraceError(ValueError):
    pass


def validate_finite(value):
    if isinstance(value, dict):
        for child in value.values():
            validate_finite(child)
    elif isinstance(value, list):
        for child in value:
            validate_finite(child)
    elif isinstance(value, float) and not math.isfinite(value):
        raise TraceError('Non-finite numeric trace value')


def validate_trace(trace):
    validate_finite(trace)
    meta = trace.get('metadata', {})
    for field in ('driver', 'run_id', 'source_revision', 'physical_tps', 'logical_tps',
                  'logical_steps_per_frame', 'trace_scope', 'timing_origin', *CONTEXT):
        if field not in meta:
            raise TraceError(f'metadata.{field}: missing')
    if meta['trace_scope'] != 'PER_LOGICAL_TICK' or meta['timing_origin'] != 'CONTROLLED_SCENARIO':
        raise TraceError('Complete logical-tick controlled-scenario capture is required')
    if meta['logical_tps'] != 40:
        raise TraceError('logical_tps must be 40')
    if not isinstance(meta['seed'], str) or not meta['seed']:
        raise TraceError('Seed must be an explicit string without floating-point conversion')
    for field in ('scenario_sha256', 'rules_sha256', 'map_sha256'):
        value = meta[field]
        if not isinstance(value, str) or len(value) != 64 or any(c not in '0123456789abcdef' for c in value):
            raise TraceError(f'metadata.{field}: invalid SHA-256')
    samples = trace.get('samples')
    if not isinstance(samples, list) or not samples:
        raise TraceError('Missing logical-tick samples')
    for tick, sample in enumerate(samples):
        if type(sample.get('tick')) is not int or sample['tick'] != tick:
            raise TraceError(f'samples[{tick}]: expected continuous logical tick {tick}')
        if set(sample) != {'tick', *CHANNELS}:
            raise TraceError(f'tick {tick}: all state/event channels must be captured, including empty ones')
        if not isinstance(sample['actors'], dict):
            raise TraceError(f'tick {tick}: actors must be keyed by stable scenario identity')
        for actor_id, actor in sample['actors'].items():
            if not isinstance(actor_id, str) or not actor_id or not isinstance(actor, dict):
                raise TraceError(f'tick {tick}: invalid scenario actor identity/state')
            for field in ('health', 'position', 'target', 'cooldown', 'alive'):
                if field not in actor:
                    raise TraceError(f'tick {tick}: {actor_id}.{field}: missing')
            if not isinstance(actor['position'], list) or len(actor['position']) != 3:
                raise TraceError(f'tick {tick}: {actor_id}.position: expected xyz')
            if type(actor['cooldown']) is not int or type(actor['alive']) is not bool:
                raise TraceError(f'tick {tick}: {actor_id}: invalid cooldown/alive type')
            if actor['target'] is not None and not isinstance(actor['target'], str):
                raise TraceError(f'tick {tick}: {actor_id}.target: expected scenario ID or null')
            for value in (actor['health'], *actor['position']):
                if type(value) not in (int, float) or not math.isfinite(value):
                    raise TraceError(f'tick {tick}: {actor_id}: non-finite numeric state')
        for channel in CHANNELS[1:]:
            if not isinstance(sample[channel], list) or not all(isinstance(event, dict) for event in sample[channel]):
                raise TraceError(f'tick {tick}: {channel}: expected ordered event objects')
    return trace


def first_difference(expected, actual, path, position_tolerance):
    if type(expected) in (int, float) and type(actual) in (int, float):
        equal = abs(expected - actual) <= position_tolerance if '.position[' in path else expected == actual
        return None if equal else {'path': path, 'expected': expected, 'actual': actual}
    if type(expected) is not type(actual):
        return {'path': path, 'expected': expected, 'actual': actual}
    if isinstance(expected, dict):
        if expected.keys() != actual.keys():
            return {'path': path + '.keys', 'expected': sorted(expected), 'actual': sorted(actual)}
        for key in sorted(expected):
            difference = first_difference(expected[key], actual[key], f'{path}.{key}', position_tolerance)
            if difference:
                return difference
    elif isinstance(expected, list):
        if len(expected) != len(actual):
            return {'path': path + '.length', 'expected': len(expected), 'actual': len(actual)}
        for index, (left, right) in enumerate(zip(expected, actual)):
            difference = first_difference(left, right, f'{path}[{index}]', position_tolerance)
            if difference:
                return difference
    elif expected != actual:
        return {'path': path, 'expected': expected, 'actual': actual}
    return None


def compare(reference, candidate, position_tolerance=1e-7):
    if not math.isfinite(position_tolerance) or position_tolerance < 0:
        raise TraceError('position_tolerance must be finite and nonnegative')
    validate_trace(reference)
    validate_trace(candidate)
    before, after = reference['metadata'], candidate['metadata']
    if before['driver'] != 'NATIVE_REFERENCE' or (before['physical_tps'], before['logical_steps_per_frame']) != (40, 1):
        raise TraceError('Reference must use original native 40 TPS with one logical step per frame')
    if before['source_revision'] != REFERENCE_REVISION:
        raise TraceError('Reference source revision does not match the pinned original mod')
    if after['driver'] != 'ASYNC_LOGICAL_SIMULATION' or (after['physical_tps'], after['logical_steps_per_frame']) != (20, 2):
        raise TraceError('Candidate must use asynchronous logical simulation at physical 20 TPS/frame 2')
    if before['run_id'] == after['run_id']:
        raise TraceError('Independent native and simulation captures are required')
    for field in CONTEXT:
        if before[field] != after[field]:
            raise TraceError(f'Incompatible {field}: historical/current rules or controlled inputs differ')
    for tick, (left, right) in enumerate(zip(reference['samples'], candidate['samples'])):
        difference = first_difference(left, right, f'tick[{tick}]', position_tolerance)
        if difference:
            return {'equal': False, 'first_divergence': {'tick': tick, **difference},
                    'scope': 'CONTROLLED_SCENARIO_NOT_EXACT_HISTORICAL_REPLAY'}
    if len(reference['samples']) != len(candidate['samples']):
        return {'equal': False, 'first_divergence': {
            'tick': min(len(reference['samples']), len(candidate['samples'])), 'path': 'samples.length',
            'expected': len(reference['samples']), 'actual': len(candidate['samples'])},
            'scope': 'CONTROLLED_SCENARIO_NOT_EXACT_HISTORICAL_REPLAY'}
    return {'equal': True, 'samples_compared': len(reference['samples']),
            'scope': 'CONTROLLED_SCENARIO_NOT_EXACT_HISTORICAL_REPLAY'}


def load_trace(path):
    with Path(path).open(encoding='utf-8') as stream:
        return json.load(stream, parse_constant=lambda value: (_ for _ in ()).throw(TraceError(f'Invalid {value}')))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('reference', type=Path)
    parser.add_argument('candidate', type=Path)
    parser.add_argument('--position-tolerance', type=float, default=1e-7)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        report = compare(load_trace(args.reference), load_trace(args.candidate), args.position_tolerance)
    except (TraceError, OSError, json.JSONDecodeError) as error:
        report = {'equal': False, 'invalid_capture': str(error)}
    result = json.dumps(report, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(result, encoding='utf-8')
    print(result, end='')
    return 0 if report['equal'] else 1


if __name__ == '__main__':
    raise SystemExit(main())

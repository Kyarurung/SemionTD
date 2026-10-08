import argparse
import json
import math
from pathlib import Path
import statistics

from run_benchmark import CANDIDATE
from run_capture import REFERENCE


class BenchmarkError(ValueError):
    pass


def finite(value, name):
    if type(value) not in (int, float) or not math.isfinite(value) or value < 0:
        raise BenchmarkError(f'{name}: expected nonnegative finite number')
    return value


def nullable_sum(trials, field):
    values = [trial.get(field) for trial in trials]
    if any(value is None for value in values):
        return None
    return sum(finite(value, field) for value in values)


def ratio(value, denominator):
    return None if value is None or denominator == 0 else value / denominator


def quantile(values, fraction):
    ordered = sorted(values)
    index = (len(ordered) - 1) * fraction
    lower = math.floor(index)
    upper = math.ceil(index)
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (index - lower)


def validate(run):
    if run.get('schema_version') != 1 or run.get('dry_run') is not False:
        raise BenchmarkError('Only complete measured benchmark schema 1 runs are accepted')
    driver = run.get('driver')
    if driver not in ('native', 'simulation'):
        raise BenchmarkError('Unknown benchmark driver')
    if run.get('production_revision') != (REFERENCE if driver == 'native' else CANDIDATE):
        raise BenchmarkError('Unexpected benchmark production pin')
    if run.get('physical_tps') != (160 if driver == 'native' else 20) or run.get('logical_target_tps') != 160:
        raise BenchmarkError('Expected native 160 or physical 20/logical 160 benchmark clocks')
    if run.get('warmup_trials', 0) < 3 or run.get('measured_trials', 0) < 5:
        raise BenchmarkError('Each independent JVM requires at least three warmups and five measured trials')
    trials = run.get('trials', [])
    if len(trials) != run['warmup_trials'] + run['measured_trials']:
        raise BenchmarkError('Missing benchmark trials')
    for index, trial in enumerate(trials):
        if trial.get('repetition') != index or trial.get('warmup') != (index < run['warmup_trials']):
            raise BenchmarkError('Warmups or repetition ordering are inconsistent')
        finite(trial.get('elapsed_ns'), 'elapsed_ns')
        finite(trial.get('logical_ticks'), 'logical_ticks')
        if not trial['elapsed_ns'] or not trial['logical_ticks']:
            raise BenchmarkError('A measured workload must have real elapsed time and completed logical ticks')
        if not trial.get('base_wait_calls') or not trial.get('frames'):
            raise BenchmarkError('Measured native base pacing and full physical frames are required')
        if trial.get('physical_frames') != len(trial['frames']):
            raise BenchmarkError('Frame samples were truncated')
        workload = trial.get('workload', {})
        if workload.get('monster_deaths') != 12 * run['engineer_lanes'] or workload.get('kill_diamonds') != 36 * run['engineer_lanes']:
            raise BenchmarkError('The controlled engineer battle did not complete its actual workload')
        if not trial.get('matched_work_sha256'):
            raise BenchmarkError('A final workload digest is required')
        for frame in trial['frames']:
            finite(frame.get('start_to_next_start_ns'), 'start_to_next_start_ns')
            finite(frame.get('outer_tick_wall_ns'), 'outer_tick_wall_ns')
            if type(frame.get('deadline_overrun')) is not bool:
                raise BenchmarkError('Frame deadline overrun must be a boolean')
    return run


def aggregate(runs):
    trials = [trial for run in runs for trial in run['trials'] if not trial['warmup']]
    wall = sum(trial['elapsed_ns'] for trial in trials)
    logical = sum(trial['logical_ticks'] for trial in trials)
    frames = [frame for trial in trials for frame in trial['frames']]
    main_cpu = nullable_sum(trials, 'main_cpu_ns')
    worker_cpu = nullable_sum(trials, 'worker_cpu_ns')
    process_cpu = nullable_sum(trials, 'process_cpu_ns')
    body_ms = [finite(frame['outer_tick_wall_ns'], 'outer_tick_wall_ns') / 1000000 for frame in frames]
    native_ms = [finite(frame.get('native_tick_wall_ns', frame['outer_tick_wall_ns']), 'native_tick_wall_ns') / 1000000 for frame in frames]
    intervals_ms = [finite(frame['start_to_next_start_ns'], 'start_to_next_start_ns') / 1000000 for frame in frames]
    return {
        'independent_jvms': len(runs), 'measured_battles': len(trials),
        'logical_ticks': logical, 'measurement_wall_seconds': wall / 1000000000,
        'observed_logical_tps': logical * 1000000000 / wall,
        'observed_physical_fps': len(frames) * 1000000000 / wall,
        'process_cpu_seconds_per_second': ratio(process_cpu, wall),
        'process_cpu_ns_per_logical_tick': ratio(process_cpu, logical),
        'whole_main_cpu_ns_per_logical_tick': ratio(main_cpu, logical),
        'worker_cpu_ns_per_logical_tick': ratio(worker_cpu, logical),
        'main_allocated_bytes_per_logical_tick': ratio(nullable_sum(trials, 'main_allocated_bytes'), logical),
        'worker_allocated_bytes_per_logical_tick': ratio(nullable_sum(trials, 'worker_allocated_bytes'), logical),
        'gc_count': nullable_sum(trials, 'gc_count'), 'gc_time_ms': nullable_sum(trials, 'gc_time_ms'),
        'mspt_outer_mean': statistics.fmean(body_ms), 'mspt_outer_p50': quantile(body_ms, .5),
        'mspt_outer_p95': quantile(body_ms, .95), 'mspt_outer_max': max(body_ms),
        'mspt_native_mean': statistics.fmean(native_ms), 'mspt_native_p95': quantile(native_ms, .95),
        'frame_interval_ms_mean': statistics.fmean(intervals_ms), 'frame_interval_ms_min': min(intervals_ms),
        'frame_interval_ms_p50': quantile(intervals_ms, .5), 'frame_interval_ms_max': max(intervals_ms),
        'deadline_overrun_frames': sum(frame['deadline_overrun'] for frame in frames),
        'physical_frames': len(frames),
        'groups_spanning_multiple_physical_frames': sum(trial['groups_spanning_multiple_physical_frames'] for trial in trials),
        'maximum_group_physical_frames': max(trial['maximum_group_physical_frames'] for trial in trials),
        'target_160_observed': logical * 1000000000 / wall >= 160,
    }


def summarize(runs):
    runs = [validate(run) for run in runs]
    if len(runs) != 4 or [run['driver'] for run in runs] not in (
            ['native', 'simulation', 'simulation', 'native'], ['simulation', 'native', 'native', 'simulation']):
        raise BenchmarkError('Use four sequential independent JVM runs in ABBA order')
    if len({run['run_id'] for run in runs}) != 4:
        raise BenchmarkError('Independent JVM run IDs are required')
    for field in ('engineer_lanes', 'scenario_sha256', 'rules_sha256', 'map_sha256', 'seed'):
        if len({run.get(field) for run in runs}) != 1:
            raise BenchmarkError(f'Incompatible {field}; do not compare different workload conditions')
    hashes = {trial['matched_work_sha256'] for run in runs for trial in run['trials']}
    if len(hashes) != 1:
        raise BenchmarkError('Actual final workload differs across repetitions or native/simulation; no performance conclusion')
    return {
        'scope': 'PACED_CONTROLLED_ENGINEER_8X_TEST_DRIVER_PRODUCTION_MAXIMUM_IS_5',
        'engineer_lanes': runs[0]['engineer_lanes'], 'matched_work_sha256': hashes.pop(),
        'native': aggregate([run for run in runs if run['driver'] == 'native']),
        'simulation': aggregate([run for run in runs if run['driver'] == 'simulation']),
        'qualifications': ['Setup/settling/warmups excluded; background JVM work remains included in process CPU',
                           'Whole main CPU includes worker continuations during native wait',
                           'No connected clients, real multiplayer or GPU measurements',
                           'Unavailable CPU/allocation counters remain null; configured rate alone does not prove throughput'],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('runs', nargs=4, type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        report = summarize([json.loads(path.read_text(encoding='utf-8')) for path in args.runs])
    except (BenchmarkError, OSError, json.JSONDecodeError) as error:
        report = {'valid_comparison': False, 'error': str(error)}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))
    return 1 if report.get('valid_comparison') is False else 0


if __name__ == '__main__':
    raise SystemExit(main())

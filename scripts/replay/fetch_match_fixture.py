import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import importlib.util
import json
import sys
from pathlib import Path
from urllib.error import HTTPError

ROOT = Path(__file__).resolve().parents[2]
SKILL_FETCH = ROOT / '.agents/skills/semiontd-live-balance-analysis/scripts/fetch_live_metrics.py'
spec = importlib.util.spec_from_file_location('live_metrics', SKILL_FETCH)
live_metrics = importlib.util.module_from_spec(spec)
sys.dont_write_bytecode = True
spec.loader.exec_module(live_metrics)

ACTION_FIELDS = ('sequence', 'round', 'action_type', 'subject_id', 'position_x',
                 'position_y', 'position_z', 'position_mode', 'cost', 'income_gain',
                 'scheduled_round', 'target_team', 'target_lane_id')
TOWER_FIELDS = ('id', 'builderId', 'tier', 'category', 'availability', 'mineralCost',
                'maxHealth', 'range', 'damage', 'attackIntervalTicks', 'aggroPriority')
EDGE_FIELDS = ('id', 'fromTowerId', 'toTowerId', 'mineralCost')
MONSTER_FIELDS = ('id', 'armor', 'count', 'health', 'healing', 'attackKind', 'dimensions',
                  'attackRange', 'attackDamage', 'mineralReward', 'targetPriority',
                  'attackIntervalTicks', 'movementSpeedMultiplier')
METRIC_FIELDS = ('round_number', 'tower_type_id', 'wave_duration_ticks', 'combat_ticks',
                 'tower_count_start', 'tower_count_end', 'tower_death_count', 'income',
                 'emerald_balance', 'diamond_balance', 'monster_kills', 'sample_count',
                 'start_count', 'end_alive_count', 'death_count', 'physical_damage_dealt',
                 'magic_damage_dealt', 'damage_taken', 'healing_done', 'kill_count',
                 'first_combat_tick', 'last_combat_tick', 'survival_ticks')
GAPS = ('build_action_execution_ticks', 'cross_participant_action_order', 'rng_seed_and_streams',
        'historical_source_revision', 'full_balance_configuration', 'map_and_lane_origins',
        'tower_uuid_and_spawn_order', 'monster_uuid_and_spawn_death_order',
        'target_selection_history', 'player_motion_and_skill_inputs',
        'projectile_history', 'circuit_topology_and_block_inputs', 'mid_round_health_and_cooldowns')


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')


def select(value, fields):
    return {key: value[key] for key in fields if key in value}


def sanitize(match, catalog, metrics):
    if str(match['match_id']) != match['match_id']:
        raise ValueError('match_id must be a decimal string')
    if catalog['versionHash'] != match['catalog_version']:
        raise ValueError('historical catalog version differs from match')
    participants = []
    identities = {}
    for index, participant in enumerate(match['participants']):
        slot = f'p{index:02d}'
        identities[participant['player_id']] = slot
        actions = [select(action, ACTION_FIELDS) for action in participant['build_actions']]
        if [action['sequence'] for action in actions] != list(range(len(actions))):
            raise ValueError(f'{slot}: missing or reordered build sequence')
        participants.append({
            'slot': slot, 'team': participant['team_id'],
            'builder': participant['job_id'].replace('semion-td:ender_towers', 'semion-td:end_towers'),
            'traits': participant['trait_loadout'], 'augments': participant['augment_selections'],
            'round_outcomes': participant['round_outcomes'], 'actions': actions,
        })
    rows = [{'slot': identities[row['player_id']], **select(row, METRIC_FIELDS)}
            for row in metrics.get('metrics', [])]
    used_ids = {action['subject_id'] for participant in participants for action in participant['actions']
                if action['action_type'].startswith('TOWER_')}
    edges = [edge for edge in catalog['upgrades'] if edge['id'] in used_ids]
    used_ids.update(edge['fromTowerId'] for edge in edges)
    used_ids.update(edge['toTowerId'] for edge in edges)
    waves = []
    for wave in catalog['waves']['rounds']:
        if wave['round'] <= match['final_round']:
            waves.append({**select(wave, ('round', 'spawnMode', 'spawnIntervalTicks', 'mineralRewardBudget')),
                          'lanes': {lane: [select(monster, MONSTER_FIELDS) for monster in monsters]
                                    for lane, monsters in wave['lanes'].items()}})
    return {
        'schema_version': 1, 'match_id': match['match_id'],
        'reference_revision': 'f2f3c2e53babce83ac07f3ea4be364d4ce349bae',
        'catalog_version': match['catalog_version'], 'augment_version': match['augment_version'],
        'balance_revision': match['start_balance_revision'], 'mixed_version': match['mixed_version'],
        'balance_patch_events': match['balance_patch_events'], 'final_round': match['final_round'],
        'match_mode': match['match_mode'], 'timing': 'ROUND_AND_PER_PARTICIPANT_SEQUENCE_ONLY',
        'missing_state': list(GAPS), 'participants': participants,
        'catalog': {
            'towers': [select(tower, TOWER_FIELDS) for tower in catalog['towers'] if tower['id'] in used_ids],
            'upgrades': [select(edge, EDGE_FIELDS) for edge in edges],
            'waves': waves,
        },
        'round_metrics': rows, 'round_metrics_complete': metrics.get('next_cursor') is None,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--match-id', default='893854454113494679')
    parser.add_argument('--raw-dir', type=Path, default=ROOT / 'build/replay-analysis')
    parser.add_argument('--output', type=Path, default=ROOT / 'src/test/resources/replay/match-893854454113494679.json')
    parser.add_argument('--offline', action='store_true')
    args = parser.parse_args()
    args.raw_dir.mkdir(parents=True, exist_ok=True)
    sources = []

    def obtain(name, path, params=None):
        raw_path = args.raw_dir / f'{name}.json'
        if args.offline:
            value = json.loads(raw_path.read_text(encoding='utf-8-sig'))
        else:
            value = live_metrics.fetch(path, params or {})
            raw_path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        sources.append({'path': path, 'params': params or {},
                        'canonical_json_sha256': hashlib.sha256(canonical(value)).hexdigest()})
        return value

    stats = obtain('stats', '/api/v1/stats')
    patches = obtain('patches', '/api/v1/patches')
    match = obtain('match', f'/api/v1/matches/{args.match_id}')
    catalog = obtain('catalog', '/api/v1/catalog', {'version': match['catalog_version']})
    obtain('participant-metrics', '/api/v1/participant-metrics', {'matchId': args.match_id, 'limit': '200'})
    if args.offline:
        metrics = json.loads((args.raw_dir / 'round-metrics.json').read_text(encoding='utf-8-sig'))
    else:
        metrics = live_metrics.fetch('/api/v1/round-metrics', {'matchId': args.match_id, 'limit': '200'})
        cursor = metrics.get('next_cursor')
        seen = set()
        while cursor:
            if cursor in seen:
                raise ValueError('repeated metrics cursor')
            seen.add(cursor)
            try:
                page = live_metrics.fetch('/api/v1/round-metrics',
                                          {'matchId': args.match_id, 'limit': '200', 'cursor': cursor})
            except HTTPError as error:
                metrics['pagination_error'] = {'status': error.code, 'body': error.read().decode('utf-8')}
                break
            metrics['metrics'].extend(page['metrics'])
            cursor = page.get('next_cursor')
        metrics['next_cursor'] = cursor
        if cursor:
            def fetch_round(round_number):
                params = {'matchId': args.match_id, 'limit': '1000',
                          'fromRound': str(round_number), 'toRound': str(round_number)}
                page = live_metrics.fetch('/api/v1/round-metrics', params)
                if page.get('next_cursor'):
                    raise ValueError(f'round {round_number}: incomplete metrics partition')
                (args.raw_dir / f'round-{round_number:02d}.json').write_text(
                    json.dumps(page, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
                return page['metrics'], {'path': '/api/v1/round-metrics', 'params': params,
                                         'canonical_json_sha256': hashlib.sha256(canonical(page)).hexdigest()}
            with ThreadPoolExecutor(max_workers=4) as executor:
                partitions = list(executor.map(fetch_round, range(1, match['final_round'] + 1)))
            metrics['metrics'] = [row for rows, source in partitions for row in rows]
            metrics['partition_sources'] = [source for rows, source in partitions]
            keys = [(row['player_id'], row['round_number'], row['tower_type_id']) for row in metrics['metrics']]
            if len(keys) != len(set(keys)):
                raise ValueError('duplicate metrics partition key')
            metrics['next_cursor'] = None
            metrics['partition_strategy'] = 'ONE_QUERY_PER_ROUND_FROM_1_TO_FINAL_ROUND_LIMIT_1000'
        (args.raw_dir / 'round-metrics.json').write_text(json.dumps(metrics, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    fixture = sanitize(match, catalog, metrics)
    fixture['sources'] = sources + metrics.get('partition_sources', [{
        'path': '/api/v1/round-metrics', 'params': {'matchId': args.match_id, 'limit': '200'},
        'canonical_json_sha256': hashlib.sha256(canonical(metrics)).hexdigest(),
    }])
    fixture['api_inventory'] = {
        'patch_match_count': next(row['match_count'] for row in patches['patches']
                                 if row['version_hash'] == match['catalog_version']),
        'round_telemetry_available': stats['tower_round_samples'] > 0,
        'round_metrics_pagination_error': metrics.get('pagination_error'),
        'round_metrics_partition_strategy': metrics.get('partition_strategy'),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(fixture, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'output': str(args.output), 'participants': len(fixture['participants']),
                      'actions': sum(len(p['actions']) for p in fixture['participants']),
                      'round_metrics': len(fixture['round_metrics']),
                      'round_metrics_complete': fixture['round_metrics_complete']}))


if __name__ == '__main__':
    main()

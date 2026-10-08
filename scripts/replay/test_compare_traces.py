import copy
import json
from pathlib import Path
import unittest

import compare_traces as comparator
import fetch_match_fixture as fetcher


def capture(driver):
    metadata = {'driver': driver, 'run_id': driver, 'source_revision': comparator.REFERENCE_REVISION,
                'physical_tps': 40 if driver == 'NATIVE_REFERENCE' else 20, 'logical_tps': 40,
                'logical_steps_per_frame': 1 if driver == 'NATIVE_REFERENCE' else 2,
                'trace_scope': 'PER_LOGICAL_TICK', 'timing_origin': 'CONTROLLED_SCENARIO',
                'seed': '9223372036854775807', 'scenario_sha256': '1' * 64,
                'rules_sha256': '2' * 64, 'map_sha256': '3' * 64}
    samples = []
    for tick in range(3):
        samples.append({'tick': tick, 'actors': {'p02/placement/2': {
            'health': 30.0, 'position': [38.0, 0.0, 2.0], 'target': 'wave/0',
            'cooldown': 14 - tick, 'alive': True}},
            'attacks': [], 'damage': [], 'deaths': [], 'spawns': [],
            'rewards': [], 'projectiles': [], 'circuits': []})
    return {'metadata': metadata, 'samples': samples}


class ReplayTraceComparatorTest(unittest.TestCase):
    def setUp(self):
        self.before = capture('NATIVE_REFERENCE')
        self.after = capture('ASYNC_LOGICAL_SIMULATION')

    def test_comparator_accepts_compatible_capture_schema(self):
        self.assertEqual(3, comparator.compare(self.before, self.after)['samples_compared'])

    def test_native_repeatability_is_a_distinct_scope_with_independent_captures(self):
        repeat = copy.deepcopy(self.before)
        repeat['metadata']['run_id'] = 'independent-native-repeat'
        result = comparator.compare_repeated_reference(self.before, repeat)
        self.assertTrue(result['equal'])
        self.assertEqual('NATIVE_CONTROLLED_SCENARIO_REPEATABILITY_ONLY', result['scope'])
        repeat['samples'][1]['actors']['p02/placement/2']['position'][0] += 0.1
        self.assertFalse(comparator.compare_repeated_reference(self.before, repeat)['equal'])
        with self.assertRaises(comparator.TraceError):
            comparator.compare_repeated_reference(self.before, self.after)
        with self.assertRaises(comparator.TraceError):
            comparator.compare_repeated_reference(self.before, self.before)

    def test_first_divergence_is_intermediate_logical_tick_not_final_frame_only(self):
        self.after['samples'][1]['actors']['p02/placement/2']['health'] = 29.0
        result = comparator.compare(self.before, self.after)
        self.assertFalse(result['equal'])
        self.assertEqual(1, result['first_divergence']['tick'])
        self.assertTrue(result['first_divergence']['path'].endswith('.health'))

    def test_every_event_channel_and_actor_state_change_is_detected(self):
        for channel in comparator.CHANNELS[1:]:
            with self.subTest(channel=channel):
                candidate = copy.deepcopy(self.after)
                candidate['samples'][1][channel].append({'actor': 'p02/placement/2', 'value': '1'})
                self.assertFalse(comparator.compare(self.before, candidate)['equal'])
        for field, value in (('cooldown', 0), ('target', None), ('alive', False), ('health', 0.0)):
            with self.subTest(field=field):
                candidate = copy.deepcopy(self.after)
                candidate['samples'][1]['actors']['p02/placement/2'][field] = value
                self.assertFalse(comparator.compare(self.before, candidate)['equal'])

    def test_event_order_is_not_normalized(self):
        self.before['samples'][1]['damage'] = [{'source': 'a'}, {'source': 'b'}]
        self.after['samples'][1]['damage'] = [{'source': 'b'}, {'source': 'a'}]
        self.assertEqual('tick[1].damage[0].source', comparator.compare(self.before, self.after)['first_divergence']['path'])

    def test_missing_channel_truncation_and_hidden_tick_are_detected(self):
        candidate = copy.deepcopy(self.after)
        del candidate['samples'][1]['circuits']
        with self.assertRaises(comparator.TraceError):
            comparator.compare(self.before, candidate)
        candidate = copy.deepcopy(self.after)
        candidate['capture_complete'] = False
        with self.assertRaises(comparator.TraceError):
            comparator.compare(self.before, candidate)
        candidate = copy.deepcopy(self.after)
        candidate['samples'].pop()
        self.assertFalse(comparator.compare(self.before, candidate)['equal'])
        candidate = copy.deepcopy(self.after)
        candidate['samples'].pop(1)
        with self.assertRaises(comparator.TraceError):
            comparator.compare(self.before, candidate)

    def test_rules_seed_map_scenario_and_self_comparison_are_refused(self):
        for field in comparator.CONTEXT:
            candidate = copy.deepcopy(self.after)
            candidate['metadata'][field] = '4' * 64 if field != 'seed' else '0'
            with self.subTest(field=field), self.assertRaises(comparator.TraceError):
                comparator.compare(self.before, candidate)
        self.after['metadata']['run_id'] = self.before['metadata']['run_id']
        with self.assertRaises(comparator.TraceError):
            comparator.compare(self.before, self.after)

    def test_wrong_physical_clock_reference_revision_and_nonfinite_position_are_refused(self):
        for field, value in (('physical_tps', 40), ('logical_steps_per_frame', 1), ('seed', 1.0)):
            candidate = copy.deepcopy(self.after)
            candidate['metadata'][field] = value
            with self.subTest(field=field), self.assertRaises(comparator.TraceError):
                comparator.compare(self.before, candidate)
        self.before['metadata']['source_revision'] = '0' * 40
        with self.assertRaises(comparator.TraceError):
            comparator.compare(self.before, self.after)
        self.before = capture('NATIVE_REFERENCE')
        self.after['samples'][1]['actors']['p02/placement/2']['position'][0] = float('nan')
        with self.assertRaises(comparator.TraceError):
            comparator.compare(self.before, self.after)

    def test_position_tolerance_never_weakens_health_or_event_comparison(self):
        self.after['samples'][1]['actors']['p02/placement/2']['position'][0] += 1e-8
        self.assertTrue(comparator.compare(self.before, self.after)['equal'])
        self.assertFalse(comparator.compare(self.before, self.after, 0)['equal'])
        self.after['samples'][1]['actors']['p02/placement/2']['health'] -= 1e-8
        self.assertFalse(comparator.compare(self.before, self.after)['equal'])

    def test_equivalent_legal_numeric_representations_compare_by_value(self):
        actor = self.after['samples'][1]['actors']['p02/placement/2']
        actor['health'] = 30
        actor['position'] = [38, 0, 2]
        self.before['samples'][1]['damage'] = [{'amount': 5}]
        self.after['samples'][1]['damage'] = [{'amount': 5.0}]
        self.assertTrue(comparator.compare(self.before, self.after)['equal'])
        actor['position'][0] = 38.00000001
        self.assertTrue(comparator.compare(self.before, self.after)['equal'])
        actor['health'] = 30.00000001
        self.assertFalse(comparator.compare(self.before, self.after)['equal'])

    def test_boolean_is_not_coerced_to_integer_and_large_integer_is_not_rounded(self):
        self.before['samples'][1]['damage'] = [{'amount': 1}]
        self.after['samples'][1]['damage'] = [{'amount': True}]
        self.assertFalse(comparator.compare(self.before, self.after)['equal'])
        self.before['samples'][1]['damage'] = [{'amount': 9007199254740993}]
        self.after['samples'][1]['damage'] = [{'amount': 9007199254740992.0}]
        self.assertFalse(comparator.compare(self.before, self.after)['equal'])


class ReplayFixtureSanitizationTest(unittest.TestCase):
    def test_native_capture_opening_contains_only_actual_selected_actions(self):
        root = fetcher.ROOT
        fixture = json.loads((root / 'src/test/resources/replay/match-893854454113494679.json').read_text(encoding='utf-8'))
        opening = json.loads((root / 'src/gametest/resources/replay/engineer-opening.json').read_text(encoding='utf-8'))
        self.assertEqual(fixture['participants'][2]['actions'][:5], opening['actions'])
        self.assertEqual(fixture['catalog_version'], opening['historical_catalog_version'])
        self.assertEqual('CONTROLLED_SCENARIO', opening['timing_origin'])
        self.assertEqual(131, sum(int(action['cost']) for action in opening['actions']))

    def test_fixture_has_no_player_identifiers_encoded_cursors_or_assets(self):
        path = fetcher.ROOT / 'src/test/resources/replay/match-893854454113494679.json'
        fixture = json.loads(path.read_text(encoding='utf-8'))
        serialized = json.dumps(fixture)
        for prohibited in ('player_id', 'player_name', 'blockbenchModel', 'build_code', 'next_cursor', 'visual'):
            self.assertNotIn(prohibited, serialized)
        self.assertEqual(893854454113494679, int(fixture['match_id']))
        self.assertEqual(1865, sum(len(p['actions']) for p in fixture['participants']))
        self.assertEqual(2073, len(fixture['round_metrics']))
        self.assertTrue(fixture['round_metrics_complete'])

    def test_mismatched_catalog_and_lost_sequence_are_refused(self):
        match = {'match_id': '893854454113494679', 'catalog_version': 'one', 'participants': []}
        with self.assertRaises(ValueError):
            fetcher.sanitize(match, {'versionHash': 'two'}, {})
        match['participants'] = [{'player_id': 'private', 'build_actions': [{'sequence': 1}]}]
        with self.assertRaises(ValueError):
            fetcher.sanitize(match, {'versionHash': 'one'}, {})


if __name__ == '__main__':
    unittest.main()

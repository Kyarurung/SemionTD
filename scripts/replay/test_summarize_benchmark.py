import copy
import unittest

import summarize_benchmark as summary


def run(driver, run_id):
    frames = [{'outer_tick_wall_ns': 1000000, 'start_to_next_start_ns': 6250000,
               'deadline_overrun': False} for _ in range(391)]
    trials = []
    for index in range(8):
        trials.append({'repetition': index, 'warmup': index < 3, 'elapsed_ns': 2443750000,
                       'logical_ticks': 391, 'base_wait_calls': 391, 'physical_frames': len(frames),
                       'frames': frames, 'main_cpu_ns': 100000000, 'worker_cpu_ns': 50000000,
                       'process_cpu_ns': 200000000, 'main_allocated_bytes': 10000,
                       'worker_allocated_bytes': 5000, 'gc_count': 0, 'gc_time_ms': 0,
                       'groups_spanning_multiple_physical_frames': 0, 'maximum_group_physical_frames': 1,
                       'matched_work_sha256': '1' * 64,
                       'workload': {'monster_deaths': 12, 'kill_diamonds': 36}})
    return {'schema_version': 1, 'run_id': run_id, 'source_revision': 'a' * 40,
            'production_revision': summary.REFERENCE if driver == 'native' else summary.CANDIDATE,
            'driver': driver, 'engineer_lanes': 1, 'warmup_trials': 3, 'measured_trials': 5,
            'dry_run': False, 'physical_tps': 160 if driver == 'native' else 20,
            'logical_target_tps': 160, 'scenario_sha256': '2' * 64, 'rules_sha256': '3' * 64,
            'map_sha256': '4' * 64, 'seed': '1', 'trials': trials}


class BenchmarkSummaryTest(unittest.TestCase):
    def setUp(self):
        self.runs = [run(driver, str(index)) for index, driver in enumerate(
            ('native', 'simulation', 'simulation', 'native'))]

    def test_warmup_excluded_and_actual_completed_ticks_drive_rates(self):
        self.runs[0]['trials'][0]['process_cpu_ns'] = 999999999999
        result = summary.summarize(self.runs)
        self.assertEqual(10, result['native']['measured_battles'])
        self.assertEqual(3910, result['native']['logical_ticks'])
        self.assertEqual(160.0, result['native']['observed_logical_tps'])
        self.assertAlmostEqual(200000000 / 391, result['native']['process_cpu_ns_per_logical_tick'])

    def test_unavailable_cpu_and_allocations_remain_null(self):
        self.runs[0]['trials'][4]['process_cpu_ns'] = None
        self.runs[1]['trials'][5]['worker_allocated_bytes'] = None
        result = summary.summarize(self.runs)
        self.assertIsNone(result['native']['process_cpu_ns_per_logical_tick'])
        self.assertIsNone(result['simulation']['worker_allocated_bytes_per_logical_tick'])

    def test_configured_160_does_not_imply_observed_160(self):
        for candidate in self.runs[1:3]:
            for trial in candidate['trials']:
                trial['elapsed_ns'] *= 2
        result = summary.summarize(self.runs)
        self.assertEqual(80.0, result['simulation']['observed_logical_tps'])
        self.assertFalse(result['simulation']['target_160_observed'])

    def test_workload_mismatch_or_dryrun_refuses_performance_comparison(self):
        mismatched = copy.deepcopy(self.runs)
        mismatched[2]['trials'][4]['matched_work_sha256'] = 'f' * 64
        with self.assertRaises(summary.BenchmarkError):
            summary.summarize(mismatched)
        self.runs[1]['dry_run'] = True
        with self.assertRaises(summary.BenchmarkError):
            summary.summarize(self.runs)

    def test_pacing_abba_independent_jvms_and_conditions_are_required(self):
        for field, value in (('driver', 'native'), ('run_id', '0'), ('rules_sha256', 'f' * 64)):
            runs = copy.deepcopy(self.runs)
            runs[1][field] = value
            with self.subTest(field=field), self.assertRaises(summary.BenchmarkError):
                summary.summarize(runs)
        self.runs[0]['trials'][3]['base_wait_calls'] = 0
        with self.assertRaises(summary.BenchmarkError):
            summary.summarize(self.runs)


if __name__ == '__main__':
    unittest.main()

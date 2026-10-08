import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess

from run_capture import PRODUCTION_PATHS, REFERENCE, git

CANDIDATE = '2e8897a70aad893cf91284a0c936a90af9c15d3e'


def source(root, mode):
    if git(root, 'status', '--porcelain', '--', *PRODUCTION_PATHS):
        raise ValueError('Benchmark production and dependency files must be clean and committed')
    pin = REFERENCE if mode == 'native' else CANDIDATE
    if git(root, 'diff', '--name-only', pin, '--', *PRODUCTION_PATHS):
        raise ValueError(f'Benchmark production/dependencies differ from required {pin}')
    if git(root, 'status', '--porcelain', '--', 'src/gametest', 'scripts/replay'):
        raise ValueError('Commit benchmark instrumentation before attaching measured source provenance')
    return git(root, 'rev-parse', 'HEAD'), pin


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=('native', 'simulation'))
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--lanes', type=int, choices=(1, 22), required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--dry-repetitions', type=int, choices=(1, 2), default=1)
    parser.add_argument('--order', choices=('A1', 'B1', 'B2', 'A2'), default='A1')
    parser.add_argument('--quiet-host-confirmed', action='store_true')
    parser.add_argument('--diagnostic-jfr-dir', type=Path)
    args = parser.parse_args()
    if not args.dry_run and not args.quiet_host_confirmed:
        parser.error('Measured trials require the main session to confirm a quiescent host first')
    root = args.root.resolve()
    revision, production = source(root, args.mode)
    output = args.output.resolve()
    if output.exists():
        raise ValueError('A benchmark must use a new output path')
    properties = {
        'fabric-api.gametest.filter': 'semion-td-gametest:combat_speed_benchmark_test*',
        'semiontd.benchmark.enabled': 'true',
        'semiontd.benchmark.mode': args.mode,
        'semiontd.benchmark.sourceRevision': revision,
        'semiontd.benchmark.productionRevision': production,
        'semiontd.benchmark.lanes': str(args.lanes),
        'semiontd.benchmark.warmups': str(args.dry_repetitions - 1) if args.dry_run else '3',
        'semiontd.benchmark.measured': '1' if args.dry_run else '5',
        'semiontd.benchmark.dry': str(args.dry_run).lower(),
        'semiontd.benchmark.order': args.order,
        'semiontd.benchmark.output': output.as_posix(),
    }
    if args.diagnostic_jfr_dir:
        diagnostic = args.diagnostic_jfr_dir.resolve()
        if diagnostic.exists():
            raise ValueError('A diagnostic must use a new JFR directory')
        properties['semiontd.benchmark.diagnosticJfrDirectory'] = diagnostic.as_posix()
    environment = dict(os.environ)
    inherited = environment.get('JAVA_TOOL_OPTIONS', '')
    if 'semiontd.benchmark.' in inherited or 'fabric-api.gametest.filter' in inherited:
        raise ValueError('Remove conflicting benchmark JAVA_TOOL_OPTIONS')
    environment['JAVA_TOOL_OPTIONS'] = (inherited + ' ' + ' '.join(f'"-D{key}={value}"' for key, value in properties.items())).strip()
    rtk = shutil.which('rtk') or str(Path.home() / '.local/bin/rtk.exe')
    result = subprocess.run([rtk, 'proxy', str(root / 'gradlew.bat'), 'runGameTest', '--console=plain', '--no-daemon'],
                            cwd=root, env=environment)
    if result.returncode:
        return result.returncode
    if not output.is_file():
        raise RuntimeError('No complete benchmark output was generated')
    benchmark = json.loads(output.read_text(encoding='utf-8'))
    if len(benchmark['trials']) != benchmark['warmup_trials'] + benchmark['measured_trials']:
        raise ValueError('Benchmark output omitted repetitions')
    print(f'Benchmark output: {output}; source={revision}; production={production}; dry={args.dry_run}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

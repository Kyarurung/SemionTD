import argparse
import os
from pathlib import Path
import shutil
import subprocess

REFERENCE = '7a389bf04a5a81a5d1a84c2c36fcf1beb5bafe13'
PRODUCTION_PATHS = ('src/main', 'compat', 'build.gradle', 'gradle.properties', 'settings.gradle')


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()


def verify_source(root, mode):
    if not (root / 'gradlew.bat').is_file() or not (root / 'src/main/java/kim/biryeong/semiontd').is_dir():
        raise ValueError('The intended SemionTD repository root is required')
    if git(root, 'status', '--porcelain', '--', *PRODUCTION_PATHS):
        raise ValueError('Commit production/dependency changes before assigning a capture source_revision')
    if mode == 'native':
        if git(root, 'diff', '--name-only', REFERENCE, '--', *PRODUCTION_PATHS):
            raise ValueError('Native capture production and dependencies must match pinned revision 7a389bf0')
        return REFERENCE
    for name in ('CombatSimulationRuntime', 'CombatSimulationSession'):
        path = f'src/main/java/kim/biryeong/semiontd/game/simulation/{name}.java'
        if not git(root, 'ls-files', '--', path):
            raise ValueError(f'Candidate requires committed {name}')
    return git(root, 'rev-parse', 'HEAD')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=('native', 'simulation'))
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    revision = verify_source(root, args.mode)
    output = (args.output or root / 'build/replay-analysis' /
              ('native40.json' if args.mode == 'native' else 'simulation20.json')).resolve()
    if output.exists():
        raise ValueError('Use a new output path to avoid confusing an old capture with this run')
    options = {
        'fabric-api.gametest.filter': 'semion-td-gametest:replay_opening_capture_test*',
        'semiontd.replay.capture': args.mode,
        'semiontd.replay.sourceRevision': revision,
        'semiontd.replay.output': output.as_posix(),
    }
    env = dict(os.environ)
    inherited = env.get('JAVA_TOOL_OPTIONS', '')
    if 'semiontd.replay.' in inherited or 'fabric-api.gametest.filter' in inherited:
        raise ValueError('Remove conflicting replay/filter JAVA_TOOL_OPTIONS before launching')
    env['JAVA_TOOL_OPTIONS'] = (inherited + ' ' + ' '.join(f'"-D{key}={value}"' for key, value in options.items())).strip()
    rtk = shutil.which('rtk') or str(Path.home() / '.local/bin/rtk.exe')
    command = [rtk, 'proxy', str(root / 'gradlew.bat'), 'runGameTest', '--console=plain', '--no-daemon']
    result = subprocess.run(command, cwd=root, env=env)
    if result.returncode:
        return result.returncode
    if not output.is_file():
        raise RuntimeError('GameTest did not produce a complete capture; no parity conclusion is available')
    print(f'Capture: {output}; verified production revision: {revision}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

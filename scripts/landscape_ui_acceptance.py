"""Native UI checks on the dedicated preview emulator only. Never uninstalls or clears data."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

PACKAGE = 'cn.walkpaint.dev'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--adb', required=True, type=Path)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--test-apk', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--build-info', type=Path, help='CI build-info.json from the exact downloaded artifact')
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        raise SystemExit('Only a dedicated emulator serial is allowed')

    def adb(*parts, timeout=120):
        result = subprocess.run([str(args.adb), '-s', args.serial, *map(str, parts)],
                                capture_output=True, text=True, timeout=timeout)
        if result.returncode:
            raise RuntimeError(result.stderr[-2000:] or result.stdout[-2000:])
        return result.stdout

    avd = adb('emu', 'avd', 'name').splitlines()[0].strip()
    if not avd.startswith('WalkPaint_UI_Preview'):
        raise SystemExit('Refusing an emulator not owned by this preview task: ' + avd)
    args.out.mkdir(parents=True, exist_ok=False)
    source = json.loads(args.build_info.read_text()) if args.build_info else None
    if source and (source.get('package') != PACKAGE or not re.fullmatch('[a-f0-9]{40}', source.get('source_sha', ''))):
        raise SystemExit('Invalid artifact build identity')
    report = {'source_sha': source['source_sha'] if source else None,
              'build_identity': 'CI artifact metadata' if source else 'local worktree build, source commit not established',
              'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
              'package': PACKAGE, 'avd': avd, 'serial': args.serial, 'cases': [],
              'physical_device': 'not_verified', 'amap_network': 'not_verified'}
    def save_diagnostics(name):
        # Dedicated empty-data emulator only. Preserve a crash even when instrumentation dies.
        for label, parts in [('crash', ('logcat', '-b', 'crash', '-d')),
                             ('runtime', ('logcat', '-d', '-s', 'AndroidRuntime'))]:
            try:
                (args.out / (name + '-' + label + '.txt')).write_text(adb(*parts))
            except Exception as error:
                (args.out / (name + '-' + label + '-error.txt')).write_text(str(error))
        shot = subprocess.run([str(args.adb), '-s', args.serial, 'exec-out', 'screencap', '-p'],
                              capture_output=True, timeout=30)
        if shot.returncode == 0 and shot.stdout.startswith(b'\x89PNG'):
            (args.out / (name + '-screen.png')).write_bytes(shot.stdout)

    original_size = adb('shell', 'wm', 'size')
    original_density = adb('shell', 'wm', 'density')
    original_font = adb('shell', 'settings', 'get', 'system', 'font_scale').strip()
    try:
        for path in (args.apk, args.test_apk):
            if 'Success' not in adb('install', '-r', path, timeout=300):
                raise RuntimeError('Install did not confirm success')
        # Check ordinary launcher startup independently of the test runner.
        (args.out / 'launcher.txt').write_text(adb('shell', 'am', 'start', '-W',
            '-n', PACKAGE + '/cn.walkpaint.MainActivity'))
        import time
        time.sleep(4)
        save_diagnostics('launcher')
        for name, size, density, font in [('normal', '1080x2400', '420', '1.0'),
                                           ('narrow-large', '960x2160', '480', '1.5')]:
            adb('shell', 'wm', 'size', size)
            adb('shell', 'wm', 'density', density)
            adb('shell', 'settings', 'put', 'system', 'font_scale', font)
            adb('shell', 'am', 'force-stop', PACKAGE)
            result = adb('shell', 'am', 'instrument', '-w', '-e', 'landscapeProbe', 'true',
                         PACKAGE + '.test/cn.walkpaint.RoadAcceptanceInstrumentation', timeout=600)
            (args.out / (name + '.txt')).write_text(result)
            save_diagnostics(name)
            passed = 'LANDSCAPE_UI_PASS:' in result and 'LANDSCAPE_UI_FAIL:' not in result
            case = {'name': name, 'viewport': size, 'density': density, 'font_scale': font,
                    'passed': passed}
            report['cases'].append(case)
            screenshots = args.out / name
            screenshots.mkdir()
            adb('pull', '/sdcard/Android/data/' + PACKAGE + '/files/.', screenshots)
            if not passed:
                raise RuntimeError('Native acceptance failed: ' + name + '\n' + result[-3000:])
    finally:
        save_diagnostics('final')
        override = re.search(r'Override size: (\d+x\d+)', original_size)
        adb('shell', 'wm', 'size', override.group(1) if override else 'reset')
        override = re.search(r'Override density: (\d+)', original_density)
        adb('shell', 'wm', 'density', override.group(1) if override else 'reset')
        if original_font == 'null':
            adb('shell', 'settings', 'delete', 'system', 'font_scale')
        else:
            adb('shell', 'settings', 'put', 'system', 'font_scale', original_font)
        (args.out / 'report.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()

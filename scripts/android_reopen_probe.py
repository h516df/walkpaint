"""Repeated re-entry timing for one WalkPaint build, on the dedicated Google AVD only.

The full acceptance pass measures re-entry once, and the reference emulator is noisy enough that a
single sample proves little. This focuses on the one user-visible number — cold start with the map
screen already holding a few thousand points — and repeats it, on a freshly booted AVD, for one
build at a time.

Usage: python -X utf8 scripts/android_reopen_probe.py <label> <apk> [repeats]
"""
import argparse, hashlib, json, re, sqlite3, subprocess, sys, tempfile, time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from google_avd_guard import require_walkpaint_google_avd  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
ADB = r'D:\Apps\Android\sdk\platform-tools\adb.exe'
SERIAL = 'emulator-5556'
PKG = 'cn.walkpaint'
TEST_PKG = 'cn.walkpaint.test'
RUNNER = 'cn.walkpaint.RoadAcceptanceInstrumentation'

parser = argparse.ArgumentParser()
parser.add_argument('label')
parser.add_argument('apk')
parser.add_argument('repeats', nargs='?', type=int, default=3)
args = parser.parse_args()

require_walkpaint_google_avd(ADB, SERIAL)
base = [ADB, '-s', SERIAL]
out = ROOT / 'verification' / 'fog-line' / (args.label + '-reopen')
out.mkdir(parents=True, exist_ok=True)
checks = {'label': args.label, 'failures': []}


def run(*cmd, binary=False, timeout=180):
    with tempfile.TemporaryFile() as out_file, tempfile.TemporaryFile() as err_file:
        process = subprocess.Popen(base + list(cmd), stdout=out_file, stderr=err_file)
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=15)
            raise RuntimeError('timed out: ' + ' '.join(cmd))
        out_file.seek(0)
        err_file.seek(0)
        data, err = out_file.read(), err_file.read()
    if code:
        raise RuntimeError((data + err).decode(errors='replace')[-500:])
    return data if binary else data.decode(errors='replace')


def sh(*cmd, **kwargs):
    return run('shell', *cmd, **kwargs)


def points():
    raw = run('exec-out', 'run-as', PKG, 'cat', 'databases/footprints.db', binary=True)
    db = sqlite3.connect(':memory:')
    db.deserialize(raw)
    count = db.execute('SELECT COUNT(*) FROM points').fetchone()[0]
    db.close()
    return count


try:
    apk = (ROOT / args.apk).resolve()
    checks['apk_sha256'] = hashlib.sha256(apk.read_bytes()).hexdigest()
    assert 'Success' in run('install', '-r', '-d', str(apk), timeout=240)
    assert 'Success' in run('install', '-r', '-d',
        str(ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'), timeout=240)
    checks['installed_apk_sha256'] = hashlib.sha256(
        run('exec-out', 'cat', sh('pm', 'path', PKG).split('package:')[1].strip(), binary=True)).hexdigest()
    if checks['installed_apk_sha256'] != checks['apk_sha256']:
        raise AssertionError('installed package is not the file that was built')
    seeded = sh('am', 'instrument', '-w', '-e', 'fogTrailProbe', 'true', '-e', 'fogTrailReset', 'true',
                TEST_PKG + '/' + RUNNER, timeout=240)
    if 'FOG_TRAIL_PROBE_PASS' not in seeded:
        raise AssertionError('fixture probe failed: ' + seeded[-200:])
    checks['fixture_points'] = points()
    if checks['fixture_points'] < 3000:
        raise AssertionError('fixture too small: %d' % checks['fixture_points'])
    checks['version_code'] = int(re.search(r'versionCode=(\d+)', sh('dumpsys', 'package', PKG)).group(1))

    samples = []
    for attempt in range(args.repeats):
        sh('am', 'force-stop', PKG)
        time.sleep(3)
        result = sh('am', 'start', '-W', '-n', PKG + '/.MainActivity')
        time.sleep(5)
        match = re.search(r'TotalTime:\s*(\d+)', result) or re.search(r'WaitTime:\s*(\d+)', result)
        if not match:
            raise AssertionError('no launch timing: ' + result[-200:])
        samples.append(int(match.group(1)))
    checks['reopen_ms'] = samples
    checks['reopen_median_ms'] = sorted(samples)[len(samples) // 2]
    checks['reopen_best_ms'] = min(samples)
except Exception as exc:  # noqa: BLE001
    checks['failures'].append(str(exc))
finally:
    sh('am', 'force-stop', PKG)
    checks['passed'] = not checks['failures']
    out.joinpath('probe.json').write_text(json.dumps(checks, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(checks, ensure_ascii=False))
    if not checks['passed']:
        sys.exit(1)

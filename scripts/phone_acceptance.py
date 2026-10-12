"""Historical phone acceptance helper; never run automatically without explicit phone authorization.
Dedicated AVD ARM64 native-bridge evidence is supported by amap_runtime_evidence.py.
Phone OEM, real location and owner account acceptance remain separate. See docs/evidence/V043.md.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from veil_measure import classify_region, VEIL_PRESENTED_RATIO  # noqa: E402

WORKTREE = Path(__file__).resolve().parent.parent
SDK = Path(r'D:\Apps\Android\sdk')
ADB = SDK / 'platform-tools' / 'adb.exe'
PACKAGE = 'cn.walkpaint'
ACTIVITY = '.MainActivity'
EXPORT_EXTRA = 'cn.walkpaint.extra.EXPORT_FIELD_REPORT'
# The exported report is the phone's own account of its engine; these are the lines that decide
# whether this run can be called an AMap acceptance at all.
REQUIRED_REPORT_MARKERS = ('引擎判定=', '地图引擎：', '雾位图=', '透出地面=')

PROBLEMS: list[str] = []
NOTES: list[str] = []


def note(text: str) -> None:
    NOTES.append(text)
    print('  ' + text, flush=True)


def problem(text: str) -> None:
    PROBLEMS.append(text)
    print('  PROBLEM: ' + text, flush=True)


def adb(serial: str, *args, binary: bool = False, timeout: int = 120):
    cmd = [str(ADB), '-s', serial, *[str(a) for a in args]]
    done = subprocess.run(cmd, capture_output=True, timeout=timeout)
    if binary:
        return done.stdout
    return done.stdout.decode('utf-8', 'replace').strip()


def shell(serial: str, *args, **kw):
    return adb(serial, 'shell', *args, **kw)


def pick_serial(requested: str | None) -> str:
    out = subprocess.run([str(ADB), 'devices'], capture_output=True, text=True).stdout
    ready = [line.split()[0] for line in out.splitlines()[1:] if line.strip().endswith('device')]
    if requested:
        if requested not in ready:
            raise SystemExit('serial %s is not attached and ready; adb sees %s' % (requested, ready))
        return requested
    if not ready:
        raise SystemExit('no phone is attached. Enable developer options + USB debugging (or '
                         'wireless debugging) and confirm the pairing prompt on the phone screen.')
    if len(ready) > 1:
        raise SystemExit('more than one device is ready (%s); choose one with --serial' % ready)
    return ready[0]


def apk_abis(apk: Path) -> list[str]:
    """Which ABIs the artifact actually carries, read from the zip rather than from Gradle."""
    import zipfile
    with zipfile.ZipFile(apk) as zf:
        return sorted({name.split('/')[1] for name in zf.namelist() if name.startswith('lib/')})


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--serial', default=None, help='adb serial; inferred when exactly one is ready')
    parser.add_argument('--apk', default=None, help='APK to install (arm64 or universal)')
    parser.add_argument('--skip-install', action='store_true', help='accept the build already on the phone')
    parser.add_argument('--stamp', default=None, help='evidence folder name suffix')
    parser.add_argument('--settle-s', type=float, default=12.0, help='seconds to let the map draw')
    args = parser.parse_args()

    serial = pick_serial(args.serial)
    safe_serial = re.sub(r'[^A-Za-z0-9]+', '-', serial)
    stamp = args.stamp or time.strftime('%Y%m%d-%H%M%S')
    out = WORKTREE / 'verification' / ('phone-%s-%s' % (safe_serial, stamp))
    out.mkdir(parents=True, exist_ok=True)
    print('evidence -> %s' % out, flush=True)

    # ---- the device itself -------------------------------------------------------------------
    abilist = shell(serial, 'getprop', 'ro.product.cpu.abilist')
    model = shell(serial, 'getprop', 'ro.product.model')
    brand = shell(serial, 'getprop', 'ro.product.brand')
    sdk = shell(serial, 'getprop', 'ro.build.version.sdk')
    (out / 'device.txt').write_text(
        'serial=%s\nbrand=%s\nmodel=%s\nsdk=%s\nabilist=%s\n' % (serial, brand, model, sdk, abilist),
        encoding='utf-8')
    note('phone: %s %s / Android %s / abilist=%s' % (brand, model, sdk, abilist))
    arm = 'arm64-v8a' in abilist.split(',')
    if not arm:
        problem('this phone is not arm64 (%s), so it cannot run the AMap SDK either; any result '
                'here describes the fallback map, not the engine the complaint is about' % abilist)
        json.dump({'verdict': 'not_arm64', 'abilist': abilist, 'problems': PROBLEMS},
                  open(out / 'verdict.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
        return 2

    # ---- the artifact ------------------------------------------------------------------------
    installed_version = None
    if not args.skip_install:
        if not args.apk:
            raise SystemExit('--apk is required unless --skip-install is given')
        apk = Path(args.apk)
        if not apk.is_file():
            raise SystemExit('APK not found: %s' % apk)
        abis = apk_abis(apk)
        (out / 'apk.txt').write_text('apk=%s\nabis=%s\n' % (apk, abis), encoding='utf-8')
        note('artifact carries ABIs: %s' % abis)
        if 'arm64-v8a' not in abis:
            raise SystemExit('this APK has no arm64-v8a code; it cannot be an AMap acceptance')
        note('installing (this replaces the installed build, data is kept)')
        result = subprocess.run([str(ADB), '-s', serial, 'install', '-r', str(apk)],
                                capture_output=True, text=True, timeout=600)
        (out / 'install.txt').write_text(result.stdout + result.stderr, encoding='utf-8')
        if result.returncode != 0 or 'Success' not in result.stdout:
            problem('adb install failed: %s%s' % (result.stdout.strip(), result.stderr.strip()))
            json.dump({'verdict': 'install_failed', 'problems': PROBLEMS},
                      open(out / 'verdict.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
            return 3

    # ---- permissions the record cannot work without -------------------------------------------
    for permission in ('android.permission.ACCESS_FINE_LOCATION',
                       'android.permission.ACCESS_COARSE_LOCATION',
                       'android.permission.ACCESS_BACKGROUND_LOCATION',
                       'android.permission.POST_NOTIFICATIONS'):
        done = shell(serial, 'pm', 'grant', PACKAGE, permission)
        if 'Exception' in done or 'not a changeable permission' in done:
            note('grant %s: %s' % (permission.split('.')[-1], done.splitlines()[0] if done else 'ok'))

    dumped = shell(serial, 'dumpsys', 'package', PACKAGE)
    version = re.search(r'versionName=(\S+)', dumped)
    code = re.search(r'versionCode=(\d+)', dumped)
    installed_version = '%s (%s)' % (version.group(1) if version else '?',
                                     code.group(1) if code else '?')
    note('installed: %s' % installed_version)

    # ---- let it run, then look at the screen --------------------------------------------------
    shell(serial, 'am', 'force-stop', PACKAGE)
    shell(serial, 'am', 'start', '-W', '-n', '%s/%s' % (PACKAGE, ACTIVITY), timeout=180)
    time.sleep(args.settle_s)
    (out / 'screen.png').write_bytes(adb(serial, 'exec-out', 'screencap', '-p', binary=True))

    from PIL import Image
    image = Image.open(out / 'screen.png').convert('RGB')
    width, height = image.size
    # A proportional crop, because a real phone is not the emulator's 1080x2400: keep the map and
    # drop the top bar and the bottom navigation, wherever they are on this screen.
    region = image.crop((0, int(height * 0.12), width, int(height * 0.88))).resize((216, 360))
    stats = classify_region(region.getdata())
    note('screen %dx%d fog: veil=%s bare_map=%s tint=%s'
         % (width, height, stats['veil_ratio'], stats['bare_map_ratio'], stats['tint_ratio']))
    (out / 'screen_stats.json').write_text(json.dumps(stats, indent=2), encoding='utf-8')

    # ---- the phone's own account of its engine -------------------------------------------------
    report_text = None
    report_path = None
    for attempt in range(1, 7):
        shell(serial, 'logcat', '-c')
        shell(serial, 'am', 'start', '-n', '%s/%s' % (PACKAGE, ACTIVITY),
              '--ez', EXPORT_EXTRA, 'true', timeout=180)
        time.sleep(4)
        log = shell(serial, 'logcat', '-d', '-s', 'WalkPaintField:I')
        match = re.search(r'field report written: (\S+)', log)
        if match:
            report_path = match.group(1)
            break
        note('no report written yet (attempt %d); the app may still have been busy' % attempt)
    if report_path:
        pulled = adb(serial, 'exec-out', 'cat', report_path, binary=True)
        if pulled:
            report_text = pulled.decode('utf-8', 'replace')
            (out / 'field-report.txt').write_text(report_text, encoding='utf-8')
            note('field report: %d characters from %s' % (len(report_text), report_path))
    if report_text is None:
        problem('the app never produced its field report; without it this run cannot say which '
                'engine ran, which is the entire point of the phone lane')
    else:
        missing = [m for m in REQUIRED_REPORT_MARKERS if m not in report_text]
        if missing:
            problem('the field report is missing %s, so this build cannot be judged' % missing)
        for line in report_text.splitlines():
            if line.startswith(('地图引擎：', '引擎判定=', '高德=', '备用地图=', '雾帧=', '雾位图=')):
                note('report | ' + line)
        if '引擎判定=正在用高德' not in report_text:
            problem('the phone did NOT run the AMap engine on this launch; the fog result above '
                    'describes the fallback map. AMap needs the ARM build, a configured key, and '
                    'the person\'s one-time consent ("是否使用高德地图？" -> 同意)')
        elif '地面图层没有' in report_text:
            problem('AMap is running but never created the ground overlay, so no veil could be '
                    'drawn at all; the report says which step was missing')

    # ---- verdict ------------------------------------------------------------------------------
    if stats['veil_ratio'] < VEIL_PRESENTED_RATIO:
        problem('the screen is not showing fog (veil_ratio %s < %s): unknown ground is readable'
                % (stats['veil_ratio'], VEIL_PRESENTED_RATIO))
    verdict = 'pass' if not PROBLEMS else 'fail'
    summary = {
        'verdict': verdict,
        'serial': serial,
        'device': '%s %s' % (brand, model),
        'android': sdk,
        'abilist': abilist,
        'installed': installed_version,
        'amap_active': bool(report_text and '引擎判定=正在用高德' in report_text),
        'screen_stats': stats,
        'report_path': report_path,
        'report_lines': (report_text or '').count('\n') + 1 if report_text else 0,
        'problems': PROBLEMS,
        'notes': NOTES,
    }
    (out / 'verdict.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2),
                                      encoding='utf-8')
    print('\nVERDICT: %s  (%d problem(s))' % (verdict, len(PROBLEMS)), flush=True)
    for p in PROBLEMS:
        print('  - ' + p, flush=True)
    print('evidence: %s' % out, flush=True)
    return 0 if verdict == 'pass' else 4


if __name__ == '__main__':
    sys.exit(main())

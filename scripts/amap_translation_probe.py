"""Does the AMap SDK really load and draw on this x86_64 emulator, through its ARM bridge?

This is the shortest honest answer to "your tests and the real phone are different". The earlier
conclusion — that no machine here can run AMap — was half wrong, and the half that was wrong is the
half that matters:

  * an arm64 *system image* is refused outright on an x86_64 host
    (`FATAL | Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host.`);
  * but the x86_64 API 30+ images ship an ARM native bridge, so an **arm64-only APK installs and its
    ARM libraries are executed through NDK translation**.

What blocked the AMap path here was therefore the app's own gate, `AmapPlatform.supported()`, which
asked only about `SUPPORTED_ABIS.first()` (`x86_64` on this AVD). That is now fixed, and this script
refuses to accept the fix on faith. Every link has to hold, from the outside in:

  1. the *published candidate* APK is arm64-only                       (otherwise the x86 libs are chosen)
  2. the installed package reports primaryCpuAbi=arm64-v8a             (the process really is arm64)
  3. /proc/<pid>/maps contains libAMapSDK_MAP_*.so                     (the SDK is really mapped in)
  4. the app's own export says 引擎判定=正在用高德 and 地面图层…有      (the SDK really got used)
  5. a screenshot exists, and its APK hash is recorded beside it       (bound to this build)

A universal APK on this device picks x86_64 for itself and then AMap is simply absent, so this
script installs the arm64-only artefact and says so. It never treats a UI label, a config flag or a
MapLibre fallback as evidence of AMap.

Usage:
    python -X utf8 scripts/amap_translation_probe.py --apk app/build/outputs/apk/debug/app-debug.apk
    python -X utf8 scripts/amap_translation_probe.py --apk ... --serial emulator-5556 --out verification/amap-translation
Exit code 0 only when links 1-5 all hold.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import time
import zipfile
from pathlib import Path

WORKTREE = Path(__file__).resolve().parent.parent
ADB = Path(r'D:\Apps\Android\sdk\platform-tools\adb.exe')
PACKAGE = 'cn.walkpaint'
ACTIVITY = '.MainActivity'
EXPORT_EXTRA = 'cn.walkpaint.extra.EXPORT_FIELD_REPORT'
DEDICATED_AVD = 'WalkPaint_Google_API36'
PREFS = '/data/user/0/cn.walkpaint/shared_prefs/ui_onboarding.xml'
# The datum shift around Shanghai is roughly 500 m. A tolerance above that would accept a missing
# conversion, and one below it would fail a correct one, so it sits between: the viewport centre
# must land on the fixture, and a raw-coordinate leak lands half a kilometre away.
CHINA_FIXTURE_TOLERANCE_M = 400.0
# The overlay is placed from the frame, so it should land on the fixture's display datum to within
# the frame's own padding; 120 m is far tighter than the ~500 m datum shift and far looser than GPS.
OVERLAY_DATUM_TOLERANCE_M = 120.0

PROBLEMS: list[str] = []
NOTES: list[str] = []
# Things this run could not measure, as opposed to things that were measured and are wrong. They
# exit non-zero too, but they must never be reported as a failing app: that distinction is the
# difference between "the fog is broken" and "the checker did not get far enough to say".
BLOCKED: list[str] = []


def metres(a, b) -> float:
    """Great-circle distance in metres between two (lat, lon) pairs."""
    import math
    lat1, lon1 = a
    lat2, lon2 = b
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = p2 - p1
    dl = math.radians(lon2 - lon1)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * 6371008.8 * math.asin(math.sqrt(h))


def gcj_forward(lat: float, lon: float):
    """WGS84 -> GCJ-02, the published algorithm, recomputed here as an independent reference.

    Deliberately a second implementation rather than a call into the app: the question this answers
    is "is the shipped artifact's frame where the datum says it should be", and a checker that
    borrowed the app's own transform could not answer it.
    """
    import math
    if not (72.004 <= lon <= 137.8347 and 0.8293 <= lat <= 55.8271):
        return lat, lon
    a, ee = 6378245.0, 0.00669342162296594323
    pi = math.pi
    x, y = lon - 105.0, lat - 35.0
    dlat = (-100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * math.sqrt(abs(x))
            + (20.0 * math.sin(6.0 * x * pi) + 20.0 * math.sin(2.0 * x * pi)) * 2.0 / 3.0
            + (20.0 * math.sin(y * pi) + 40.0 * math.sin(y / 3.0 * pi)) * 2.0 / 3.0
            + (160.0 * math.sin(y / 12.0 * pi) + 320.0 * math.sin(y * pi / 30.0)) * 2.0 / 3.0)
    dlon = (300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * math.sqrt(abs(x))
            + (20.0 * math.sin(6.0 * x * pi) + 20.0 * math.sin(2.0 * x * pi)) * 2.0 / 3.0
            + (20.0 * math.sin(x * pi) + 40.0 * math.sin(x / 3.0 * pi)) * 2.0 / 3.0
            + (150.0 * math.sin(x / 12.0 * pi) + 300.0 * math.sin(x / 30.0 * pi)) * 2.0 / 3.0)
    rad = lat / 180.0 * pi
    magic = 1 - ee * math.sin(rad) ** 2
    return (lat + (dlat * 180.0) / ((a * (1 - ee)) / (magic * math.sqrt(magic)) * pi),
            lon + (dlon * 180.0) / (a / math.sqrt(magic) * math.cos(rad) * pi))


def note(text: str) -> None:
    NOTES.append(text)
    print('  ' + text, flush=True)


def problem(text: str) -> None:
    PROBLEMS.append(text)
    print('  PROBLEM: ' + text, flush=True)


def adb(serial: str, *args, binary: bool = False, timeout: int = 180):
    done = subprocess.run([str(ADB), '-s', serial, *[str(a) for a in args]],
                          capture_output=True, timeout=timeout)
    return done.stdout if binary else done.stdout.decode('utf-8', 'replace').strip()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--apk', required=True, help='the arm64-only candidate APK')
    parser.add_argument('--serial', default='emulator-5556')
    parser.add_argument('--out', default='verification/amap-translation')
    parser.add_argument('--settle-s', type=float, default=20.0)
    parser.add_argument('--china-lat', type=float, default=31.2304,
                        help='anonymous fixture latitude for the datum check (0 disables it)')
    parser.add_argument('--china-lon', type=float, default=121.4737,
                        help='anonymous fixture longitude for the datum check (0 disables it)')
    parser.add_argument('--debug-consent-apk', default=None,
                        help='an arm64 *debuggable* build of the same app, installed first only so '
                             'the AMap consent switch can be written with run-as. A release build '
                             'is deliberately not debuggable, so its own consent can only be given '
                             'through the UI, which a person does on their own phone. The candidate '
                             'APK is then installed over it with -r, keeping that data.')
    parser.add_argument('--keep-data', action='store_true',
                        help='do not uninstall before installing the candidate')
    args = parser.parse_args()

    serial = args.serial
    apk = Path(args.apk).resolve()
    out = (WORKTREE / args.out) if not Path(args.out).is_absolute() else Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    if not apk.is_file():
        raise SystemExit('APK not found: %s' % apk)
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    print('evidence -> %s\ncandidate -> %s\nsha256    -> %s' % (out, apk.name, digest), flush=True)

    avd = adb(serial, 'shell', 'getprop', 'ro.boot.qemu.avd_name')
    if avd != DEDICATED_AVD:
        # The project keeps one dedicated AVD for acceptance; probing someone's own emulator would
        # put this evidence next to an environment nobody verified.
        raise SystemExit('refusing: %s is not the dedicated acceptance AVD (got %r)' % (serial, avd))
    note('device is the dedicated AVD %s' % avd)

    abilist = adb(serial, 'shell', 'getprop', 'ro.product.cpu.abilist')
    bridge = adb(serial, 'shell', 'getprop', 'ro.enable.native.bridge.exec')
    note('abilist=%s  native.bridge.exec=%s' % (abilist, bridge))
    (out / 'device.txt').write_text(
        'serial=%s\navd=%s\nabilist=%s\nnative_bridge_exec=%s\napk=%s\napk_sha256=%s\n'
        % (serial, avd, abilist, bridge, apk, digest), encoding='utf-8')

    # ---- link 1: the artefact itself carries ARM only ----------------------------------------
    with zipfile.ZipFile(apk) as zf:
        abis = sorted({n.split('/')[1] for n in zf.namelist() if n.startswith('lib/')})
        amap_libs = sorted({n.split('/')[-1] for n in zf.namelist()
                            if n.startswith('lib/arm64-v8a/') and 'AMap' in n})
    note('candidate ABIs=%s  AMap libs=%s' % (abis, amap_libs))
    if abis != ['arm64-v8a']:
        problem('the candidate is not arm64-only (%s); on this device a universal APK selects '
                'x86_64 for itself and AMap is not installed at all' % abis)
    if not amap_libs:
        problem('the candidate carries no arm64 AMap library')

    # ---- install cleanly, then make the app willing to use AMap ------------------------------
    adb(serial, 'shell', 'am', 'force-stop', PACKAGE)
    if not args.keep_data:
        adb(serial, 'uninstall', PACKAGE)
    if args.debug_consent_apk:
        ready = subprocess.run([str(ADB), '-s', serial, 'install', '-r', str(args.debug_consent_apk)],
                              capture_output=True, text=True, timeout=600)
        note('debuggable build installed first for the consent switch: %s'
             % (ready.stdout.strip().splitlines() or ['?'])[-1])
        adb(serial, 'shell', 'pm', 'grant', PACKAGE, 'android.permission.ACCESS_FINE_LOCATION')
        adb(serial, 'shell', 'run-as', PACKAGE, 'mkdir', '-p', 'shared_prefs')
        prefs = ('<?xml version=\'1.0\' encoding=\'utf-8\' standalone=\'yes\' ?>\n<map>\n'
                 '    <boolean name="amap_sdk_consent" value="true" />\n'
                 '    <boolean name="amap_sdk_decision_seen" value="true" />\n'
                 '    <boolean name="auto_notice_v3_seen" value="true" />\n</map>\n')
        (out / 'prefs.xml').write_text(prefs, encoding='utf-8')
        subprocess.run([str(ADB), '-s', serial, 'push', str(out / 'prefs.xml'), '/data/local/tmp/wp.xml'],
                       capture_output=True, timeout=120)
        adb(serial, 'shell', 'run-as', PACKAGE, 'cp', '/data/local/tmp/wp.xml', PREFS)
        readback = adb(serial, 'shell', 'run-as', PACKAGE, 'cat', PREFS)
        note('AMap consent written before the candidate: %s'
             % ('yes' if 'amap_sdk_consent' in readback else 'NO'))
        # Auto-record on, so the map follows the person. Without it this app deliberately leaves the
        # camera wherever the person put it, and a checker that moves the fake fix would be
        # measuring a camera that never went there.
        auto = ('<?xml version=\'1.0\' encoding=\'utf-8\' standalone=\'yes\' ?>\n<map>\n'
                '    <boolean name="automatic_enabled" value="true" />\n</map>\n')
        (out / 'auto-record.xml').write_text(auto, encoding='utf-8')
        subprocess.run([str(ADB), '-s', serial, 'push', str(out / 'auto-record.xml'),
                        '/data/local/tmp/wp-auto.xml'], capture_output=True, timeout=120)
        adb(serial, 'shell', 'run-as', PACKAGE, 'cp', '/data/local/tmp/wp-auto.xml',
            '/data/user/0/cn.walkpaint/shared_prefs/footprint_preferences.xml')
        note('auto-record switch written: %s'
             % ('yes' if 'automatic_enabled' in adb(
                 serial, 'shell', 'run-as', PACKAGE, 'cat',
                 '/data/user/0/cn.walkpaint/shared_prefs/footprint_preferences.xml') else 'NO'))
        adb(serial, 'shell', 'pm', 'grant', PACKAGE, 'android.permission.ACCESS_BACKGROUND_LOCATION')
    installed = subprocess.run([str(ADB), '-s', serial, 'install', '-r', str(apk)],
                               capture_output=True, text=True, timeout=600)
    (out / 'install.txt').write_text(installed.stdout + installed.stderr, encoding='utf-8')
    note('install: %s' % (installed.stdout.strip().splitlines() or ['?'])[-1])
    if 'Success' not in installed.stdout:
        problem('install failed: %s%s' % (installed.stdout.strip(), installed.stderr.strip()))
        json.dump({'verdict': 'install_failed', 'problems': PROBLEMS},
                  open(out / 'verdict.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
        return 3

    # ---- link 2: did the installer really choose the ARM ABI? --------------------------------
    dumped = adb(serial, 'shell', 'dumpsys', 'package', PACKAGE)
    primary = re.search(r'primaryCpuAbi=(\S+)', dumped)
    secondary = re.search(r'secondaryCpuAbi=(\S+)', dumped)
    version = re.search(r'versionName=(\S+)', dumped)
    code = re.search(r'versionCode=(\d+)', dumped)
    note('installed %s (%s) primaryCpuAbi=%s secondary=%s'
         % (version.group(1) if version else '?', code.group(1) if code else '?',
            primary.group(1) if primary else 'none', secondary.group(1) if secondary else 'none'))
    if not primary or primary.group(1) != 'arm64-v8a':
        problem('primaryCpuAbi is %r, not arm64-v8a: the process is not running the ARM libraries, '
                'so any AMap result here would be meaningless'
                % (primary.group(1) if primary else None))

    for permission in ('android.permission.ACCESS_FINE_LOCATION',
                       'android.permission.ACCESS_COARSE_LOCATION',
                       'android.permission.POST_NOTIFICATIONS'):
        adb(serial, 'shell', 'pm', 'grant', PACKAGE, permission)

    # Consent is the person's switch, so the probe sets exactly the two keys the UI would set, and
    # only on this disposable dedicated AVD. A non-debuggable candidate cannot be written to with
    # run-as; for those the switch was already written through a debuggable build installed over the
    # same data (--debug-consent-apk), and the release artifact is installed after it with -r.
    if args.debug_consent_apk:
        note('consent switch was prepared by the debuggable build; the candidate is installed over it')
    else:
        prefs = ('<?xml version=\'1.0\' encoding=\'utf-8\' standalone=\'yes\' ?>\n<map>\n'
                 '    <boolean name="amap_sdk_consent" value="true" />\n'
                 '    <boolean name="amap_sdk_decision_seen" value="true" />\n</map>\n')
        (out / 'prefs.xml').write_text(prefs, encoding='utf-8')
        subprocess.run([str(ADB), '-s', serial, 'push', str(out / 'prefs.xml'), '/data/local/tmp/wp.xml'],
                       capture_output=True, timeout=120)
        adb(serial, 'shell', 'run-as', PACKAGE, 'mkdir', '-p', 'shared_prefs')
        copied = adb(serial, 'shell', 'run-as', PACKAGE, 'cp', '/data/local/tmp/wp.xml', PREFS)
        readback = adb(serial, 'shell', 'run-as', PACKAGE, 'cat', PREFS)
        note('AMap consent written: %s'
             % ('yes' if 'amap_sdk_consent' in readback else 'NO (%s)' % copied))

    # ---- launch it and look ---------------------------------------------------------------------
    adb(serial, 'shell', 'logcat', '-c')
    adb(serial, 'shell', 'am', 'start', '-W', '-n', '%s/%s' % (PACKAGE, ACTIVITY), timeout=240)
    time.sleep(args.settle_s)
    pid = adb(serial, 'shell', 'pidof', PACKAGE).split()[0] if adb(serial, 'shell', 'pidof', PACKAGE) else ''
    note('pid=%s' % (pid or 'not running'))
    if not pid:
        problem('the app is not running after launch (it exited or crashed)')

    # ---- link 3: did the platform really load the ARM AMap library? ---------------------------
    #
    # The first version of this check grepped /proc/<pid>/maps for "AMap" and reported "no AMap
    # library is mapped" while the library was demonstrably loaded. That was a measurement bug in
    # this probe, not a defect in the app: AMap is packaged uncompressed inside the APK, so the
    # loader maps it out of the APK zip and the maps entry names the *APK*, never the .so. The
    # loader's own line is the evidence:
    #   D nativeloader: Load /data/app/.../base.apk!/lib/arm64-v8a/libAMapSDK_MAP_v11_3_100.so
    log = adb(serial, 'shell', 'logcat', '-d')
    (out / 'logcat.txt').write_text(log, encoding='utf-8')
    loaded = re.findall(r'Load \S*lib(AMapSDK_MAP\w*|apssdk\w*)\.so', log)
    loaded = sorted(set(loaded))
    maps = adb(serial, 'shell', 'cat', '/proc/%s/maps' % pid) if pid else ''
    (out / 'maps-amap.txt').write_text(
        '\n'.join(line for line in maps.splitlines()
                  if 'base.apk' in line or 'ndk_translation' in line), encoding='utf-8')
    note('ARM libraries the loader reported: %s' % (loaded or 'none'))
    if pid and not loaded:
        problem('the platform never loaded the AMap ARM library into the process, so the SDK is '
                'not actually running even though it is installed')

    crash = [line for line in log.splitlines()
             if 'FATAL EXCEPTION' in line or 'UnsatisfiedLinkError' in line
             or 'dlopen failed' in line]
    if crash:
        problem('the log contains a load/crash failure: %s' % crash[:4])
    for line in log.splitlines():
        if 'nativeloader' in line and 'cn.walkpaint' in line:
            note('log | ' + line.strip()[:200])
        elif 'AMapException' in line or 'amap' in line.lower() and 'Exception' in line:
            note('log | ' + line.strip()[:200])

    (out / 'screen.png').write_bytes(adb(serial, 'exec-out', 'screencap', '-p', binary=True))

    # ---- link 4: what does the app itself say it is using? ------------------------------------
    def fetch_report(tag: str):
        """Asks the running app for its field export and returns the text, or None."""
        for _ in range(5):
            adb(serial, 'shell', 'logcat', '-c')
            adb(serial, 'shell', 'am', 'start', '-n', '%s/%s' % (PACKAGE, ACTIVITY),
                '--ez', EXPORT_EXTRA, 'true', timeout=240)
            time.sleep(5)
            found = re.search(r'field report written: (\S+)',
                              adb(serial, 'shell', 'logcat', '-d', '-s', 'WalkPaintField:I'))
            if found:
                text = adb(serial, 'exec-out', 'cat', found.group(1), binary=True)
                text = text.decode('utf-8', 'replace')
                (out / ('field-report%s.txt' % tag)).write_text(text, encoding='utf-8')
                return text
        return None

    report = fetch_report('')
    if report:
        for line in report.splitlines():
            if line.startswith(('地图引擎：', '引擎判定=', '高德=', '备用地图=', '雾帧=', '雾位图=')):
                note('report | ' + line)
        if '引擎判定=正在用高德' not in report:
            problem('the app did not actually run the AMap engine, so the screenshot describes the '
                    'MapLibre fallback')
        if re.search(r'地面图层没有', report):
            problem('AMap is active but no ground overlay was created, so no veil was drawn at all')
    else:
        problem('the app never produced its field report, so nothing here can say which engine ran')

    # ---- link 5: is the datum applied once, not zero times or twice? ---------------------------
    #
    # The fixture the project has used all along is in Paris, where the GCJ-02 offset is the
    # identity — so "the frame is in the wrong datum" could not show up in any local check. This
    # moves the fake fix into a Chinese city and reads two numbers out of the same export: the
    # viewport centre in WGS84 (what the veil is framed against) and the engine's own camera target
    # (which AMap reports in the datum it draws in). They must be *different* by roughly the datum
    # shift, and the WGS84 one must sit on the fixture. Zero difference means no conversion on
    # display; the WGS84 one being off by the shift means the conversion was applied twice.
    if args.china_lat not in (None, 0) and args.china_lon not in (None, 0):
        fixture = (args.china_lat, args.china_lon)
        note('moving the fake fix to the China fixture %.5f,%.5f' % fixture)
        # The emulator's own GPS feed is delivered to the app as a normal fix, but the shell has to
        # be allowed to drive it; the previous value is put back so this probe leaves the device as
        # it found it. Same mechanism the existing fog lane uses.
        appops = adb(serial, 'shell', 'appops', 'get', '2000', 'android:mock_location')
        original = re.search(r'(?:MOCK_LOCATION|mock_location):\s*(allow|ignore|deny|default|foreground)',
                            appops)
        adb(serial, 'shell', 'appops', 'set', '2000', 'android:mock_location', 'allow')
        try:
            adb(serial, 'shell', 'am', 'force-stop', PACKAGE)
            adb(serial, 'emu', 'geo', 'fix', str(args.china_lon), str(args.china_lat), '0', '5')
            adb(serial, 'shell', 'logcat', '-c')
            adb(serial, 'shell', 'am', 'start', '-W', '-n', '%s/%s' % (PACKAGE, ACTIVITY), timeout=240)
            time.sleep(args.settle_s)
            (out / 'screen-china.png').write_bytes(
                adb(serial, 'exec-out', 'screencap', '-p', binary=True))
            china = fetch_report('-china')
        finally:
            if original:
                adb(serial, 'shell', 'appops', 'set', '2000', 'android:mock_location', original.group(1))
        if not china:
            problem('no export after moving the fixture, so the datum check could not run')
        else:
            for line in china.splitlines():
                if line.startswith(('引擎判定=', '高德=', '可见中心')) or '可见中心' in line:
                    note('china report | ' + line)
            wgs = re.search(r'可见中心\(WGS84\)=(-?[\d.]+),(-?[\d.]+)', china)
            gcj = re.search(r'中心=(-?[\d.]+),(-?[\d.]+)', china)
            overlay = re.search(r'边界=(-?[\d.]+),(-?[\d.]+)~(-?[\d.]+),(-?[\d.]+)', china)
            if not wgs and not overlay:
                problem('the export carries neither a WGS84 viewport centre nor the ground '
                        'overlay bounds, so the datum cannot be judged at all')
            elif not gcj:
                problem('the engine reported no camera target, so the two datums cannot be compared')
            else:
                gcj_lat, gcj_lon = float(gcj.group(1)), float(gcj.group(2))
                if wgs:
                    # The build reports the viewport centre in WGS84 itself: compare it with the
                    # fixture and with the engine's own (display-datum) target.
                    wgs_lat, wgs_lon = float(wgs.group(1)), float(wgs.group(2))
                    to_fixture = metres(fixture, (wgs_lat, wgs_lon))
                    between = metres((wgs_lat, wgs_lon), (gcj_lat, gcj_lon))
                    note('WGS84 viewport centre is %.0f m from the fixture; the engine target '
                         'differs from it by %.0f m' % (to_fixture, between))
                    if to_fixture > CHINA_FIXTURE_TOLERANCE_M:
                        problem('the WGS84 viewport centre is %.0f m from the fixture (> %.0f m): '
                                'the frame is not placed on the ground the person is standing on'
                                % (to_fixture, CHINA_FIXTURE_TOLERANCE_M))
                    if between < 100.0:
                        problem('the engine target and the WGS84 centre are only %.0f m apart, so '
                                'no datum conversion reached the map in China' % between)
                    elif between > 900.0:
                        problem('the two centres are %.0f m apart, far more than a datum shift, '
                                'which is what a doubled conversion looks like' % between)
                elif overlay:
                    # The shipped export has no WGS84 centre line, so the check is made on the
                    # ground overlay's own bounds: where the veil was actually placed.
                    #
                    # Careful: the field report's contract is not fixed across builds. This probe was
                    # first calibrated against a build whose `中心=` printed the engine's raw target
                    # (the display datum, GCJ-02); the published v0.43 prints that same line converted
                    # back to WGS84 with fromAmap(). Comparing either value against the wrong datum
                    # produces a clean 481 m "failure" that is entirely the checker's assumption —
                    # which is what this run reported before this note existed. The datum the line is
                    # in is therefore detected, not assumed.
                    lat1, lon1, lat2, lon2 = (float(overlay.group(i)) for i in (1, 2, 3, 4))
                    centre = ((lat1 + lat2) / 2, (lon1 + lon2) / 2)
                    expected = gcj_forward(*fixture)
                    bounds_to_fixture = metres(centre, fixture)
                    camera_to_raw = metres((gcj_lat, gcj_lon), fixture)
                    camera_to_display = metres((gcj_lat, gcj_lon), expected)
                    datum = 'WGS84' if camera_to_raw < camera_to_display else 'display(GCJ-02)'
                    note('the report prints the engine centre in %s: %.0f m from the fixture and '
                         '%.0f m from its display datum; the overlay bounds centre is %.0f m from '
                         'the fixture' % (datum, camera_to_raw, camera_to_display, bounds_to_fixture))
                    if min(camera_to_raw, camera_to_display) > 2000.0:
                        # The map is not looking at the fixture at all, so nothing here describes the
                        # fixture's placement. This app only follows the person while recording, and
                        # the switch is written by the debuggable pre-install; if that did not take,
                        # the camera stays wherever it was. Not a failure, not a measurement.
                        blocked.append(
                            'the map is not on the fixture (engine centre is %.0f m away), so the '
                            'China datum placement was NOT measured on this artifact'
                            % min(camera_to_raw, camera_to_display))
                    elif bounds_to_fixture > CHINA_FIXTURE_TOLERANCE_M:
                        problem('the veil was placed %.0f m from the fixture itself (> %.0f m), which '
                                'no datum conversion can explain' % (
                                    bounds_to_fixture, CHINA_FIXTURE_TOLERANCE_M))
                    elif datum == 'display(GCJ-02)':
                        # The engine's own centre is at the display datum of the fixture: the
                        # conversion reached the map. This is the decisive, build-independent case.
                        note('the engine centre sits on the display datum, so the conversion did '
                             'reach the map')
                        if camera_to_display > OVERLAY_DATUM_TOLERANCE_M:
                            problem('the engine centre is %.0f m from the display datum of the '
                                    'fixture' % camera_to_display)
                    else:
                        blocked.append(
                            'this build prints the engine centre already converted back to WGS84, so '
                            'the numbers alone cannot show where the veil landed on the basemap; '
                            'the report contract changed between builds (fromAmap was added to '
                            'cameraSummary) and this check needs a raw display-datum value or a '
                            'renamed field to decide it')

    verdict = 'fail' if PROBLEMS else ('blocked' if BLOCKED else 'pass')
    json.dump({'verdict': verdict, 'apk': str(apk), 'apk_sha256': digest, 'serial': serial,
               'avd': avd, 'abilist': abilist, 'native_bridge_exec': bridge,
               'candidate_abis': abis, 'amap_libs_in_apk': amap_libs,
               'primary_cpu_abi': primary.group(1) if primary else None,
               'pid': pid, 'arm_libraries_loaded': loaded,
               'engine_amap': bool(report and '引擎判定=正在用高德' in report),
               'ground_overlay': bool(report and re.search(r'地面图层有', report)),
               'problems': PROBLEMS, 'not_measured': BLOCKED, 'notes': NOTES},
              open(out / 'verdict.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
    print('\nVERDICT: %s (%d problem(s), %d not measured)'
          % (verdict, len(PROBLEMS), len(BLOCKED)), flush=True)
    for item in PROBLEMS:
        print('  PROBLEM: ' + item, flush=True)
    for item in BLOCKED:
        print('  NOT MEASURED: ' + item, flush=True)
    print('evidence: %s' % out, flush=True)
    if verdict == 'fail':
        return 4
    if verdict == 'blocked':
        return 5
    return 0


if __name__ == '__main__':
    sys.exit(main())

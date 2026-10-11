"""Fog, trail and jank acceptance on the dedicated Google AVD only.

One fixed anonymous fixture, one fixed viewport, one fixed gesture script, run against both the
build being replaced and the candidate, so the three complaints are shown, fixed and then shown
again on the same scene:

* the veil no longer arrives as squares,
* no string of circles sits on the line and repeated passes do not braid,
* the map stays responsive: entering the screen and every state update are measured, not assumed.

Everything is fail-closed. A missing measurement, a zero frame count, a changed old row or an
installed package that is not the file we built all end the run with a non-zero exit, and the
reason is written into acceptance.json. The engine that actually ran is read back from the running
app and recorded: a pass on the fallback map is never reported as a pass on AMap.

Never targets a phone or the personal MuMu; refuses anything but emulator-5556 running the
dedicated WalkPaint_Google_API36 AVD. Restores the app-op and viewport it changed.
"""
import argparse, hashlib, json, re, sqlite3, subprocess, sys, tempfile, time
from pathlib import Path
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parent))
from google_avd_guard import require_walkpaint_google_avd  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
ADB = r'D:\Apps\Android\sdk\platform-tools\adb.exe'
SERIAL = 'emulator-5556'
PKG = 'cn.walkpaint'
TEST_PKG = 'cn.walkpaint.test'
RUNNER = 'cn.walkpaint.RoadAcceptanceInstrumentation'
FIXTURE_PREFIX = 'fogperf-fixture-v2'
TABLES = {
    'trips': ('id',),
    'points': ('id',),
    'roads': ('id',),
    'road_matches': ('point_id',),
    'coverage': ('road_id', 'start'),
}
PROVIDERS = ('高德联网地图', 'OpenFreeMap 联网备用地图', '离线足迹视图')
# Bounds come from the product requirement, not from whatever the emulator happened to report:
# * UI thread: a dropped frame at 60 Hz is 16.7 ms, so the median must stay well under that and even
#   the worst update must not block a drag for long. This is the jank criterion.
# * Veil repaint: it runs off the UI thread, so it cannot drop a frame; 30 ms every couple of seconds
#   is about one percent of a core, which is not what makes a map stutter. The worst case is a
#   pathology bound, because a shared emulator does occasionally stall a thread for a moment.
# * The whole-update numbers exclude the first update after launch (reported separately as
#   first_update_ms): that one pays for opening the database and warming the JIT, and the cold-start
#   measurement already covers it.
UPDATE_MED_MS_MAX = 400.0
UPDATE_MAX_MS_MAX = 1200.0
FIRST_UPDATE_MS_MAX = 6000.0
VEIL_INCREMENT_MED_MS_MAX = 30.0
VEIL_INCREMENT_MAX_MS_MAX = 250.0
VEIL_REBUILD_MAX_MS_MAX = 800.0
UI_MED_MS_MAX = 20.0
UI_MAX_MS_MAX = 120.0
UPLOAD_AVG_MS_MAX = 40.0
UPLOAD_MAX_MS_MAX = 250.0
# The reference AVD is shared and noisy, so the fastest of three launches is the number that says
# anything; a build that cannot start inside this window is not usable whatever the noise is.
COLD_START_BEST_MS_MAX = 12000
MIN_FIXTURE_POINTS = 3000
WALK_FIXES = 10
WALK_SPACING_S = 3.0

parser = argparse.ArgumentParser()
parser.add_argument('--apk', default='app/build/outputs/apk/debug/app-debug.apk')
parser.add_argument('--label', required=True, help='evidence folder, e.g. before / after')
parser.add_argument('--skip-install', action='store_true')
parser.add_argument('--skip-walk', action='store_true', help='do not inject extra fixes on top of the fixture')
parser.add_argument('--allow-missing-perf', action='store_true',
                    help='baseline runs only: the build being replaced has no counters at all')
parser.add_argument('--online-basemap', action='store_true',
                    help='turn the fallback basemap on so the veil is judged over real streets')
args = parser.parse_args()

require_walkpaint_google_avd(ADB, SERIAL)
base = [ADB, '-s', SERIAL]
out = ROOT / 'verification' / 'fog-line' / args.label
out.mkdir(parents=True, exist_ok=True)
checks = {'label': args.label, 'failures': []}


def run(*cmd, binary=False, timeout=90):
    """Runs one adb command with a hard timeout.

    Output goes to temporary files rather than pipes: on Windows a grandchild holding the pipe
    open makes the parent block forever even after the timeout fires, which is how one stuck
    `uiautomator dump` swallowed a whole acceptance run.
    """
    with tempfile.TemporaryFile() as out_file, tempfile.TemporaryFile() as err_file:
        process = subprocess.Popen(base + list(cmd), stdout=out_file, stderr=err_file)
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=15)
            raise RuntimeError('timed out after %ss: %s' % (timeout, ' '.join(cmd)))
        out_file.seek(0)
        err_file.seek(0)
        stdout = out_file.read()
        stderr = err_file.read()
    if code:
        raise RuntimeError((stdout + stderr).decode(errors='replace')[-700:])
    return stdout if binary else stdout.decode(errors='replace')


def sh(*cmd, **kwargs):
    return run('shell', *cmd, **kwargs)


def ui():
    """uiautomator occasionally finds the hierarchy busy; retry instead of losing the run."""
    last = None
    for attempt in range(3):
        try:
            sh('uiautomator', 'dump', '--compressed', '/sdcard/wp-fog-ui.xml', timeout=45)
            return ET.fromstring(run('exec-out', 'cat', '/sdcard/wp-fog-ui.xml', timeout=30))
        except Exception as exc:  # noqa: BLE001
            last = exc
            time.sleep(3)
    raise AssertionError('could not read the screen hierarchy: %s' % last)


def labels():
    return [n.get('text') for n in ui().iter('node') if n.get('text')]


def dismiss_system_dialogs():
    """A stressed emulator shows "System UI isn't responding"; that is the AVD, not the app."""
    try:
        text = [t for t in labels() if t]
    except Exception:  # noqa: BLE001
        return False
    if not any("isn't responding" in t or 'no response' in t.lower() for t in text):
        return False
    checks.setdefault('system_dialogs_dismissed', 0)
    for n in ui().iter('node'):
        if n.get('text') in ('Wait', '等待'):
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
            sh('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
            checks['system_dialogs_dismissed'] += 1
            time.sleep(2)
            return True
    return False


def tap(*texts, wait=1.0):
    for attempt in range(3):
        for n in ui().iter('node'):
            if any(v in texts for v in (n.get('text'), n.get('content-desc'))):
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
                sh('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(wait)
                return
        if not dismiss_system_dialogs():
            break
    raise AssertionError('missing control ' + str(texts) + '; visible=' + str(labels()))


def shot(name):
    out.joinpath(name + '.png').write_bytes(run('exec-out', 'screencap', '-p', binary=True))


def launch():
    sh('am', 'force-stop', PKG)
    result = sh('am', 'start', '-W', '-n', PKG + '/.MainActivity')
    time.sleep(4)
    match = re.search(r'TotalTime:\s*(\d+)', result)
    if match:
        return int(match.group(1))
    # A build slow enough to blow the ten second window reports WaitTime and no TotalTime: that is
    # a lower bound on the launch, and it is worth keeping rather than losing the whole run.
    wait = re.search(r'WaitTime:\s*(\d+)', result)
    if wait:
        checks.setdefault('cold_start_timeouts_ms', []).append(int(wait.group(1)))
        return int(wait.group(1))
    raise AssertionError('am start -W reported no timing at all: ' + result[-300:])


def db_bytes():
    return run('exec-out', 'run-as', PKG, 'cat', 'databases/footprints.db', binary=True)


def row_map():
    db = sqlite3.connect(':memory:')
    db.deserialize(db_bytes())
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    result = {}
    for table, keys in TABLES.items():
        columns = [row[1] for row in db.execute('PRAGMA table_info(' + table + ')')]
        rows = db.execute('SELECT * FROM ' + table).fetchall()
        index = {name: position for position, name in enumerate(columns)}
        result[table] = {
            tuple(row[index[k]] for k in keys): tuple(row) for row in rows
        }
    db.close()
    return result


def points_of(prefix):
    db = sqlite3.connect(':memory:')
    db.deserialize(db_bytes())
    count = db.execute(
        'SELECT COUNT(*) FROM points p JOIN trips t ON t.id=p.trip_id WHERE t.id LIKE ?',
        (prefix + '%',)).fetchone()[0]
    db.close()
    return count


def gfxinfo_total():
    text = sh('dumpsys', 'gfxinfo', PKG)
    match = re.search(r'Total frames rendered:\s*(\d+)', text)
    return int(match.group(1)) if match else None


def perf_from_logcat():
    """Reads the debug-only counters the candidate build writes for the two hot paths."""
    text = sh('logcat', '-d', '-s', 'WalkPaintPerf:D')
    lines = re.findall(r'PERF\s+([^\r\n]+)', text)
    if not lines:
        return None
    fields = {}
    for pair in lines[-1].split():
        if '=' in pair:
            key, value = pair.split('=', 1)
            try:
                fields[key] = float(value)
            except ValueError:
                pass
    return fields


def scroll_down(times=1):
    wx, wy = (int(v) for v in re.findall(r'\d+', sh('wm', 'size'))[:2])
    for _ in range(times):
        sh('input', 'swipe', str(wx // 2), str(int(wy * 0.72)), str(wx // 2), str(int(wy * 0.32)), '400')
        time.sleep(0.7)


def day_entry():
    """The day row is the last thing on the footprints page, under the calendar.

    Its wording has changed between versions ("查看今天的足迹", then "今天 733 米 ›"), and the same
    words appear in the header summary, so the row that matters is the lowest match after the page
    has been scrolled to the bottom.
    """
    scroll_down(4)
    best = None
    for n in ui().iter('node'):
        text = n.get('text') or ''
        if '查看今天的足迹' in text:
            return n
        # "今天" alone is the list row; "今天 781 米" up in the header is the summary, and the row
        # that opens the day map is the lowest match after scrolling to the bottom.
        if text.startswith('今天'):
            bounds = list(map(int, re.findall(r'\d+', n.get('bounds'))))
            if best is None or bounds[1] > best[0][1]:
                best = (bounds, n)
    return best[1] if best else None


def open_map_privacy_page():
    """Walks 我的 → 更多设置 → 隐私与权限 and returns the labels of that page.

    The reference AVD occasionally swallows a tap while its System UI is busy, so each step is
    retried and checked rather than assumed to have landed.
    """
    for _ in range(4):
        if not any('我的' == (t or '') for t in labels()):
            sh('input', 'keyevent', 'KEYCODE_BACK')
            time.sleep(1)
            continue
        tap('我的', wait=2.0)
        for _ in range(3):
            if any('更多设置' == (t or '') for t in labels()):
                break
            time.sleep(1)
        tap('更多设置', wait=2.0)
        for _ in range(3):
            if any('隐私与权限' == (t or '') for t in labels()):
                break
            time.sleep(1)
        tap('隐私与权限', wait=2.0)
        page = labels()
        if any(any(provider in (t or '') for t in page) for provider in PROVIDERS):
            return page
    fail('could not open the map privacy page; visible=' + str(labels()))


def open_map_tab(timeout_s=25):
    """Taps 地图 and waits until the map screen reports itself ready, timing the whole thing.

    This is the user-visible "switch to the map and it hangs for a moment" measurement, taken the
    same way for the replaced build and the candidate.
    """
    started = time.time()
    tap('地图', wait=0.5)
    deadline = started + timeout_s
    while time.time() < deadline:
        text = ' '.join(t for t in labels() if t)
        if '解锁' in text or '附近' in text:
            return int((time.time() - started) * 1000)
        time.sleep(0.5)
    fail('the map page never became ready')


def gestures():
    """Identical pan and double-tap zoom script for every run, on the same viewport."""
    wx, wy = (int(v) for v in re.findall(r'\d+', sh('wm', 'size'))[:2])
    cx, cy = wx // 2, wy // 2 - 200
    for _ in range(3):
        sh('input', 'swipe', str(cx - 260), str(cy), str(cx + 260), str(cy), '300')
        sh('input', 'swipe', str(cx), str(cy - 260), str(cx), str(cy + 260), '300')
        sh('input', 'tap', str(cx), str(cy))
        time.sleep(0.08)
        sh('input', 'tap', str(cx), str(cy))
        time.sleep(1.0)


def walk_ten_fixes():
    """Ten real fixes through the app's own recording path, so the veil has to update for real."""
    db = sqlite3.connect(':memory:')
    db.deserialize(db_bytes())
    origin = db.execute('SELECT lat,lon FROM points ORDER BY time DESC LIMIT 1').fetchone()
    total_before = db.execute('SELECT COUNT(*) FROM points').fetchone()[0]
    db.close()
    lat, lon = origin
    for step in range(1, WALK_FIXES + 1):
        run('emu', 'geo', 'fix', str(lon + 0.00002 * step), str(lat + 0.00002 * step), '0', '6')
        time.sleep(WALK_SPACING_S)
    time.sleep(3)
    db = sqlite3.connect(':memory:')
    db.deserialize(db_bytes())
    total_after = db.execute('SELECT COUNT(*) FROM points').fetchone()[0]
    db.close()
    return total_after - total_before


def fail(message):
    checks['failures'].append(message)
    raise AssertionError(message)


original_mock = re.search(
    r'(?:MOCK_LOCATION|mock_location):\s*(allow|ignore|deny|default|foreground)',
    sh('appops', 'get', '2000', 'android:mock_location')).group(1)
size = sh('wm', 'size')
density = sh('wm', 'density')
PREFS = 'shared_prefs/ui_onboarding.xml'
original_prefs = None
if args.online_basemap:
    try:
        original_prefs = run('exec-out', 'run-as', PKG, 'cat', PREFS, binary=True)
    except RuntimeError:
        original_prefs = None


def set_basemap_consent(enabled):
    """The fallback basemap consent, set from the test host; restored in the finally block."""
    payload = ('<?xml version=\'1.0\' encoding=\'utf-8\' standalone=\'yes\' ?>\n'
               '<map>\n    <boolean name="openfreemap_basemap_consent" value="%s" />\n</map>\n'
               % ('true' if enabled else 'false')).encode()
    local = out / 'ui_onboarding.xml'
    local.write_bytes(payload)
    run('push', str(local), '/data/local/tmp/wp-fog-prefs.xml')
    sh('run-as', PKG, 'cp', '/data/local/tmp/wp-fog-prefs.xml', PREFS)
    sh('rm', '-f', '/data/local/tmp/wp-fog-prefs.xml')

try:
    checks['device_model'] = sh('getprop', 'ro.product.model').strip()
    checks['device_abilist'] = sh('getprop', 'ro.product.cpu.abilist').strip()
    checks['device_sdk'] = sh('getprop', 'ro.build.version.sdk').strip()
    first_abi = checks['device_abilist'].split(',')[0].strip()
    # AmapPlatform only accepts an ARM process, so this AVD cannot run the AMap renderer at all.
    checks['amap_supported_on_this_device'] = first_abi in ('arm64-v8a', 'armeabi-v7a')

    apk = (ROOT / args.apk).resolve()
    checks['apk'] = str(apk)
    checks['apk_sha256'] = hashlib.sha256(apk.read_bytes()).hexdigest()
    rows_installed_before = row_map()
    if not args.skip_install:
        # -d: the baseline run installs the build being replaced, whose version code is lower.
        assert 'Success' in run('install', '-r', '-d', str(apk), timeout=240), 'app install failed'
        assert 'Success' in run('install', '-r', '-d',
            str(ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'),
            timeout=240), 'test install failed'
    version = re.search(r'versionCode=(\d+)', sh('dumpsys', 'package', PKG))
    name = re.search(r'versionName=([\w.]+)', sh('dumpsys', 'package', PKG))
    if not version or not name:
        fail('installed package reports no version')
    checks['version_code'] = int(version.group(1))
    checks['version_name'] = name.group(1)
    installed = hashlib.sha256(
        run('exec-out', 'cat', sh('pm', 'path', PKG).split('package:')[1].strip(), binary=True)).hexdigest()
    checks['installed_apk_sha256'] = installed
    if installed != checks['apk_sha256']:
        fail('installed package is not the file that was built')

    # Installing over the previous build must not touch a single stored row.
    rows_installed_after = row_map()
    checks['install_preserved_rows'] = rows_installed_before == rows_installed_after
    if not checks['install_preserved_rows']:
        fail('installing the build changed or lost stored rows')

    # One deterministic scene: the test AVD's tables are cleared and the same anonymous fixture is
    # written again, so the replaced build and the candidate are shown exactly the same thing.
    seeded = sh('am', 'instrument', '-w', '-e', 'fogTrailProbe', 'true', '-e', 'fogTrailReset', 'true',
                TEST_PKG + '/' + RUNNER, timeout=240)
    checks['fixture_probe'] = [line for line in seeded.splitlines() if 'FOG_TRAIL_PROBE' in line]
    if 'FOG_TRAIL_PROBE_PASS' not in seeded:
        fail('fixture probe failed: ' + seeded[-300:])
    checks['fixture_points'] = points_of(FIXTURE_PREFIX)
    if checks['fixture_points'] < MIN_FIXTURE_POINTS:
        fail('fixture has only %d points; a handful proves nothing' % checks['fixture_points'])
    end = re.search(r'end_lat=([-\d.]+) end_lon=([-\d.]+)', seeded)
    if not end:
        fail('the fixture did not report where it ends, so the viewport cannot be pinned')
    end_lat, end_lon = float(end.group(1)), float(end.group(2))
    checks['fixture_end'] = {'lat': end_lat, 'lon': end_lon}
    # Pin the emulator's position to the end of the fixture: "定位我" then shows the same scene in
    # every run, and the ten extra fixes land inside it instead of a kilometre away.
    run('emu', 'geo', 'fix', str(end_lon), str(end_lat), '0', '6')
    time.sleep(3)

    before_rows = row_map()
    checks['rows_before'] = {t: len(v) for t, v in before_rows.items()}
    checks['basemap'] = 'online-fallback' if args.online_basemap else 'offline-fallback'
    if args.online_basemap:
        sh('am', 'force-stop', PKG)
        set_basemap_consent(True)

    cold = [launch() for _ in range(3)]
    cold.sort()
    checks['cold_start_ms'] = cold
    checks['cold_start_best_ms'] = cold[0]
    checks['cold_start_median_ms'] = cold[1]
    if cold[0] > COLD_START_BEST_MS_MAX:
        fail('the fastest of three launches still took %d ms, above the %d ms window'
             % (cold[0], COLD_START_BEST_MS_MAX))

    # Read the engine back from the running app, not from what we expect it to be.
    shot('profile')
    profile = open_map_privacy_page()
    shot('profile-map-privacy')
    engine = next((provider for provider in PROVIDERS if any(provider in (t or '') for t in profile)), None)
    if engine is None:
        fail('the app reported no map provider; visible=' + str(profile))
    checks['engine_label'] = engine
    checks['engine'] = 'amap' if '高德' in engine else 'fallback'
    checks['amap_path_verified'] = checks['engine'] == 'amap'
    if not checks['amap_supported_on_this_device'] and checks['amap_path_verified']:
        fail('engine claims AMap on an ABI that cannot load it')
    for _ in range(4):
        if '地图' in labels():
            break
        sh('input', 'keyevent', 'KEYCODE_BACK')
        time.sleep(1)
    checks['map_tab_ready_ms'] = open_map_tab()
    tap('定位我', wait=2.5)
    shot('map-follow')
    checks['map_note'] = [t for t in labels() if t and ('解锁' in t or '附近' in t)]

    sh('logcat', '-c')
    shot('map-before-walk')
    if not args.skip_walk:
        checks['extra_fixes_recorded'] = walk_ten_fixes()
    shot('map-after-walk')
    perf_before_gesture = perf_from_logcat() or {}
    gestures()
    perf_after_gesture = perf_from_logcat() or {}
    # Nothing should be repainting while a finger is on the map: the counters say whether the app
    # did any work at all during the fixed drag-and-zoom script.
    checks['gesture_counter_delta'] = {
        key: perf_after_gesture.get(key, 0) - perf_before_gesture.get(key, 0)
        for key in ('update_n', 'ui_n', 'upload_n')
    }
    if checks['gesture_counter_delta'].get('ui_n', 0) > 2:
        fail('the app repainted the map on the UI thread while dragging: %s'
             % checks['gesture_counter_delta'])
    time.sleep(1)
    shot('map-gesture')
    checks['ui_frames_rendered'] = gfxinfo_total()
    perf = perf_from_logcat()
    checks['perf'] = perf
    if perf is None:
        if not args.allow_missing_perf:
            fail('no PERF counters were written: the veil update cost was never measured')
        checks['perf_note'] = 'this build has no counters; a baseline run only'
    else:
        required = ('update_n', 'update_med_ms', 'update_avg_ms', 'update_max_ms',
                    'first_update_ms', 'prep_med_ms',
                    'veil_inc_n', 'veil_inc_med_ms', 'veil_inc_avg_ms', 'veil_inc_max_ms',
                    'veil_rebuild_n', 'veil_rebuild_max_ms',
                    'ui_n', 'ui_med_ms', 'ui_max_ms',
                    'upload_n', 'upload_avg_ms', 'upload_max_ms')
        for field in required:
            if field not in perf:
                fail('PERF line is missing ' + field)
        if perf['update_n'] <= 0 or perf['veil_inc_n'] <= 0 or perf['upload_n'] <= 0:
            fail('PERF counters are zero: nothing was measured')
        if perf['ui_n'] <= 0:
            fail('no UI-thread update was measured at all')
        if perf['ui_n'] > perf['update_n']:
            fail('more UI handovers than state updates')
        if perf['first_update_ms'] > FIRST_UPDATE_MS_MAX:
            fail('the first update after launch took %.1f ms, above the %.0f ms limit'
                 % (perf['first_update_ms'], FIRST_UPDATE_MS_MAX))
        if perf['update_med_ms'] > UPDATE_MED_MS_MAX:
            fail('a state update takes %.1f ms at the median, above the %.0f ms limit'
                 % (perf['update_med_ms'], UPDATE_MED_MS_MAX))
        if perf['update_max_ms'] > UPDATE_MAX_MS_MAX:
            fail('the slowest state update is %.1f ms, above the %.0f ms limit'
                 % (perf['update_max_ms'], UPDATE_MAX_MS_MAX))
        # The per-fix repaint: the median is what a fix during a walk typically costs, and the
        # worst case is bounded separately so a single outlier cannot hide behind the average.
        if perf['veil_inc_med_ms'] > VEIL_INCREMENT_MED_MS_MAX:
            fail('repainting the veil for one new fix takes %.1f ms at the median, above the %.0f ms limit'
                 % (perf['veil_inc_med_ms'], VEIL_INCREMENT_MED_MS_MAX))
        if perf['veil_inc_max_ms'] > VEIL_INCREMENT_MAX_MS_MAX:
            fail('the slowest per-fix veil repaint is %.1f ms, above the %.0f ms limit'
                 % (perf['veil_inc_max_ms'], VEIL_INCREMENT_MAX_MS_MAX))
        if perf['veil_rebuild_max_ms'] > VEIL_REBUILD_MAX_MS_MAX:
            fail('the slowest full veil rebuild is %.1f ms, above the %.0f ms limit'
                 % (perf['veil_rebuild_max_ms'], VEIL_REBUILD_MAX_MS_MAX))
        # This is the part that can drop a frame while a finger is on the map.
        if perf['ui_med_ms'] > UI_MED_MS_MAX:
            fail('the UI thread spends %.1f ms at the median per update, above the %.0f ms limit'
                 % (perf['ui_med_ms'], UI_MED_MS_MAX))
        if perf['ui_max_ms'] > UI_MAX_MS_MAX:
            fail('the worst UI-thread update is %.1f ms, above the %.0f ms limit'
                 % (perf['ui_max_ms'], UI_MAX_MS_MAX))
        if perf['upload_avg_ms'] > UPLOAD_AVG_MS_MAX:
            fail('handing the veil to the map averages %.1f ms, above the %.0f ms limit'
                 % (perf['upload_avg_ms'], UPLOAD_AVG_MS_MAX))
        if perf['upload_max_ms'] > UPLOAD_MAX_MS_MAX:
            fail('slowest handover is %.1f ms, above the %.0f ms limit'
                 % (perf['upload_max_ms'], UPLOAD_MAX_MS_MAX))
        if perf['upload_n'] > perf['update_n']:
            fail('the map was handed more images than there were state updates')
        checks['perf_attribution'] = {
            'prep_med_ms': perf['prep_med_ms'],
            'veil_inc_med_ms': perf['veil_inc_med_ms'],
            'ui_med_ms': perf['ui_med_ms'],
            'note': 'prep is the pre-existing database snapshot and history grouping; '
                    'ui is the only work on the UI thread',
        }

    # Switching tabs recreates the map view and its style; the veil has to come back with it.
    tap('我的', wait=1.5)
    checks['map_tab_ready_after_tab_switch_ms'] = open_map_tab()
    shot('map-tab-return')

    # The selected day draws that day's route; it is the only line left, so its shape matters.
    tap('足迹', wait=1.5)
    shot('trips')
    day_row = day_entry()
    if day_row is None:
        fail('今天的足迹 entry is missing; visible=' + str(labels()))
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', day_row.get('bounds')))
    sh('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(3.5)
    shot('map-day')
    if not any(t and '返回全部探索' in t for t in labels()):
        fail('the day map did not open; visible=' + str(labels()))

    reopen = launch()
    checks['reopen_ms'] = reopen
    checks['map_tab_ready_after_reopen_ms'] = open_map_tab()
    shot('map-reopen')
    checks['map_note_after_reopen'] = [t for t in labels() if t and ('解锁' in t or '附近' in t)]

    after_rows = row_map()
    checks['rows_after'] = {t: len(v) for t, v in after_rows.items()}
    for table, before in before_rows.items():
        after = after_rows[table]
        for key, row in before.items():
            if key not in after:
                fail('%s row %s was lost' % (table, key))
            if after[key] != row:
                fail('%s row %s was changed' % (table, key))
except Exception as exc:  # noqa: BLE001 - the point is to fail loudly and keep the report
    if not checks['failures']:
        checks['failures'].append(str(exc))
    try:
        shot('failure')
    except Exception:
        pass
finally:
    sh('am', 'force-stop', PKG)
    if args.online_basemap:
        if original_prefs is None:
            sh('run-as', PKG, 'rm', '-f', PREFS)
        else:
            local = out / 'ui_onboarding-restore.xml'
            local.write_bytes(original_prefs)
            run('push', str(local), '/data/local/tmp/wp-fog-prefs.xml')
            sh('run-as', PKG, 'cp', '/data/local/tmp/wp-fog-prefs.xml', PREFS)
            sh('rm', '-f', '/data/local/tmp/wp-fog-prefs.xml')
    sh('appops', 'set', '2000', 'android:mock_location', original_mock)
    for what, original in (('size', size), ('density', density)):
        keep = re.search(r'Override (?:size|density): (\S+)', original)
        sh('wm', what, keep.group(1) if keep else 'reset')
    checks['device_restored'] = original_mock in sh('appops', 'get', '2000', 'android:mock_location')
    checks['passed'] = not checks['failures'] and checks.get('device_restored') is True
    out.joinpath('acceptance.json').write_text(
        json.dumps(checks, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(checks, ensure_ascii=False))
    if not checks['passed']:
        sys.exit(1)

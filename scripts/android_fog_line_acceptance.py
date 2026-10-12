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
import argparse, hashlib, json, math, re, sqlite3, subprocess, sys, tempfile, time
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
PAN_MAX_MS_MAX = 800.0
UI_MED_MS_MAX = 20.0
UI_MAX_MS_MAX = 120.0
UPLOAD_MED_MS_MAX = 20.0
UPLOAD_AVG_MS_MAX = 40.0
UPLOAD_MAX_MS_MAX = 250.0
# The reference AVD is shared and noisy, so the fastest of three launches is the number that says
# anything; a build that cannot start inside this window is not usable whatever the noise is.
COLD_START_BEST_MS_MAX = 12000
MIN_FIXTURE_POINTS = 3000
WALK_FIXES = 10
WALK_SPACING_S = 3.0
# A veiled map pixel is dark navy (blue above both red and green) and below this luminance; a bare
# OpenFreeMap street pixel is light. Calibrated in section 4 of the evidence document: the same
# view scores ~0.32 with the veil, ~0.002 on the pale veil of 0.36, and the gate is far below both.
VEIL_DARK_LEVEL = 165
VEIL_PRESENTED_RATIO = 0.05
# Two clusters about twelve kilometres apart: the far one has to keep its paint and the ground
# between them has to keep its veil.
FAR_CLUSTER_MIN_METRES = 11000

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


def tap(*texts, wait=1.0, exact=False):
    """Taps a control.

    Exact matching is for the bottom tabs, whose labels also appear inside page text ("地图" is in
    "地图与隐私"); a substring match is for chips and rows, which carry a date prefix.
    """
    for attempt in range(3):
        for n in ui().iter('node'):
            values = [v for v in (n.get('text'), n.get('content-desc')) if v]
            if exact:
                hit = any(v == wanted for v in values for wanted in texts)
            else:
                hit = any(v == wanted or wanted in v for v in values for wanted in texts)
            if hit:
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
                sh('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(wait)
                return
        if not dismiss_system_dialogs():
            break
    raise AssertionError('missing control ' + str(texts) + '; visible=' + str(labels()))


def shot(name):
    out.joinpath(name + '.png').write_bytes(run('exec-out', 'screencap', '-p', binary=True))


def veil_stats(name):
    """Measures the veil and the walked-ground tint in the map area of one screenshot.

    This is the observable signal that the fog layer is actually on screen, rather than merely
    having been handed to the map engine: the counters say the image was passed over, a picture
    says whether a person can see it. The veil is dark navy, so it is the only thing in this scene
    with blue above both red and green while still being dark; the pale veil of 0.36 scores ~0.002
    on this test, which is what makes it a usable signal rather than a colour guess.
    """
    from PIL import Image
    image = Image.open(out / (name + '.png')).convert('RGB')
    # Skip the top chip and the bottom tab bar; keep the map itself.
    region = image.crop((0, 300, image.width, image.height - 300)).resize((216, 360))
    veil = 0
    bare = 0
    tinted = 0
    total = 0
    for r, g, b in region.getdata():
        total += 1
        luminance = 0.299 * r + 0.587 * g + 0.114 * b
        if b > g and b > r and luminance < VEIL_DARK_LEVEL:
            veil += 1
        elif luminance > 200 and g >= r:
            bare += 1
        if g > r + 8 and g > b and 90 < g < 200:
            tinted += 1
    return {'veil_ratio': round(veil / total, 3),
            'bare_map_ratio': round(bare / total, 3),
            'tint_ratio': round(tinted / total, 3)}


def map_presented_ms(started, timeout_s=25):
    """Waits until the veil is visibly on screen and returns the time it took.

    The Activity launch number only says the window came up; the map state is refreshed on a
    background thread, so a fast first frame is not a usable map.
    """
    deadline = started + timeout_s
    while time.time() < deadline:
        shot('presented-check')
        stats = veil_stats('presented-check')
        if stats['veil_ratio'] >= VEIL_PRESENTED_RATIO:
            return int((time.time() - started) * 1000), stats
        time.sleep(0.6)
    fail('the veil never became visible on screen; last stats=' + str(veil_stats('presented-check')))


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
        if dismiss_system_dialogs():
            time.sleep(1)
            continue
        if not any('我的' == (t or '') for t in labels()):
            sh('input', 'keyevent', 'KEYCODE_BACK')
            time.sleep(1)
            continue
        tap('我的', wait=2.0, exact=True)
        for _ in range(3):
            if any('更多设置' == (t or '') for t in labels()):
                break
            dismiss_system_dialogs()
            time.sleep(1)
        tap('更多设置', wait=2.0)
        for _ in range(3):
            if any('隐私与权限' == (t or '') for t in labels()):
                break
            dismiss_system_dialogs()
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
    tap('地图', wait=0.5, exact=True)
    deadline = started + timeout_s
    while time.time() < deadline:
        text = ' '.join(t for t in labels() if t)
        if '解锁' in text or '附近' in text:
            return int((time.time() - started) * 1000)
        time.sleep(0.5)
    fail('the map page never became ready')


def tap_node(node, wait=1.0):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    sh('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(wait)


def day_row_matching(predicate, scrolls=4):
    scroll_down(scrolls)
    for node in ui().iter('node'):
        if predicate(node.get('text') or ''):
            return node
    return None


CAMERA_TAG = 'WalkPaintCam'


def camera_state():
    """The last camera record the app wrote: where the map is looking, in which mode, and where the
    person is. Screenshots are tied to this, because colour alone cannot tell a near view from a far
    one — which is exactly how a near view once passed as the far cluster."""
    text = sh('logcat', '-d', '-s', CAMERA_TAG + ':D')
    lines = [line for line in text.splitlines() if CAMERA_TAG in line]
    if not lines:
        return None
    fields = dict(re.findall(r'([A-Za-z]+)=([^\s]+)', lines[-1].split(CAMERA_TAG + ': ')[-1]))
    state = {}
    for key, value in fields.items():
        try:
            state[key] = float(value)
        except ValueError:
            state[key] = value
    return state


def check_scene(name, state, predicate, description):
    """Fails unless the camera record taken with a screenshot is really in the scene it claims.

    The state is passed in rather than read again: the camera can move on between a picture and a
    later reading, and judging one against the other is how a near view once passed as the far one.
    """
    checks['scene_' + name] = state
    if state is None:
        fail('no camera record for the %s scene, so the screenshot proves nothing' % name)
    if not predicate(state):
        fail('the %s screenshot was not taken in the scene it claims (%s): %s'
             % (name, description, state))
    return state


def require_scene(name, predicate, description):
    """Same as check_scene, for a moment when the camera is known to be standing still."""
    return check_scene(name, camera_state(), predicate, description)


def person_stayed_near(state, expected_lon=2.3523, tolerance=0.004):
    """The person's own position must not have moved just because the map did."""
    return state is not None and abs(state.get('fixLon', 0.0) - expected_lon) < tolerance


def pan_camera(to_lon, to_lat, tolerance=0.0015, max_swipes=90, on_way=None):
    """Drags the map with real swipes until the camera reaches the target, checking as it goes.

    This is the path a person takes to look at old ground far away: drag the map until it is there.
    A debug camera jump would not exercise the same code, so it is not used here. The swipe length
    is worked out from the current view so a wide view does not overshoot and a close one is not
    stuck taking a few metres at a time.
    """
    wx, wy = (int(v) for v in re.findall(r'\d+', sh('wm', 'size'))[:2])
    cx, cy = wx // 2, wy // 2 - 200
    swipes = 0
    previous = None
    overshoot = 0
    for _ in range(max_swipes):
        state = camera_state()
        if state is None:
            break
        dlon = to_lon - state.get('lon', to_lon)
        dlat = to_lat - state.get('lat', to_lat)
        if on_way is not None:
            on_way(state)
        if abs(dlon) < tolerance and abs(dlat) < tolerance:
            break
        # If a drag made the distance worse the step was too long for this view; halve it, and give
        # up after three in a row rather than wandering off across the map.
        distance = abs(dlon) + abs(dlat)
        if previous is not None and distance >= previous:
            overshoot += 1
            if overshoot >= 3:
                break
        else:
            overshoot = 0
        previous = distance
        damping = 1.0 if overshoot == 0 else 0.5 ** overshoot
        view_lon = state.get('viewLon') or 0.0
        view_lat = state.get('viewLat') or 0.0
        # Pixels needed for the remaining distance, with a small floor so a close view can still
        # converge and a ceiling so a drag stays a drag.
        if abs(dlon) >= tolerance and view_lon > 0:
            step = int(min(600, max(30, abs(dlon) / view_lon * wx * damping)))
            step = step if dlon < 0 else -step
            sh('input', 'swipe', str(cx - step // 2), str(cy), str(cx + step // 2), str(cy), '250')
        if abs(dlat) >= tolerance and view_lat > 0:
            step = int(min(600, max(30, abs(dlat) / view_lat * wy * damping)))
            # Screen y grows downward, so dragging up moves the map south: the opposite sign to the
            # horizontal case. Getting this backwards walked the camera north by three kilometres.
            step = -step if dlat < 0 else step
            sh('input', 'swipe', str(cx), str(cy - step // 2), str(cx), str(cy + step // 2), '250')
        swipes += 1
        time.sleep(0.35)
    checks['pan_swipes_to_%.3f' % to_lon] = swipes
    return camera_state()


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


def route_line_starts(name, row_fraction=0.5, tolerance=55):
    """Where the day's route crosses one horizontal scanline, in x pixels.

    The reported defect was several parallel lines where one was expected — the same street walked
    four times drawn as four lines a few metres apart. A colour share cannot see that, because four
    thin lines and one thicker line cover a similar part of the screen; the distance between the
    crossings can. The colour is the app's own route pigment (0x0B8C77), which the pale walked-ground
    tint does not match.
    """
    from PIL import Image
    image = Image.open(out / (name + '.png')).convert('RGB')
    y = int(image.height * row_fraction)
    starts = []
    inside = False
    for x in range(image.width):
        r, g, b = image.getpixel((x, y))
        route = (abs(r - 11) < tolerance and abs(g - 140) < tolerance
                 and abs(b - 119) < tolerance and g - r > 50)
        if route and not inside:
            starts.append(x)
        inside = route
    return starts


def closest_line_gap(name):
    """The smallest gap between two route crossings, or None when fewer than two were found."""
    starts = route_line_starts(name)
    if len(starts) < 2:
        return None
    return min(b - a for a, b in zip(starts, starts[1:]))


def host_memory_mb():
    """Available and total host memory.

    Recorded because every timing here is measured through an emulator running on this host: with
    the machine short of memory every figure roughly doubles, including the pre-existing database
    work this change never touches.
    """
    try:
        import ctypes

        class MemoryStatus(ctypes.Structure):
            _fields_ = [('dwLength', ctypes.c_ulong), ('dwMemoryLoad', ctypes.c_ulong),
                        ('ullTotalPhys', ctypes.c_ulonglong), ('ullAvailPhys', ctypes.c_ulonglong),
                        ('ullTotalPageFile', ctypes.c_ulonglong), ('ullAvailPageFile', ctypes.c_ulonglong),
                        ('ullTotalVirtual', ctypes.c_ulonglong), ('ullAvailVirtual', ctypes.c_ulonglong),
                        ('ullAvailExtendedVirtual', ctypes.c_ulonglong)]

        status = MemoryStatus()
        status.dwLength = ctypes.sizeof(MemoryStatus)
        ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status))
        return int(status.ullAvailPhys / (1024 * 1024)), int(status.ullTotalPhys / (1024 * 1024))
    except Exception:
        return None, None


def fail(message):
    checks['failures'].append(message)
    raise AssertionError(message)


def note_failure(message):
    """Records a failure without stopping. Used for the timing bounds, so the pictures and the data
    checks are still collected in the same run instead of being hidden behind a slow number."""
    checks['failures'].append(message)


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
    # Nothing may be writing while the snapshot is taken: a running app records fixes every second,
    # and an added row would be read as the install having changed the data.
    sh('am', 'force-stop', PKG)
    time.sleep(1)
    rows_installed_before = row_map()
    if not args.skip_install:
        # -d: the baseline run installs the build being replaced, whose version code is lower.
        assert 'Success' in run('install', '-r', '-d', str(apk), timeout=240), 'app install failed'
        assert 'Success' in run('install', '-r', '-d',
            str(ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'),
            timeout=240), 'test install failed'
    # The package manager can still be busy straight after an install on a loaded host, so the
    # version is polled instead of read once.
    version = name = None
    reported = ''
    attempts = 0
    for attempt in range(20):
        attempts = attempt + 1
        reported = sh('dumpsys', 'package', PKG)
        version = re.search(r'versionCode=(\d+)', reported)
        name = re.search(r'versionName=([\w.]+)', reported)
        if version and name:
            break
        time.sleep(1)
    if not version or not name:
        # Record what the query actually returned: an empty dump (service not ready), an error
        # string (install failed) and an unparsable dump are three different problems.
        checks['version_query_raw'] = reported[-400:]
        checks['version_query_attempts'] = attempts
        fail('installed package reports no version after %d attempts; raw=%r'
             % (attempts, reported[-200:]))
    checks['version_code'] = int(version.group(1))
    checks['version_name'] = name.group(1)
    installed = hashlib.sha256(
        run('exec-out', 'cat', sh('pm', 'path', PKG).split('package:')[1].strip(), binary=True)).hexdigest()
    checks['installed_apk_sha256'] = installed
    if installed != checks['apk_sha256']:
        fail('installed package is not the file that was built')

    # Installing over the previous build must not touch a single stored row. With the install
    # skipped there is nothing to compare, so the check is recorded as not applicable rather than
    # passed or failed.
    if args.skip_install:
        checks['install_preserved_rows'] = 'not_applicable: install skipped'
    else:
        rows_installed_after = row_map()
        checks['install_preserved_rows'] = rows_installed_before == rows_installed_after
        if not checks['install_preserved_rows']:
            changed = sorted(k for k in rows_installed_before if rows_installed_before.get(k) != rows_installed_after.get(k))
            checks['rows_changed_by_install'] = changed[:20]
            fail('installing the build changed or lost stored rows: %s' % changed[:5])

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
    available, total = host_memory_mb()
    checks['host_available_mb'] = available
    checks['host_total_mb'] = total

    # The veil image itself, read pixel by pixel: a step edge, a border that ends in a right angle,
    # or a second pass that darkens the paint would all survive a constants-only test.
    pixels = sh('am', 'instrument', '-w', '-e', 'fogPixelProbe', 'true',
                TEST_PKG + '/' + RUNNER, timeout=180)
    checks['fog_pixel_probe'] = [line for line in pixels.splitlines() if 'FOG_PIXEL_PROBE' in line]
    if 'FOG_PIXEL_PROBE_PASS' not in pixels:
        fail('the rendered veil failed its pixel checks: ' + pixels[-400:])
    end = re.search(r'end_lat=([-\d.]+) end_lon=([-\d.]+)', seeded)
    if not end:
        fail('the fixture did not report where it ends, so the viewport cannot be pinned')
    end_lat, end_lon = float(end.group(1)), float(end.group(2))
    checks['fixture_end'] = {'lat': end_lat, 'lon': end_lon}
    far = re.search(r'far_lat=([-\d.]+) far_lon=([-\d.]+)', seeded)
    if not far:
        fail('the fixture did not report its far cluster')
    far_lat, far_lon = float(far.group(1)), float(far.group(2))
    checks['fixture_far'] = {'lat': far_lat, 'lon': far_lon}
    # The two places the map has to be dragged between, named once so the scene checks below and
    # the drag itself cannot drift apart.
    FAR_LAT, FAR_LON = far_lat, far_lon
    NEAR_LAT, NEAR_LON = end_lat, end_lon
    separation = math.hypot((far_lat - end_lat) * 111194.9,
                            (far_lon - end_lon) * 111194.9 * math.cos(math.radians(end_lat)))
    checks['fixture_cluster_separation_m'] = int(separation)
    if separation < FAR_CLUSTER_MIN_METRES:
        fail('the two clusters are only %d m apart; that does not test a large history'
             % separation)
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

    launches = [launch() for _ in range(3)]
    launches.sort()
    # Renamed on purpose: `am start -W` reports when the Activity came up, which is not the same as
    # the map being usable — the map state is refreshed on a background thread afterwards.
    checks['activity_launch_ms'] = launches
    checks['activity_launch_best_ms'] = launches[0]
    checks['activity_launch_median_ms'] = launches[1]
    checks['activity_launch_note'] = ('am start -W TotalTime: the Activity window came up; '
                                      'map_presented_ms is when the veil is actually on screen')
    if launches[0] > COLD_START_BEST_MS_MAX:
        fail('the fastest of three Activity launches still took %d ms, above the %d ms window'
             % (launches[0], COLD_START_BEST_MS_MAX))
    # Same re-entry, but timed until the fog layer is visibly presented rather than until the
    # window appears.
    sh('am', 'force-stop', PKG)
    time.sleep(3)
    presented_start = time.time()
    launched = sh('am', 'start', '-W', '-n', PKG + '/.MainActivity')
    match = re.search(r'(?:TotalTime|WaitTime):\s*(\d+)', launched)
    checks['reentry_activity_launch_ms'] = int(match.group(1)) if match else None
    checks['map_presented_ms'], checks['map_presented_stats'] = map_presented_ms(presented_start)

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
    # The person keeps walking while the finger is on the map. Running the drag only after the walk
    # finished proved nothing about the case the user complained about, so the fixes are injected
    # from a thread while the same gesture script runs, and the counters have to show that updates
    # really happened during it.
    import threading
    walker = threading.Thread(target=walk_ten_fixes, daemon=True)
    walker.start()
    gestures()
    walker.join()
    perf_after_gesture = perf_from_logcat() or {}
    checks['gesture_counter_delta'] = {
        key: perf_after_gesture.get(key, 0) - perf_before_gesture.get(key, 0)
        for key in ('update_n', 'ui_n', 'upload_n', 'pan_n')
    }
    if checks['gesture_counter_delta'].get('update_n', 0) <= 0:
        fail('no state update happened while the finger was on the map, so a live fix during a '
             'drag was never exercised: %s' % checks['gesture_counter_delta'])
    if perf_after_gesture.get('pan_n', 0) > 0 and perf_after_gesture.get('pan_max_ms', 0) > PAN_MAX_MS_MAX:
        fail('redrawing the veil for a new view took %.1f ms, above the %.0f ms limit'
             % (perf_after_gesture['pan_max_ms'], PAN_MAX_MS_MAX))
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
                    'upload_n', 'upload_med_ms', 'upload_avg_ms', 'upload_max_ms',
                    'pan_n', 'pan_med_ms', 'pan_max_ms')
        for field in required:
            if field not in perf:
                note_failure('PERF line is missing ' + field)
        if perf['update_n'] <= 0 or perf['veil_inc_n'] <= 0 or perf['upload_n'] <= 0:
            note_failure('PERF counters are zero: nothing was measured')
        if perf['ui_n'] <= 0:
            note_failure('no UI-thread update was measured at all')
        if perf['ui_n'] > perf['update_n']:
            note_failure('more UI handovers than state updates')
        if perf['first_update_ms'] > FIRST_UPDATE_MS_MAX:
            note_failure('the first update after launch took %.1f ms, above the %.0f ms limit'
                 % (perf['first_update_ms'], FIRST_UPDATE_MS_MAX))
        if perf['update_med_ms'] > UPDATE_MED_MS_MAX:
            note_failure('a state update takes %.1f ms at the median, above the %.0f ms limit'
                 % (perf['update_med_ms'], UPDATE_MED_MS_MAX))
        if perf['update_max_ms'] > UPDATE_MAX_MS_MAX:
            note_failure('the slowest state update is %.1f ms, above the %.0f ms limit'
                 % (perf['update_max_ms'], UPDATE_MAX_MS_MAX))
        # The per-fix repaint: the median is what a fix during a walk typically costs, and the
        # worst case is bounded separately so a single outlier cannot hide behind the average.
        if perf['veil_inc_med_ms'] > VEIL_INCREMENT_MED_MS_MAX:
            note_failure('repainting the veil for one new fix takes %.1f ms at the median, above the %.0f ms limit'
                 % (perf['veil_inc_med_ms'], VEIL_INCREMENT_MED_MS_MAX))
        if perf['veil_inc_max_ms'] > VEIL_INCREMENT_MAX_MS_MAX:
            note_failure('the slowest per-fix veil repaint is %.1f ms, above the %.0f ms limit'
                 % (perf['veil_inc_max_ms'], VEIL_INCREMENT_MAX_MS_MAX))
        if perf['veil_rebuild_max_ms'] > VEIL_REBUILD_MAX_MS_MAX:
            note_failure('the slowest full veil rebuild is %.1f ms, above the %.0f ms limit'
                 % (perf['veil_rebuild_max_ms'], VEIL_REBUILD_MAX_MS_MAX))
        # The original bound, unchanged: the veil image was cut to 512 px and unchanged GeoJSON is
        # no longer resubmitted, which brought the whole UI block to 18 ms even on a loaded host.
        if perf['ui_med_ms'] > UI_MED_MS_MAX:
            note_failure('the UI thread spends %.1f ms at the median per update, above the %.0f ms limit'
                 % (perf['ui_med_ms'], UI_MED_MS_MAX))
        if perf['ui_max_ms'] > UI_MAX_MS_MAX:
            note_failure('the worst UI-thread update is %.1f ms, above the %.0f ms limit'
                 % (perf['ui_max_ms'], UI_MAX_MS_MAX))
        # The share this work owns: handing the veil image to the map.
        if perf['upload_med_ms'] > UPLOAD_MED_MS_MAX:
            note_failure('handing the veil to the map takes %.1f ms at the median, above the %.0f ms limit'
                 % (perf['upload_med_ms'], UPLOAD_MED_MS_MAX))
        if perf['upload_avg_ms'] > UPLOAD_AVG_MS_MAX:
            note_failure('handing the veil to the map averages %.1f ms, above the %.0f ms limit'
                 % (perf['upload_avg_ms'], UPLOAD_AVG_MS_MAX))
        if perf['upload_max_ms'] > UPLOAD_MAX_MS_MAX:
            note_failure('slowest handover is %.1f ms, above the %.0f ms limit'
                 % (perf['upload_max_ms'], UPLOAD_MAX_MS_MAX))
        if perf['upload_n'] > perf['update_n'] + perf['pan_n']:
            note_failure('the map was handed more images than there were state updates and pans')
        checks['perf_attribution'] = {
            'prep_med_ms': perf['prep_med_ms'],
            'veil_inc_med_ms': perf['veil_inc_med_ms'],
            'ui_med_ms': perf['ui_med_ms'],
            'note': 'prep is the pre-existing database snapshot and history grouping; '
                    'ui is the only work on the UI thread',
        }

    # Switching tabs recreates the map view and its style; the veil and the paint have to come back
    # with it. Re-centring on the person first makes this a picture of ground that was walked, so
    # the assertion below is about what the person sees, not about an empty corner of the map.
    tap('我的', wait=1.5, exact=True)
    checks['map_tab_ready_after_tab_switch_ms'] = open_map_tab()
    tap('定位我', wait=3.0)
    tab_state = capture_scene('map-tab-return')
    check_scene('tab_return', tab_state, lambda s: s.get('day') == 'cumulative',
                'the cumulative view is showing after the tab switch')
    if checks['map-tab-return_stats']['veil_ratio'] < 0.25:
        fail('the veil is missing after switching tabs: %s' % checks['map-tab-return_stats'])
    if checks['map-tab-return_stats']['tint_ratio'] < 0.003:
        fail('the walked ground is not painted after switching tabs: %s'
             % checks['map-tab-return_stats'])

    # The selected day draws that day's route; it is the only line left, so its shape matters.
    # The tab needs a moment to settle before its screenshot, or the picture is still the map.
    tap('足迹', wait=3.0, exact=True)
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
    # One street must be one line. The replaced build drew the four passes over the same street as
    # four parallel lines: measured on its own saved screenshot the closest pair of crossings is
    # 19 px apart, while the candidate's is 282 px. Twenty pixels at this zoom is about twelve
    # metres, so two different streets are never that close together.
    checks['map_day_line_starts'] = route_line_starts('map-day')
    checks['map_day_closest_line_gap'] = closest_line_gap('map-day')
    if checks['map_day_closest_line_gap'] is not None and checks['map_day_closest_line_gap'] < 20:
        fail('the day route is drawn as parallel double lines (closest pair %d px apart): %s'
             % (checks['map_day_closest_line_gap'], checks['map_day_line_starts']))

    # The second cluster, about twelve kilometres away and two days old. Opening that day shows it,
    # then the cumulative view is restored and the map is dragged there with real swipes, which is
    # what a person does; every screenshot is tied to the camera record so a near view cannot pass
    # as the far cluster again.
    tap('足迹', wait=3.0, exact=True)
    older = day_row_matching(lambda text: bool(re.fullmatch(r'\d{4}/\d{2}/\d{2}', text.strip())))
    if older is None:
        fail('no older day on the footprints page; visible=' + str(labels()))
    tap_node(older, wait=3.5)
    if not any(t and '返回全部探索' in t for t in labels()):
        fail('the older day map did not open; visible=' + str(labels()))
    checks['far_day_opened'] = True
    tap('返回全部探索', wait=3.0)
    start_state = camera_state()
    shot('map-cumulative-start')
    check_scene('cumulative_start', start_state,
                lambda s: s.get('day') == 'cumulative',
                'the cumulative view is showing')

    # The scene record, the screenshot and the pixel statistics are taken as one observation: the
    # camera can move on between them, and a picture of one place judged against the coordinates of
    # another is exactly how a near view once passed as the far cluster.
    def capture_scene(name):
        state = camera_state()
        shot(name)
        checks['scene_' + name] = state
        checks[name + '_stats'] = veil_stats(name)
        return state

    def note_between(state):
        if 'scene_map-between-clusters' in checks:
            return
        if 'lon' in state and 2.40 < state['lon'] < 2.47:
            # Hold still, then take the picture and the camera reading together.
            time.sleep(0.8)
            capture_scene('map-between-clusters')

    pan_camera(FAR_LON, FAR_LAT, on_way=note_between)
    # Zoom in on the far cluster with real double taps, so the paint there is big enough to judge,
    # then check again that the camera is still on it.
    wx, wy = (int(v) for v in re.findall(r'\d+', sh('wm', 'size'))[:2])
    for _ in range(3):
        sh('input', 'tap', str(wx // 2), str(wy // 2 - 200))
        time.sleep(0.08)
        sh('input', 'tap', str(wx // 2), str(wy // 2 - 200))
        time.sleep(1.2)
    time.sleep(1.5)
    far_state = capture_scene('map-far-cluster')
    check_scene('far_cluster', far_state,
                lambda s: (abs(s.get('lat', 0.0) - FAR_LAT) < 0.003
                           and abs(s.get('lon', 0.0) - FAR_LON) < 0.003
                           and s.get('day') == 'cumulative'),
                'camera at the cluster twelve kilometres east, in the cumulative view')
    if not person_stayed_near(far_state):
        fail('the person was moved to the far cluster instead of the map: %s' % far_state)
    checks['far_cluster_stats'] = checks['map-far-cluster_stats']
    # The ground walked twelve kilometres away has to still be painted, and the ground nobody has
    # walked around it has to still be veiled. Before the frame followed the view this scene
    # measured 0.00/0.00, because the frame had been clamped around the person.
    if checks['far_cluster_stats']['veil_ratio'] < 0.25:
        fail('the far cluster is not veiled: %s' % checks['far_cluster_stats'])
    if checks['far_cluster_stats']['tint_ratio'] < 0.003:
        fail('the ground walked twelve kilometres away is not painted: %s'
             % checks['far_cluster_stats'])

    # Halfway between the two clusters: ground nobody has walked, and it has to stay behind the
    # veil. If the drag passed through without stopping there, the camera is taken back and the
    # scene, the picture and the statistics are captured together.
    if 'scene_map-between-clusters' not in checks:
        pan_camera((FAR_LON + NEAR_LON) / 2.0, FAR_LAT)
        time.sleep(1.5)
        capture_scene('map-between-clusters')
    between_state = checks['scene_map-between-clusters']
    check_scene('between_clusters', between_state,
                lambda s: 2.39 < s.get('lon', 0.0) < 2.48 and s.get('day') == 'cumulative',
                'camera between the two clusters, in the cumulative view')
    checks['between_clusters_stats'] = checks['map-between-clusters_stats']
    if checks['between_clusters_stats']['veil_ratio'] < 0.25:
        fail('the ground between the two clusters is not veiled: %s'
             % checks['between_clusters_stats'])
    if checks['between_clusters_stats']['tint_ratio'] > 0.05:
        fail('ground nobody has walked is painted between the clusters: %s'
             % checks['between_clusters_stats'])

    # And back to the near cluster, still by dragging. Walking ground has to be painted here, and
    # the ground around it veiled: this is the same assertion the far cluster gets.
    pan_camera(NEAR_LON, NEAR_LAT)
    time.sleep(1.5)
    near_state = capture_scene('map-near-cluster')
    check_scene('near_cluster', near_state,
                lambda s: (abs(s.get('lon', 0.0) - NEAR_LON) < 0.004
                           and s.get('day') == 'cumulative'),
                'camera back at the near cluster, in the cumulative view')
    if not person_stayed_near(near_state):
        fail('the person did not stay near while the map was dragged: %s' % near_state)
    checks['near_cluster_stats'] = checks['map-near-cluster_stats']
    if checks['near_cluster_stats']['veil_ratio'] < 0.25:
        fail('the ground around the near cluster is not veiled: %s' % checks['near_cluster_stats'])
    if checks['near_cluster_stats']['tint_ratio'] < 0.003:
        fail('the ground walked near the person is not painted: %s' % checks['near_cluster_stats'])

    # Reopening the app: the explored ground has to be visible again and the unknown still veiled.
    # Row counts only prove the database survived; they say nothing about the picture.
    reopen = launch()
    checks['reopen_ms'] = reopen
    checks['map_tab_ready_after_reopen_ms'] = open_map_tab()
    tap('定位我', wait=3.0)
    reopen_state = capture_scene('map-reopen')
    check_scene('reopen', reopen_state,
                lambda s: s.get('day') == 'cumulative',
                'the map is back in the cumulative view after a restart')
    if not person_stayed_near(reopen_state):
        fail('the person is not where they were after a restart: %s' % reopen_state)
    reopen_stats = checks['map-reopen_stats']
    if reopen_stats['veil_ratio'] < 0.25:
        fail('the veil is missing after reopening the app: %s' % reopen_stats)
    if reopen_stats['tint_ratio'] < 0.003:
        fail('the walked ground is not painted after reopening the app: %s' % reopen_stats)
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

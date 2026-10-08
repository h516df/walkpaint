#!/usr/bin/env python3
"""Emulator-only real entry acceptance. Keeps every pre-existing SQLite row.

Synthetic GPS is injected only on the explicitly allowed emulator serials.
Provider, mock app-op and location/permission state are restored in finally.
No uninstall, data clear or production fixture injection.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--serial', default='127.0.0.1:16384')
p.add_argument('--adb', default=None)
p.add_argument('--apk', default='app/build/outputs/apk/debug/app-debug.apk')
p.add_argument('--output', default='verification/v04')
p.add_argument('--quick', action='store_true', help='Short final-quality check; avoids repeating the long GPS/provider gap chain')
args = p.parse_args()
if args.serial != '127.0.0.1:16384' and not re.fullmatch(r'emulator-\d+', args.serial):
    p.error('Refusing non-emulator serial')
adb = args.adb or shutil.which('adb') or r'D:\Apps\Android\sdk\platform-tools\adb.exe'
base = [adb, '-s', args.serial]
out = Path(args.output)
out.mkdir(parents=True, exist_ok=True)
checks = {}

def run(*parts, binary=False, timeout=30, allow_completed_dump=False):
    r = subprocess.run(base + list(parts), capture_output=True, timeout=timeout)
    transport = (r.stdout+r.stderr).decode(errors='replace')
    if r.returncode and any(msg in transport for msg in ['device offline', 'error: closed', 'Transport endpoint is not connected']):
        # MuMu briefly drops ADB when an app display is destroyed. Wait for the
        # same allowed emulator; never restart the shared ADB server or touch a phone.
        subprocess.run(base+['wait-for-device'],capture_output=True,timeout=20,check=True)
        r = subprocess.run(base+list(parts),capture_output=True,timeout=timeout)
    # MuMu's uiautomator can exit nonzero AFTER writing a valid fresh hierarchy.
    # Only tolerate that exact completion message; ui() still parses the new XML.
    completed_dump = allow_completed_dump and b'UI hierchary dumped to:' in r.stdout
    if r.returncode and not completed_dump:
        raise RuntimeError('ADB failed: ' + (r.stdout+r.stderr).decode(errors='replace')[-700:])
    return r.stdout if binary else r.stdout.decode(errors='replace')

def shell(*parts):
    return run('shell', *parts)

def database():
    raw = run('exec-out', 'run-as', 'cn.walkpaint', 'cat', 'databases/footprints.db', binary=True)
    db = sqlite3.connect(':memory:')
    db.deserialize(raw)
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    return db

def rows():
    with database() as db:
        return {table: set(db.execute('SELECT * FROM ' + table).fetchall()) for table in ['trips', 'points']}

def count():
    with database() as db:
        return db.execute('SELECT COUNT(*) FROM points p JOIN trips t ON t.id=p.trip_id WHERE t.demo=0').fetchone()[0]

def diagnostics(expected=None):
    deadline = time.monotonic() + 35
    while True:
        raw = run('exec-out', 'run-as', 'cn.walkpaint', 'cat', 'shared_prefs/recording_diagnostics.xml')
        root = ET.fromstring(raw)
        values = {n.attrib['name']: n.attrib.get('value', n.text) for n in root}
        if not expected or all(values.get(k) == v for k,v in expected.items()):
            return values
        if time.monotonic() >= deadline:
            raise AssertionError('Batched diagnostic fields did not persist within 35 seconds')
        time.sleep(2)

def foreground():
    return 'isForeground=true' in shell('dumpsys', 'activity', 'services', 'cn.walkpaint')

def launch():
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    shell('am', 'start', '--display', '0', '-n', 'cn.walkpaint/.MainActivity')
    # MuMu may give apps a separate physical display. Move only this app task.
    text = shell('am', 'stack', 'list')
    for block in text.split('RootTask id=')[1:]:
        match = re.match(r'(\d+)[^\n]*displayId=(\d+)', block)
        if match and 'cn.walkpaint/cn.walkpaint.MainActivity' in block and match.group(2) != '0':
            shell('am', 'display', 'move-stack', match.group(1), '0')
    time.sleep(5)

def ui(name=None):
    shell('rm', '-f', '/sdcard/wp-v04-ui.xml')
    for attempt in range(2):
        try:
            run('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/wp-v04-ui.xml', allow_completed_dump=True)
            break
        except RuntimeError:
            if attempt:
                raise
            time.sleep(3)
    raw = shell('cat', '/sdcard/wp-v04-ui.xml')
    if name:
        (out / (name + '.xml')).write_text(raw, encoding='utf-8')
    return ET.fromstring(raw)

def tap(label, exact=False):
    root = ui()
    for n in root.iter('node'):
        values = [n.attrib.get('text', ''), n.attrib.get('content-desc', '')]
        if any((v == label if exact else label in v) for v in values):
            nums = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
            if len(nums) == 4 and nums[2] > nums[0] and nums[3] > nums[1]:
                shell('input', '-d', '0', 'tap', str((nums[0]+nums[2])//2), str((nums[1]+nums[3])//2))
                time.sleep(1)
                return
    raise AssertionError('UI control not found: ' + label)

def shot(name):
    # MuMu requires the physical display ID, not Android's logical display number.
    display = re.search(r'Display (\d+) \(HWC display 0\)', shell('dumpsys','SurfaceFlinger','--display-id'))
    assert display, 'Default physical display not found'
    raw = run('exec-out', 'screencap', '-p', '-d', display.group(1), binary=True)
    assert raw.startswith(b'\x89PNG')
    (out / (name + '.png')).write_bytes(raw)
    ui(name)

def check_saved(label, lat, lon, accuracy=5, time_ms=None, expect=True):
    before = count()
    parts = ['cmd', 'location', 'providers', 'set-test-provider-location', 'gps',
             '--location', f'{lat},{lon}', '--accuracy', str(accuracy)]
    if time_ms is not None:
        parts += ['--time', str(time_ms)]
    shell(*parts)
    deadline = time.monotonic() + 7
    after = before
    while time.monotonic() < deadline:
        time.sleep(1)
        after = count()
        if expect and after > before:
            break
    if expect:
        assert after > before, f'{label}: no new SQLite row ({before}->{after})'
    else:
        assert after == before, f'{label}: filtered fix inserted'
    checks[label] = {'before': before, 'after': after, 'pass': True}
    print(label, 'PASS', before, '->', after, flush=True)
    # Maintain plausible walking speed between injected fixes.
    time.sleep(2)

initial = rows()
initial_count = count()
permission_state = shell('dumpsys', 'package', 'cn.walkpaint')
permissions = ['ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION', 'ACCESS_BACKGROUND_LOCATION']
granted = {key: bool(re.search(key + r': granted=true', permission_state)) for key in permissions}
mock_state = shell('appops', 'get', '2000', 'android:mock_location')
mode = re.search(r'(?:MOCK_LOCATION|mock_location):\s*(allow|ignore|deny|default|foreground)', mock_state)
restore_mode = mode.group(1) if mode else 'default'
location_enabled = shell('cmd', 'location', 'is-location-enabled').strip()
rotation = shell('wm', 'user-rotation', '-d', '0').strip().split()
provider_set = mock_set = permission_changed = False
try:
    # Existing rows are the baseline before the actual upgrade, never reconstructed fixtures.
    result = run('install', '-r', str(Path(args.apk).resolve()), timeout=120)
    assert 'Success' in result, result
    assert 'versionCode=5' in shell('dumpsys', 'package', 'cn.walkpaint')
    assert all(initial[t] <= rows()[t] for t in initial)
    checks['upgrade_history_retained'] = {'points': initial_count, 'pass': True}
    shell('wm', 'user-rotation', '-d', '0', 'lock', '0')
    shell('cmd', 'location', 'set-location-enabled', 'true')
    launch()

    # Exercise permission return on an already-consented automatic recording session.
    prefs = run('exec-out', 'run-as', 'cn.walkpaint', 'cat', 'shared_prefs/footprint_preferences.xml')
    assert 'name="automatic_enabled" value="true"' in prefs, 'Requires an already-consented emulator session'
    if not args.quick:
        permission_changed = True
        for key in ['ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION']:
            shell('pm', 'revoke', 'cn.walkpaint', 'android.permission.' + key)
        launch()
        tap('定位我')
        root = ui()
        allow_nodes = [n for n in root.iter('node') if n.attrib.get('resource-id', '').endswith('permission_allow_foreground_only_button')]
        assert allow_nodes, 'Expected actual Android permission dialog'
        nums = list(map(int, re.findall(r'\d+', allow_nodes[0].attrib['bounds'])))
        shell('input', 'tap', str((nums[0]+nums[2])//2), str((nums[1]+nums[3])//2))
        time.sleep(3)
        assert foreground(), 'Permission return did not automatically start service'
        checks['permission_return_auto_start'] = True
    else:
        # MuMu package replacement can defer service dispatch beyond Activity launch.
        # Require observed foreground state within a bound, never assume launch success.
        deadline = time.monotonic() + 20
        while not foreground() and time.monotonic() < deadline:
            time.sleep(1)
        assert foreground(), 'Existing automatic session did not resume on visible launch'
        checks['existing_session_auto_resume'] = True

    shell('appops', 'set', '2000', 'android:mock_location', 'allow'); mock_set = True
    shell('cmd', 'location', 'providers', 'add-test-provider', 'gps', '--requiresSatellite', '--supportsSpeed', '--supportsBearing')
    provider_set = True
    shell('cmd', 'location', 'providers', 'set-test-provider-enabled', 'gps', 'true')
    # Start next to the newest emulator point, keeping reruns plausible without deleting history.
    with database() as db:
        latest = db.execute('SELECT lat,lon FROM points p JOIN trips t ON t.id=p.trip_id WHERE t.demo=0 ORDER BY p.time DESC,p.id DESC LIMIT 1').fetchone()
    lat, lon = latest if latest else (31.231,121.4742)
    if args.quick:
        initial_saved = int(diagnostics().get('saved_count', 0))
        check_saved('quick_foreground',lat+0.00008,lon+0.00008)
        shell('input','-d','0','keyevent','KEYCODE_HOME')
        check_saved('quick_home',lat+0.00016,lon+0.00016)
        shell('input','keyevent','KEYCODE_SLEEP')
        power = shell('dumpsys','power')
        assert 'mWakefulness=Asleep' in power or 'mWakefulness=Dozing' in power
        check_saved('quick_screen_off',lat+0.00024,lon+0.00024)
        assert not re.search(r"PARTIAL_WAKE_LOCK[^\n]*'WalkPaint:recording'",shell('dumpsys','power'))
        checks['no_app_cpu_wake_lock'] = True
        assert foreground()
        # Only one bounded wait: prove the routine batch reaches disk on this APK.
        time.sleep(31)
        persisted = diagnostics()
        assert int(persisted.get('saved_count',0)) >= initial_saved+3
        checks['routine_diagnostics_batch_persisted'] = True
        final = rows()
        assert all(initial[t] <= final[t] for t in initial)
        checks['all_original_rows_retained'] = True
        checks['added_synthetic_points'] = count()-initial_count
        checks['apk_sha256'] = hashlib.sha256(Path(args.apk).read_bytes()).hexdigest()
        checks['long_gap_and_pause_chain_rerun'] = False
        (out/'diagnostic-fields.json').write_text(json.dumps(persisted,ensure_ascii=False,indent=2),encoding='utf-8')
        (out/'foreground-service.txt').write_text(shell('dumpsys','activity','services','cn.walkpaint'),encoding='utf-8')
        launch()
        tap('我的',exact=True)
        tap('查看记录诊断')
        shot('final-diagnostics')
        tap('关闭',exact=True)
        print('V04_FINAL_QUALITY_SMOKE_PASS',json.dumps(checks,ensure_ascii=False),flush=True)
        sys.exit(0)
    for i in range(1,4):
        check_saved('short_walk_'+str(i),lat+i*0.00008,lon+i*0.00008)
    lat += 3*0.00008; lon += 3*0.00008
    check_saved('stationary_drift',lat+0.000005,lon+0.000005,accuracy=12,expect=False)
    diagnostics({'filter_reason': 'STATIONARY_OR_SMALL_MOVE'})
    check_saved('poor_accuracy',lat+0.00008,lon+0.00008,accuracy=80,expect=False)
    diagnostics({'filter_reason': 'UNRELIABLE_ACCURACY'})
    check_saved('zero_accuracy',lat+0.00008,lon+0.00008,accuracy=0,expect=False)
    diagnostics({'filter_reason': 'UNRELIABLE_ACCURACY'})
    lat += 0.00008; lon += 0.00008
    check_saved('fresh_monotonic_old_wall_time',lat,lon,time_ms=int(time.time()*1000)-240000)

    shell('input', '-d', '0', 'keyevent', 'KEYCODE_HOME')
    assert 'cn.walkpaint/.MainActivity' not in shell('dumpsys', 'activity', 'activities').split('ResumedActivity:')[-1].split('\n')[0]
    for i in range(1,3):
        check_saved('home_background_'+str(i),lat+i*0.00008,lon+i*0.00008)
    lat += 2*0.00008; lon += 2*0.00008
    shell('input', 'keyevent', 'KEYCODE_SLEEP')
    power = shell('dumpsys', 'power')
    assert 'mWakefulness=Asleep' in power or 'mWakefulness=Dozing' in power, 'Screen sleep not proven'
    (out / 'screen-off-power.txt').write_text(power,encoding='utf-8')
    for i in range(1,3):
        check_saved('screen_off_'+str(i),lat+i*0.00008,lon+i*0.00008)
    lat += 2*0.00008; lon += 2*0.00008
    assert foreground()
    checks['screen_off_foreground_service'] = True
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    launch()
    check_saved('return_foreground',lat+0.00008,lon+0.00008)
    lat += 0.00008; lon += 0.00008

    # A real interruption, long enough to create a new persisted segment, then provider recovery.
    shell('cmd', 'location', 'providers', 'set-test-provider-enabled', 'gps', 'false')
    before_gap = count()
    for _ in range(5):
        time.sleep(10)
        assert count() == before_gap
    shell('cmd', 'location', 'providers', 'set-test-provider-enabled', 'gps', 'true')
    check_saved('gps_reenabled_after_gap',lat+0.00008,lon+0.00008)
    assert int(diagnostics()['last_gap_ms']) > 45000
    with database() as db:
        segments = db.execute('SELECT segment FROM points p JOIN trips t ON t.id=p.trip_id WHERE t.demo=0 ORDER BY time DESC,p.id DESC LIMIT 2').fetchall()
    assert segments[0][0] > segments[1][0], 'Gap should break visual line'
    checks['gap_persisted_and_line_broken'] = True
    launch()
    time.sleep(4)
    tap('定位我')
    shot('map')
    tap('足迹',exact=True); shot('history')
    tap('我的',exact=True); shot('profile')
    tap('查看记录诊断'); shot('diagnostics')
    diagnostic_ui = '\n'.join(n.attrib.get('text','') for n in ui().iter('node'))
    assert '服务启动' in diagnostic_ui and 'GPS 最近回调' in diagnostic_ui and '最近入库' in diagnostic_ui
    scroll = next(n for n in ui().iter('node') if n.attrib.get('scrollable') == 'true')
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',scroll.attrib['bounds']))
    shell('input','-d','0','swipe',str((x1+x2)//2),str(y2-30),str((x1+x2)//2),str(y1+30),'500')
    time.sleep(1)
    shot('diagnostics-bottom')
    tap('关闭',exact=True)
    # Recreate only the activity/process after a deliberate force-stop: restart via visible UI.
    # Force-stop is expected to stop recording; no claim of automatic bypass.
    persisted = diagnostics()
    shell('am', 'force-stop', 'cn.walkpaint')
    assert not foreground()
    launch()
    time.sleep(3)
    assert foreground()
    recovered = diagnostics()
    assert int(recovered['saved_count']) >= int(persisted['saved_count'])
    assert int(recovered['start_count']) > int(persisted['start_count'])
    checks['visible_reopen_after_force_stop'] = True
    # Pause must prevent writes; this version never acquires a CPU wake lock.
    tap('我的',exact=True)
    toggle = next(n for n in ui().iter('node') if n.attrib.get('checkable') == 'true' and n.attrib.get('checked') == 'true')
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',toggle.attrib['bounds']))
    shell('input','-d','0','tap',str((x1+x2)//2),str((y1+y2)//2))
    time.sleep(2)
    assert not foreground()
    assert not re.search(r"PARTIAL_WAKE_LOCK[^\n]*'WalkPaint:recording'",shell('dumpsys','power'))
    assert diagnostics()['stop_reason'] == '已暂停自动足迹'
    check_saved('paused_no_write',lat+0.00016,lon+0.00016,expect=False)
    # Restore the user's already-enabled emulator session through the real consent UI.
    shell('input','-d','0','tap',str((x1+x2)//2),str((y1+y2)//2))
    tap('启用自动足迹',exact=True)
    time.sleep(2)
    assert foreground()
    checks['pause_no_write_and_resume_without_wake_lock'] = True
    final = rows()
    assert all(initial[t] <= final[t] for t in initial), 'Pre-upgrade rows were changed/deleted'
    checks['all_original_rows_retained'] = True
    checks['added_synthetic_points'] = count()-initial_count
    (out / 'diagnostic-fields.json').write_text(json.dumps(recovered,ensure_ascii=False,indent=2),encoding='utf-8')
    service = shell('dumpsys','activity','services','cn.walkpaint')
    (out / 'foreground-service.txt').write_text(service,encoding='utf-8')
    checks['apk_sha256'] = hashlib.sha256(Path(args.apk).read_bytes()).hexdigest()
    print('V04_EMULATOR_ACCEPTANCE_PASS', json.dumps(checks,ensure_ascii=False))
finally:
    # Independent cleanup attempts: provider cleanup failure must not skip app-op restoration.
    cleanup_errors = []
    actions = []
    if provider_set:
        actions.append(('cmd','location','providers','remove-test-provider','gps'))
    if mock_set:
        actions.append(('appops','set','2000','android:mock_location',restore_mode))
    if permission_changed:
        for key in permissions:
            actions.append(('pm','grant' if granted[key] else 'revoke','cn.walkpaint','android.permission.'+key))
    actions.append(('cmd','location','set-location-enabled',location_enabled))
    if rotation:
        actions.append(('wm','user-rotation','-d','0',*rotation))
    actions.append(('input','keyevent','KEYCODE_WAKEUP'))
    actions.append(('rm','-f','/sdcard/wp-v04-ui.xml'))
    for action in actions:
        try:
            shell(*action)
        except Exception as exc:
            cleanup_errors.append(str(exc))
    checks['cleanup_errors'] = cleanup_errors
    (out / 'acceptance.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    if cleanup_errors:
        raise RuntimeError('Cleanup incomplete: '+str(cleanup_errors))
    print('MOCK_PROVIDER_AND_PERMISSIONS_RESTORED')

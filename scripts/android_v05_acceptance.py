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
p.add_argument('--output', default='verification/v05')
p.add_argument('--quick', action='store_true', help='Final text/render check on existing painted emulator data; preserves all rows')
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
    # MuMu resets display rotation during instrumentation/task recreation. Reapply
    # the acceptance viewport AFTER launch; a black screenshot cannot pass UI QA.
    shell('wm','user-rotation','-d','0','lock','0')
    time.sleep(5)

def ui(name=None):
    shell('rm', '-f', '/sdcard/wp-v05-ui.xml')
    for attempt in range(2):
        try:
            run('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/wp-v05-ui.xml', allow_completed_dump=True)
            break
        except RuntimeError:
            if attempt:
                raise
            time.sleep(3)
    raw = shell('cat', '/sdcard/wp-v05-ui.xml')
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

def coverage():
    with database() as db:
        return db.execute('SELECT COALESCE(SUM(end-start),0) FROM coverage').fetchone()[0]

def select_pack():
    tap('选择道路包文件')
    root=ui()
    texts=[n.attrib.get('text','') for n in root.iter('node')]
    if 'wp-oxford-roads.geojson' not in texts:
        # Android's own DocumentsUI roots, not a direct app import backdoor.
        menu=[n for n in root.iter('node') if n.attrib.get('resource-id','').endswith('toolbar')]
        shell('input','tap','36','62')
        time.sleep(1)
        try: tap('Downloads',exact=True)
        except AssertionError: tap('下载',exact=True)
    tap('wp-oxford-roads.geojson',exact=True)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        root=ui()
        if any('已导入 6 条真实道路' in n.attrib.get('text','') for n in root.iter('node')):
            return
        time.sleep(1)
    raise AssertionError('Road import did not finish in actual picker flow')

def focus_roads():
    tap('青绿：已涂道路')
    tap('查看地图',exact=True)
    shell('wm','user-rotation','-d','0','lock','0')
    time.sleep(3)

def assert_colored_render(name):
    # Read only the real screencap, never create or alter a screenshot.
    import struct,zlib
    data=(out/(name+'.png')).read_bytes()
    pos=8; compressed=b''
    while pos<len(data):
        size=struct.unpack('>I',data[pos:pos+4])[0]; kind=data[pos+4:pos+8]; body=data[pos+8:pos+8+size]; pos+=12+size
        if kind==b'IHDR': width,height,depth,color,*_=struct.unpack('>IIBBBBB',body)
        elif kind==b'IDAT': compressed+=body
    assert depth==8 and color in (2,6), 'Unexpected screencap format'
    channels=3 if color==2 else 4; stride=width*channels
    raw=zlib.decompress(compressed); previous=bytearray(stride); count=0
    for y in range(height):
        start=y*(stride+1); mode=raw[start]; row=bytearray(raw[start+1:start+1+stride])
        for x in range(stride):
            left=row[x-channels] if x>=channels else 0; up=previous[x]; ul=previous[x-channels] if x>=channels else 0
            if mode==1: value=left
            elif mode==2: value=up
            elif mode==3: value=(left+up)//2
            elif mode==4:
                estimate=left+up-ul; distances=[abs(estimate-left),abs(estimate-up),abs(estimate-ul)]
                value=[left,up,ul][distances.index(min(distances))]
            elif mode==0: value=0
            else: raise AssertionError('Invalid PNG filter')
            row[x]=(row[x]+value)&255
        # The middle map surface excludes top statistic cards and bottom controls.
        if height*0.30<y<height*0.73:
            for x in range(int(width*0.10),int(width*0.90)):
                r,g,b=row[x*channels:x*channels+3]
                if r<35 and 100<g<160 and 85<b<140: count+=1
        previous=row
    assert count>100, f'No colored road on real map surface: {count}'
    checks['colored_road_pixels_on_real_map']=count

initial=rows()
initial_count=count()
with database() as db: initial_schema=db.execute('PRAGMA user_version').fetchone()[0]
checks['before_schema']=initial_schema
checks['before_points']=initial_count
mock_state=shell('appops','get','2000','android:mock_location')
mode=re.search(r'(?:MOCK_LOCATION|mock_location):\s*(allow|ignore|deny|default|foreground)',mock_state)
restore_mode=mode.group(1) if mode else 'default'
location_enabled=shell('cmd','location','is-location-enabled').strip()
rotation=shell('wm','user-rotation','-d','0').strip().split()
provider_set=mock_set=False
cleanup_errors=[]
fixture=Path('app/src/test/resources/oxford-roads.geojson')
try:
    assert 'Success' in run('install','-r',str(Path(args.apk).resolve()),timeout=120)
    assert 'versionCode=6' in shell('dumpsys','package','cn.walkpaint')
    launch() # SQLiteOpenHelper migration occurs on first Application start.
    with database() as db: assert db.execute('PRAGMA user_version').fetchone()[0]==2
    assert all(initial[t] <= rows()[t] for t in initial)
    checks['cover_upgrade_original_rows_retained']=True
    shell('wm','user-rotation','-d','0','lock','0')
    shell('cmd','location','set-location-enabled','true')
    launch()
    shot('before-road-pack')
    assert 'name="automatic_enabled" value="true"' in run('exec-out','run-as','cn.walkpaint','cat','shared_prefs/footprint_preferences.xml'), 'Requires existing consent'
    run('push',str(fixture.resolve()),'/sdcard/Download/wp-oxford-roads.geojson')
    # Works on first run and later repetitions.
    try: tap('导入真实道路包')
    except AssertionError: tap('青绿：已涂道路')
    select_pack()
    shot('imported-road-pack')
    tap('查看地图',exact=True)
    time.sleep(3)
    with database() as db: assert db.execute('SELECT COUNT(*) FROM roads').fetchone()[0]==6
    checks['real_document_picker_import']=True
    print('PICKER_IMPORT_PASS',flush=True)
    assert 'Success' in run('install','-r','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',timeout=120)
    instrumentation=shell('am','instrument','-w','cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation')
    (out/'database-tests.txt').write_text(instrumentation,encoding='utf-8')
    assert 'V05_ISOLATED_DATABASE_PASS' in instrumentation,instrumentation
    checks['isolated_schema_and_backup_roundtrip']=True
    launch()
    deadline=time.monotonic()+20
    while not foreground() and time.monotonic()<deadline: time.sleep(1)
    assert foreground()
    shell('appops','set','2000','android:mock_location','allow'); mock_set=True
    shell('cmd','location','providers','add-test-provider','gps','--requiresSatellite','--supportsSpeed','--supportsBearing'); provider_set=True
    shell('cmd','location','providers','set-test-provider-enabled','gps','true')
    geometry=json.loads(fixture.read_text(encoding='utf-8'))['features'][0]['geometry']['coordinates']
    def at(distance):
        import math
        for a,b in zip(geometry,geometry[1:]):
            lat=(a[1]+b[1])/2
            length=math.hypot((b[1]-a[1])*111195,(b[0]-a[0])*111195*math.cos(math.radians(lat)))
            if distance<=length:
                t=distance/length
                # Four meters of GPS noise: painted geometry must follow the OSM
                # road rather than merely drawing the injected GPS straight chord.
                return a[1]+(b[1]-a[1])*t+4.0/111195,a[0]+(b[0]-a[0])*t
            distance-=length
        raise AssertionError('fixture too short')
    if args.quick:
        before_coverage=coverage()
        assert before_coverage>0, 'Quick check requires previously verified painted emulator data'
        with database() as db:
            latest=db.execute('SELECT lat,lon FROM points ORDER BY time DESC,id DESC LIMIT 1').fetchone()
        check_saved('final_apk_new_fix',latest[0]+0.0002,latest[1]+0.0003)
        assert abs(coverage()-before_coverage)<0.3, 'A gap or off-road quick fix must not extend coverage'
        launch(); focus_roads(); shot('final-apk-map'); assert_colored_render('final-apk-map')
        assert any('仅确认匹配的道路上色' in n.attrib.get('text','') for n in ui().iter('node'))
        assert all(initial[t]<=rows()[t] for t in initial)
        checks.update({'all_original_rows_retained':True,'new_status_text_rendered':True,
            'painted_meters':coverage(),'apk_sha256':hashlib.sha256(Path(args.apk).read_bytes()).hexdigest(),
            'added_emulator_gps_points':count()-initial_count,'pass':True,'long_chain_repeated':False})
        print('V05_FINAL_APK_SMOKE_PASS',json.dumps(checks,ensure_ascii=False),flush=True)
        sys.exit(0)
    with database() as db:
        latest=db.execute('SELECT lat,lon FROM points WHERE time=(SELECT MAX(time) FROM points) ORDER BY id DESC LIMIT 1').fetchone()
    close_to_60=latest and abs(latest[0]-at(60)[0])+abs(latest[1]-at(60)[1])<0.00001
    first_pass=[18,32,46,60] if close_to_60 else [60,46,32,18]
    repeated=[46,32,18,32,46,60] if close_to_60 else [32,46,60,46,32,18]
    for i,d in enumerate(first_pass):
        check_saved('real_geometry_forward_'+str(i),*at(d))
    forward=coverage()
    assert forward>=40,forward
    checks['forward_painted_meters']=forward
    for i,d in enumerate(repeated):
        check_saved('reverse_repeat_'+str(i),*at(d))
    assert abs(coverage()-forward)<0.3,(forward,coverage())
    checks['reversed_repeated_traversal_deduplicated']=True
    # Accepted by the recorder (<=35m), refused by road matcher (>20m).
    time.sleep(5)
    poor_distance=94 if close_to_60 else 60
    check_saved('unreliable_for_road',*at(poor_distance),accuracy=25)
    assert abs(coverage()-forward)<0.3
    checks['poor_road_accuracy_not_painted']=True
    checks['gps_fixture_offset_meters']=4
    print('ROAD_COVERAGE_AND_DEDUP_PASS',forward,flush=True)
    launch()
    focus_roads()
    shot('colored-real-road')
    assert_colored_render('colored-real-road')
    # Reopen the app: persisted coverage must survive process recreation.
    shell('am','force-stop','cn.walkpaint')
    launch()
    assert abs(coverage()-forward)<0.3
    tap('青绿：已涂道路')
    select_pack()
    assert abs(coverage()-forward)<0.3
    tap('查看地图',exact=True)
    checks['process_reopen_and_reimport_persistent']=True
    # No location bridge is generated after the acceptance pause (>45 seconds).
    time.sleep(47)
    check_saved('after_gap',*at(18 if close_to_60 else 94))
    assert abs(coverage()-forward)<0.3
    checks['gap_not_painted']=True
    focus_roads()
    shot('final-road-map')
    assert_colored_render('final-road-map')
    final=rows()
    assert all(initial[t]<=final[t] for t in initial)
    checks['all_original_rows_retained']=True
    checks['added_emulator_gps_points']=count()-initial_count
    checks['apk_sha256']=hashlib.sha256(Path(args.apk).read_bytes()).hexdigest()
    checks['fixture_sha256']=hashlib.sha256(fixture.read_bytes()).hexdigest()
    checks['serial']=args.serial
    checks['pass']=True
    print('V05_REAL_ROAD_EMULATOR_PASS',json.dumps(checks,ensure_ascii=False),flush=True)
finally:
    def clean(name,*parts):
        try: shell(*parts)
        except Exception as e: cleanup_errors.append(name+': '+str(e))
    if provider_set: clean('provider','cmd','location','providers','remove-test-provider','gps')
    if mock_set: clean('mock_appop','appops','set','2000','android:mock_location',restore_mode)
    clean('location','cmd','location','set-location-enabled',location_enabled)
    if len(rotation)>=2: clean('rotation','wm','user-rotation','-d','0',*rotation)
    clean('temporary_fixture','rm','-f','/sdcard/Download/wp-oxford-roads.geojson','/sdcard/wp-v05-ui.xml')
    checks['cleanup_errors']=cleanup_errors
    (out/'acceptance.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    if cleanup_errors: raise RuntimeError('Cleanup failed: '+str(cleanup_errors))

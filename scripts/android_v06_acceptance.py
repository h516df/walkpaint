#!/usr/bin/env python3
"""Real UI/network acceptance on the allowed emulator, preserving all old rows."""
import sys
from pathlib import Path
import time
import re
import json
import hashlib

# Reuse the audited ADB, in-memory SQLite, touch and actual screencap helpers.
sys.argv = [sys.argv[0], '--output', 'verification/v06-final']
shared = {}
source = Path('scripts/android_v05_acceptance.py').read_text(encoding='utf-8')
exec(compile(source.split('\ninitial=rows()')[0], 'android_v05_helpers', 'exec'), shared)
globals().update({k: v for k, v in shared.items() if not k.startswith('__')})

def texts():
    return '\n'.join(n.attrib.get('text', '') for n in ui().iter('node'))

def road_rows():
    with database() as db:
        return tuple(db.execute('SELECT * FROM roads ORDER BY id'))

initial = rows()
original_roads = road_rows()
original_coverage = coverage()
state = shell('appops','get','2000','android:mock_location')
mode = re.search(r'(?:MOCK_LOCATION|mock_location):\s*(allow|ignore|deny|default|foreground)', state)
old_mode = mode.group(1) if mode else 'default'
location_enabled = shell('cmd','location','is-location-enabled').strip()
rotation = shell('wm','user-rotation','-d','0').strip().split()
cleanup_errors = []
provider_set = mock_set = False
try:
    shell('am','force-stop','cn.walkpaint')
    launch()
    tap('青绿：已涂道路')
    tap('下载附近道路', exact=True)
    if '同意并下载本次' in texts():
        # Previous smoke supplied a recent fix; do not pretend it is stale.
        tap('暂不下载', exact=True)
        checks['stale_location_ui']='not tested: emulator retained recent fix'
    else:
        assert '取得当前位置后再下载' in texts()
        checks['stale_location_ui']=True
    assert road_rows() == original_roads
    tap('查看地图', exact=True)
    shell('appops','set','2000','android:mock_location','allow'); mock_set=True
    shell('cmd','location','set-location-enabled','true')
    shell('cmd','location','providers','add-test-provider','gps','--requiresSatellite','--supportsSpeed','--supportsBearing'); provider_set=True
    shell('cmd','location','providers','set-test-provider-enabled','gps','true')
    shell('cmd','location','providers','set-test-provider-location','gps','--location','51.754,-1.255','--accuracy','5')
    time.sleep(4)
    tap('青绿：已涂道路')
    tap('下载附近道路', exact=True)
    shot('download-consent')
    assert 'overpass-api.de' in texts() and '不发送完整足迹' in texts()
    tap('暂不下载', exact=True)
    assert road_rows() == original_roads
    checks['consent_decline_keeps_roads'] = True
    tap('下载附近道路', exact=True)
    tap('同意并下载本次', exact=True)
    shot('download-loading')
    deadline=time.monotonic()+90
    result=''
    while time.monotonic()<deadline:
        result=texts()
        if '已保存 ' in result or any(x in result for x in ['原足迹和道路保留', '下载已取消']): break
        time.sleep(2)
    shot('download-result')
    print('DOWNLOAD_RESULT', next((t for t in result.splitlines() if '已保存 ' in t or '原足迹和道路保留' in t), 'missing result'), flush=True)
    assert '现在可以离线走路上色' in result, 'Real nearby road download did not complete'
    downloaded=road_rows()
    assert len(downloaded)>=len(original_roads)
    assert all(initial[t] <= rows()[t] for t in initial)
    checks['real_network_to_atomic_import']={'roads_before':len(original_roads),'roads_after':len(downloaded),'pass':True}
    checks['all_original_gps_rows_retained']=True
    checks['coverage_after_road_refresh']=coverage()
    # Repeat download must be idempotent for unchanged OSM versions/geometries.
    tap('下载附近道路', exact=True)
    tap('同意并下载本次', exact=True)
    deadline=time.monotonic()+90
    while time.monotonic()<deadline:
        result=texts()
        if '现在可以离线走路上色' in result or '原足迹和道路保留' in result: break
        time.sleep(2)
    assert '现在可以离线走路上色' in result
    assert road_rows() == downloaded
    checks['repeat_download_no_duplicate_roads']=True
    tap('查看地图', exact=True)
    shot('downloaded-road-map')
    shell('am','force-stop','cn.walkpaint')
    launch()
    assert road_rows() == downloaded
    assert all(initial[t] <= rows()[t] for t in initial)
    checks['restart_preserves_download_and_gps']=True
    shot('after-restart')
    # MuMu Wi-Fi disable also severs ADB; network fault cases run in JVM transport tests.
    checks['offline_ui']='not tested: prior attempt disconnected MuMu ADB; see v06-nearby evidence'
    checks['apk_sha256']=hashlib.sha256(Path('app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest()
    print('V06_NEARBY_ROADS_UI_NETWORK_PASS',flush=True)
finally:
    for label,action in [
        ('provider',lambda:shell('cmd','location','providers','remove-test-provider','gps') if provider_set else None),
        ('mock',lambda:shell('appops','set','2000','android:mock_location',old_mode) if mock_set else None),
        ('location',lambda:shell('cmd','location','set-location-enabled',location_enabled)),
        ('rotation',lambda:shell('wm','user-rotation','-d','0',*rotation)),
        ('temporary_ui',lambda:shell('rm','-f','/sdcard/wp-v05-ui.xml','/sdcard/walkpaint-takeover.xml'))]:
        try: action()
        except Exception as e: cleanup_errors.append(label+':'+type(e).__name__)
    (out/'acceptance.json').write_text(json.dumps({'checks':checks,'cleanup_errors':cleanup_errors},ensure_ascii=False,indent=2),encoding='utf-8')
    assert not cleanup_errors, cleanup_errors

"""Same-engine AMap UI regression on the dedicated AVD; never installs or clears data.
Run amap_runtime_evidence.py against the same APK as well. Seed only the owned public China
fixture with the existing instrumentation beforehand. Owner phone acceptance remains separate.
"""
import json
import re
import time
import xml.etree.ElementTree as ET
from pathlib import Path
import argparse
import subprocess
from google_avd_guard import require_walkpaint_google_avd
from amap_runtime_evidence import ADB, SERIAL

def run(*args, timeout=30, binary=False):
    result=subprocess.run([ADB,'-s',SERIAL,*map(str,args)],capture_output=True,timeout=timeout)
    if result.returncode:
        raise RuntimeError('Dedicated AVD command failed')
    return result.stdout if binary else result.stdout.decode('utf-8','replace').strip()

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk',type=Path,required=True)
parser.add_argument('--out',type=Path,required=True)
args=parser.parse_args()
require_walkpaint_google_avd(ADB,SERIAL)

out = args.out
out.mkdir(parents=True,exist_ok=True)
import hashlib
apk=args.apk
assert 'WalkPaint_Google_API36' in run('emu','avd','name').splitlines()
evidence = {'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
            'phone_path':'not_verified'}

def installed_hash():
    paths=run('shell','pm','path','cn.walkpaint').splitlines()
    assert len(paths)==1 and paths[0].startswith('package:')
    return run('shell','sha256sum',paths[0].removeprefix('package:')).split()[0]

assert installed_hash()==evidence['apk_sha256'], 'Installed APK does not match the tested artifact'

def nodes():
    run('shell','uiautomator','dump','--compressed','/sdcard/wp-codex-ui.xml',timeout=25)
    return list(ET.fromstring(run('exec-out','cat','/sdcard/wp-codex-ui.xml')).iter('node'))

def tap(label):
    for node in nodes():
        if node.get('text')==label or node.get('content-desc')==label:
            bounds = list(map(int,re.findall(r'\d+',node.get('bounds',''))))
            assert len(bounds)==4
            run('shell','input','tap',(bounds[0]+bounds[2])//2,(bounds[1]+bounds[3])//2)
            time.sleep(1.5)
            return
    raise RuntimeError('Visible control missing: '+label)

def report():
    path='/sdcard/Android/data/cn.walkpaint/files/field-report.txt'
    text=''
    for attempt in range(8):
        result=run('shell','am','instrument','-w','-e','fieldReportProbe','true',
                   'cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation',timeout=45)
        assert 'FIELD_REPORT_PROBE_PASS' in result, 'Install matching same-signature test instrumentation'
        text=run('exec-out','cat',path)
        if '引擎判定=正在用高德' in text and '地图加载完成=true' in text: break
    assert '引擎判定=正在用高德' in text and '地图加载完成=true' in text
    match = re.search(r'缩放=([\d.]+) 中心=([-\d.]+),([-\d.]+)',text)
    assert match
    return {'zoom':float(match[1]),'lat':float(match[2]),'lon':float(match[3]),
            'map_loaded':True,'engine':'amap'}

def scene(name):
    evidence[name]=report()
    (out/('v043-'+name+'.png')).write_bytes(run('exec-out','screencap','-p',binary=True))

try:
    run('shell','am','start','-W','-n','cn.walkpaint/.MainActivity')
    for attempt in range(15):
        current=report()
        if current['zoom']>=16.5: break
    assert current['zoom']>=16.5
    scene('release-near')
    initial_texts=[node.get('text') for node in nodes() if node.get('text')]
    points_index=initial_texts.index('足迹点')
    initial_points=initial_texts[points_index-1]
    assert initial_points.isdigit() and int(initial_points)>0
    tap('足迹')
    texts=[node.get('text') for node in nodes() if node.get('text')]
    assert any('探索' in text or '足迹' in text for text in texts)
    evidence['footprints_visible']=True
    (out/'v043-footprints.png').write_bytes(run('exec-out','screencap','-p',binary=True))
    tap('地图')
    for attempt in range(15):
        current=report()
        if current['zoom']>=16.5: break
    assert current['zoom']>=16.5
    scene('tab-return')
    assert evidence['tab-return']['zoom']>=16.5
    run('shell','input','swipe','820','900','280','900','550')
    time.sleep(3)
    scene('pan')
    assert abs(evidence['pan']['lon']-evidence['tab-return']['lon'])>0.0002
    tap('定位我')
    time.sleep(3)
    scene('pan-return')
    assert abs(evidence['pan-return']['lon']-116.391423)<0.001
    run('shell','am','force-stop','cn.walkpaint')
    run('shell','am','start','-W','-n','cn.walkpaint/.MainActivity')
    for attempt in range(15):
        current=report()
        if current['zoom']>=16.5: break
    assert current['zoom']>=16.5
    scene('reopen')
    assert abs(evidence['reopen']['lon']-116.391423)<0.001
    final_texts=[node.get('text') for node in nodes() if node.get('text')]
    assert final_texts[final_texts.index('足迹点')-1]==initial_points
    evidence['today_points_still_visible']=int(initial_points)
    assert installed_hash()==evidence['apk_sha256'], 'Artifact changed during UI acceptance'
    evidence['passed']=True
finally:
    run('shell','rm','-f','/sdcard/wp-codex-ui.xml')
    (out/'v043-ui.json').write_text(json.dumps(evidence,indent=2),encoding='utf-8')
    print(json.dumps(evidence),flush=True)

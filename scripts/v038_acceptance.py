"""Scoped recovery acceptance. Dedicated Google AVD only; no phone, deletion or private upload."""
import hashlib,json,subprocess,sys,time,sqlite3,re,xml.etree.ElementTree as ET
from pathlib import Path
from google_avd_guard import require_walkpaint_google_avd
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'verification/v038';OUT.mkdir(parents=True,exist_ok=True)
ADB=r'D:\Apps\Android\sdk\platform-tools\adb.exe'
SERIAL='emulator-5556'
require_walkpaint_google_avd(ADB,SERIAL)
def run(*args,binary=False,timeout=60):
    p=subprocess.run([ADB,'-s',SERIAL,*map(str,args)],capture_output=True,timeout=timeout)
    if p.returncode: raise RuntimeError('adb failed: '+str(args[:3])+' '+p.stderr.decode(errors='replace')[:200])
    return p.stdout if binary else p.stdout.decode('utf-8',errors='replace').strip()
def sh(*args,**kw):return run('shell',*args,**kw)
def snapshot(name):
    sh('am','force-stop','cn.walkpaint')
    data=run('exec-out','run-as','cn.walkpaint','cat','databases/footprints.db',binary=True)
    path=OUT/(name+'.db');path.write_bytes(data)
    with sqlite3.connect(path) as db:
        assert db.execute('pragma integrity_check').fetchone()[0]=='ok'
        tables={t:db.execute('select * from '+t).fetchall() for t in ['trips','points','roads','road_matches','coverage']}
    return tables
def screen(name):
    (OUT/(name+'.png')).write_bytes(run('exec-out','screencap','-p',binary=True))
def probe(key,name):
    result=sh('am','instrument','-w','-e',key,'true','cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation',timeout=120)
    (OUT/(name+'.txt')).write_text(result,encoding='utf-8');print(result,flush=True);return result

def rows():
    db=sqlite3.connect(':memory:')
    db.deserialize(run('exec-out','run-as','cn.walkpaint','cat','databases/footprints.db',binary=True))
    assert db.execute('pragma integrity_check').fetchone()[0]=='ok'
    result={t:db.execute('select * from '+t).fetchall() for t in ['trips','points','roads','road_matches','coverage']}
    db.close();return result

def labels():
    # Reuse the existing acceptance behavior: Compose can briefly be busy while polling GPS.
    for attempt in range(3):
        try:
            sh('uiautomator','dump','--compressed','/sdcard/wp-v038-ui.xml',timeout=45)
            return ET.fromstring(run('exec-out','cat','/sdcard/wp-v038-ui.xml'))
        except (RuntimeError,subprocess.TimeoutExpired,ET.ParseError):
            if attempt==2:raise
            time.sleep(2)

def tap(label):
    hits=[n for n in labels().iter('node') if n.get('text')==label or n.get('content-desc')==label]
    assert hits,'UI label missing: '+label
    n=hits[0];x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    sh('input','tap',(x1+x2)//2,(y1+y2)//2)

def walk():
    before=rows()
    origin=max(before['points'],key=lambda p:p[4])
    lat,lon=origin[2:4]
    step=0;counts={}
    def fixes(phase,n):
        nonlocal step
        old=len(rows()['points'])
        for _ in range(n):
            step+=1
            run('emu','geo','fix',lon+0.000035*step,lat+0.000035*step,'0','6')
            time.sleep(3)
        time.sleep(2)
        counts[phase]=len(rows()['points'])-old
        print(phase+'_saved='+str(counts[phase]),flush=True)
        assert counts[phase]>=n//2,'GPS path did not save enough points: '+phase
    tap('地图');time.sleep(2)
    run('emu','geo','fix',lon,lat,'0','6');time.sleep(3);tap('定位我');time.sleep(4)
    screen('walk-before');run('logcat','-c')
    fixes('foreground',12);screen('walk-after')
    sh('input','keyevent','KEYCODE_HOME');fixes('background',6)
    sh('input','keyevent','KEYCODE_SLEEP');fixes('screen_off',6)
    sh('input','keyevent','KEYCODE_WAKEUP');sh('wm','dismiss-keyguard')
    sh('am','start','-n','cn.walkpaint/.MainActivity');time.sleep(5);screen('walk-reopened')
    logs=run('logcat','-d','-s','WalkPaintFog:D','AndroidRuntime:E')
    (OUT/'walk-render.log').write_text(logs,encoding='utf-8')
    after=snapshot('after-v038-walk')
    checks={'saved_by_phase':counts,'old_rows_preserved':all(set(before[t])<=set(after[t]) for t in before),
            'before_counts':{t:len(v) for t,v in before.items()},'after_counts':{t:len(v) for t,v in after.items()},
            'map_handover_revisions':sorted(set(map(int,re.findall(r'handover-after rev=(\d+)',logs))))}
    (OUT/'walk.json').write_text(json.dumps(checks,indent=2),encoding='utf-8')
    assert checks['old_rows_preserved'];assert len(checks['map_handover_revisions'])>=2
    print(json.dumps(checks),flush=True)
if __name__=='__main__':
    stage=sys.argv[1]
    if stage=='baseline':
        before=snapshot('before-baseline')
        for apk in ['baseline-v037.apk','baseline-tests.apk']:
            assert 'Success' in run('install','-r',OUT/apk,timeout=150)
        permissions=sh('dumpsys','package','cn.walkpaint')
        print('\n'.join(l.strip() for l in permissions.splitlines() if 'versionCode=' in l or 'ACCESS_FINE_LOCATION: granted' in l),flush=True)
        sh('am','start','-n','cn.walkpaint/.MainActivity');time.sleep(4)
        screen('baseline-map')
        result=probe('recoveryProbe','baseline-recovery')
        after=snapshot('after-baseline')
        checks={'baseline_recovery_failed':'RECORDING_RECOVERY_FAIL' in result,'old_rows_preserved':all(set(before[t])<=set(after[t]) for t in before),'before_counts':{t:len(v) for t,v in before.items()},'after_counts':{t:len(v) for t,v in after.items()}}
        (OUT/'baseline.json').write_text(json.dumps(checks,indent=2),encoding='utf-8');print(json.dumps(checks),flush=True)
    elif stage=='probe':
        probe(sys.argv[2],sys.argv[3])
    elif stage=='walk':walk()

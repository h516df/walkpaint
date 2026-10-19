"""Real UI acceptance of the selected redesign. Dedicated Google AVD only; never clears data."""
import argparse,hashlib,json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
from google_avd_guard import require_walkpaint_google_avd
from ui_acceptance_assertions import assert_day_results, assert_saved_state
from datetime import datetime

ROOT=Path(__file__).resolve().parents[1]
ADB=r'D:\Apps\Android\sdk\platform-tools\adb.exe'
from amap_runtime_evidence import SERIAL
OUT=ROOT/'verification/mobile-ui'

def run(*args,binary=False,timeout=90):
    p=subprocess.run([ADB,'-s',SERIAL,*map(str,args)],capture_output=True,timeout=timeout)
    if p.returncode:raise RuntimeError(p.stderr.decode(errors='replace')[-200:])
    return p.stdout if binary else p.stdout.decode('utf-8',errors='replace')
def shell(*args):return run('shell',*args)
def ui():
    for attempt in range(3):
        text=shell('uiautomator','dump','--compressed','/sdcard/wp-mobile-ui-acceptance.xml')
        if 'ERROR' not in text:
            try:return ET.fromstring(run('exec-out','cat','/sdcard/wp-mobile-ui-acceptance.xml'))
            except (ET.ParseError,RuntimeError):pass
        time.sleep(2)
    raise RuntimeError('Actual UI hierarchy unavailable')
def labels():return [n.get('text') for n in ui().iter('node') if n.get('text')]
def shot(name):
    (OUT/(name+'.png')).write_bytes(run('exec-out','screencap','-p',binary=True))
    (OUT/(name+'.xml')).write_bytes(ET.tostring(ui(),encoding='utf-8'))
def tap(text,contains=False,scroll=False):
    for attempt in range(7 if scroll else 1):
        nodes=list(ui().iter('node'))
        def matches(value):return text in (value or '') if contains else value==text
        hits=[n for n in nodes if matches(n.get('content-desc'))]
        if not hits:hits=[n for n in nodes if matches(n.get('text'))]
        if hits:
            b=list(map(int,re.findall(r'\d+',hits[0].get('bounds'))))
            shell('input','tap',(b[0]+b[2])//2,(b[1]+b[3])//2)
            time.sleep(.7);return
        if scroll:
            size=re.findall(r'(\d+)x(\d+)',shell('wm','size'))[-1];w,h=map(int,size)
            shell('input','swipe',w//2,int(h*.75),w//2,int(h*.30),350)
            time.sleep(.4)
    raise AssertionError('Required UI control missing: '+text)
def fingerprint(text):return json.loads(re.search(r'DATA_PRESERVATION_PASS: (\{.*\})',text).group(1))
def data_probe(counts=None):
    args=['am','instrument','-w','-e','dataProbe','true']
    if counts is not None:args+=['-e','baselineCounts',"'"+json.dumps(counts,separators=(',',':'))+"'"]
    result=shell(*args,'cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation')
    return fingerprint(result)

def saved_state():
    result=shell('am','instrument','-w','-e','uiStateProbe','true',
                 'cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation')
    return json.loads(re.search(r'UI_STATE_PASS: (\{.*\})',result).group(1))

def launch(page=None,restart=False):
    if restart:shell('am','force-stop','cn.walkpaint')
    shell('am','start','-n','cn.walkpaint/.MainActivity');time.sleep(2)
    if page:tap(page)

def scroll_to_top():
    size=re.findall(r'(\d+)x(\d+)',shell('wm','size'))[-1];w,h=map(int,size)
    for _ in range(4):shell('input','swipe',w//2,int(h*.3),w//2,int(h*.8),250)

def visible_day_results(today):
    found=set();previous=None;stable=0
    size=re.findall(r'(\d+)x(\d+)',shell('wm','size'))[-1];w,h=map(int,size)
    for _ in range(30):
        values=labels()
        for value in values:
            if value=='今天的足迹':found.add(today)
            elif re.fullmatch(r'\d{4}-\d{2}-\d{2}',value):found.add(value)
        marker=tuple(values)
        stable=stable+1 if marker==previous else 0
        if stable>=2:break
        previous=marker
        shell('input','swipe',w//2,int(h*.78),w//2,int(h*.30),300);time.sleep(.3)
    else:raise AssertionError('Date list did not reach a stable end')
    return found

def main():
    global OUT
    parser=argparse.ArgumentParser();parser.add_argument('--apk',type=Path,required=True)
    parser.add_argument('--baseline-file',type=Path,required=True)
    parser.add_argument('--out',type=Path)
    args=parser.parse_args()
    require_walkpaint_google_avd(ADB,SERIAL)
    old=fingerprint(args.baseline_file.read_text(encoding='utf-8'))
    OUT=args.out or ROOT/'verification'/('mobile-ui-'+datetime.now().strftime('%Y%m%d-%H%M%S'))
    OUT.mkdir(parents=True,exist_ok=False)
    report={'apk_sha256':hashlib.sha256(args.apk.read_bytes()).hexdigest(),'device':SERIAL,
        'avd':'WalkPaint_Google_API36','engine':'not_verified','checks':{},'target_phone':'not_verified'}
    checks=report['checks']
    original_size=shell('wm','size')
    original_override=re.search(r'Override size: (\d+x\d+)',original_size)
    original_automatic=None
    original_appearance=None
    try:
        assert 'Success' in run('install','-r',args.apk)
        assert 'Success' in run('install','-r',ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
        kept=data_probe({t:v['count'] for t,v in old.items()})
        (OUT/'original-rows-check.json').write_text(json.dumps(kept,indent=2),encoding='utf-8')
        checks['all_v040_rows_preserved']=all(kept[t]['baselineSha256']==old[t]['sha256'] for t in old)
        assert checks['all_v040_rows_preserved'],'Original v0.40 rows changed'
        original=saved_state()
        original_automatic=original['automatic_enabled'];original_appearance=original['appearance_mode']
        assert_saved_state(original,apk_sha256=report['apk_sha256'])
        checks['installed_artifact_matches_report']=True
        shell('am','start','-n','cn.walkpaint/.MainActivity');time.sleep(3)
        visible=labels()
        if '先看看，暂不记录' in visible:tap('先看看，暂不记录')
        assert '有效记录时长' in labels()
        if original_automatic:tap('暂停记录')
        assert '继续记录' in labels()
        paused=saved_state();assert_saved_state(paused,automatic=False)
        launch()
        services=shell('dumpsys','activity','services','cn.walkpaint')
        assert not re.search(r'ServiceRecord.*RecordingService',services),'Recording service still active while paused'
        shot('map');checks['map_summary_and_pause']=True
        tap('足迹');assert any('个探索日' in t for t in labels())
        report['filter_results']={}
        for choice in ('本周','本月','全部'):
            scroll_to_top();tap(choice)
            result=visible_day_results(paused['today'])
            assert_day_results(result,paused['days'],paused['today'],choice)
            report['filter_results'][choice]=sorted(result)
        scroll_to_top()
        checks['time_filters']=True
        visible=labels();day=next(t for t in visible if t=='今天的足迹' or re.fullmatch(r'\d{4}-\d{2}-\d{2}',t))
        tap(day);assert '行程详情' in labels();shot('detail')
        tap('在地图查看这天的足迹');assert any('返回全部探索' in t for t in labels())
        tap('返回全部探索',contains=True);checks['detail_to_real_history_map']=True
        tap('我的');assert '累计里程' in labels();shot('mine')
        tap('深色',scroll=True);launch('我的',restart=True);shot('dark')
        assert_saved_state(saved_state(),appearance=2,automatic=False)
        checks['dark_mode']=True
        for choice,mode in (('浅色',1),('跟随系统',0)):
            launch('我的');tap(choice,scroll=True);launch('我的',restart=True)
            assert_saved_state(saved_state(),appearance=mode,automatic=False)
        launch('我的');checks['theme_selection']=True;checks['theme_survives_restart']=True
        tap('检查更新',scroll=True);assert '当前版本 0.41' in labels();shot('updates')
        checks['offline_current_version_and_update_entry']=True
        tap('返回');tap('返回')
        tap('首次使用引导',scroll=True);assert '同意并开启自动记录' in labels();shot('onboarding')
        tap('先看看，暂不记录');checks['onboarding_without_forced_permission']=True
        tap('导出足迹 JSON',scroll=True)
        assert 'cn.walkpaint' not in shell('dumpsys','activity','activities').split('topResumedActivity=')[-1].split('\n')[0]
        shell('input','keyevent','KEYCODE_BACK');checks['system_backup_picker']=True
        tap('诊断与反馈',scroll=True);assert '问题反馈' in labels()
        checks['diagnostics_page']=True;tap('返回')
        # The actual map provider, read from the running app rather than source defaults.
        tap('返回');tap('地图');visible=labels()
        report['engine']='AMap' if any('高德' in t for t in visible) else 'not_verified'
        density=int(re.findall(r'\d+',shell('wm','density'))[-1])
        shell('wm','size',f'{320*density//160}x{720*density//160}')
        time.sleep(2);shot('small-map');assert '有效记录时长' in labels()
        tap('我的');shot('small-mine');checks['320dp_real_render']=True
        shell('wm','size',original_override.group(1) if original_override else 'reset');time.sleep(1)
        tap('地图')
        if original_automatic:tap('继续记录')
        assert_saved_state(saved_state(),automatic=original_automatic)
        launch()
        if original_automatic:
            assert re.search(r'ServiceRecord.*RecordingService',shell('dumpsys','activity','services','cn.walkpaint')),'Recording service did not resume'
        checks['recording_state_matches_action']=True
        kept=data_probe({t:v['count'] for t,v in old.items()})
        checks['all_original_rows_after_ui']=all(kept[t]['baselineSha256']==old[t]['sha256'] for t in old)
        assert checks['all_original_rows_after_ui']
        shell('am','start','-n','cn.walkpaint/.MainActivity');time.sleep(1)
        logs=run('logcat','-d','-t','300','-s','AndroidRuntime:E')
        checks['no_runtime_crash']='FATAL EXCEPTION' not in logs
        assert checks['no_runtime_crash']
        report['status']='passed'
    except Exception as exc:
        report['status']='failed';report['error']=str(exc);raise
    finally:
        shell('wm','size',original_override.group(1) if original_override else 'reset')
        # Also restore presentation/recording when a later UI assertion fails.
        try:
            launch('我的')
            if original_appearance is not None:tap(('跟随系统','浅色','深色')[original_appearance],scroll=True)
            tap('地图')
            visible=labels()
            if original_automatic is True and '继续记录' in visible:tap('继续记录')
            if original_automatic is False and '暂停记录' in visible:tap('暂停记录')
            report['cleanup_restored_viewport_theme_recording']=True
        except Exception as exc:
            report['cleanup_restored_viewport_theme_recording']=False
            report['cleanup_error']=str(exc)
        shell('rm','-f','/sdcard/wp-mobile-ui-acceptance.xml')
        (OUT/'acceptance.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps(report,ensure_ascii=False),flush=True)
    if not report['cleanup_restored_viewport_theme_recording']:
        raise RuntimeError('UI acceptance cleanup did not restore original settings')

if __name__=='__main__':main()

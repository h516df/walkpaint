"""Real UI acceptance of the selected redesign. Dedicated Google AVD only; never clears data."""
import argparse,hashlib,json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
from google_avd_guard import require_walkpaint_google_avd

ROOT=Path(__file__).resolve().parents[1]
ADB=r'D:\Apps\Android\sdk\platform-tools\adb.exe'
SERIAL='emulator-5556'
OUT=ROOT/'verification/mobile-ui'
OUT.mkdir(parents=True,exist_ok=True)

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

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--apk',type=Path,required=True)
    args=parser.parse_args()
    require_walkpaint_google_avd(ADB,SERIAL)
    old=fingerprint((OUT/'before-data.txt').read_text(encoding='utf-8'))
    report={'apk_sha256':hashlib.sha256(args.apk.read_bytes()).hexdigest(),'device':SERIAL,
        'avd':'WalkPaint_Google_API36','engine':'not_verified','checks':{},'target_phone':'not_verified'}
    checks=report['checks']
    original_size=shell('wm','size')
    original_override=re.search(r'Override size: (\d+x\d+)',original_size)
    original_automatic=None
    try:
        assert 'Success' in run('install','-r',args.apk)
        assert 'Success' in run('install','-r',ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
        kept=data_probe({t:v['count'] for t,v in old.items()})
        (OUT/'original-rows-check.json').write_text(json.dumps(kept,indent=2),encoding='utf-8')
        checks['all_v040_rows_preserved']=all(kept[t]['baselineSha256']==old[t]['sha256'] for t in old)
        assert checks['all_v040_rows_preserved'],'Original v0.40 rows changed'
        shell('am','start','-n','cn.walkpaint/.MainActivity');time.sleep(3)
        visible=labels()
        if '先看看，暂不记录' in visible:tap('先看看，暂不记录')
        assert '有效记录时长' in labels()
        original_automatic='暂停记录' in labels()
        if original_automatic:tap('暂停记录')
        assert '继续记录' in labels()
        shot('map');checks['map_summary_and_pause']=True
        tap('足迹');assert any('个探索日' in t for t in labels())
        for choice in ('本周','本月','全部'):tap(choice)
        checks['time_filters']=True
        visible=labels();day=next(t for t in visible if t=='今天的足迹' or re.fullmatch(r'\d{4}-\d{2}-\d{2}',t))
        tap(day);assert '行程详情' in labels();shot('detail')
        tap('在地图查看这天的足迹');assert any('返回全部探索' in t for t in labels())
        tap('返回全部探索',contains=True);checks['detail_to_real_history_map']=True
        tap('我的');assert '累计里程' in labels();shot('mine')
        tap('深色',scroll=True);shot('dark');checks['dark_mode']=True
        tap('浅色');tap('跟随系统');checks['theme_selection']=True
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
        report['engine']='OpenFreeMap/MapLibre' if 'OpenFreeMap' in visible else 'offline' if '离线地图' in visible else 'AMap'
        density=int(re.findall(r'\d+',shell('wm','density'))[-1])
        shell('wm','size',f'{320*density//160}x{720*density//160}')
        time.sleep(2);shot('small-map');assert '有效记录时长' in labels()
        tap('我的');shot('small-mine');checks['320dp_real_render']=True
        shell('wm','size',original_override.group(1) if original_override else 'reset');time.sleep(1)
        tap('地图')
        if original_automatic:tap('继续记录')
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
            shell('am','start','-n','cn.walkpaint/.MainActivity')
            tap('我的');tap('跟随系统',scroll=True);tap('地图')
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

if __name__=='__main__':main()

"""Direct GPS painting UI on dedicated Google AVD only. Public Paris fixture, preserves old rows."""
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]
provided_apk=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'app/build/outputs/apk/debug/app-debug.apk'
sys.path.insert(0,str(root/'scripts'))
sys.argv=[__file__]
__file__=str(root/'scripts/android_profile_acceptance.py')
exec(compile((root/'scripts/android_profile_acceptance.py').read_text(encoding='utf-8').split('\npermissions=')[0],__file__,'exec'))
out=root/'verification/direct-google';out.mkdir(parents=True,exist_ok=True)
checks={};saved={};before=rows()
size=sh('wm','size');density=sh('wm','density')

def scroll():
    nodes=[n for n in ui().iter('node') if n.get('scrollable')=='true']
    assert nodes
    x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[0].get('bounds')))
    sh('input','swipe',str((x1+x2)//2),str(y2-40),str((x1+x2)//2),str(y1+40),'400')
    time.sleep(.5)

def ensure(text):
    for _ in range(7):
        if text in labels():tap(text);return
        scroll()
    raise AssertionError('Missing '+text)

def no_preparation():
    text='\n'.join(labels())
    assert '准备这里的地图' not in text and '先准备地图' not in text and '下载附近道路' not in text

def painted_pixels(name,color=(87,155,140)):
    from PIL import Image
    im=Image.open(out/(name+'.png')).convert('RGB')
    # Exclude footer active-tab icons; search only real map region.
    count=sum(1 for r,g,b in im.crop((0,100,im.width,im.height-230)).getdata()
              if abs(r-color[0])<=3 and abs(g-color[1])<=3 and abs(b-color[2])<=3)
    assert count>80,(name,count)
    return count

try:
    sh('am','force-stop','cn.walkpaint')
    for name in ['footprint_preferences','road_appearance','ui_onboarding','map_preferences']:
        try:saved[name]=run('exec-out','run-as','cn.walkpaint','cat','shared_prefs/'+name+'.xml',binary=True)
        except RuntimeError:saved[name]=None
    apk=provided_apk
    assert 'Success' in run('install','-r',str(apk),timeout=150)
    assert re.search(r'versionCode=18\b',sh('dumpsys','package','cn.walkpaint'))
    remote=sh('pm','path','cn.walkpaint').split('package:')[1].strip()
    checks['apk_sha256']=hashlib.sha256(apk.read_bytes()).hexdigest()
    assert hashlib.sha256(run('exec-out','cat',remote,binary=True,timeout=90)).hexdigest()==checks['apk_sha256']
    assert before==rows()
    checks['upgrade_old_five_tables_unchanged']={t:len(v) for t,v in before.items()}
    print('UPGRADE_DATA_PRESERVED',flush=True)
    assert 'Success' in run('install','-r',str(root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'),timeout=150)
    result=sh('am','instrument','-w','-e','directTrailProbe','true','cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation')
    out.joinpath('database-probe.txt').write_text(result,encoding='utf-8')
    assert 'DIRECT_TRAIL_NO_ROADS_DB_PASS' in result,result
    after=rows();assert all(before[t]<=after[t] for t in before)
    added=int(re.search(r'added=(\d+)',result).group(1))
    assert len(after['points'])==len(before['points'])+added
    checks['isolated_no_road_db_walking_cycling_driving_gap']=True
    checks['public_fixture_added_points']=added
    print('DIRECT_NO_ROAD_DATABASE_PASS',flush=True)
    sh('am','force-stop','cn.walkpaint');launch()
    if 'Wait' in labels():tap('Wait');time.sleep(5);launch()
    tap('我的');ensure('涂色颜色');tap('恢复青绿');tap('完成');tap('地图')
    no_preparation();shot('home-direct')
    # Latest public fixture is the initial focus; one 100m segment is visible.
    checks['home_line_pixels']=painted_pixels('home-direct')
    tap('足迹');no_preparation();shot('trips-direct')
    assert '累计记录里程' in labels()
    ensure('查看今天的足迹');time.sleep(3);shot('today-direct')
    checks['today_line_pixels']=painted_pixels('today-direct')
    tap('我的');ensure('涂色颜色');shot('color-direct')
    assert '足迹轨迹' in labels();tap('雾蓝');tap('完成')
    tap('地图');time.sleep(2);shot('recolored-direct')
    # Preset exact color is read from source, no sensitive data.
    presets=(root/'app/src/main/java/cn/walkpaint/RoadPaintColor.kt').read_text(encoding='utf-8')
    argb=re.search(r'"雾蓝",\s*(0x[0-9a-fA-F]+)',presets)
    assert argb,presets
    value=int(argb.group(1),16)
    checks['recolored_line_pixels']=painted_pixels('recolored-direct',((value>>16)&255,(value>>8)&255,value&255))
    tap('我的');ensure('涂色颜色');tap('恢复青绿');tap('完成');ensure('更多设置');no_preparation();shot('settings-direct')
    assert '地图数据' not in labels()
    ensure('隐私与权限');ensure('地图服务与隐私');tap('同意使用高德')
    tap('地图');time.sleep(12);no_preparation();shot('online-direct')
    checks['online_line_pixels']=painted_pixels('online-direct')
    checks['amap_consent_and_direct_paint']=True
    print('REAL_UI_DIRECT_HISTORY_COLOR_ONLINE_PASS',flush=True)
    sh('am','force-stop','cn.walkpaint');launch();time.sleep(12);shot('restart-direct')
    checks['restart_line_pixels']=painted_pixels('restart-direct')
    sh('wm','size','320x710');sh('wm','density','160')
    sh('am','force-stop','cn.walkpaint');launch();tap('足迹');no_preparation()
    assert '累计记录里程' in labels();time.sleep(2);shot('small-trips')
    ensure('查看今天的足迹');time.sleep(2);shot('small-map')
    checks['small_320dp_navigation']=True
    assert all(before[t]<=rows()[t] for t in before)
    checks['old_rows_retained_after_ui']=True
except Exception as exc:
    checks['failure']=str(exc);raise
finally:
    sh('am','force-stop','cn.walkpaint')
    for name,content in saved.items():
        if content is None:sh('run-as','cn.walkpaint','rm','-f','shared_prefs/'+name+'.xml')
        else:
            temp=out/'restore.xml';temp.write_bytes(content)
            run('push',str(temp),'/data/local/tmp/wp-direct-restore.xml')
            sh('run-as','cn.walkpaint','cp','/data/local/tmp/wp-direct-restore.xml','shared_prefs/'+name+'.xml')
            assert run('exec-out','run-as','cn.walkpaint','cat','shared_prefs/'+name+'.xml',binary=True)==content
            temp.unlink()
    sh('rm','-f','/data/local/tmp/wp-direct-restore.xml','/sdcard/wp-profile-ui.xml')
    for what,original in [('size',size),('density',density)]:
        override=re.search(r'Override (?:size|density): (\S+)',original)
        sh('wm',what,override.group(1) if override else 'reset')
    checks['preferences_and_viewport_restored']=True
    out.joinpath('acceptance.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(checks,ensure_ascii=False),flush=True)

"""Real UI on the dedicated Google AVD. No uninstall, data clear or private device.

Keeps original database rows, restores permissions/app-ops/viewport after testing.
Uses only the dedicated AVD's synthetic GPS position. Never a physical device.
"""
import argparse, hashlib, json, os, re, sqlite3, subprocess, time
from pathlib import Path
import xml.etree.ElementTree as ET
from google_avd_guard import require_walkpaint_google_avd
os.chdir(Path(__file__).resolve().parents[1])
parser=argparse.ArgumentParser()
parser.add_argument('--apk',default='app/build/outputs/apk/debug/app-debug.apk')
parser.add_argument('--resume-appearance',action='store_true',help='Complete appearance/small-screen checks after a recorded run on the identical APK')
args=parser.parse_args()

ADB=r'D:\Apps\Android\sdk\platform-tools\adb.exe'
SERIAL='emulator-5556'
require_walkpaint_google_avd(ADB,SERIAL)
base=[ADB,'-s',SERIAL]
out=Path('verification/v012-google');out.mkdir(parents=True,exist_ok=True)
checks={};cleanup=[];geo_changed=False
if args.resume_appearance:
    checks=json.loads(out.joinpath('acceptance.json').read_text(encoding='utf-8'))
    assert checks.get('secondary_real_entries_preserved')
    assert checks.get('universal_apk_sha256')==hashlib.sha256(Path(args.apk).read_bytes()).hexdigest()
    checks.pop('failure',None)
def run(*args,binary=False,timeout=40):
    p=subprocess.run(base+list(args),capture_output=True,timeout=timeout)
    if p.returncode: raise RuntimeError((p.stdout+p.stderr).decode(errors='replace')[-700:])
    return p.stdout if binary else p.stdout.decode(errors='replace')
def sh(*args):return run('shell',*args)
def ui():
    sh('uiautomator','dump','--compressed','/sdcard/wp-profile-ui.xml')
    return ET.fromstring(run('exec-out','cat','/sdcard/wp-profile-ui.xml'))
def labels():return [n.get('text') for n in ui().iter('node') if n.get('text')]
def tap_node(n):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    sh('input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(1)
def tap(*texts):
    def norm(text):return (text or '').replace('’',"'")
    for n in ui().iter('node'):
        if any(norm(v) in [norm(t) for t in texts] for v in [n.get('text'),n.get('content-desc')]):tap_node(n);return
    raise AssertionError('Missing control '+str(texts)+'; visible='+str(labels()))
def switch():return next(n for n in ui().iter('node') if n.get('checkable')=='true')
def toggle():tap_node(switch())
def enabled():return switch().get('checked')=='true'
def launch():sh('am','start','-n','cn.walkpaint/.MainActivity');time.sleep(3)
def shot(name):
    out.joinpath(name+'.png').write_bytes(run('exec-out','screencap','-p',binary=True))
    out.joinpath(name+'.xml').write_bytes(ET.tostring(ui(),encoding='utf-8'))
def rows():
    raw=run('exec-out','run-as','cn.walkpaint','cat','databases/footprints.db',binary=True)
    db=sqlite3.connect(':memory:');db.deserialize(raw)
    assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
    result={t:set(db.execute('SELECT * FROM '+t).fetchall()) for t in ['trips','points','roads','road_matches','coverage']}
    db.close();return result
def count():return len(rows()['points'])
def foreground():return 'isForeground=true' in sh('dumpsys','activity','services','cn.walkpaint')
def back():sh('input','keyevent','KEYCODE_BACK');time.sleep(1)
def location_allow():
    settings='去设置' in labels()
    if settings:
        tap('去设置');tap('Permissions','权限');tap('Location','位置','位置信息')
    if 'Precise' in labels():tap('Precise')
    tap('While using the app','使用应用时','仅在使用该应用时允许')
    if settings:
        for _ in range(5):
            if '允许后台定位' in labels():return
            back()
        raise AssertionError('Foreground settings return did not continue setup')
def continue_intro():
    if '同意并继续' in labels():tap('同意并继续')
def recording_fix(lat,lon):
    global geo_changed
    before=count()
    run('emu','geo','fix',str(lon),str(lat),'0','8')
    geo_changed=True
    for _ in range(8):
        time.sleep(1)
        if count()>before:return
    raise AssertionError('No real SQLite commit after virtual hardware GPS')

permissions=['ACCESS_COARSE_LOCATION','ACCESS_FINE_LOCATION','ACCESS_BACKGROUND_LOCATION','POST_NOTIFICATIONS']
pkg=sh('dumpsys','package','cn.walkpaint')
original_permissions={p:bool(re.search(r'android.permission.'+p+r': granted=true',pkg)) for p in permissions}
original_flags={p:re.search(r'android.permission.'+p+r': granted=\w+, flags=\[([^\]]*)\]',pkg).group(1) for p in permissions}
original_prefs={}
for name in ['footprint_preferences','road_appearance','ui_onboarding']:
    try:original_prefs[name]=run('exec-out','run-as','cn.walkpaint','cat','shared_prefs/'+name+'.xml',binary=True)
    except RuntimeError:original_prefs[name]=None
original=rows()
db=sqlite3.connect(':memory:')
db.deserialize(run('exec-out','run-as','cn.walkpaint','cat','databases/footprints.db',binary=True))
origin=db.execute('SELECT lat,lon FROM points ORDER BY time DESC LIMIT 1').fetchone()
db.close()
# Fresh AVDs use the official emulator's default test location.
origin=origin or (37.4219983,-122.084)
size=sh('wm','size');density=sh('wm','density')
mock_mode=re.search(r'(?:MOCK_LOCATION|mock_location):\s*(allow|ignore|deny|default|foreground)',sh('appops','get','2000','android:mock_location'))
original_mock=mock_mode.group(1) if mock_mode else 'default'
provider=False;initial_tracking=False
try:
    if args.resume_appearance:
        for permission in permissions:sh('pm','grant','cn.walkpaint','android.permission.'+permission)
        launch();tap('我的')
    else:
        # A failed preceding run may leave PermissionController above a stopped app.
        if any('access this device' in text for text in labels()):tap("Don't allow",'不允许')
        apk=Path(args.apk)
        checks['universal_apk_sha256']=hashlib.sha256(apk.read_bytes()).hexdigest()
        assert 'Success' in run('install','-r',str(apk.resolve()),timeout=150)
        assert 'versionCode=13' in sh('dumpsys','package','cn.walkpaint')
        remote=sh('pm','path','cn.walkpaint').split('package:')[1].strip()
        assert hashlib.sha256(run('exec-out','cat',remote,binary=True,timeout=60)).hexdigest()==checks['universal_apk_sha256']
        checks['installed_apk_matches']=True
        assert all(original[t]<=rows()[t] for t in original)
        checks['upgrade_original_rows_retained']={t:len(v) for t,v in original.items()}
        assert 'Success' in run('install','-r',str(Path('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk').resolve()),timeout=150)
        result=sh('am','instrument','-w','cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation')
        out.joinpath('isolated-database.txt').write_text(result,encoding='utf-8')
        assert 'V05_ISOLATED_DATABASE_PASS' in result
        checks['isolated_database_suite']=True
        sh('input','keyevent','KEYCODE_BACK')
        launch();tap('我的');initial_tracking=enabled()
        if initial_tracking:toggle()
        assert not enabled()
        text=labels()
        assert all(x in text for x in ['自动记录','涂色颜色','更多设置'])
        assert not any(x in ' '.join(text) for x in ['GPS','OSM','定位权限','附近道路','自动快照','撤回自动记录授权'])
        shot('profile-off');checks['profile_simple_off']=True
        toggle();continue_intro();shot('foreground-permission')
        tap("Don't allow",'不允许')
        tap('稍后');assert not enabled() and not foreground()
        checks['foreground_denial_stays_off']=True;shot('permission-denied')
        toggle();continue_intro();location_allow()
        assert '允许后台定位' in labels();shot('background-explanation')
        tap('去允许');tap('Permissions','权限');tap('Location','位置','位置信息')
        tap('Allow all the time','始终允许');shot('system-background-location')
        for _ in range(5):
            if 'Allow' in labels() or '允许' in labels():break
            if '自动记录' in labels():break
            back()
        if 'Allow' in labels() or '允许' in labels():tap('Allow','允许')
        time.sleep(3)
        assert enabled() and foreground()
        checks['one_toggle_sequential_system_permissions_and_return']=True;shot('profile-on')
        # New foreground-service data, through the same user entry used on a phone.
        # Console geo fix updates the Google AVD's virtual hardware, without replacing
        # a live LocationManager provider. Keep the existing conservative jump filter.
        lat,lon=origin
        before=count();recording_fix(lat+.00009,lon);time.sleep(3)
        sh('input','keyevent','KEYCODE_HOME');recording_fix(lat+.00018,lon)
        sh('input','keyevent','KEYCODE_SLEEP');recording_fix(lat+.00027,lon)
        sh('input','keyevent','KEYCODE_WAKEUP');sh('wm','dismiss-keyguard');launch();tap('我的')
        checks['actual_foreground_background_screen_off_commits']={'before':before,'after':count()}
        toggle();assert not foreground();paused=count()
        run('emu','geo','fix',str(lon),str(lat+.00036),'0','8');time.sleep(3)
        assert count()==paused
        checks['switch_off_stops_new_records_and_retains_history']=True
        toggle();time.sleep(2);assert enabled() and foreground()
        assert '允许后台定位' not in labels() and '同意并继续' not in labels()
        checks['already_authorized_enable_has_no_repeated_prompts']=True
        toggle();tap('更多设置');shot('more-settings')
        tap('备份与恢复');assert all(x in labels() for x in ['导出备份','恢复备份']);shot('backup-settings');back()
        tap('隐私与权限');shot('privacy-settings');tap('撤回自动记录授权');tap('取消');back()
        tap('高级诊断');assert '复制诊断' in labels();tap('关闭')
        tap('地图数据');assert '准备这里的地图' in labels();back()
        checks['secondary_real_entries_preserved']=True
        tap('关于');assert '版本 0.12' in labels();back();back()
    tap('涂色颜色');assert '涂色外观' in labels();tap('雾蓝');tap('完成')
    assert '雾蓝' in labels();checks['existing_color_entry_works']=True
    sh('wm','size','840x1680');sh('wm','density','420');time.sleep(2);shot('profile-320dp')
    assert all(x in labels() for x in ['自动记录','涂色颜色','更多设置'])
    checks['320dp_profile']=True
    # Declined background permission must not cause a loop or pretend full readiness.
    sh('am','force-stop','cn.walkpaint')
    sh('pm','revoke','cn.walkpaint','android.permission.ACCESS_BACKGROUND_LOCATION')
    launch();tap('我的');toggle();tap('先这样记录');assert enabled()
    assert '需要允许后台定位' in labels();shot('background-deferred')
    checks['background_decline_honest_status_no_loop']=True
    toggle()
    assert all(original[t]<=rows()[t] for t in original)
    checks['all_original_rows_retained_at_end']=True
except Exception as exc:
    checks['failure']=str(exc)
    try:shot('failure')
    except Exception:pass
    raise
finally:
    def restore(name,fn):
        try:fn()
        except Exception as e:cleanup.append(name+': '+str(e))
    def dismiss_permission():
        text=labels()
        if any('access this device' in t for t in text) or any("Don't allow" in t.replace('’',"'") for t in text):
            tap("Don't allow",'不允许')
    restore('pending system dialog',dismiss_permission)
    restore('stop test app',lambda:sh('am','force-stop','cn.walkpaint'))
    if geo_changed:restore('virtual GPS',lambda:run('emu','geo','fix',str(origin[1]),str(origin[0]),'0','8'))
    if provider:restore('test provider',lambda:sh('cmd','location','providers','remove-test-provider','gps'))
    restore('mock app-op',lambda:sh('appops','set','2000','android:mock_location',original_mock))
    for permission,granted in original_permissions.items():
        restore(permission,lambda p=permission,g=granted:sh('pm','grant' if g else 'revoke','cn.walkpaint','android.permission.'+p))
        for flag in ['user-set','user-fixed']:
            restore(permission+' '+flag,lambda p=permission,f=flag:sh('pm',
                'set-permission-flags' if f.upper().replace('-','_') in original_flags[p] else 'clear-permission-flags',
                'cn.walkpaint','android.permission.'+p,f))
    def restore_pref(name,payload):
        target='shared_prefs/'+name+'.xml'
        if payload is None:sh('run-as','cn.walkpaint','rm','-f',target)
        else:
            local=out/(name+'-restore.xml');local.write_bytes(payload)
            remote='/data/local/tmp/wp-profile-'+name+'.xml'
            run('push',str(local.resolve()),remote)
            sh('run-as','cn.walkpaint','cp',remote,target)
            sh('rm','-f',remote);local.unlink()
    for name,payload in original_prefs.items():restore(name,lambda n=name,p=payload:restore_pref(n,p))
    for command,value in [('size',size),('density',density)]:
        match=re.search(r'Override '+command+r': (\S+)',value)
        restore(command,lambda c=command,m=match:sh('wm',c,m.group(1) if m else 'reset'))
    restore('temporary UI',lambda:sh('rm','-f','/sdcard/wp-profile-ui.xml'))
    checks['cleanup_errors']=cleanup
    out.joinpath('acceptance.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(checks,ensure_ascii=True,indent=2),flush=True)

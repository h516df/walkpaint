"""Private-updater real UI baseline. No credentials or fabricated successful network flow."""
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]
provided_apk=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'app/build/outputs/apk/debug/app-debug.apk'
sys.path.insert(0,str(root/'scripts'));sys.argv=[__file__];__file__=str(root/'scripts/android_profile_acceptance.py')
exec(compile((root/'scripts/android_profile_acceptance.py').read_text(encoding='utf-8').split('\npermissions=')[0],__file__,'exec'))
out=root/'verification/private-updates-google';out.mkdir(parents=True,exist_ok=True)
original=rows()
original_density=sh('wm','density')
def enter_updates():
    if '检查新版' in labels():return
    tap('我的')
    if '检查新版' in labels():return
    if '检查更新' not in labels():tap('更多设置')
    tap('检查更新')
try:
    assert 'Success' in run('install','-r',str(provided_apk),timeout=150)
    checks['apk_sha256']=hashlib.sha256(provided_apk.read_bytes()).hexdigest()
    remote=sh('pm','path','cn.walkpaint').split('package:')[1].strip()
    checks['installed_apk_matches']=hashlib.sha256(run('exec-out','cat',remote,binary=True)).hexdigest()==checks['apk_sha256']
    assert checks['installed_apk_matches']
    checks['source_sha']=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root).decode().strip()
    checks['source_dirty']=bool(subprocess.check_output(['git','status','--porcelain'],cwd=root).strip())
    assert 'versionCode=15' in sh('dumpsys','package','cn.walkpaint')
    checks['upgrade_rows_retained']=all(original[t]<=rows()[t] for t in original)
    assert checks['upgrade_rows_retained']
    launch();enter_updates();shot('private-updates-entry')
    assert all(t in labels() for t in ['当前版本 0.14','点击检查后才联网','检查新版','授权私有更新'])
    checks['real_page_and_explicit_network_trigger']=True
    tap('检查新版');shot('private-updates-unconfigured')
    assert '私有更新授权尚未开通，可先打开发布页核对版本' in labels()
    checks['unconfigured_channel_not_reported_as_latest']=True
    tap('授权私有更新')
    assert '私有更新授权尚未开通，可先打开发布页核对版本' in labels()
    checks['unconfigured_authorization_no_fake_code']=True
    sh('wm','density','540');time.sleep(3)
    # Android recreates this Activity on a density change; restore the real navigation path.
    enter_updates()
    shot('private-updates-320dp')
    assert '检查新版' in labels() and '授权私有更新' in labels()
    sh('input','swipe','540','1900','540','700','600');time.sleep(1)
    shot('private-updates-notes-320dp')
    assert '本版更新说明' in labels()
    checks['small_viewport_buttons_and_notes_reachable']=True
    back();tap('返回');tap('地图');shot('map-after-updater')
    assert original==rows();checks['check_and_back_preserve_all_original_rows']=True
    checks['authenticated_private_download']='NOT_VERIFIED: requires account-controlled GitHub App setup'
except Exception as exc:
    checks['failure']=repr(exc);raise
finally:
    density=re.search(r'Override density: (\d+)',original_density)
    sh('wm','density',density.group(1) if density else 'reset')
    sh('am','force-stop','cn.walkpaint');sh('rm','-f','/sdcard/wp-profile-ui.xml')
    out.joinpath('acceptance.json').write_text(json.dumps(checks,indent=2,ensure_ascii=False),encoding='utf-8')
print(json.dumps(checks,ensure_ascii=False))

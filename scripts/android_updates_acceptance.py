from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1];task=root/'verification'
provided_apk=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'app/build/outputs/apk/debug/app-debug.apk'
sys.path.insert(0,str(root/'scripts'));sys.argv=[__file__];__file__=str(root/'scripts/android_profile_acceptance.py')
exec(compile((root/'scripts/android_profile_acceptance.py').read_text(encoding='utf-8').split('\npermissions=')[0],__file__,'exec'))
out=task/'updates-google';out.mkdir(exist_ok=True)
original=rows()
try:
    assert 'Success' in run('install','-r',str(provided_apk),timeout=150)
    checks['apk_sha256']=hashlib.sha256(provided_apk.read_bytes()).hexdigest()
    remote=sh('pm','path','cn.walkpaint').split('package:')[1].strip()
    checks['installed_apk_matches']=hashlib.sha256(run('exec-out','cat',remote,binary=True)).hexdigest()==checks['apk_sha256']
    assert checks['installed_apk_matches']
    checks['source_sha']=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root).decode().strip()
    assert 'versionCode=14' in sh('dumpsys','package','cn.walkpaint')
    assert 'Success' in run('install','-r',str(root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'),timeout=150)
    checks['upgrade_rows_retained']=all(original[t]<=rows()[t] for t in original)
    assert checks['upgrade_rows_retained']
    launch();tap('我的');tap('更多设置');tap('检查更新');shot('updates-current-notes')
    assert all(t in labels() for t in ['当前版本 0.13','最新版尚未核对','本版更新说明','新增','改进'])
    checks['current_notes_and_truthful_unknown']=True
    tap('去发布页检查更新');time.sleep(6)
    if 'Use without an account' in labels():tap('Use without an account');time.sleep(4)
    if 'No thanks' in labels():tap('No thanks');time.sleep(3)
    if 'Got it' in labels():tap('Got it');time.sleep(3)
    shot('private-browser-entry')
    activity=sh('dumpsys','activity','activities')
    checks['browser_intent_trusted_url']=any('github.com/h516df/walkpaint/releases' in t for t in labels()) or 'https://github.com/h516df/walkpaint/releases' in activity
    checks['browser_opened']='com.android.chrome' in activity
    assert checks['browser_intent_trusted_url'] and checks['browser_opened']
    # Never sign into a private account or download/send anything in this AVD.
    launch()
    if '检查更新' not in labels():tap('我的');tap('更多设置');tap('检查更新')
    shot('updates-return');checks['no_fabricated_latest_after_browser']='最新版尚未核对' in labels()
    assert checks['no_fabricated_latest_after_browser']
    assert original==rows();checks['checking_updates_keeps_database']=True
finally:
    sh('am','force-stop','cn.walkpaint');sh('rm','-f','/sdcard/wp-profile-ui.xml')
    out.joinpath('acceptance.json').write_text(json.dumps(checks,indent=2),encoding='utf-8')
print(json.dumps(checks))

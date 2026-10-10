"""Continue only after recorded real system cover installation; never downgrade or clear data."""
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1];candidate=Path(sys.argv[1]).resolve()
sys.path.insert(0,str(root/'scripts'));sys.argv=[__file__];__file__=str(root/'scripts/android_profile_acceptance.py')
exec(compile((root/'scripts/android_profile_acceptance.py').read_text(encoding='utf-8').split('\npermissions=')[0],__file__,'exec'))
out=root/'verification/private-update-e2e'
record=out/'acceptance.json';previous=record.read_text(encoding='utf-8')
checks=json.loads(previous)
assert checks['real_system_cover_install_code_15_to_16'] and checks['installed_apk_matches_final_artifact']
assert checks['all_original_database_rows_unchanged']
expected=hashlib.sha256(candidate.read_bytes()).hexdigest();assert checks['expected_apk_sha256']==expected
out.joinpath('acceptance-first-navigation-failure.json').write_text(previous,encoding='utf-8')
checks.pop('failure',None)
baseline=json.loads((root/'verification/private-update-authorized/baseline-row-hashes.json').read_text())
def unchanged():
    data=rows()
    return baseline=={t:sorted(hashlib.sha256(json.dumps(row,ensure_ascii=False).encode()).hexdigest() for row in data[t]) for t in baseline}
def wait_for(text):
    deadline=time.monotonic()+120
    while time.monotonic()<deadline:
        if text in labels():return
        time.sleep(1)
    raise AssertionError('Expected non-secret update state did not appear')
try:
    assert re.search(r'versionCode=16\b',sh('dumpsys','package','cn.walkpaint'))
    remote=sh('pm','path','cn.walkpaint').split('package:')[1].strip()
    assert hashlib.sha256(run('exec-out','cat',remote,binary=True)).hexdigest()==expected
    assert unchanged()
    visible=labels()
    if 'App installed.' in visible:tap('Done')
    launch()
    if '检查新版' not in labels():
        tap('我的')
        if '检查新版' not in labels():
            if '检查更新' not in labels():tap('更多设置')
            tap('检查更新')
    tap('检查新版');wait_for('最新发布 0.14，当前版本无需更新')
    checks['authorization_survives_cover_install_and_no_update_is_real']=True
    shot('updated-version-private-check')
    tap('退出更新授权');tap('退出授权');wait_for('已退出更新授权，原足迹保留')
    assert '退出更新授权' not in labels()
    try:cache=sh('run-as','cn.walkpaint','ls','cache/updates').splitlines()
    except RuntimeError:cache=[]
    assert not cache and unchanged()
    checks['local_logout_cache_cleanup_and_rows_retained']=True
    checks['staged_continuation_after_installer_done']=True
    shot('logout-complete')
except Exception as exc:
    checks['failure']=repr(exc)
    try:shot('continuation-failed-stage')
    except Exception:pass
    raise
finally:
    sh('am','force-stop','cn.walkpaint');sh('rm','-f','/sdcard/wp-profile-ui.xml')
    record.write_text(json.dumps(checks,indent=2,ensure_ascii=False),encoding='utf-8')
print(json.dumps(checks,ensure_ascii=False))

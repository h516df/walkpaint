"""Approved private-channel update: actual download, Android installer, retained rows and local logout."""
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1];candidate=Path(sys.argv[1]).resolve()
sys.path.insert(0,str(root/'scripts'));sys.argv=[__file__];__file__=str(root/'scripts/android_profile_acceptance.py')
exec(compile((root/'scripts/android_profile_acceptance.py').read_text(encoding='utf-8').split('\npermissions=')[0],__file__,'exec'))
out=root/'verification/private-update-e2e';out.mkdir(parents=True,exist_ok=True)
original=rows();expected=hashlib.sha256(candidate.read_bytes()).hexdigest()
op=sh('cmd','appops','get','cn.walkpaint','REQUEST_INSTALL_PACKAGES')
matched=re.search(r'REQUEST_INSTALL_PACKAGES: (\w+)',op)
original_install_mode=matched.group(1) if matched else 'default'
checks={'expected_apk_sha256':expected,'original_install_mode':original_install_mode}
def wait_for(*texts,seconds=100):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        visible=labels()
        if any(t in visible for t in texts):return visible
        time.sleep(.5)
    raise AssertionError('Expected update UI did not appear: '+str(texts))
def update_page():
    if '检查新版' in labels():return
    tap('我的')
    if '检查新版' in labels():return
    if '检查更新' not in labels():tap('更多设置')
    tap('检查更新')
def installed_code():
    return int(re.search(r'versionCode=(\d+)',sh('dumpsys','package','cn.walkpaint')).group(1))
def cache_files():
    try:return sh('run-as','cn.walkpaint','ls','cache/updates').splitlines()
    except RuntimeError:return []
try:
    assert installed_code()==15
    launch();update_page();tap('检查新版')
    wait_for('发现新版本 0.14')
    checks['actual_private_new_version_detected']=True;shot('new-private-version')
    tap('下载新版')
    visible=labels()
    if '取消' in visible:
        tap('取消');wait_for('已取消，仍可使用原版本');time.sleep(2)
        assert 'pending.apk' not in cache_files()
        checks['actual_download_cancelled_and_partial_removed']=True;shot('real-download-cancelled')
        tap('下载新版')
    else:checks['actual_download_cancelled_and_partial_removed']='SKIPPED: transfer completed before cancellation; unit cancellation remains verified'
    deadline=time.monotonic()+600;last_progress=None
    while time.monotonic()<deadline:
        visible=labels()
        if '安装包已校验，可交给系统安装' in visible:break
        progress=next((t for t in visible if re.fullmatch(r'已下载 \d+%',t)),None)
        if progress and progress!=last_progress:
            print(progress,flush=True);last_progress=progress
        if '下载新版' in visible and '取消' not in visible:raise AssertionError('Download failed; safe app message='+';'.join(t for t in visible if '重试' in t or '失败' in t or '授权' in t))
        time.sleep(1)
    else:raise AssertionError('Actual private download timed out')
    checks['actual_private_download_finished']=True
    cache_sha=sh('run-as','cn.walkpaint','sha256sum','cache/updates/update.apk').split()[0]
    assert cache_sha==expected;checks['download_hash_matches_final_artifact']=True;shot('verified-apk-ready')
    assert original==rows()
    tap('交给系统安装')
    visible=wait_for('Allow from this source','允许来自此来源的应用','Update','更新',seconds=80)
    if 'Allow from this source' in visible or '允许来自此来源的应用' in visible:
        assert not enabled();shot('system-install-source-denied')
        back();wait_for('交给系统安装')
        assert installed_code()==15 and original==rows()
        checks['refusing_source_permission_keeps_old_app_and_rows']=True
        tap('交给系统安装');wait_for('Allow from this source','允许来自此来源的应用',seconds=80)
        toggle();assert enabled();back();wait_for('交给系统安装')
        tap('交给系统安装');wait_for('Update','更新',seconds=80)
    else:checks['refusing_source_permission_keeps_old_app_and_rows']='SKIPPED: source already allowed before test'
    assert '足迹涂色' in labels();shot('real-system-installer')
    tap('Cancel','取消');wait_for('交给系统安装')
    assert installed_code()==15 and original==rows();checks['cancelling_installer_preserves_old_app_and_rows']=True
    tap('交给系统安装');wait_for('Update','更新',seconds=80);tap('Update','更新')
    deadline=time.monotonic()+120
    while time.monotonic()<deadline:
        if installed_code()==16:break
        time.sleep(2)
    else:raise AssertionError('System installer did not produce code 16')
    checks['real_system_cover_install_code_15_to_16']=True
    remote=sh('pm','path','cn.walkpaint').split('package:')[1].strip()
    checks['installed_apk_matches_final_artifact']=hashlib.sha256(run('exec-out','cat',remote,binary=True)).hexdigest()==expected
    assert checks['installed_apk_matches_final_artifact']
    assert original==rows();checks['all_original_database_rows_unchanged']=True
    if 'App installed.' in labels():tap('Done')
    launch();update_page();tap('检查新版');wait_for('最新发布 0.14，当前版本无需更新')
    checks['authorization_survives_cover_install_and_no_update_is_real']=True;shot('updated-version-private-check')
    tap('退出更新授权');tap('退出授权');wait_for('已退出更新授权，原足迹保留')
    assert '退出更新授权' not in labels();assert not cache_files();assert original==rows()
    checks['local_logout_cache_cleanup_and_rows_retained']=True;shot('logout-complete')
except Exception as exc:
    checks['failure']=repr(exc)
    # The update page/installer never includes tokens or a device challenge in this test.
    try:shot('failed-stage')
    except Exception:pass
    raise
finally:
    sh('cmd','appops','set','cn.walkpaint','REQUEST_INSTALL_PACKAGES',original_install_mode)
    sh('am','force-stop','cn.walkpaint');sh('rm','-f','/sdcard/wp-profile-ui.xml')
    checks['install_permission_restored']=True
    out.joinpath('acceptance.json').write_text(json.dumps(checks,indent=2,ensure_ascii=False),encoding='utf-8')
print(json.dumps(checks,ensure_ascii=False))

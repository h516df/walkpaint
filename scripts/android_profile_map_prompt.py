"""Check map preparation prompt/deferral and disclosure without any download."""
from pathlib import Path
exec(Path(__file__).with_name('android_profile_first_permission.py').read_text(encoding='utf-8').split('\ntry:')[0])
tracking=run('exec-out','run-as','cn.walkpaint','cat','shared_prefs/footprint_preferences.xml',binary=True)
def write_tracking(payload):
    local=out/'tracking-restore.xml';local.write_bytes(payload)
    remote='/data/local/tmp/wp-profile-tracking-restore.xml'
    run('push',str(local.resolve()),remote)
    sh('run-as','cn.walkpaint','cp',remote,'shared_prefs/footprint_preferences.xml')
    sh('rm',remote);local.unlink()
try:
    for p in permissions:sh('pm','grant','cn.walkpaint','android.permission.'+p)
    launch();tap('我的');toggle();time.sleep(2);tap('地图')
    assert '还没准备好这里的地图' in labels();shot('map-preparation-prompt')
    tap('暂不');tap('我的');tap('地图')
    assert '还没准备好这里的地图' not in labels()
    checks['map_preparation_deferral_survives_tab_switch']=True
    tap('我的');toggle();tap('更多设置');tap('地图数据');tap('准备这里的地图')
    text=' '.join(labels())
    assert 'overpass-api.de' in text and 'IP' in text and '同意并下载本次' in text
    shot('road-download-independent-consent');tap('暂不下载');back()
    checks['download_disclosure_and_cancel']=True
    assert all(original_rows[t]<=rows()[t] for t in original_rows)
    checks['map_prompt_original_rows_retained']=True
except Exception as e:
    checks['failure']=str(e);shot('map-prompt-failure');raise
finally:
    sh('am','force-stop','cn.walkpaint');write_tracking(tracking)
    for p,g in original_permissions.items():
        sh('pm','grant' if g else 'revoke','cn.walkpaint','android.permission.'+p)
        for f in ['user-set','user-fixed']:
            sh('pm','set-permission-flags' if f.upper().replace('-','_') in original_flags[p] else 'clear-permission-flags',
                'cn.walkpaint','android.permission.'+p,f)
    sh('rm','-f','/sdcard/wp-profile-ui.xml')
    checks['original_tracking_preference_restored']=run('exec-out','run-as','cn.walkpaint','cat','shared_prefs/footprint_preferences.xml',binary=True)==tracking
    out.joinpath('map-prompt.json').write_text(json.dumps(checks,indent=2),encoding='utf-8')
    print(json.dumps(checks),flush=True)

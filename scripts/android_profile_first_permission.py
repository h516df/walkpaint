"""Fresh permission-choice UI on Google AVD; restores onboarding and permissions."""
from pathlib import Path
exec(Path(__file__).with_name('android_profile_acceptance.py').read_text(encoding='utf-8').split('\npermissions=')[0])
permissions=['ACCESS_COARSE_LOCATION','ACCESS_FINE_LOCATION','ACCESS_BACKGROUND_LOCATION','POST_NOTIFICATIONS']
package=sh('dumpsys','package','cn.walkpaint')
original_permissions={p:bool(re.search(r'android.permission.'+p+r': granted=true',package)) for p in permissions}
original_flags={p:re.search(r'android.permission.'+p+r': granted=\w+, flags=\[([^\]]*)\]',package).group(1) for p in permissions}
original_onboarding=run('exec-out','run-as','cn.walkpaint','cat','shared_prefs/ui_onboarding.xml',binary=True)
original_rows=rows()
def write_onboarding(payload):
    local=out/'first-permission-state.xml';local.write_bytes(payload)
    remote='/data/local/tmp/wp-first-permission.xml'
    run('push',str(local.resolve()),remote)
    sh('run-as','cn.walkpaint','cp',remote,'shared_prefs/ui_onboarding.xml')
    sh('rm',remote);local.unlink()
try:
    sh('am','force-stop','cn.walkpaint')
    for p in permissions:
        sh('pm','revoke','cn.walkpaint','android.permission.'+p)
        sh('pm','clear-permission-flags','cn.walkpaint','android.permission.'+p,'user-set','user-fixed')
    state=ET.fromstring(original_onboarding)
    for name in ['auto_notice_v3_seen','fine_request_seen']:
        for n in list(state):
            if n.get('name')==name:state.remove(n)
        ET.SubElement(state,'boolean',{'name':name,'value':'false'})
    write_onboarding(ET.tostring(state,encoding='utf-8',xml_declaration=True))
    launch();tap('我的');assert not enabled()
    toggle();assert '同意并继续' in labels();shot('first-recording-consent')
    tap('暂不开启');assert not enabled() and not foreground()
    toggle();tap('同意并继续');assert 'While using the app' in labels();shot('first-location-system')
    tap("Don't allow",'不允许');tap('稍后');assert not enabled() and not foreground()
    checks['first_consent_cancel_and_single_system_request']=True
    assert rows()==original_rows
    checks['first_permission_ui_changes_no_database_rows']=True
except Exception as e:
    checks['failure']=str(e);shot('first-permission-failure');raise
finally:
    sh('am','force-stop','cn.walkpaint');write_onboarding(original_onboarding)
    for p,g in original_permissions.items():
        sh('pm','grant' if g else 'revoke','cn.walkpaint','android.permission.'+p)
        for f in ['user-set','user-fixed']:
            sh('pm','set-permission-flags' if f.upper().replace('-','_') in original_flags[p] else 'clear-permission-flags',
                'cn.walkpaint','android.permission.'+p,f)
    sh('rm','-f','/sdcard/wp-profile-ui.xml')
    checks['original_onboarding_restored']=run('exec-out','run-as','cn.walkpaint','cat','shared_prefs/ui_onboarding.xml',binary=True)==original_onboarding
    out.joinpath('first-permission.json').write_text(json.dumps(checks,indent=2),encoding='utf-8')
    print(json.dumps(checks),flush=True)

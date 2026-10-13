"""Verify a real higher-code candidate before publishing, without installing or reading credentials."""
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]
apk=Path(sys.argv[1]).resolve();test_apk=Path(sys.argv[2]).resolve()
sys.path.insert(0,str(root/'scripts'));sys.argv=[__file__];__file__=str(root/'scripts/android_profile_acceptance.py')
exec(compile((root/'scripts/android_profile_acceptance.py').read_text(encoding='utf-8').split('\npermissions=')[0],__file__,'exec'))
out=root/'verification/private-update-candidate';out.mkdir(parents=True,exist_ok=True)
original=rows();digest=hashlib.sha256(apk.read_bytes()).hexdigest()
temporary='/data/local/tmp/walkpaint-candidate-probe.apk'
try:
    assert 'versionCode=15' in sh('dumpsys','package','cn.walkpaint')
    assert 'Success' in run('install','-r',str(test_apk),timeout=120)
    run('push',str(apk),temporary,timeout=150)
    sh('run-as','cn.walkpaint','cp',temporary,'cache/walkpaint-candidate-probe.apk')
    result=sh('am','instrument','-w','-e','candidateSha',digest,'cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation')
    assert 'PRIVATE_UPDATE_NEW_APK_PASS' in result,result
    checks={'candidate_validation':result.strip(),'candidate_sha256':digest,'old_version_still_installed':True,
            'all_rows_unchanged':original==rows()}
    assert checks['all_rows_unchanged']
finally:
    sh('rm','-f',temporary)
    sh('run-as','cn.walkpaint','rm','-f','cache/walkpaint-candidate-probe.apk')
    sh('am','force-stop','cn.walkpaint')
    out.joinpath('acceptance.json').write_text(json.dumps(checks,indent=2),encoding='utf-8')
print(json.dumps(checks))

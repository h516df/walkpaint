"""Actual Android crypto/package/provider probe on guarded Google AVD only."""
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]
test_apk=Path(sys.argv[1]).resolve()
sys.path.insert(0,str(root/'scripts'));sys.argv=[__file__];__file__=str(root/'scripts/android_profile_acceptance.py')
exec(compile((root/'scripts/android_profile_acceptance.py').read_text(encoding='utf-8').split('\npermissions=')[0],__file__,'exec'))
out=root/'verification/private-update-boundaries';out.mkdir(parents=True,exist_ok=True)
original=rows()
try:
    assert 'Success' in run('install','-r',str(test_apk),timeout=100)
    result=sh('am','instrument','-w','-e','privateUpdateProbe','true','cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation')
    assert 'PRIVATE_UPDATE_ANDROID_BOUNDARIES_PASS' in result,result
    checks['android_boundaries']=result.strip()
    remote=sh('pm','path','cn.walkpaint').split('package:')[1].strip()
    checks['installed_apk_sha256']=hashlib.sha256(run('exec-out','cat',remote,binary=True)).hexdigest()
    checks['database_exactly_retained']=original==rows();assert checks['database_exactly_retained']
    checks['isolated_probe_cleaned']=not any('update-probe-' in sh('run-as','cn.walkpaint','ls',d) for d in ['cache','shared_prefs'])
    assert checks['isolated_probe_cleaned']
finally:
    sh('am','force-stop','cn.walkpaint')
    out.joinpath('acceptance.json').write_text(json.dumps(checks,indent=2,ensure_ascii=False),encoding='utf-8')
print(json.dumps(checks,ensure_ascii=False))

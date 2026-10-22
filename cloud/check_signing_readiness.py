"""Actual APK signature probe plus rejection checks. Never uploads its APK."""
import json,os,pathlib,secrets,subprocess,sys,tempfile
from sign_apk import execute
identity=json.loads((pathlib.Path(__file__).resolve().parent.parent/'release/signing-identity.json').read_text())
receipt=execute(identity,probe=True)
script=pathlib.Path(__file__).with_name('sign_apk.py')
def rejected(env,identity_path=None):
    args=[sys.executable,str(script),'--probe']
    if identity_path:args+=['--identity',str(identity_path)]
    result=subprocess.run(args,env=env,capture_output=True)
    if result.returncode==0:raise SystemExit('Fail-closed rejection check failed.')
missing=dict(os.environ);missing.pop('SIGNING_KEYSTORE_BASE64',None);rejected(missing)
wrong=dict(os.environ,SIGNING_STORE_PASSWORD=secrets.token_hex(32));rejected(wrong)
with tempfile.TemporaryDirectory(prefix='wrong-identity-',dir=os.environ.get('RUNNER_TEMP')) as folder:
    fake=pathlib.Path(folder)/'identity.json'
    fake.write_text(json.dumps(dict(identity,certificateSha256='0'*64)))
    rejected(os.environ.copy(),fake)
receipt['missingInputsRejected']=True
receipt['wrongPasswordRejected']=True
receipt['wrongCertificateRejected']=True
print(json.dumps(receipt,sort_keys=True))

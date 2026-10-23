#!/usr/bin/env python3
"""Explicit, fail-closed APK signing. No Gradle code executes with signing secrets."""
import argparse,base64,hashlib,json,os,pathlib,re,shutil,subprocess,tempfile

HERE=pathlib.Path(__file__).resolve().parent
def run(args,env=None,check=True):
    result=subprocess.run([str(x) for x in args],env=env,capture_output=True,text=True,encoding='utf8',errors='replace')
    if check and result.returncode:raise ValueError(pathlib.Path(str(args[0])).name+' failed; signing aborted (sensitive tool output withheld).')
    return result
def tools():
    sdk=pathlib.Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '')
    build=sdk/'build-tools'/'36.0.0'
    aapt=build/('aapt2.exe' if os.name=='nt' else 'aapt2')
    signer=build/'lib'/'apksigner.jar'
    java=pathlib.Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java') if os.environ.get('JAVA_HOME') else 'java'
    if not aapt.is_file() or not signer.is_file():raise ValueError('Android Build Tools 36.0.0 are required.')
    return sdk,aapt,signer,java
def credentials(env):
    names=['SIGNING_KEYSTORE_BASE64','SIGNING_STORE_PASSWORD','SIGNING_KEY_PASSWORD']
    if not all(env.get(n) for n in names):raise ValueError('Original signing inputs missing; official signing refused.')
    try:data=base64.b64decode(env[names[0]],validate=True)
    except Exception:raise ValueError('Invalid keystore encoding; official signing refused.') from None
    if not data:raise ValueError('Empty original keystore; official signing refused.')
    return data
def check_identity(proof,identity):
    if not proof.get('privateKeyVerified') or proof.get('certificateSha256')!=identity['certificateSha256']:
        raise ValueError('Original certificate/private-key mismatch; official signing refused.')
def check_apk(text,identity):
    match=re.search(r"package: name='([^']+)'",text)
    if not match or match[1]!=identity['packageName']:raise ValueError('APK package mismatch; official signing refused.')
    if 'application-debuggable' in text:raise ValueError('Debuggable APK; official signing refused.')
def execute(identity,input_apk=None,output=None,probe=False):
    data=credentials(os.environ)
    sdk,aapt,signer,java=tools()
    if not probe and (not input_apk or not output):raise ValueError('Explicit unsigned input and output required.')
    if output and output.exists():raise ValueError('Output already exists; replacement refused.')
    # Python TemporaryDirectory is private (0700) on the disposable Linux runner.
    with tempfile.TemporaryDirectory(prefix='original-signing-',dir=os.environ.get('RUNNER_TEMP')) as folder:
        temp=pathlib.Path(folder)
        key=temp/'original.keystore';key.write_bytes(data);key.chmod(0o600)
        env=dict(os.environ,PROBE_STORE_PASSWORD=os.environ['SIGNING_STORE_PASSWORD'],PROBE_KEY_PASSWORD=os.environ['SIGNING_KEY_PASSWORD'])
        proof=json.loads(run([java,HERE/'KeyProbe.java',key,identity['alias']],env).stdout)
        check_identity(proof,identity)
        if probe:
            manifest=temp/'AndroidManifest.xml'
            manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'+identity['packageName']+'" android:versionCode="1" android:versionName="signing-probe"><uses-sdk android:minSdkVersion="24" android:targetSdkVersion="36"/><application android:label="Synthetic signing probe" android:debuggable="false"/></manifest>',encoding='utf8')
            input_apk=temp/'synthetic-unsigned.apk'
            run([aapt,'link','-I',sdk/'platforms/android-36/android.jar','--manifest',manifest,'-o',input_apk])
        check_apk(run([aapt,'dump','badging',input_apk]).stdout,identity)
        # A build candidate must be explicitly unsigned; do not silently re-sign another identity.
        if run([java,'-jar',signer,'verify',input_apk],check=False).returncode==0:
            raise ValueError('Input is already signed; original unsigned candidate required.')
        candidate=temp/'verified-signed.apk'
        run([java,'-jar',signer,'sign','--ks',key,'--ks-key-alias',identity['alias'],'--ks-pass','env:SIGNING_STORE_PASSWORD','--key-pass','env:SIGNING_KEY_PASSWORD','--out',candidate,input_apk])
        verified=run([java,'-jar',signer,'verify','--print-certs',candidate]).stdout
        cert=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})',verified)
        if not cert or cert[1].lower()!=identity['certificateSha256']:raise ValueError('Signed APK certificate mismatch.')
        receipt={'packageName':identity['packageName'],'certificateSha256':cert[1].lower(),'privateKeyVerified':True,'apkSignatureVerified':True,'syntheticProbe':probe,'sha256':hashlib.sha256(candidate.read_bytes()).hexdigest(),'size':candidate.stat().st_size}
        if output:
            output.parent.mkdir(parents=True,exist_ok=True)
            # Exclusive creation prevents replacement even if another process races this task.
            with output.open('xb') as target,candidate.open('rb') as source:shutil.copyfileobj(source,target)
        return receipt
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--identity',type=pathlib.Path,default=HERE.parent/'release/signing-identity.json')
    parser.add_argument('--input',type=pathlib.Path)
    parser.add_argument('--output',type=pathlib.Path)
    parser.add_argument('--probe',action='store_true')
    args=parser.parse_args()
    try:
        identity=json.loads(args.identity.read_text(encoding='utf8'))
        print(json.dumps(execute(identity,args.input,args.output,args.probe),sort_keys=True))
    except Exception as e:raise SystemExit('Signing blocked: '+str(e)) from None
if __name__=='__main__':main()

"""Verify the installed APK, loaded AMap ELF and map-loaded callback on the dedicated AVD.

Install the same-signature instrumentation separately with adb install -r. This command never
installs, uninstalls, clears data, changes consent or targets a phone. Fallback is a failure.
"""
import argparse
import os
import hashlib
import json
import re
import struct
import subprocess
import zipfile
from pathlib import Path
from google_avd_guard import require_walkpaint_google_avd

ADB = r'D:\Apps\Android\sdk\platform-tools\adb.exe'
SERIAL = os.environ.get('WALKPAINT_AVD_SERIAL', 'emulator-5560')


def mapped_amap_libraries(apk, offsets):
    found = []
    with Path(apk).open('rb') as source, zipfile.ZipFile(apk) as archive:
        for entry in archive.infolist():
            if not entry.filename.startswith('lib/arm64-v8a/libAMapSDK_MAP'):
                continue
            if entry.compress_type != zipfile.ZIP_STORED:
                continue
            source.seek(entry.header_offset)
            header = source.read(30)
            name_len, extra_len = struct.unpack_from('<HH', header, 26)
            start = entry.header_offset + 30 + name_len + extra_len
            if any(start <= int(offset, 16) < start + entry.file_size for offset in offsets):
                found.append(entry.filename)
    return found


def verify_runtime(apk, runtime):
    with Path(apk).open('rb') as source:
        expected = hashlib.file_digest(source, 'sha256').hexdigest()
    if runtime.get('apk_sha256') != expected:
        raise ValueError('Installed APK differs from the supplied artifact')
    if runtime.get('map_loaded') is not True:
        raise ValueError('AMap did not complete loading')
    libraries = mapped_amap_libraries(apk, runtime.get('apk_mapping_offsets', []))
    if not libraries and runtime.get('direct_amap_mappings', 0) < 1:
        raise ValueError('AMap JNI is not mapped in the actual application process')
    return {'apk_sha256': expected, 'engine': 'amap', 'map_loaded': True,
            'mapped_libraries': libraries, 'phone_path': 'not_verified',
            'coordinate_roundtrip_max_metres': runtime.get('coordinate_roundtrip_max_metres')}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    evidence = {'serial': SERIAL, 'passed': False, 'phone_path': 'not_verified'}
    try:
        require_walkpaint_google_avd(ADB, SERIAL)
        result = subprocess.run([ADB, '-s', SERIAL, 'shell', 'am', 'instrument', '-w',
            '-e', 'amapRuntimeProbe', 'true',
            'cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation'],
            capture_output=True, timeout=150)
        output = result.stdout.decode('utf-8', 'replace')
        match = re.search(r'AMAP_RUNTIME_PASS: (\{[^\r\n]+\})', output)
        if result.returncode or not match:
            failure = re.search(r'AMAP_RUNTIME_FAIL: [^\r\n]+', output)
            raise RuntimeError(failure.group(0) if failure else 'Runtime probe did not report success')
        evidence.update(verify_runtime(args.apk, json.loads(match.group(1))))
        evidence['passed'] = True
    except Exception as exc:
        evidence['error'] = str(exc)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(evidence, ensure_ascii=False))
    return 0 if evidence['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())


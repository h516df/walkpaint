"""Read-only UI journeys on the dedicated AVD and an already installed, verified release APK.

Install the same-signature test APK separately. This never installs, clears data or targets a phone.
The native display fixture stays in memory; all output is anonymous counts and alignment errors.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from google_avd_guard import require_walkpaint_google_avd
from amap_runtime_evidence import verify_runtime

ADB = r'D:\Apps\Android\sdk\platform-tools\adb.exe'
SERIAL = os.environ.get('WALKPAINT_AVD_SERIAL', 'emulator-5560')
PROBES = {'return': ('mapReturnProbe', 'MAP_RETURN_RESULT'),
          'history': ('historyCameraProbe', 'HISTORY_CAMERA_PASS'),
          'scene': ('mapSceneProbe', 'MAP_SCENE_PASS')}


def parse_result(output, prefix):
    match = re.search(r'^' + re.escape(prefix) + r': (\{[^\r\n]+\})', output, re.MULTILINE)
    if not match or '_FAIL:' in output or 'INSTRUMENTATION_FAILED' in output:
        raise ValueError('Native journey did not pass; preserve output')
    result = json.loads(match.group(1))
    if result.get('fixture_points', result.get('fixture_total_points')) != 7959:
        raise ValueError('Wrong display fixture')
    if prefix == 'MAP_RETURN_RESULT':
        journeys = result.get('journeys', [])
        if len(journeys) != 4 or not all(j.get('same_map') and j.get('kept_while_hidden') for j in journeys):
            raise ValueError('Map was recreated in the actual journey')
    if prefix == 'HISTORY_CAMERA_PASS' and not all(result.get(k) is True for k in
            ('fog_only_selected_day', 'marker_is_day_endpoint', 'locate_keeps_day')):
        raise ValueError('Day review is incomplete')
    if prefix == 'MAP_SCENE_PASS' and len(result.get('samples', [])) < 25:
        raise ValueError('Animation samples are incomplete')
    return result


def native(flag):
    proc = subprocess.run([ADB, '-s', SERIAL, 'shell', 'am', 'instrument', '-w',
        '-e', flag, 'true', 'cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation'],
        capture_output=True, timeout=180)
    if proc.returncode:
        raise ValueError('Native instrumentation command failed')
    return proc.stdout.decode('utf-8', 'replace')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--probe', choices=PROBES, required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    evidence = {'passed': False, 'serial': SERIAL, 'phone_path': 'not_verified', 'probe': args.probe}
    try:
        require_walkpaint_google_avd(ADB, SERIAL)
        runtime = native('amapRuntimeProbe')
        match = re.search(r'AMAP_RUNTIME_PASS: (\{[^\r\n]+\})', runtime)
        if not match:
            raise ValueError('Actual AMap runtime not available')
        evidence['runtime'] = verify_runtime(args.apk, json.loads(match.group(1)))
        flag, prefix = PROBES[args.probe]
        output = native(flag)
        (args.out / 'native-result.txt').write_text(output, encoding='utf-8')
        evidence['result'] = parse_result(output, prefix)
        evidence['passed'] = True
    except (ValueError, OSError, subprocess.TimeoutExpired) as exc:
        evidence['error'] = str(exc)
    (args.out / 'evidence.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(evidence, ensure_ascii=False), flush=True)
    return 0 if evidence['passed'] else 1


if __name__ == '__main__':
    sys.exit(main())

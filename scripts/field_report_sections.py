"""Walk to the field export on the installed build and check every section the objective asks for.

Version-neutral on purpose: the export grew a map-engine and veil-pixel block in 0.41, and both the
old and the new material have to keep arriving, so this lists what the export must contain rather
than snapshotting one release.

Usage: python -X utf8 scripts/field_report_sections.py   (emulator-5556 up, a build installed)
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = r'D:\Apps\Android\sdk\platform-tools\adb.exe'
D = 'emulator-5556'

REQUIRED = {
    '环境与权限': '权限与后台',
    '地图引擎': '地图引擎',
    '引擎判定(0.41)': '引擎判定=',
    '引擎明细(0.41)': '本机首选ABI=',
    '迷雾像素统计(0.41)': '雾位图=',
    '迷雾不透光(0.41)': '不透光=',
    '迷雾纯黑(0.41)': '其中纯黑=',
    '雾帧分辨率(0.41)': '每米=',
    '诊断结论': '结论（自动判定',
    '记录状态': '记录状态',
    '相机与视野': '地图、视野与迷雾',
    '原始定位流': '原始定位',
    '被丢弃的点与原因': '被丢弃的点与原因',
    '事件时间线': '事件时间线',
    '数据库硬指标': '数据库点数',
    '隐私声明': '不会自动上传',
}


def sh(*a):
    return subprocess.run([ADB, '-s', D, *a], capture_output=True, text=True).stdout


def dump():
    for _ in range(6):
        sh('shell', 'uiautomator', 'dump', '/sdcard/ui.xml')
        raw = subprocess.run([ADB, '-s', D, 'exec-out', 'cat', '/sdcard/ui.xml'],
                             capture_output=True).stdout.decode('utf-8', 'replace')
        try:
            return ET.fromstring(raw)
        except ET.ParseError:
            time.sleep(3)
    raise SystemExit('no parseable dump')


def items(root):
    return [(n.attrib.get('text', '').strip(), n.attrib.get('bounds', ''))
            for n in root.iter() if (n.attrib.get('text') or '').strip()]


def tap(root, label):
    for text, bounds in items(root):
        if label in text:
            m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
            if m:
                sh('shell', 'input', 'tap', str((int(m.group(1)) + int(m.group(3))) // 2),
                   str((int(m.group(2)) + int(m.group(4))) // 2))
                time.sleep(2.5)
                return True
    return False


sh('shell', 'am', 'force-stop', 'cn.walkpaint')
sh('shell', 'am', 'start', '-n', 'cn.walkpaint/.MainActivity')
time.sleep(12)


def must_tap(root, label):
    """A missed tap makes every later finding meaningless, so it stops the run instead."""
    if not tap(root, label):
        print('FAILED: the screen has no "%s" to tap; the export was never reached' % label,
              flush=True)
        raise SystemExit(4)


root = dump()
must_tap(root, '我的')
root = dump()
must_tap(root, '更多设置')
root = dump()
must_tap(root, '诊断与反馈')
root = dump()
print('export button ->', tap(root, '导出记录现场'), flush=True)
time.sleep(12)
root = dump()
joined = '\n'.join(t for t, _ in items(root))
print('report length on screen:', len(joined), flush=True)
missing = [name for name, marker in REQUIRED.items() if marker not in joined]
for name, marker in REQUIRED.items():
    print('  %-22s %s' % (name, 'OK' if marker in joined else 'MISSING'), flush=True)
print('MISSING:', missing, flush=True)
sample = [line for line in joined.split('\n') if '权限与后台' in line or '数据库点数' in line
          or '引擎判定' in line or '雾位图' in line or '雾帧' in line or '本机首选ABI' in line]
for line in sample[:6]:
    print('  >', line[:260], flush=True)

# A missing section is a failure, not a note: the export is the only instrument this project has
# for the phone's own engine, and a silently thinner report is how a "verified" claim goes wrong.
if not joined.strip():
    print('FAILED: the export produced nothing on screen', flush=True)
    raise SystemExit(2)
if missing:
    print('FAILED: the export is missing %d section(s): %s' % (len(missing), missing), flush=True)
    raise SystemExit(3)
print('PASSED: all %d expected sections are present' % len(REQUIRED), flush=True)
sys.exit(0)

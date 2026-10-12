"""Install the signed universal release build and walk the in-app update entry on the device."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = r'D:\Apps\Android\sdk\platform-tools\adb.exe'
D = 'emulator-5556'
APK = r'D:\Worktrees\WalkPaint\dsh-v039-fog\dist\v0.40\input\WalkPaint-v0.40-universal.apk'


def sh(*a, timeout=None):
    return subprocess.run([ADB, '-s', D, *a], capture_output=True, text=True, timeout=timeout).stdout


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


print('installing the release build over the debug one', flush=True)
print(sh('install', '-r', '-d', APK, timeout=900)[-120:], flush=True)
version = sh('shell', 'dumpsys', 'package', 'cn.walkpaint')
m = re.search(r'versionName=(\S+)', version)
print('installed:', m.group(1) if m else 'unknown', flush=True)
flags = re.search(r'flags=\[([^\]]*)\]', version)
print('flags:', (flags.group(1)[:120] if flags else 'n/a'), flush=True)

sh('shell', 'am', 'force-stop', 'cn.walkpaint')
sh('shell', 'am', 'start', '-n', 'cn.walkpaint/.MainActivity')
time.sleep(12)
root = dump()
tap(root, '我的')
root = dump()
tap(root, '更多设置')
root = dump()
print('settings has 检查更新 ->', any('检查更新' in t for t, _ in items(root)), flush=True)
tap(root, '检查更新')
root = dump()
texts = [t for t, _ in items(root)]
print('--- updates page ---', flush=True)
for t in texts[:14]:
    print('  ', t[:90], flush=True)

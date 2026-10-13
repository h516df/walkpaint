"""Reboot the dedicated AVD, then walk the profile -> diagnostics -> field export path on device."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = r'D:\Apps\Android\sdk\platform-tools\adb.exe'
EMU = r'D:\Apps\Android\sdk\emulator\emulator.exe'
D = 'emulator-5556'


def sh(*a):
    return subprocess.run([ADB, '-s', D, *a], capture_output=True, text=True).stdout


def dump():
    for _ in range(5):
        sh('shell', 'uiautomator', 'dump', '/sdcard/ui.xml')
        raw = subprocess.run([ADB, '-s', D, 'exec-out', 'cat', '/sdcard/ui.xml'],
                             capture_output=True).stdout.decode('utf-8', 'replace')
        try:
            return ET.fromstring(raw)
        except ET.ParseError:
            time.sleep(3)
    raise SystemExit('uiautomator never produced a parseable dump')


def items(root):
    return [(n.attrib.get('text', '').strip(), n.attrib.get('bounds', ''))
            for n in root.iter() if (n.attrib.get('text') or '').strip()]


def tap(root, label):
    for text, bounds in items(root):
        if label in text:
            m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
            if m:
                x = (int(m.group(1)) + int(m.group(3))) // 2
                y = (int(m.group(2)) + int(m.group(4))) // 2
                sh('shell', 'input', 'tap', str(x), str(y))
                time.sleep(2.5)
                return True
    return False


print('restarting the AVD', flush=True)
subprocess.run([ADB, '-s', D, 'emu', 'kill'], capture_output=True)
time.sleep(8)
subprocess.run(['taskkill', '/F', '/IM', 'emulator.exe'], capture_output=True)
subprocess.run(['taskkill', '/F', '/IM', 'qemu-system-x86_64.exe'], capture_output=True)
time.sleep(4)
subprocess.Popen([EMU, '-avd', 'WalkPaint_Google_API36', '-port', '5556',
                  '-no-snapshot-save', '-no-boot-anim', '-gpu', 'host'],
                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
booted = False
for _ in range(72):
    if subprocess.run([ADB, '-s', D, 'shell', 'getprop', 'sys.boot_completed'],
                      capture_output=True, text=True).stdout.strip() == '1':
        booted = True
        break
    time.sleep(5)
print('booted =', booted, flush=True)
if not booted:
    raise SystemExit('the AVD did not boot')
time.sleep(45)

version = sh('shell', 'dumpsys', 'package', 'cn.walkpaint')
m = re.search(r'versionName=(\S+)', version)
print('installed version =', m.group(1) if m else 'unknown', flush=True)

sh('shell', 'am', 'force-stop', 'cn.walkpaint')
sh('shell', 'am', 'start', '-n', 'cn.walkpaint/.MainActivity')
time.sleep(12)

root = dump()
print('map tab ->', [t for t, _ in items(root)][:4], flush=True)
tap(root, '我的')
root = dump()
tap(root, '更多设置')
root = dump()
print('更多设置含诊断与反馈 ->', any('诊断与反馈' in t for t, _ in items(root)), flush=True)
tap(root, '诊断与反馈')
root = dump()
texts = [t for t, _ in items(root)]
print('反馈页前 5 条 ->', [t[:36] for t in texts[:5]], flush=True)
print('导出记录现场按钮存在 ->', any('导出记录现场' in t for t in texts), flush=True)
print('点导出 ->', tap(root, '导出记录现场'), flush=True)
time.sleep(10)
root = dump()
preview = [t for t, _ in items(root) if any(k in t for k in ('记录现场', '原始定位', '被丢弃', '行走坐标', '结论'))]
print('预览命中片段 =', len(preview), flush=True)
for t in preview[:2]:
    print('  ', t[:500].replace('\n', ' | '), flush=True)

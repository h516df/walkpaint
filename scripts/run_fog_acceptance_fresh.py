"""Runs one fog/line acceptance pass on a freshly booted dedicated AVD.

The reference emulator slows down as it accumulates installs and launches, and the pair has to be
measured under the same conditions: this restarts the AVD, waits for it to settle, and only then
hands over to the acceptance script.

Usage: python -X utf8 scripts/run_fog_acceptance_fresh.py <label> <apk> [extra acceptance args...]
"""
import subprocess, sys, time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = r'D:\Apps\Android\sdk\platform-tools\adb.exe'
SERIAL = 'emulator-5556'
EMULATOR = r'D:\Apps\Android\sdk\emulator\emulator.exe'

label, apk, *extra = sys.argv[1:]
if not label or not apk:
    raise SystemExit('usage: run_fog_acceptance_fresh.py <label> <apk> [args...]')

subprocess.run([ADB, '-s', SERIAL, 'emu', 'kill'], capture_output=True)
time.sleep(8)
subprocess.run(['taskkill', '/F', '/IM', 'emulator.exe'], capture_output=True)
subprocess.run(['taskkill', '/F', '/IM', 'qemu-system-x86_64.exe'], capture_output=True)
time.sleep(4)
subprocess.Popen([EMULATOR, '-avd', 'WalkPaint_Google_API36', '-port', '5556',
                  '-no-snapshot-save', '-no-boot-anim', '-gpu', 'host'],
                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

booted = False
for _ in range(60):
    state = subprocess.run([ADB, '-s', SERIAL, 'shell', 'getprop', 'sys.boot_completed'],
                           capture_output=True, text=True)
    if state.stdout.strip() == '1':
        booted = True
        break
    time.sleep(5)
if not booted:
    raise SystemExit('the AVD did not boot')
name = subprocess.run([ADB, '-s', SERIAL, 'shell', 'getprop', 'ro.boot.qemu.avd_name'],
                      capture_output=True, text=True).stdout.strip()
if name != 'WalkPaint_Google_API36':
    raise SystemExit('refusing: the device is not the dedicated AVD (' + name + ')')
# Let the freshly booted system finish its own startup work before anything is timed.
time.sleep(45)

command = [sys.executable, '-X', 'utf8', str(ROOT / 'scripts' / 'android_fog_line_acceptance.py'),
           '--label', label, '--apk', apk, *extra]
raise SystemExit(subprocess.call(command, cwd=str(ROOT)))

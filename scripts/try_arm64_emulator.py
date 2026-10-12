"""Can this x86_64 host run an arm64 Android AVD? Create one and find out, keeping the reason.

A fully emulated aarch64 guest boots far slower than a native x86_64 one, so the budget here is
15 minutes rather than the usual 3. Verdict and the emulator's own log tail are written to
verification/arm64-emulator.log; the log is the evidence, not this script's summary.
"""
import subprocess
import sys
import time
from pathlib import Path

SDK = Path(r'D:\Apps\Android\sdk')
AVDMANAGER = SDK / 'cmdline-tools' / 'latest' / 'bin' / 'avdmanager.bat'
EMULATOR = SDK / 'emulator' / 'emulator.exe'
ADB = SDK / 'platform-tools' / 'adb.exe'
IMAGE = 'system-images;android-34;google_apis;arm64-v8a'
NAME = 'WalkPaint_ARM64_API34'
SERIAL = 'emulator-5558'
WORKTREE = Path(r'D:\Worktrees\WalkPaint\dsh-v039-fog')
LOG = WORKTREE / 'verification' / 'arm64-emulator.log'
BOOT_BUDGET_S = 900
LOG.parent.mkdir(parents=True, exist_ok=True)


def sh(cmd, **kw):
    return subprocess.run([str(c) for c in cmd], capture_output=True, text=True, **kw)


def devices():
    return sh([ADB, 'devices']).stdout.strip().replace('\n', ' | ')


print('existing image?', sh([AVDMANAGER, 'list', 'target']).returncode == 0, flush=True)
listing = sh([AVDMANAGER, 'list', 'avd']).stdout
if NAME not in listing:
    create = sh([AVDMANAGER, 'create', 'avd', '-n', NAME, '-k', IMAGE, '-d', 'pixel_5',
                 '--force', '-c', '512M'], **{'input': 'no\n'})
    print('create rc =', create.returncode, flush=True)
    print((create.stdout or '')[-600:], flush=True)
    print((create.stderr or '')[-600:], flush=True)
    if create.returncode != 0:
        raise SystemExit('avdmanager could not create the AVD')
else:
    print('AVD already exists, reusing it', flush=True)

print('starting the arm64 emulator; its own output goes to', LOG, flush=True)
handle = open(LOG, 'w', encoding='utf-8', errors='replace')
proc = subprocess.Popen([str(EMULATOR), '-avd', NAME, '-port', '5558',
                         '-no-window', '-no-snapshot-save', '-no-boot-anim',
                         '-no-audio', '-gpu', 'swiftshader_indirect',
                         '-memory', '2048', '-cores', '2'],
                        stdout=handle, stderr=subprocess.STDOUT)

verdict = 'unknown'
state = ''
deadline = time.time() + BOOT_BUDGET_S
while time.time() < deadline:
    time.sleep(5)
    if proc.poll() is not None:
        verdict = 'emulator process exited, code %s' % proc.returncode
        break
    state = sh([ADB, '-s', SERIAL, 'shell', 'getprop', 'sys.boot_completed']).stdout.strip()
    if state == '1':
        verdict = 'booted'
        break
    if int((deadline - time.time()) // 5) % 12 == 0:
        print('waiting, boot_completed=%r devices=%s' % (state, devices()), flush=True)
else:
    verdict = 'did not boot within %d s' % BOOT_BUDGET_S

handle.flush()
print('VERDICT:', verdict, flush=True)
print('devices:', devices(), flush=True)
print('--- emulator log tail ---', flush=True)
tail = LOG.read_text(encoding='utf-8', errors='replace').splitlines()[-25:]
print('\n'.join(tail), flush=True)

if verdict == 'booted':
    for prop in ('ro.product.cpu.abi', 'ro.product.cpu.abilist', 'ro.build.version.sdk',
                 'ro.product.cpu.abilist32'):
        print(prop, '=', sh([ADB, '-s', SERIAL, 'shell', 'getprop', prop]).stdout.strip(), flush=True)
else:
    sh([ADB, '-s', SERIAL, 'emu', 'kill'])
    time.sleep(3)
    if proc.poll() is None:
        proc.terminate()
    print('arm64 path is not usable on this host; see the log above for the emulator reason',
          flush=True)
sys.exit(0 if verdict == 'booted' else 3)

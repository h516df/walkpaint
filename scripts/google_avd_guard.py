"""Fail-closed target verification for WalkPaint Android UI/GPS tests.

Never let an ADB regression test target the user's personal MuMu or a phone.
The target must be the dedicated Google official AVD, already booted.
"""
import subprocess

GOOGLE_SERIAL = "emulator-5556"
GOOGLE_AVD_NAME = "WalkPaint_Google_API36"
GOOGLE_AVDS = {GOOGLE_SERIAL: GOOGLE_AVD_NAME, "emulator-5560": "WalkPaint_Google_API36_V044"}


def require_walkpaint_google_avd(adb, serial):
    if serial not in GOOGLE_AVDS:
        raise RuntimeError(
            "Test device denied: use Google AVD emulator-5556; "
            "personal MuMu and phones must never be test targets. Only the fixed dedicated pairs are allowed."
        )
    try:
        result = subprocess.run(
            [str(adb), "-s", serial, "emu", "avd", "name"],
            capture_output=True, text=True, timeout=12,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise RuntimeError("Google AVD identity unavailable; refusing tests") from exc
    lines = [line.strip() for line in result.stdout.splitlines()]
    if result.returncode != 0 or GOOGLE_AVDS[serial] not in lines:
        raise RuntimeError(
            "Expected dedicated WalkPaint_Google_API36 AVD, not another emulator; "
            "refusing any device changes"
        )
    try:
        state = subprocess.run(
            [str(adb), "-s", serial, "shell", "getprop", "sys.boot_completed"],
            capture_output=True, text=True, timeout=12,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise RuntimeError("Google AVD boot state unavailable; refusing tests") from exc
    if state.returncode != 0 or state.stdout.strip() != "1":
        raise RuntimeError("Dedicated Google AVD not fully booted; refusing tests")

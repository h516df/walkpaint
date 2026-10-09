#!/usr/bin/env python3
"""WalkPaint Android emulator-only acceptance test.

Injects synthetic GPS fixes, checks actual SQLite writes, rejects stationary
drift, and confirms repair of fresh fixes with outdated provider timestamps.
Never run against a real user's phone. It does not clear application data.
"""
import argparse
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import time

p = argparse.ArgumentParser()
p.add_argument("--serial", default="emulator-5556")
p.add_argument("--adb", default=None)
args = p.parse_args()

adb = args.adb or shutil.which("adb") or r"D:\Apps\Android\sdk\platform-tools\adb.exe"
if not Path(adb).is_file() and not shutil.which(adb):
    p.error("ADB executable not found")
from google_avd_guard import require_walkpaint_google_avd
require_walkpaint_google_avd(adb, args.serial)
base = [adb, "-s", args.serial]

def cmd(*parts):
    result = subprocess.run(
        base + ["shell", *parts], text=True, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, timeout=30,
    )
    if result.returncode:
        raise RuntimeError("Android shell failed: " + result.stdout[-400:])
    return result.stdout

def real_points():
    raw = subprocess.check_output(
        base + ["exec-out", "run-as", "cn.walkpaint", "cat", "databases/footprints.db"],
        timeout=15,
    )
    db = sqlite3.connect(":memory:")
    db.deserialize(raw)
    return db.execute(
        "SELECT COUNT(*) FROM points p JOIN trips t ON t.id=p.trip_id WHERE t.demo=0"
    ).fetchone()[0]

def send_fix(lat, lon, accuracy=5, time_ms=None):
    # The coordinates below are fixed synthetic test data, never user location.
    parts = ["cmd", "location", "providers", "set-test-provider-location",
             "gps", "--location", f"{lat},{lon}", "--accuracy", str(accuracy)]
    if time_ms is not None:
        parts += ["--time", str(time_ms)]
    cmd(*parts)
    time.sleep(3)

before = real_points()
permission_state = cmd("appops", "get", "2000", "android:mock_location")
restore_mode = "allow" if "allow" in permission_state.lower() else "default"
permission_set = False
provider_set = False
try:
    cmd("appops", "set", "2000", "android:mock_location", "allow")
    permission_set = True
    cmd("cmd", "location", "providers", "add-test-provider",
        "gps", "--requiresSatellite", "--supportsSpeed", "--supportsBearing")
    provider_set = True
    cmd("cmd", "location", "providers", "set-test-provider-enabled", "gps", "true")

    for lat, lon in (
        (31.231000, 121.474200),
        (31.231082, 121.474284),
        (31.231164, 121.474368),
    ):
        send_fix(lat, lon)
    after_walk = real_points()
    if after_walk - before < 3:
        raise AssertionError(f"Short walk not saved: {before} -> {after_walk}")

    send_fix(31.231169, 121.474373, accuracy=12)
    after_drift = real_points()
    if after_drift != after_walk:
        raise AssertionError("Stationary drift was saved as a new step")

    # Four-minute-old GNSS wall-clock but fresh Android monotonic timestamp.
    send_fix(31.231250, 121.474450, time_ms=int(time.time() * 1000) - 240_000)
    after_repair = real_points()
    if after_repair <= after_drift:
        raise AssertionError("Fresh position with stale GNSS wall clock was lost")

    service = cmd("dumpsys", "activity", "services", "cn.walkpaint")
    if "isForeground=true" not in service:
        raise AssertionError("Foreground location service not running")

    print(
        "ANDROID_SMOKE_PASS",
        f"short_walk_added={after_walk-before}",
        "stationary_drift_added=0",
        f"repaired_timestamp_added={after_repair-after_drift}",
        "foreground_service=true",
    )
finally:
    if provider_set:
        cmd("cmd", "location", "providers", "remove-test-provider", "gps")
    if permission_set:
        cmd("appops", "set", "2000", "android:mock_location", restore_mode)
    print("MOCK_LOCATION_CLEANUP_DONE")

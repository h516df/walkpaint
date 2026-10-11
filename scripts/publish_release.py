#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Adapted from h516df/android-update-kit 3cf277b6d034c4bb999f9e894e3a73e06e67505b.

发布一个版本：出正式包 -> 核对版本号 -> 生成中文更新说明和 SHA256 -> 挂 GitHub Release。

仓库名和资产前缀从项目里的 UpdateConfig.kt 读，不在这里重复写一遍。

用法：
    python scripts/publish_release.py            # 构建 + 发布
    python scripts/publish_release.py --no-build # 用现成 APK
    python scripts/publish_release.py --dry-run  # 只准备文件
"""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
NOTES = ROOT / "app" / "src" / "main" / "assets" / "release-notes.json"
CONFIG = ROOT / "app" / "src" / "main" / "java" / "cn" / "walkpaint" / "PrivateUpdatePolicy.kt"
GRADLE = ROOT / ("gradlew.bat" if sys.platform.startswith("win") else "gradlew")
DIST = ROOT / "dist"
HEADINGS = ["新增", "改进", "修复", "说明"]


def run(argv, timeout=2400):
    proc = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    out = proc.stdout.decode("utf-8", "replace")
    if proc.returncode != 0:
        print(out[-3000:], flush=True)
        raise SystemExit("命令失败：" + " ".join(str(a) for a in argv))
    return out


def config():
    text = CONFIG.read_text(encoding="utf-8")
    repo = re.search(r'const val REPO\s*=\s*"([^"]+)"', text).group(1)
    prefix = "WalkPaint"
    return repo, prefix


def version_from_gradle():
    text = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    name = re.search(r'versionName = "([^"]+)"', text)
    code = re.search(r"versionCode = (\d+)", text)
    if not name or not code:
        raise SystemExit("在 app/build.gradle.kts 里找不到 versionName/versionCode")
    return name.group(1), int(code.group(1))


def markdown(notes, sha):
    lines = [f"## {notes['versionName']} 更新", "", "**覆盖安装即可，旧数据保留。**", ""]
    for heading in HEADINGS:
        items = notes.get(heading) or []
        if not items:
            continue
        lines.append(f"### {heading}")
        lines.extend(f"- {item}" for item in items)
        lines.append("")
    lines.append(f"SHA256: `{sha}`")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-build", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--apk", type=Path, help="Already verified signed release APK")
    parser.add_argument("--variant", choices=["universal","arm64"], default="universal")
    parser.add_argument("--target", help="Exact verified commit for this release")
    args = parser.parse_args()

    repo, prefix = config()
    version, code = version_from_gradle()
    notes = json.loads(NOTES.read_text(encoding="utf-8"))
    if notes["versionName"] != version or int(notes["versionCode"]) != code:
        raise SystemExit(
            "更新说明和代码里的版本号不一致：build.gradle.kts 是 %s(%d)，release-notes.json 是 %s(%s)"
            % (version, code, notes["versionName"], notes["versionCode"])
        )

    if not args.no_build:
        print("== 出正式包 ==", flush=True)
        run([str(GRADLE), "-p", str(ROOT), ":app:assembleRelease", "--console=plain"])
    source_apk = args.apk.resolve() if args.apk else APK
    if not source_apk.is_file():
        raise SystemExit("没有正式包：" + str(source_apk))

    sha = hashlib.sha256(source_apk.read_bytes()).hexdigest()
    out = DIST / f"v{version}"
    out.mkdir(parents=True, exist_ok=True)
    apk_out = out / f"{prefix}-v{version}-{args.variant}.apk"
    notes_out = out / f"{prefix}-v{version}-release-notes.json"
    sha_out = out / f"{prefix}-v{version}-SHA256.txt"
    shutil.copyfile(source_apk, apk_out)
    legacy_out = out / f"{prefix}-v{version}-{args.variant}-debug.apk"
    shutil.copyfile(source_apk, legacy_out)
    shutil.copyfile(NOTES, notes_out)
    sha_out.write_text(f"{sha}  {apk_out.name}\n{sha}  {legacy_out.name}\n", encoding="utf-8")

    for f in (apk_out, legacy_out, notes_out, sha_out):
        print("  %s  %d bytes" % (f.name, f.stat().st_size), flush=True)
    print("  sha256=%s" % sha, flush=True)
    if args.dry_run:
        return 0

    # Create a draft only. The caller validates server digests and a fresh download before opening it.
    if not args.target:
        raise SystemExit("Publishing requires --target with the verified commit")
    metadata=json.loads(run(["gh","repo","view",repo,"--json","isPrivate"]))
    if metadata.get("isPrivate") is not True:
        raise SystemExit("Refusing publication outside the private project")
    existing = subprocess.run(["gh","release","view",f"v{version}","--repo",repo],
        stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0
    if existing:
        raise SystemExit("Release already exists; refusing to overwrite it")
    body_file=out/"release-body.md"
    body_file.write_text(markdown(notes,sha),encoding="utf-8")
    title=f"足迹涂色 v{version}｜探索迷雾"
    run(["gh","release","create",f"v{version}",str(apk_out),str(legacy_out),str(notes_out),str(sha_out),
        "--repo",repo,"--target",args.target,"--draft","--prerelease","--title",title,"--notes-file",str(body_file)])
    print(f"Draft prepared: v{version}; server verification and publication still required",flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())

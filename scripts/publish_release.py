#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""发布一个版本：出正式包 -> 核对版本号 -> 生成中文更新说明和 SHA256 -> 挂 GitHub Release。

基于 h516df/android-update-kit 的发布脚本，适配 WalkPaint 既有更新器。
正式包及旧文件名别名保持相同字节，先上传草稿并核验资产，再开放发布。

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
import time
from pathlib import Path
from release_remote import publish_verified

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
NOTES = ROOT / "app" / "src" / "main" / "assets" / "release-notes.json"
CONFIG = ROOT / "app" / "src" / "main" / "java" / "cn" / "walkpaint" / "PrivateUpdatePolicy.kt"
GRADLE = ROOT / ("gradlew.bat" if sys.platform.startswith("win") else "gradlew")
DIST = ROOT / "dist"
HEADINGS = ["新增", "改进", "修复", "说明"]
SIGNER = "21b74e3e2a10e12da8284d4e6bd7d99e55b00a385215902e94d7896f7a46c967"


def run(argv, timeout=2400):
    read_api = argv[:2] == ["gh", "api"] and "--method" not in argv
    for attempt in range(5 if read_api else 1):
        proc = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
        out = proc.stdout.decode("utf-8", "replace")
        if proc.returncode == 0:
            return out
        if read_api and "unexpected EOF" in out and attempt < 4:
            time.sleep(min(2 ** attempt, 8))
            continue
        print(out[-3000:], flush=True)
        raise SystemExit("命令失败：" + " ".join(str(a) for a in argv))


def config():
    text = CONFIG.read_text(encoding="utf-8")
    repo = re.search(r'const val REPO\s*=\s*"([^"]+)"', text).group(1)
    prefix = re.search(r'return "([^"$]+)-v\$version', text).group(1)
    return repo, prefix


def version_from_gradle():
    text = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    name = re.search(r'versionName = "([^"]+)"', text)
    code = re.search(r"versionCode = (\d+)", text)
    if not name or not code:
        raise SystemExit("在 app/build.gradle.kts 里找不到 versionName/versionCode")
    return name.group(1), int(code.group(1))


def verify_apk(path, variant, version, code):
    sdk = Path(__import__('os').environ.get('ANDROID_HOME', ''))
    build_tools = sdk / 'build-tools' / '36.0.0'
    badging = run([str(build_tools / 'aapt.exe'), 'dump', 'badging', str(path)])
    expected = f"package: name='cn.walkpaint' versionCode='{code}' versionName='{version}'"
    if expected not in badging or 'application-debuggable' in badging:
        raise SystemExit('包名、版本或正式包调试属性不符合约定')
    native = re.search(r"native-code: (.+)", badging)
    if not native or "'arm64-v8a'" not in native.group(1):
        raise SystemExit('正式包缺少 ARM64')
    if variant == 'arm64' and native.group(1).strip() != "'arm64-v8a'":
        raise SystemExit('ARM64 包包含额外架构')
    if variant == 'universal' and "'x86_64'" not in native.group(1):
        raise SystemExit('通用包缺少测试设备架构')
    signature = run([str(build_tools / 'apksigner.bat'), 'verify', '--print-certs', str(path)])
    if f'Signer #1 certificate SHA-256 digest: {SIGNER}' not in signature:
        raise SystemExit('原签名不匹配，拒绝发布')


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
    parser.add_argument("--verify-only", action="store_true", help="只读核对已发布版本，禁止远端写入")
    parser.add_argument("--target", help="已推送的精确提交 SHA，发布时必填")
    parser.add_argument("--prepared-dir", type=Path, help="已验收的两个正式包所在目录")
    parser.add_argument("--phone-only", action="store_true", help="仅分发 ARM64 手机包；通用包仍可用于本机验收")
    parser.add_argument("--no-legacy-alias", action="store_true", help="仅在已结束旧更新器兼容时省略重复 APK 别名")
    args = parser.parse_args()
    if args.verify_only and (not args.no_build or args.prepared_dir is None):
        raise SystemExit("只读核对必须提供 --no-build --prepared-dir")

    repo, prefix = config()
    version, code = version_from_gradle()
    notes = json.loads(NOTES.read_text(encoding="utf-8"))
    if notes["versionName"] != version or int(notes["versionCode"]) != code:
        raise SystemExit(
            "更新说明和代码里的版本号不一致：build.gradle.kts 是 %s(%d)，release-notes.json 是 %s(%s)"
            % (version, code, notes["versionName"], notes["versionCode"])
        )

    if args.prepared_dir is None:
        if args.no_build:
            raise SystemExit("--no-build 必须指定 --prepared-dir，不能把最后一次构建误当两个架构的包")
        prepared = DIST / f"v{version}" / "input"
        prepared.mkdir(parents=True, exist_ok=True)
        for variant in ('universal', 'arm64'):
            print("== 出正式包："+variant+" ==", flush=True)
            run([str(GRADLE), "-p", str(ROOT), ":app:assembleRelease",
                 '-PwalkpaintArm64Only='+str(variant=='arm64').lower(),
                 '--no-daemon','--max-workers=2','--console=plain'])
            if not APK.is_file():
                raise SystemExit("没有正式包：" + str(APK))
            shutil.copyfile(APK,prepared/f'{prefix}-v{version}-{variant}.apk')
        args.prepared_dir=prepared

    out = DIST / f"v{version}"
    out.mkdir(parents=True, exist_ok=True)
    assets = []
    for variant in (('arm64',) if args.phone_only else ('universal', 'arm64')):
        source = args.prepared_dir / f'{prefix}-v{version}-{variant}.apk'
        verify_apk(source, variant, version, code)
        target = out / source.name
        shutil.copyfile(source, target)
        assets.append(target)
        # v0.37 and older select only this historical name. It is still the signed release bytes.
        if not args.no_legacy_alias:
            legacy = out / f'{prefix}-v{version}-{variant}-debug.apk'
            shutil.copyfile(source, legacy)
            assets.append(legacy)
    sha = hashlib.sha256(assets[0].read_bytes()).hexdigest()
    notes_out = out / f"{prefix}-v{version}-release-notes.json"
    sha_out = out / f"{prefix}-v{version}-SHA256.txt"
    shutil.copyfile(NOTES, notes_out)
    expected_assets = {f.name: {'size': f.stat().st_size, 'sha256': hashlib.sha256(f.read_bytes()).hexdigest()} for f in assets+[notes_out]}
    sha_out.write_text(''.join(f"{v['sha256']}  {name}\n" for name,v in expected_assets.items()), encoding="utf-8")
    assets += [notes_out, sha_out]
    expected_assets[sha_out.name] = {'size':sha_out.stat().st_size,'sha256':hashlib.sha256(sha_out.read_bytes()).hexdigest()}

    for f in assets:
        print("  %s  %d bytes" % (f.name, f.stat().st_size), flush=True)
    print("  sha256=%s" % sha, flush=True)
    if args.dry_run:
        return 0

    if not args.target or not re.fullmatch(r'[a-f0-9]{40}', args.target):
        raise SystemExit('发布必须绑定精确的已推送提交 SHA')
    if not args.verify_only and (run(['git','rev-parse','HEAD']).strip() != args.target or run(['git','status','--porcelain']).strip()):
        raise SystemExit('发布只允许当前已提交且干净的工作区')
    receipt = publish_verified(repo, f'v{version}', args.target,
        f"v{version}｜足迹涂色更新", markdown(notes, sha), assets, out, run,
        verify_only=args.verify_only)
    print(json.dumps(receipt, ensure_ascii=False), flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())

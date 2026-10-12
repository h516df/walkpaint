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
from pathlib import Path

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
    proc = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    out = proc.stdout.decode("utf-8", "replace")
    if proc.returncode != 0:
        print(out[-3000:], flush=True)
        raise SystemExit("命令失败：" + " ".join(str(a) for a in argv))
    return out


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
    parser.add_argument("--target", help="已推送的精确提交 SHA，发布时必填")
    parser.add_argument("--prepared-dir", type=Path, help="已验收的两个正式包所在目录")
    args = parser.parse_args()

    repo, prefix = config()
    version, code = version_from_gradle()
    notes = json.loads(NOTES.read_text(encoding="utf-8"))
    if notes["versionName"] != version or int(notes["versionCode"]) != code:
        raise SystemExit(
            "更新说明和代码里的版本号不一致：build.gradle.kts 是 %s(%d)，release-notes.json 是 %s(%s)"
            % (version, code, notes["versionName"], notes["versionCode"])
        )

    if not args.no_build and args.prepared_dir is None:
        print("== 出正式包 ==", flush=True)
        run([str(GRADLE), "-p", str(ROOT), ":app:assembleRelease", "--console=plain"])
    if args.prepared_dir is None and not APK.is_file():
        raise SystemExit("没有正式包：" + str(APK))

    out = DIST / f"v{version}"
    out.mkdir(parents=True, exist_ok=True)
    assets = []
    for variant in ('universal', 'arm64'):
        source = args.prepared_dir / f'{prefix}-v{version}-{variant}.apk' if args.prepared_dir else APK
        if variant == 'arm64' and args.prepared_dir is None:
            continue
        verify_apk(source, variant, version, code)
        target = out / source.name if args.prepared_dir else out / f'{prefix}-v{version}-{variant}.apk'
        shutil.copyfile(source, target)
        assets.append(target)
        # v0.37 and older select only this historical name. It is still the signed release bytes.
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
    if run(['git','rev-parse','HEAD']).strip() != args.target or run(['git','status','--porcelain']).strip():
        raise SystemExit('发布只允许当前已提交且干净的工作区')
    metadata = json.loads(run(['gh','api',f'repos/{repo}']))
    if not metadata.get('private'):
        raise SystemExit('只允许向原私有仓库发布')
    run(['gh','api',f'repos/{repo}/commits/{args.target}'])

    title = "v%s｜%s" % (version, (notes.get("新增") or ["小改动"])[0][:24])
    body = markdown(notes, sha)
    existing = subprocess.run(
        ["gh", "release", "view", f"v{version}", "--repo", repo],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    ).returncode == 0
    if existing:
        raise SystemExit('这个版本已存在，拒绝覆盖；先核对现有发布')
    tag_sha = run(['gh','api',f'repos/{repo}/git/ref/tags/v{version}','--jq','.object.sha']).strip()
    if tag_sha != args.target:
        raise SystemExit('远端标签未固定到当前提交')
    body_file = out / 'release-body.md'
    body_file.write_text(body,encoding='utf-8')
    run(["gh", "release", "create", f"v{version}", *map(str,assets),
         "--repo", repo, "--title", title, "--notes-file", str(body_file), '--draft','--verify-tag'])
    remote = json.loads(run(['gh','api',f'repos/{repo}/releases/tags/v{version}']))
    for asset in remote['assets']:
        expected = expected_assets.pop(asset['name'], None)
        if not expected or asset['size'] != expected['size'] or asset.get('digest') != 'sha256:'+expected['sha256']:
            raise SystemExit('草稿资产校验失败，保持草稿不发布')
    if expected_assets:
        raise SystemExit('草稿缺少资产，保持草稿不发布')
    receipt = {'tag':f'v{version}','target':args.target,'assets':remote['assets'],'draft_verified':True}
    (out/'publication.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
    run(['gh','release','edit',f'v{version}','--repo',repo,'--draft=false','--latest'])
    final = json.loads(run(['gh','api',f'repos/{repo}/releases/tags/v{version}']))
    if final['draft'] or not final['published_at']:
        raise SystemExit('未确认发布状态，请核对发布页')
    print("已发布 v%s 到 %s" % (version, repo), flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())

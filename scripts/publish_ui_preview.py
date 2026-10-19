"""Publish an isolated developer APK as a non-latest prerelease. Never replaces assets/tags."""
import argparse
import hashlib
import json
import re
import subprocess
import urllib.request
from pathlib import Path

REPOSITORY = 'h516df/walkpaint'


def validate_assets(release, expected):
    assets = release.get('assets', [])
    if len(assets) != len(expected) or {a['name'] for a in assets} != set(expected):
        raise RuntimeError('Missing, extra, or duplicate prerelease assets')
    for asset in assets:
        size, digest = expected[asset['name']]
        if asset.get('size') != size or asset.get('digest') != 'sha256:' + digest or asset.get('state') != 'uploaded':
            raise RuntimeError('Asset bytes or upload state do not match: ' + asset['name'])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--assets', type=Path, required=True)
    parser.add_argument('--public-release', action='store_true', required=True)
    args = parser.parse_args()
    if not re.fullmatch('[a-f0-9]{40}', args.source_sha):
        raise SystemExit('A full source commit SHA is required')
    files = sorted(args.assets.iterdir())
    if {p.name for p in files} != {'walkpaint-ui-preview.apk', 'SHA256SUMS', 'build-info.json'}:
        raise SystemExit('Unexpected preview asset set')
    info = json.loads((args.assets / 'build-info.json').read_text())
    if info.get('package') != 'cn.walkpaint.dev' or info.get('source_sha') != args.source_sha:
        raise SystemExit('Developer package or source identity mismatch')
    expected = {p.name: (p.stat().st_size, hashlib.sha256(p.read_bytes()).hexdigest()) for p in files}
    if any(size <= 0 for size, _ in expected.values()):
        raise SystemExit('Empty assets are prohibited')

    def gh(*parts):
        return subprocess.check_output(['gh', *map(str, parts)], text=True)

    def api(path, *parts):
        return json.loads(gh('api', 'repos/' + REPOSITORY + '/' + path, *parts))

    repo = json.loads(gh('api', 'repos/' + REPOSITORY))
    if repo.get('private') is not False:
        raise SystemExit('Only the explicitly authorized public repository is allowed')
    if api('commits/' + args.source_sha)['sha'] != args.source_sha:
        raise SystemExit('Remote source mismatch')
    tag = 'ui-preview-' + args.source_sha[:12]
    # Existing tags/releases are never moved, overwritten, promoted or retried blindly.
    if api('git/matching-refs/tags/' + tag):
        raise SystemExit('Tag already exists; inspect the existing release before retrying')
    latest = api('releases/latest')['id']
    body = ('基于v0.63的独立原生界面预览：山景行记卡、分组设置、原创徽章、自适应导航。\n\n'
            '当前正式版已更新至v0.64；本预览不包含其历史回放、单高德引擎和新版首次地图授权，也不包含PR104的行记/明信片候选功能。它不是最新正式版。\n\n'
            '这是 cn.walkpaint.dev 独立开发调试包，与正式版并存，不覆盖正式版，也不读取正式版足迹。'
            '本次使用临时开发签名，不能承诺与其他开发包覆盖兼容。\n\n'
            '公开构建不含高德Key；高德地图与真实区县查询不可用。此包用于界面预览，不能代替完整正式版。'
            'API29 模拟器已验证普通启动和核心界面交互。Android16/API36 的测试进程退出（Process crashed），尚未定位，不能视为Android16通过，也不能仅凭此断言主应用崩溃。'
            '实体手机、户外GPS与后台持续记录未在本次验收。\n\n'
            '源码：' + args.source_sha + '\n'
            '界面验收与限制：https://github.com/h516df/walkpaint/pull/103\n'
            'APK SHA256：' + expected['walkpaint-ui-preview.apk'][1])
    api('git/refs', '--method', 'POST', '-f', 'ref=refs/tags/' + tag, '-f', 'sha=' + args.source_sha)
    created = api('releases', '--method', 'POST', '-f', 'tag_name=' + tag,
                  '-f', 'target_commitish=' + args.source_sha, '-f', 'name=WalkPaint · 原生界面独立预览',
                  '-f', 'body=' + body, '-F', 'draft=true', '-F', 'prerelease=true')
    release_id = created['id']
    gh('release', 'upload', tag, '--repo', REPOSITORY, *files)
    release = api('releases/' + str(release_id))
    if not release['draft'] or not release['prerelease'] or release['target_commitish'] != args.source_sha:
        raise RuntimeError('Draft release identity does not match')
    validate_assets(release, expected)
    if api('git/ref/tags/' + tag)['object']['sha'] != args.source_sha:
        raise RuntimeError('Prerelease tag is not bound to source SHA')
    api('releases/' + str(release['id']), '--method', 'PATCH',
        '-F', 'draft=false', '-F', 'prerelease=true', '-f', 'make_latest=false')
    final = api('releases/tags/' + tag)
    validate_assets(final, expected)
    if final['draft'] or not final['prerelease'] or api('releases/latest')['id'] != latest:
        raise RuntimeError('Prerelease status or stable update channel changed unexpectedly')
    for asset in final['assets']:
        digest = hashlib.sha256()
        # Public, anonymous read-back of the published asset; no Authorization header.
        with urllib.request.urlopen(asset['browser_download_url'], timeout=120) as response:
            while chunk := response.read(1024 * 1024):
                digest.update(chunk)
        if digest.hexdigest() != expected[asset['name']][1]:
            raise RuntimeError('Public read-back digest mismatch: ' + asset['name'])
    print(json.dumps({'url': final['html_url'], 'source_sha': args.source_sha,
                      'stable_release_unchanged': True, 'assets': expected}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()

"""Resume one verified private release. Never clobber an asset or published version."""
import hashlib
import json
import re
from pathlib import Path


def file_digest(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    return 'sha256:' + digest.hexdigest()


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def publish_verified(repo, tag, target, title, body, assets, output, run, verify_only=False):
    require(bool(re.fullmatch(r'[\w.-]+/[\w.-]+', repo)), '仓库名不合法')
    require(bool(re.fullmatch(r'v[0-9]+(?:\.[0-9]+)*', tag)), '版本标签不合法')
    require(bool(re.fullmatch(r'[a-f0-9]{40}', target)), '必须指定完整提交 SHA')
    assets = [Path(path) for path in assets]
    require(bool(assets) and len({p.name for p in assets}) == len(assets), '资产缺失或重名')
    expected = {p.name: {'size': p.stat().st_size, 'digest': file_digest(p)} for p in assets}
    require(all(v['size'] > 0 for v in expected.values()), '拒绝发布空资产')
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    api = f'repos/{repo}'

    def get(path, *args):
        return json.loads(run(['gh', 'api', f'{api}/{path}', *args]))

    def write(path, values, name):
        payload = output / name
        payload.write_text(json.dumps(values, ensure_ascii=False), encoding='utf-8')
        return get(path, '--method', 'POST', '--input', str(payload))

    def tag_commit(obj):
        for _ in range(4):
            if obj['type'] == 'commit':
                return obj['sha']
            require(obj['type'] == 'tag', '标签没有指向提交')
            obj = get(f"git/tags/{obj['sha']}")['object']
        raise RuntimeError('标签嵌套过深，停止')

    metadata = json.loads(run(['gh', 'api', api]))
    require(metadata.get('private') is True, '仅允许原私有仓库')
    require(get(f'commits/{target}').get('sha') == target, '远端提交不匹配')
    pages = get('releases?per_page=100', '--paginate', '--slurp')
    require(isinstance(pages, list) and all(isinstance(p, list) for p in pages), '发布列表格式异常')
    matches = [r for page in pages for r in page if r.get('tag_name') == tag]
    require(len(matches) <= 1, '发现重复版本，停止')
    remote = matches[0] if matches else None
    if remote:
        require(remote.get('target_commitish') == target and not remote.get('prerelease'),
                '现有发布属于其他提交或预发布状态，拒绝覆盖')
    require(remote is not None or not verify_only, '指定版本不存在，只读核对不创建发布')

    def check_assets(release, complete=False):
        known = {}
        for asset in release.get('assets', []):
            name = asset['name']
            require(name not in known and name in expected, '发现重复或未知资产，拒绝覆盖')
            require(asset.get('state') == 'uploaded' and
                    asset.get('size') == expected[name]['size'] and
                    asset.get('digest') == expected[name]['digest'],
                    '资产内容或上传状态不一致：' + name)
            known[name] = asset
        if complete:
            require(set(known) == set(expected), '缺少发布资产')
        return known

    # Inspect every existing asset before any tag, upload or release mutation.
    if remote:
        check_assets(remote, complete=not remote['draft'] or verify_only)
        if verify_only:
            require(not remote['draft'] and remote.get('published_at'), '指定版本尚未正式发布')

    refs = get(f'git/matching-refs/tags/{tag}')
    exact = [ref for ref in refs if ref.get('ref') == f'refs/tags/{tag}']
    require(len(exact) <= 1, '标签身份不明确')
    if exact:
        require(tag_commit(exact[0]['object']) == target, '标签提交不匹配，拒绝覆盖')
    else:
        require(not verify_only and (remote is None or remote['draft']), '已发布版本缺少固定标签')
        created = write('git/refs', {'ref': f'refs/tags/{tag}', 'sha': target}, 'fixed-tag.json')
        require(created['object']['sha'] == target, '新标签提交不匹配')

    if remote is None:
        remote = write('releases', {'tag_name': tag, 'target_commitish': target,
                                   'name': title, 'body': body, 'draft': True,
                                   'prerelease': False}, 'create-release.json')
    release_id = remote['id']

    def current():
        result = get(f'releases/{release_id}')
        require(result.get('tag_name') == tag and result.get('target_commitish') == target and
                not result.get('prerelease'), '发布身份发生变化，停止')
        return result

    was_draft = remote['draft']
    if was_draft and not verify_only:
        for path in assets:
            fresh = current()
            require(fresh['draft'], '其他执行者已发布，停止写入')
            known = check_assets(fresh)
            if path.name not in known:
                require(file_digest(path) == expected[path.name]['digest'], '本地资产发生变化')
                print('上传缺少的资产：' + path.name, flush=True)
                # No --clobber. After an uncertain failure, rerun to inspect server state.
                run(['gh', 'release', 'upload', tag, str(path), '--repo', repo])
        fresh = current()
        require(fresh['draft'], '发布状态发生变化，停止写入')
        check_assets(fresh, complete=True)
        payload = output / 'publish-release.json'
        payload.write_text(json.dumps({'draft': False, 'prerelease': False,
                                      'make_latest': 'true', 'name': title, 'body': body},
                                     ensure_ascii=False), encoding='utf-8')
        get(f'releases/{release_id}', '--method', 'PATCH', '--input', str(payload))

    final = current()
    require(not final['draft'] and final.get('published_at'), '未确认正式发布状态')
    check_assets(final, complete=True)
    require(tag_commit(get(f'git/ref/tags/{tag}')['object']) == target, '最终标签提交不匹配')
    # GET /latest returns 404 when a repository has only older/non-latest releases.
    # The normal publication path must have a latest release; read-only historical checks
    # inspect the list instead, and never promote an old version.
    if was_draft and not verify_only:
        latest = get('releases/latest')
        require(latest['id'] == release_id, '最新更新渠道未指向本次发布')
        latest_verified = True
    else:
        latest_verified = None
    receipt = {'tag': tag, 'target': target, 'url': final['html_url'],
               'published_at': final['published_at'], 'status': 'published',
               'read_only': not was_draft or verify_only, 'latest_verified': latest_verified,
               'assets': [{'name': a['name'], 'size': a['size'], 'digest': a['digest']}
                          for a in final['assets']]}
    (output / 'publication.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    return receipt

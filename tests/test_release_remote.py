import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).parents[1] / 'scripts/release_remote.py'
spec = importlib.util.spec_from_file_location('release_remote', SCRIPT)
remote = importlib.util.module_from_spec(spec)
spec.loader.exec_module(remote)
TARGET = 'a' * 40


class FakeGitHub:
    def __init__(self, assets, release=None, refs=None):
        self.files = {p.name: p for p in assets}
        self.release = copy.deepcopy(release)
        self.refs = copy.deepcopy(refs if refs is not None else [])
        self.private = True
        self.writes = []
        self.fail_upload_after_save = False

    def asset(self, name):
        path = self.files[name]
        return {'name': name, 'size': path.stat().st_size,
                'digest': remote.file_digest(path), 'state': 'uploaded'}

    def __call__(self, args):
        if args[:3] == ['gh', 'release', 'upload']:
            self.writes.append(('upload', Path(args[4]).name))
            self.release['assets'].append(self.asset(Path(args[4]).name))
            if self.fail_upload_after_save:
                self.fail_upload_after_save = False
                raise RuntimeError('connection lost after server saved asset')
            return ''
        assert args[:2] == ['gh', 'api'], args
        path = args[2].removeprefix('repos/owner/app').lstrip('/')
        method = args[args.index('--method') + 1] if '--method' in args else 'GET'
        payload = json.loads(Path(args[args.index('--input') + 1]).read_text(encoding='utf-8')) if '--input' in args else None
        if method != 'GET':
            self.writes.append((method, path))
            if path == 'git/refs':
                result = {'ref': payload['ref'], 'object': {'type': 'commit', 'sha': payload['sha']}}
                self.refs.append(result)
                return json.dumps(result)
            if path == 'releases':
                self.release = dict(payload, id=1, assets=[], published_at=None,
                                    html_url='https://github.com/owner/app/releases/tag/v1.0')
            elif path == 'releases/1' and method == 'PATCH':
                self.release.update(payload, published_at='2026-10-13T00:00:00Z')
            else:
                raise AssertionError(args)
            return json.dumps(self.release)
        if path == '':
            return json.dumps({'private': self.private})
        if path == 'commits/' + TARGET:
            return json.dumps({'sha': TARGET})
        if path.startswith('releases?'):
            # Keep a matching version on a later page to exercise pagination.
            return json.dumps([[], [self.release] if self.release else []])
        if path.startswith('git/matching-refs/'):
            return json.dumps(self.refs)
        if path.startswith('git/ref/tags/'):
            return json.dumps(self.refs[0])
        if path in ('releases/1', 'releases/latest'):
            return json.dumps(self.release)
        raise AssertionError(args)


class ReleaseRecoveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='.release-test-', dir=Path(__file__).parent)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.assertEqual(Path(__file__).parent.resolve(), self.root.resolve().parent)
        self.assets = [self.root / 'App-v1.0-arm64.apk', self.root / 'App-v1.0-release-notes.json']
        for path in self.assets:
            path.write_bytes(('verified ' + path.name).encode())
        self.ref = {'ref': 'refs/tags/v1.0', 'object': {'type': 'commit', 'sha': TARGET}}

    def release(self, draft=True):
        return {'id': 1, 'tag_name': 'v1.0', 'target_commitish': TARGET, 'draft': draft,
                'prerelease': False, 'published_at': None if draft else '2026-10-12',
                'html_url': 'https://github.com/owner/app/releases/tag/v1.0', 'assets': []}

    def publish(self, fake, verify_only=False):
        return remote.publish_verified('owner/app', 'v1.0', TARGET, '更新', '说明', self.assets,
                                       self.root / 'receipt', fake, verify_only=verify_only)

    def test_new_release_is_draft_until_all_assets_are_verified(self):
        fake = FakeGitHub(self.assets)
        result = self.publish(fake)
        self.assertEqual('published', result['status'])
        self.assertEqual([('POST', 'git/refs'), ('POST', 'releases'),
                          ('upload', self.assets[0].name), ('upload', self.assets[1].name),
                          ('PATCH', 'releases/1')], fake.writes)
        self.assertTrue(result['latest_verified'])

    def test_partial_draft_only_uploads_missing_file(self):
        fake = FakeGitHub(self.assets, self.release(), [self.ref])
        fake.release['assets'] = [fake.asset(self.assets[0].name)]
        self.publish(fake)
        self.assertEqual([('upload', self.assets[1].name), ('PATCH', 'releases/1')], fake.writes)

    def test_complete_draft_only_publishes(self):
        fake = FakeGitHub(self.assets, self.release(), [self.ref])
        fake.release['assets'] = [fake.asset(p.name) for p in self.assets]
        self.publish(fake)
        self.assertEqual([('PATCH', 'releases/1')], fake.writes)

    def test_published_release_is_read_only_even_without_verify_flag(self):
        fake = FakeGitHub(self.assets, self.release(False), [self.ref])
        fake.release['assets'] = [fake.asset(p.name) for p in self.assets]
        self.assertTrue(self.publish(fake)['read_only'])
        self.assertEqual([], fake.writes)

    def test_verify_only_never_creates_or_publishes(self):
        for release in (None, self.release()):
            with self.subTest(release=release):
                fake = FakeGitHub(self.assets, release, [self.ref])
                with self.assertRaises(RuntimeError):
                    self.publish(fake, verify_only=True)
                self.assertEqual([], fake.writes)

    def test_wrong_digest_stops_before_any_mutation(self):
        fake = FakeGitHub(self.assets, self.release())
        asset = fake.asset(self.assets[0].name)
        asset['digest'] = 'sha256:' + 'b' * 64
        fake.release['assets'] = [asset]
        with self.assertRaisesRegex(RuntimeError, '资产内容'):
            self.publish(fake)
        self.assertEqual([], fake.writes)

    def test_unknown_or_incomplete_asset_is_preserved(self):
        for value in ({'name': 'someone-else.apk'}, {'state': 'starter'}):
            fake = FakeGitHub(self.assets, self.release())
            asset = fake.asset(self.assets[0].name)
            asset.update(value)
            fake.release['assets'] = [asset]
            with self.assertRaises(RuntimeError):
                self.publish(fake)
            self.assertEqual([], fake.writes)

    def test_wrong_tag_is_never_moved(self):
        ref = copy.deepcopy(self.ref)
        ref['object']['sha'] = 'b' * 40
        fake = FakeGitHub(self.assets, self.release(), [ref])
        with self.assertRaisesRegex(RuntimeError, '标签提交'):
            self.publish(fake)
        self.assertEqual([], fake.writes)

    def test_public_repo_is_never_modified(self):
        fake = FakeGitHub(self.assets)
        fake.private = False
        with self.assertRaisesRegex(RuntimeError, '私有仓库'):
            self.publish(fake)
        self.assertEqual([], fake.writes)

    def test_different_release_commit_is_never_modified(self):
        fake = FakeGitHub(self.assets, self.release(), [self.ref])
        fake.release['target_commitish'] = 'b' * 40
        with self.assertRaisesRegex(RuntimeError, '其他提交'):
            self.publish(fake)
        self.assertEqual([], fake.writes)

    def test_uncertain_upload_recovers_from_server_state(self):
        fake = FakeGitHub(self.assets, self.release(), [self.ref])
        fake.fail_upload_after_save = True
        with self.assertRaisesRegex(RuntimeError, 'connection lost'):
            self.publish(fake)
        self.publish(fake)
        self.assertEqual(1, fake.writes.count(('upload', self.assets[0].name)))
        self.assertEqual(1, fake.writes.count(('upload', self.assets[1].name)))


if __name__ == '__main__':
    unittest.main()

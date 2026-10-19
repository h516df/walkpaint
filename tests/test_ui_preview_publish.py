import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('preview', Path(__file__).parents[1] / 'scripts/publish_ui_preview.py')
preview = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preview)


class PreviewAssetChecks(unittest.TestCase):
    def setUp(self):
        self.expected = {'preview.apk': (100, 'abc')}
        self.asset = {'name': 'preview.apk', 'size': 100, 'digest': 'sha256:abc', 'state': 'uploaded'}

    def test_valid_upload(self):
        preview.validate_assets({'assets': [self.asset]}, self.expected)

    def test_missing_upload(self):
        with self.assertRaises(RuntimeError):
            preview.validate_assets({'assets': []}, self.expected)

    def test_duplicate_upload(self):
        with self.assertRaises(RuntimeError):
            preview.validate_assets({'assets': [self.asset, self.asset]}, self.expected)

    def test_changed_bytes(self):
        for field, value in [('size', 99), ('digest', 'sha256:wrong'), ('state', 'new')]:
            with self.subTest(field=field), self.assertRaises(RuntimeError):
                preview.validate_assets({'assets': [dict(self.asset, **{field: value})]}, self.expected)

    def test_unexpected_asset(self):
        with self.assertRaises(RuntimeError):
            preview.validate_assets({'assets': [dict(self.asset, name='official.apk')]}, self.expected)

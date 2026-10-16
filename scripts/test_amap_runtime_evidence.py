import hashlib
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path
from amap_runtime_evidence import verify_runtime


class RuntimeEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.apk = Path(self.directory.name) / 'artifact.apk'
        with zipfile.ZipFile(self.apk, 'w', compression=zipfile.ZIP_STORED) as archive:
            archive.writestr('classes.dex', b'DEX' * 80)
            archive.writestr('lib/arm64-v8a/libAMapSDK_MAP_test.so', b'ELF' * 80)
        with zipfile.ZipFile(self.apk) as archive, self.apk.open('rb') as source:
            entry = archive.getinfo('lib/arm64-v8a/libAMapSDK_MAP_test.so')
            source.seek(entry.header_offset)
            name_len, extra_len = struct.unpack_from('<HH', source.read(30), 26)
            self.offset = entry.header_offset + 30 + name_len + extra_len
        self.runtime = {'apk_sha256': hashlib.sha256(self.apk.read_bytes()).hexdigest(),
                        'map_loaded': True, 'apk_mapping_offsets': [format(self.offset, 'x')]}

    def test_direct_apk_elf_mapping_is_accepted(self):
        self.assertEqual('amap', verify_runtime(self.apk, self.runtime)['engine'])

    def test_dex_mapping_is_not_native_evidence(self):
        self.runtime['apk_mapping_offsets'] = ['0']
        with self.assertRaises(ValueError):
            verify_runtime(self.apk, self.runtime)

    def test_changed_installed_artifact_is_rejected(self):
        self.runtime['apk_sha256'] = '0' * 64
        with self.assertRaises(ValueError):
            verify_runtime(self.apk, self.runtime)

    def test_unloaded_or_fallback_map_is_rejected(self):
        self.runtime['map_loaded'] = False
        with self.assertRaises(ValueError):
            verify_runtime(self.apk, self.runtime)


if __name__ == '__main__':
    unittest.main()

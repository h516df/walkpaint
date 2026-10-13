import unittest
from unittest.mock import patch
from pathlib import Path
import publish_release as p

class SigningGateTest(unittest.TestCase):
    badging = "package: name='cn.walkpaint' versionCode='50' versionName='0.48'\nnative-code: 'arm64-v8a'"
    def output(self, **schemes):
        return "Verifies\n" + "\n".join(f"Verified using {k} scheme ({k}): {str(v).lower()}" for k,v in schemes.items()) + f"\nSigner #1 certificate SHA-256 digest: {p.SIGNER}"
    def verify(self, signatures):
        with patch.object(p, 'run', side_effect=[self.badging, signatures, signatures]):
            p.verify_apk(Path('candidate.apk'), 'arm64', '0.48', 50)
    def test_previous_v2_only_release_is_rejected(self):
        with self.assertRaises(SystemExit): self.verify(self.output(v1=False,v2=True,v3=False))
    def test_missing_modern_scheme_is_rejected(self):
        for scheme in ('v2','v3'):
            schemes=dict(v1=True,v2=True,v3=True);schemes[scheme]=False
            with self.subTest(scheme=scheme),self.assertRaises(SystemExit): self.verify(self.output(**schemes))
    def test_missing_legacy_certificate_is_rejected(self):
        with self.assertRaises(SystemExit): self.verify(self.output(v1=False,v2=True,v3=True))
    def test_legacy_identity_must_match_modern_identity(self):
        modern=self.output(v1=True,v2=True,v3=True)
        with patch.object(p,'run',side_effect=[self.badging,modern,modern.replace(p.SIGNER,'0'*64)]),self.assertRaises(SystemExit):
            p.verify_apk(Path('candidate.apk'),'arm64','0.48',50)
    def test_different_identity_is_rejected(self):
        with self.assertRaises(SystemExit): self.verify(self.output(v1=True,v2=True,v3=True).replace(p.SIGNER,'0'*64))
    def test_windows_crlf_output_passes(self):
        self.verify(self.output(v1=True,v2=True,v3=True).replace('\n','\r\n'))
    def test_original_identity_all_schemes_passes(self):
        self.verify(self.output(v1=True,v2=True,v3=True))

if __name__=='__main__': unittest.main()

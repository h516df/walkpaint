"""Policy regression tests; only synthetic metadata, no production credentials."""
import unittest
from sign_apk import credentials,check_identity,check_apk
class SigningPolicy(unittest.TestCase):
 def test_missing_inputs_fail(self):
  with self.assertRaises(ValueError):credentials({})
 def test_invalid_base64_fails(self):
  with self.assertRaises(ValueError):credentials(dict(SIGNING_KEYSTORE_BASE64='!!!',SIGNING_STORE_PASSWORD='fixture',SIGNING_KEY_PASSWORD='fixture'))
 def test_wrong_certificate_fails(self):
  with self.assertRaises(ValueError):check_identity({'certificateSha256':'a'*64,'privateKeyVerified':True},{'certificateSha256':'b'*64})
 def test_public_certificate_without_private_key_fails(self):
  with self.assertRaises(ValueError):check_identity({'certificateSha256':'a'*64,'privateKeyVerified':False},{'certificateSha256':'a'*64})
 def test_wrong_package_fails(self):
  with self.assertRaises(ValueError):check_apk("package: name='dev.synthetic.other'",{'packageName':'dev.synthetic.expected'})
 def test_debuggable_fails(self):
  with self.assertRaises(ValueError):check_apk("package: name='dev.synthetic.expected'\napplication-debuggable",{'packageName':'dev.synthetic.expected'})
 def test_release_metadata_allowed(self):
  check_apk("package: name='dev.synthetic.expected'",{'packageName':'dev.synthetic.expected'})
if __name__=='__main__':unittest.main()

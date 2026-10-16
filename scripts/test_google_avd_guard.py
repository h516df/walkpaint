"""Safety tests: refuse MuMu/real phones before any ADB command."""
import subprocess
import unittest
from unittest.mock import patch

from google_avd_guard import require_walkpaint_google_avd


class GoogleAvdGuardTest(unittest.TestCase):
    @patch("google_avd_guard.subprocess.run")
    def test_isolated_avd_requires_its_exact_pair(self, run):
        run.side_effect = [
            subprocess.CompletedProcess([], 0, "WalkPaint_Google_API36_V044\nOK\n", ""),
            subprocess.CompletedProcess([], 0, "1\n", ""),
        ]
        require_walkpaint_google_avd("adb", "emulator-5560")
        self.assertEqual(run.call_count,2)

    @patch("google_avd_guard.subprocess.run")
    def test_isolated_port_with_legacy_or_foreign_name_is_rejected(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, "WalkPaint_Google_API36\nOK\n", "")
        with self.assertRaisesRegex(RuntimeError,"Expected dedicated"):
            require_walkpaint_google_avd("adb", "emulator-5560")

    @patch("google_avd_guard.subprocess.run")
    def test_personal_mumu_is_never_contacted(self, run):
        with self.assertRaisesRegex(RuntimeError, "Test device denied"):
            require_walkpaint_google_avd("adb", "127.0.0.1:16384")
        run.assert_not_called()

    @patch("google_avd_guard.subprocess.run")
    def test_other_emulator_is_never_contacted(self, run):
        with self.assertRaisesRegex(RuntimeError, "Test device denied"):
            require_walkpaint_google_avd("adb", "emulator-5554")
        run.assert_not_called()

    @patch("google_avd_guard.subprocess.run")
    def test_real_phone_is_never_contacted(self, run):
        with self.assertRaisesRegex(RuntimeError, "Test device denied"):
            require_walkpaint_google_avd("adb", "R5CW12345")
        run.assert_not_called()

    @patch("google_avd_guard.subprocess.run")
    def test_avd_identity_must_match(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, "Another_AVD\nOK\n", "")
        with self.assertRaisesRegex(RuntimeError, "Expected dedicated"):
            require_walkpaint_google_avd("adb", "emulator-5556")
        self.assertEqual(run.call_count, 1)

    @patch("google_avd_guard.subprocess.run")
    def test_offline_is_rejected(self, run):
        run.return_value = subprocess.CompletedProcess([], 1, "", "error: device offline")
        with self.assertRaisesRegex(RuntimeError, "Expected dedicated"):
            require_walkpaint_google_avd("adb", "emulator-5556")
        self.assertEqual(run.call_count, 1)

    @patch("google_avd_guard.subprocess.run")
    def test_boot_incomplete_is_rejected(self, run):
        run.side_effect = [
            subprocess.CompletedProcess([], 0, "WalkPaint_Google_API36\nOK\n", ""),
            subprocess.CompletedProcess([], 0, "\n", ""),
        ]
        with self.assertRaisesRegex(RuntimeError, "not fully booted"):
            require_walkpaint_google_avd("adb", "emulator-5556")
        self.assertEqual(run.call_count, 2)

    @patch("google_avd_guard.subprocess.run")
    def test_only_named_and_booted_google_avd_is_accepted(self, run):
        run.side_effect = [
            subprocess.CompletedProcess([], 0, "WalkPaint_Google_API36\nOK\n", ""),
            subprocess.CompletedProcess([], 0, "1\n", ""),
        ]
        require_walkpaint_google_avd("adb", "emulator-5556")
        self.assertEqual(run.call_count, 2)
        self.assertEqual(run.call_args_list[0].args[0], ["adb", "-s", "emulator-5556", "emu", "avd", "name"])
        self.assertEqual(run.call_args_list[1].args[0], ["adb", "-s", "emulator-5556", "shell", "getprop", "sys.boot_completed"])


if __name__ == "__main__":
    unittest.main()

import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('assertions', Path(__file__).parents[1] / 'scripts/ui_acceptance_assertions.py')
a = importlib.util.module_from_spec(spec)
spec.loader.exec_module(a)


class ResultAssertionTest(unittest.TestCase):
    def setUp(self):
        self.days = ['2026-09-30', '2026-10-01', '2026-10-11', '2026-10-12', '2026-10-13', '2026-10-14']

    def test_week_results_exclude_previous_sunday_and_future(self):
        a.assert_day_results({'2026-10-12', '2026-10-13'}, self.days, '2026-10-13', '本周')

    def test_wrong_filtered_results_fail_even_if_controls_were_clicked(self):
        with self.assertRaises(AssertionError):
            a.assert_day_results(self.days, self.days, '2026-10-13', '本周')
        with self.assertRaises(AssertionError):
            a.assert_day_results(set(), self.days, '2026-10-13', '本月')

    def test_month_results_cross_year_boundary(self):
        a.assert_day_results({'2027-01-01'}, ['2026-12-31', '2027-01-01', '2027-01-02'], '2027-01-01', '本月')

    def test_all_keeps_original_date_keys(self):
        a.assert_day_results(self.days, self.days, '2026-10-13', '全部')

    def test_theme_recording_and_artifact_mismatch_fail(self):
        state = {'appearance_mode': 2, 'automatic_enabled': False, 'installed_apk_sha256': 'original'}
        a.assert_saved_state(state, appearance=2, automatic=False, apk_sha256='original')
        for kwargs in ({'appearance': 1}, {'automatic': True}, {'apk_sha256': 'other'}):
            with self.subTest(kwargs=kwargs), self.assertRaises(AssertionError):
                a.assert_saved_state(state, **kwargs)


if __name__ == '__main__':
    unittest.main()

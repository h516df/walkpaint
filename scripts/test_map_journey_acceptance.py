import json
import unittest
from map_journey_acceptance import parse_result


class JourneyResultTest(unittest.TestCase):
    def test_recreated_map_is_a_failure_even_when_native_command_returns_zero(self):
        report = {'fixture_points': 7959, 'journeys': [
            {'same_map': False, 'kept_while_hidden': False} for _ in range(4)]}
        with self.assertRaises(ValueError):
            parse_result('MAP_RETURN_RESULT: ' + json.dumps(report), 'MAP_RETURN_RESULT')

    def test_day_review_cannot_pass_with_live_gps_marker_or_other_days_fog(self):
        report = {'fixture_total_points': 7959, 'fog_only_selected_day': False,
                  'marker_is_day_endpoint': False, 'locate_keeps_day': True}
        with self.assertRaises(ValueError):
            parse_result('HISTORY_CAMERA_PASS: ' + json.dumps(report), 'HISTORY_CAMERA_PASS')

    def test_failed_or_missing_native_report_never_becomes_a_pass(self):
        for text in ('INSTRUMENTATION_CODE: 0', 'MAP_SCENE_FAIL: unknown ground exposed'):
            with self.assertRaises(ValueError):
                parse_result(text, 'MAP_SCENE_PASS')

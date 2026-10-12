"""Result assertions used by the real Android journey, independent of tap success."""
from datetime import date, timedelta


def expected_days(keys, today, label):
    today = date.fromisoformat(today)
    days = {date.fromisoformat(key) for key in keys}
    if label == '全部':
        return {day.isoformat() for day in days}
    if label == '本周':
        start = today - timedelta(days=today.weekday())
    elif label == '本月':
        start = today.replace(day=1)
    else:
        raise ValueError('Unknown range')
    return {day.isoformat() for day in days if start <= day <= today}


def assert_day_results(actual, keys, today, label):
    wanted = expected_days(keys, today, label)
    if set(actual) != wanted:
        raise AssertionError(f'{label}: missing={sorted(wanted-set(actual))}, extra={sorted(set(actual)-wanted)}')


def assert_saved_state(state, appearance=None, automatic=None, apk_sha256=None):
    if appearance is not None and state['appearance_mode'] != appearance:
        raise AssertionError('Theme did not persist after restart')
    if automatic is not None and state['automatic_enabled'] is not automatic:
        raise AssertionError('Recording preference does not match the visible action')
    if apk_sha256 is not None and state['installed_apk_sha256'] != apk_sha256:
        raise AssertionError('Installed APK is not the verified artifact')

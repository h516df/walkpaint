"""Real diagnostic UI on the guarded Google AVD, with reversible non-location fixtures.

No GPS injection, permissions changes, update authorization, or database writes.
All synthetic diagnostic preferences are restored even if a UI assertion fails.
"""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / 'scripts'))
sys.argv = [__file__]
__file__ = str(root / 'scripts/android_profile_acceptance.py')
exec(compile((root / 'scripts/android_profile_acceptance.py').read_text(encoding='utf-8').split('\npermissions=')[0], __file__, 'exec'))
out = root / 'verification/diagnostic-google'
out.mkdir(parents=True, exist_ok=True)
saved = {}
before = rows()
size = sh('wm', 'size')
density = sh('wm', 'density')

def scroll():
    nodes = [n for n in ui().iter('node') if n.get('scrollable') == 'true']
    assert nodes, 'Expected a scrollable report'
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', nodes[0].get('bounds')))
    sh('input', 'swipe', str((x1+x2)//2), str(y2-45), str((x1+x2)//2), str(y1+45), '350')
    time.sleep(.5)

def ensure(text):
    for _ in range(12):
        if text in labels():
            tap(text)
            return
        scroll()
    raise AssertionError('Missing control ' + text)

def top():
    for _ in range(12):
        if '复制报告' in labels():
            return
        nodes = [n for n in ui().iter('node') if n.get('scrollable') == 'true']
        assert nodes
        x1,y1,x2,y2 = map(int,re.findall(r'\d+',nodes[0].get('bounds')))
        sh('input','swipe',str((x1+x2)//2),str(y1+45),str((x1+x2)//2),str(y2-45),'350')
        time.sleep(.5)
    raise AssertionError('Cannot return to report copy control')

def install_pref(name, content):
    local = out / 'fixture.xml'
    local.write_bytes(content)
    run('push', str(local), '/data/local/tmp/wp-diagnostic-fixture.xml')
    sh('run-as', 'cn.walkpaint', 'cp', '/data/local/tmp/wp-diagnostic-fixture.xml', 'shared_prefs/'+name+'.xml')
    local.unlink()

def report_assertions():
    text = '\n'.join(labels())
    for expected in ['静止/小位移 900', '跳变/过快 JUMP_OR_TOO_FAST 100', '精度 15', '时间 5', '其他 5',
                     '已分类 1025 / 过滤总数 1025', '最近30分钟', '最近2小时', '不可回溯', '跨窗口起点桶']:
        assert expected in text, 'Missing report fact: ' + expected
    assert '无坐标诊断测试' not in text

try:
    sh('am', 'force-stop', 'cn.walkpaint')
    for name in ['recording_diagnostics', 'feedback_history']:
        try:
            saved[name] = run('exec-out', 'run-as', 'cn.walkpaint', 'cat', 'shared_prefs/'+name+'.xml', binary=True)
        except RuntimeError:
            saved[name] = None
    apk = root / 'app/build/outputs/apk/debug/app-debug.apk'
    assert 'Success' in run('install', '-r', str(apk), timeout=150)
    checks['source_sha'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root).decode().strip()
    checks['apk_sha256'] = hashlib.sha256(apk.read_bytes()).hexdigest()
    remote = sh('pm', 'path', 'cn.walkpaint').split('package:')[1].strip()
    assert hashlib.sha256(run('exec-out', 'cat', remote, binary=True, timeout=60)).hexdigest() == checks['apk_sha256']
    assert re.search(r'versionCode=17\b', sh('dumpsys', 'package', 'cn.walkpaint'))
    checks['installed_apk_matches'] = True
    assert before == rows()
    checks['upgrade_keeps_all_five_tables'] = {k:len(v) for k,v in before.items()}
    now = int(sh('date', '+%s').strip())*1000
    values = {'session_gps_count':1088, 'session_valid_gps_count':1088, 'session_saved_count':63,
              'session_filtered_count':1025, 'session_filter_stationary_count':900,
              'session_filter_jump_count':100, 'session_filter_accuracy_count':15,
              'session_filter_time_count':5, 'session_filter_other_count':5}
    doc = ET.Element('map')
    for key,value in values.items():
        ET.SubElement(doc, 'long', name=key, value=str(value))
    install_pref('recording_diagnostics', ET.tostring(doc, encoding='utf-8'))
    history = ET.Element('map')
    ET.SubElement(history, 'boolean', name='enabled', value='true')
    ET.SubElement(history, 'string', name='events').text = '\n'.join([
        f'GPS_STATIONARY|{now-600000}|{now-590000}|900',
        f'GPS_JUMP|{now-500000}|{now-490000}|100',
        f'GPS_ACCURACY|{now-400000}|{now-390000}|15',
        f'GPS_STALE|{now-300000}|{now-290000}|5',
        f'GPS_FILTERED|{now-200000}|{now-190000}|5',
        f'GPS_JUMP|{now-1900000}|{now-1700000}|20'])
    install_pref('feedback_history', ET.tostring(history, encoding='utf-8'))
    launch()
    assert not foreground(), 'Dedicated AVD must have automatic recording disabled before this test'
    tap('我的'); tap('更多设置'); ensure('高级诊断'); time.sleep(3)
    report_assertions(); shot('diagnostic-first-screen')
    scroll(); shot('diagnostic-filter-breakdown')
    scroll(); shot('diagnostic-window')
    tap('复制诊断'); checks['diagnostic_copy_action'] = True
    # Android 16's transient clipboard overlay can cover the dialog footer.
    # Let it disappear before clicking a control behind it.
    time.sleep(10)
    tap('关闭')
    ensure('问题反馈'); tap('道路涂色'); tap('预览反馈报告'); time.sleep(3)
    report_assertions(); shot('feedback-first-screen')
    scroll(); shot('feedback-filter-breakdown')
    scroll(); shot('feedback-window')
    top(); tap('复制报告'); checks['feedback_copy_action'] = True
    time.sleep(10)
    for _ in range(10):
        scroll()
    shot('feedback-report-end')
    assert '自动信息仅含版本' in '\n'.join(labels())
    back()
    sh('wm','size','320x710'); sh('wm','density','160'); time.sleep(3)
    ensure('预览反馈报告'); time.sleep(2); report_assertions(); shot('feedback-320dp')
    scroll(); shot('feedback-320dp-filters')
    top(); tap('复制报告'); time.sleep(10); back()
    checks['small_screen_report_scroll_copy_close'] = True
    checks['both_actual_reports_show_all_classifications_and_windows'] = True
    assert before == rows()
    checks['database_unchanged_by_diagnostics_and_feedback'] = True
except BaseException as exc:
    checks['failure'] = type(exc).__name__ + ': ' + str(exc)
    raise
finally:
    sh('am', 'force-stop', 'cn.walkpaint')
    for name, content in saved.items():
        if content is None:
            sh('run-as', 'cn.walkpaint', 'rm', '-f', 'shared_prefs/'+name+'.xml')
        else:
            install_pref(name, content)
            assert run('exec-out', 'run-as', 'cn.walkpaint', 'cat', 'shared_prefs/'+name+'.xml', binary=True) == content
    for command, prior in [('size',size), ('density',density)]:
        override = re.search(r'Override (?:size|density):\s*(\S+)', prior)
        sh('wm',command,override.group(1) if override else 'reset')
    sh('rm', '-f', '/sdcard/wp-profile-ui.xml', '/data/local/tmp/wp-diagnostic-fixture.xml')
    checks['synthetic_preferences_restored'] = True
    checks['viewport_restored'] = sh('wm','size') == size and sh('wm','density') == density
    checks['all_five_tables_unchanged_after_cleanup'] = before == rows()
    out.joinpath('acceptance.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2), encoding='utf-8')
print(json.dumps(checks,ensure_ascii=False))

"""v0.44 real same-engine UI acceptance, only the guarded dedicated AVD.
Preserves original rows and settings. All screenshots use existing public fixtures.
"""
import json, re, time, hashlib, sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import mobile_ui_acceptance as ui
from google_avd_guard import require_walkpaint_google_avd

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'verification/v044-ui'
OUT.mkdir(parents=True,exist_ok=True)
APK=ROOT/'dist/v0.44/input/WalkPaint-v0.44-arm64.apk'
e={'device':ui.SERIAL,'avd':'WalkPaint_Google_API36','engine':'amap','phone_path':'not_verified','checks':{},'passed':False}
def snap(name):
    tree=ui.ui()
    texts=[n.get('text') for n in tree.iter('node') if n.get('text')]
    time.sleep(.3)
    (OUT/(name+'.png')).write_bytes(ui.run('exec-out','screencap','-p',binary=True))
    return texts
def camera():
    report_path='/sdcard/Android/data/cn.walkpaint/files/field-report.txt'
    for _ in range(8):
        prior=ui.shell('stat','-c','%y',report_path)
        ui.shell('am','start','-n','cn.walkpaint/.MainActivity','--ez','cn.walkpaint.extra.EXPORT_FIELD_REPORT','true')
        time.sleep(1.2)
        text=ui.run('exec-out','cat',report_path)
        match=re.search(r'缩放=([\d.]+) 中心=([-\d.]+),([-\d.]+)',text)
        if ui.shell('stat','-c','%y',report_path)!=prior and match and '地图加载完成=true' in text and '引擎判定=正在用高德' in text:
            return dict(zoom=float(match[1]),lat=float(match[2]),lon=float(match[3]))
    raise AssertionError('Fresh loaded AMap camera report unavailable')
def installed_hash():
    path=ui.shell('pm','path','cn.walkpaint').strip().removeprefix('package:')
    return ui.shell('sha256sum',path).split()[0]

require_walkpaint_google_avd(ui.ADB,ui.SERIAL)
e['apk_sha256']=hashlib.sha256(APK.read_bytes()).hexdigest()
try:
    assert installed_hash()==e['apk_sha256']
    ui.shell('am','start','-W','-n','cn.walkpaint/.MainActivity')
    ui.tap('探索')
    before=camera()
    labels=snap('home')
    assert '探索' in labels and '足迹' in labels
    assert not any(x in labels for x in ('暂停记录','继续记录','高德地图','足迹点','今日里程','今天的足迹'))
    e['checks']['map_only_home']=True
    ui.tap('放大地图'); enlarged=camera()
    assert enlarged['zoom']>before['zoom']+.5
    ui.tap('缩小地图'); reduced=camera()
    assert reduced['zoom']<enlarged['zoom']-.5
    e['checks']['zoom_controls_change_camera']=True
    ui.shell('input','swipe','820','1000','280','1000','450'); moved=camera()
    assert abs(moved['lon']-reduced['lon'])>.0002
    ui.tap('定位我'); returned=camera()
    assert returned['zoom']>=16.5
    e['checks']['pan_and_locate']=True
    ui.tap('足迹');labels=snap('calendar')
    assert '足迹日历' in labels and '统计' in labels
    assert not any(x in labels for x in ('今天的足迹','本周','回顾','查看探索日历与统计'))
    ui.tap('统计'); labels=snap('statistics');assert '回顾' in labels and '日历' in labels
    ui.tap('日历'); labels=snap('calendar-return');assert '足迹日历' in labels and '回顾' not in labels
    # The fixture intentionally contains today's recorded day; verify a date click opens details.
    from datetime import datetime
    ui.tap(str(datetime.now().day));labels=snap('detail')
    assert '行程详情' in labels
    ui.tap('在地图查看这天的足迹'); current=camera();labels=snap('day-map')
    assert any('返回全部探索' in t for t in labels)
    ui.tap('返回全部探索',contains=True);camera();snap('all-exploration')
    e['checks']['calendar_statistics_day_details_day_map']=True
    ui.tap('我的');labels=snap('profile')
    assert '自动记录' in labels
    labels=snap('settings')
    assert '联网备用地图' not in labels and '地图服务与隐私' in labels
    assert '诊断与反馈' in labels or '数据与备份' in labels
    ui.tap('诊断与反馈',scroll=True);labels=snap('feedback');assert '问题反馈' in labels
    ui.tap('返回');ui.tap('检查更新',scroll=True);labels=snap('updates');assert any('0.44' in x for x in labels)
    e['checks']['recording_and_diagnostics_in_profile_no_backup_entry']=True
    ui.tap('探索');camera()
    ui.shell('am','force-stop','cn.walkpaint')
    ui.shell('am','start','-W','-n','cn.walkpaint/.MainActivity')
    assert camera()['zoom']>=16.5;snap('reopen')
    e['checks']['reopen_exploration']=True
    assert installed_hash()==e['apk_sha256']
    e['passed']=True
finally:
    ui.shell('rm','-f','/sdcard/wp-mobile-ui-acceptance.xml')
    (OUT/'acceptance.json').write_text(json.dumps(e,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(e,ensure_ascii=False))



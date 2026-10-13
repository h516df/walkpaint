"""Real same-engine UI acceptance, only the guarded dedicated AVD.
Preserves original rows and settings. All screenshots use existing public fixtures.
"""
import json, re, time, hashlib, sys, argparse
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import mobile_ui_acceptance as ui
from google_avd_guard import require_walkpaint_google_avd, GOOGLE_AVDS

ROOT=Path(__file__).resolve().parents[1]
VERSION=json.loads((ROOT/'app/src/main/assets/release-notes.json').read_text(encoding='utf-8'))['versionName']
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk',type=Path,default=ROOT/f'dist/v{VERSION}/input/WalkPaint-v{VERSION}-arm64.apk')
parser.add_argument('--out',type=Path,default=ROOT/f'verification/v{VERSION.replace(".","")}-ui')
parser.add_argument('--reopen-only',action='store_true')
parser.add_argument('--profile-only',action='store_true')
args=parser.parse_args()
OUT=args.out
OUT.mkdir(parents=True,exist_ok=True)
APK=args.apk
e={'device':ui.SERIAL,'avd':GOOGLE_AVDS[ui.SERIAL],'engine':'amap','phone_path':'not_verified','checks':{},'passed':False}
def snap(name,expected=None):
    deadline=time.monotonic()+12
    while True:
        tree=ui.ui()
        visible={n.get('text') for n in tree.iter('node')}
        if expected is None or expected<=visible: break
        assert time.monotonic()<deadline, f'Visible page never ready: {name}, missing {expected-visible}'
        time.sleep(.4)
    import xml.etree.ElementTree as ET
    (OUT/(name+'.xml')).write_bytes(ET.tostring(tree,encoding='utf-8'))
    texts=[value for n in tree.iter('node') for key in ('text','content-desc') if (value:=n.get(key))]
    time.sleep(.3)
    (OUT/(name+'.png')).write_bytes(ui.run('exec-out','screencap','-p',binary=True))
    return texts
def bottom_navigation(name, map_controls=False):
    tree=ui.ui()
    size=re.findall(r'(\d+)x(\d+)',ui.shell('wm','size'))[-1]
    width,height=map(int,size)
    boxes={}
    for label in ('探索','足迹','我的'):
        nodes=[n for n in tree.iter('node') if n.get('text')==label
               and int(re.findall(r'\d+',n.get('bounds'))[1])>height*.75]
        assert len(nodes)==1, f'{label}: expected one visible navigation label'
        box=list(map(int,re.findall(r'\d+',nodes[0].get('bounds'))))
        assert box[1]>height*.75 and box[3]<=height and 0<=box[0]<box[2]<=width, (label,box,size)
        boxes[label]=box
    if map_controls:
        navigation_top=min(b[1] for b in boxes.values())
        for label in ('放大地图','缩小地图','定位我'):
            node=next(n for n in tree.iter('node') if n.get('content-desc')==label)
            box=list(map(int,re.findall(r'\d+',node.get('bounds'))))
            assert box[3]<navigation_top, (label,box,boxes)
            boxes[label]=box
    e.setdefault('bottom_navigation_bounds',{})[name]=boxes
    e['checks']['navigation_at_bottom_and_map_controls_clear']=True
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
def settled_camera():
    deadline=time.monotonic()+25
    previous=camera()
    while True:
        current=camera()
        if all(abs(current[k]-previous[k])<(.01 if k=='zoom' else .00001) for k in current): return current
        assert time.monotonic()<deadline, f'Camera never settled: {previous} -> {current}'
        previous=current

def installed_hash():
    path=ui.shell('pm','path','cn.walkpaint').strip().removeprefix('package:')
    return ui.shell('sha256sum',path).split()[0]

require_walkpaint_google_avd(ui.ADB,ui.SERIAL)
e['apk_sha256']=hashlib.sha256(APK.read_bytes()).hexdigest()
try:
    assert installed_hash()==e['apk_sha256']
    if '--profile-only' in sys.argv or '--reopen-only' in sys.argv:
        prior=json.loads((OUT/'acceptance.json').read_text(encoding='utf-8'))
        assert prior['apk_sha256']==e['apk_sha256']
        names=('map_only_home','zoom_controls_change_camera','pan_and_locate','calendar_statistics_day_details_day_map')
        assert all(prior['checks'].get(k) is True for k in names)
        e['checks']={k:True for k in names}
        if '--reopen-only' in sys.argv:
            assert prior['checks'].get('recording_and_diagnostics_in_profile_no_backup_entry') is True
            e['checks']['recording_and_diagnostics_in_profile_no_backup_entry']=True
    else:
        ui.shell('am','force-stop','cn.walkpaint')
        ui.shell('am','start','-W','-n','cn.walkpaint/.MainActivity')
        ui.tap('探索')
        before=camera()
        for _ in range(12):
            if before['zoom']>=16.5: break
            before=camera()
        assert before['zoom']>=16.5, 'Initial locate animation did not settle'
        e['camera_before_zoom']=before
        labels=snap('home')
        bottom_navigation('home',map_controls=True)
        assert '探索' in labels and '足迹' in labels
        assert not any(x in labels for x in ('暂停记录','继续记录','高德地图','足迹点','今日里程','今天的足迹'))
        e['checks']['map_only_home']=True
        ui.tap('放大地图'); enlarged=camera();e['camera_after_zoom_in']=enlarged
        assert enlarged['zoom']>before['zoom']+.5
        ui.tap('缩小地图'); reduced=camera();e['camera_after_zoom_out']=reduced
        assert reduced['zoom']<enlarged['zoom']-.5
        e['checks']['zoom_controls_change_camera']=True
        ui.shell('input','swipe','820','1000','280','1000','450'); moved=settled_camera()
        assert abs(moved['lon']-reduced['lon'])>.0002
        ui.tap('足迹');ui.tap('探索');held=settled_camera()
        e['panned_camera']=moved;e['camera_after_tab_return']=held
        assert abs(held['lat']-moved['lat'])<.00005 and abs(held['lon']-moved['lon'])<.00005
        assert abs(held['zoom']-moved['zoom'])<.05
        e['checks']['tab_switch_preserves_panned_camera']=True
        e['panned_camera']=moved;e['camera_after_tab_return']=held
        ui.tap('定位我'); returned=camera()
        assert returned['zoom']>=16.5
        e['checks']['pan_and_locate']=True
        ui.tap('足迹');labels=snap('calendar',{'足迹日历','统计'})
        bottom_navigation('calendar')
        assert '足迹日历' in labels and '统计' in labels
        assert not any(x in labels for x in ('今天的足迹','本周','回顾','查看探索日历与统计'))
        ui.tap('统计'); labels=snap('statistics',{'回顾','日历'});assert '回顾' in labels and '日历' in labels
        ui.tap('日历'); labels=snap('calendar-return',{'足迹日历'});assert '足迹日历' in labels and '回顾' not in labels
        # The fixture intentionally contains today's recorded day; verify a date click opens details.
        from datetime import datetime
        ui.tap(str(datetime.now().day));labels=snap('detail')
        assert '行程详情' in labels
        ui.tap('在地图查看这天的足迹'); current=camera();labels=snap('day-map')
        assert any('返回全部探索' in t for t in labels)
        ui.tap('返回全部探索',contains=True);camera();snap('all-exploration')
        e['checks']['calendar_statistics_day_details_day_map']=True
    if '--reopen-only' not in sys.argv:
        ui.tap('探索');profile_camera=camera();ui.tap('我的');labels=snap('profile')
        bottom_navigation('profile')
        assert '自动记录' in labels
        labels=snap('settings')
        assert '联网备用地图' not in labels and '地图服务与隐私' in labels
        ui.tap('诊断与反馈',scroll=True);labels=snap('feedback');assert '问题反馈' in labels or '报告预览' in labels
        hidden_camera=camera()
        e['profile_hidden_camera_before']=profile_camera;e['profile_hidden_camera_after']=hidden_camera
        assert all(abs(hidden_camera[k]-profile_camera[k])<(.01 if k=='zoom' else .00001) for k in hidden_camera), 'Profile scroll moved the covered map'
        e['checks']['profile_scroll_leaves_map_camera_unchanged']=True
        ui.tap('返回')
        if '问题反馈' in snap('feedback-return'): ui.tap('返回')
        ui.tap('检查更新',scroll=True);labels=snap('updates');assert any(VERSION in x for x in labels)
        e['checks']['recording_and_diagnostics_in_profile_no_backup_entry']=True
    ui.tap('探索');camera()
    ui.shell('am','force-stop','cn.walkpaint')
    ui.shell('am','start','-W','-n','cn.walkpaint/.MainActivity')
    reopened=camera()
    for attempt in range(15):
        if reopened['zoom']>=16.5: break
        reopened=camera()
    assert reopened['zoom']>=16.5;snap('reopen')
    bottom_navigation('reopen',map_controls=True)
    e['checks']['reopen_exploration']=True
    assert installed_hash()==e['apk_sha256']
    e['passed']=True
finally:
    ui.shell('rm','-f','/sdcard/wp-mobile-ui-acceptance.xml')
    (OUT/'acceptance.json').write_text(json.dumps(e,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(e,ensure_ascii=False))

#!/usr/bin/env python3
"""Issue #6 / v0.9 real Android UI acceptance; only allowed emulator and fixed public Oxford data.
Preserves all existing rows, does not clear/uninstall, restores permission/mock/viewport state.
"""
from pathlib import Path
helper_source=Path('scripts/android_v05_acceptance.py').read_text(encoding='utf-8').split('\ninitial=rows()')[0]
helper_source=helper_source.replace('if r<35 and 100<g<160 and 85<b<140: count+=1','if abs(r-87)<=6 and abs(g-155)<=6 and abs(b-140)<=6: count+=1').replace('height*0.30<y<height*0.73','height*0.12<y<height*0.78')
exec(helper_source)
base_launch=launch
def launch():
    base_launch()
    # Move-stack can leave a previous system Settings activity above the app on MuMu.
    shell('am','start','--display','0','--activity-reorder-to-front','-n','cn.walkpaint/.MainActivity')
    time.sleep(2)
initial=rows();before=count();before_coverage=coverage()
checks['initial_points']=before
checks['source_diff_sha256']=hashlib.sha256(subprocess.check_output(['git','diff','HEAD'])).hexdigest()
permissions=['ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','ACCESS_BACKGROUND_LOCATION']
permission_state=shell('dumpsys','package','cn.walkpaint')
granted={key:bool(re.search(key+r': granted=true',permission_state)) for key in permissions}
mock_state=shell('appops','get','2000','android:mock_location')
mode=re.search(r'(?:MOCK_LOCATION|mock_location):\s*(allow|ignore|deny|default|foreground)',mock_state)
original_mode=mode.group(1) if mode else 'default'
rotation=shell('wm','user-rotation','-d','0').strip().split()
size_state=shell('wm','size');override=re.search(r'Override size: (\d+x\d+)',size_state)
original_size=override.group(1) if override else 'reset'
geometry=json.loads(Path('app/src/androidTest/assets/oxford-roads.geojson').read_text(encoding='utf-8'))['features'][0]['geometry']['coordinates']
mock=provider=permissions_changed=False
def at(distance):
    import math
    for a,b in zip(geometry,geometry[1:]):
        length=math.hypot((b[1]-a[1])*111195,(b[0]-a[0])*111195*math.cos(math.radians((a[1]+b[1])/2)))
        if distance<=length:
            t=distance/length;return a[1]+(b[1]-a[1])*t,a[0]+(b[0]-a[0])*t
        distance-=length
    raise AssertionError('Public fixture too short')
def texts():return '\n'.join(n.attrib.get('text','') for n in ui().iter('node'))
def scroll(up=False):
    n=next(n for n in ui().iter('node') if n.attrib.get('scrollable')=='true')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
    a,b=(y1+60,y2-60) if up else (y2-60,y1+60)
    shell('input','-d','0','swipe',str((x1+x2)//2),str(a),str((x1+x2)//2),str(b),'350');time.sleep(1)
def ensure(label):
    for i in range(6):
        if label in texts():tap(label);return
        scroll()
    raise AssertionError('Missing scrolled setting '+label)
def top():
    for i in range(4):
        if '授权一次，日常自动记录' in texts():return
        scroll(up=True)
def toggle():
    n=next(n for n in ui().iter('node') if n.attrib.get('checkable')=='true')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
    shell('input','-d','0','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(2)
def resume():toggle();tap('启用自动足迹',exact=True);time.sleep(3);assert foreground()
def home_clean():
    root=ui();txt='\n'.join(n.attrib.get('text','') for n in root.iter('node'))
    assert not any(n.attrib.get('checkable')=='true' for n in root.iter('node'))
    for banned in ['足迹涂色','已涂色','已存','正在搜寻','导入道路','查看详情','自动足迹']:
        assert banned not in txt, 'Home contains technical card: '+banned
def pixels(name):
    assert_colored_render(name)
try:
    assert 'Success' in run('install','-r',str(Path(args.apk).resolve()),timeout=120)
    assert f'versionCode={args.version_code}' in shell('dumpsys','package','cn.walkpaint')
    assert all(initial[t]<=rows()[t] for t in initial)
    print('SAME_SIGNATURE_UPGRADE_ROWS_PASS',flush=True)
    assert 'Success' in run('install','-r','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',timeout=120)
    result=shell('am','instrument','-w','cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation')
    (out/'isolated-database-tests.txt').write_text(result,encoding='utf-8')
    assert 'V05_ISOLATED_DATABASE_PASS' in result
    checks['isolated_database_migration_failure_backup_daily']=True
    shell('wm','size','810x1440');launch()
    if '是否使用高德地图' in texts():tap('同意使用高德',exact=True)
    shell('appops','set','2000','android:mock_location','allow');mock=True
    shell('cmd','location','providers','add-test-provider','gps','--requiresSatellite','--supportsSpeed','--supportsBearing');provider=True
    shell('cmd','location','providers','set-test-provider-enabled','gps','true')
    tap('我的',exact=True);toggle();assert not foreground();resume();tap('地图',exact=True)
    with database() as db:matched_before=db.execute('SELECT COALESCE(SUM(matched),0) FROM road_matches').fetchone()[0]
    for i,d in enumerate([18,32,46,60]):check_saved('confirmed_public_movement_'+str(i),*at(d))
    with database() as db:matched_after=db.execute('SELECT COALESCE(SUM(matched),0) FROM road_matches').fetchone()[0]
    assert matched_after>=matched_before+3
    checks['fresh_confirmed_pairs']=matched_after-matched_before
    # Exercise an unpainted public section, no database reset or invented road.
    with database() as db:
        public_id=db.execute("SELECT id FROM roads WHERE osm_id=1675518").fetchone()[0]
        existing=db.execute("SELECT start,end FROM coverage WHERE road_id=?",(public_id,)).fetchall()
    new_start=68.0
    assert not any(end>new_start and start<new_start+30 for start,end in existing), 'Public new section already painted; choose another untouched fixture'
    check_saved('new_section_anchor',*at(new_start))
    tap('定位我');time.sleep(3);shot('new-section-before')
    initial_meters=coverage()
    with database() as db:new_matches=db.execute('SELECT COALESCE(SUM(matched),0) FROM road_matches').fetchone()[0]
    for i,d in enumerate([new_start+10,new_start+20,new_start+30]):
        check_saved('new_section_walk_'+str(i),*at(d))
    assert coverage()>initial_meters+29, 'No new contiguous confirmed coverage'
    with database() as db:
        assert db.execute('SELECT COALESCE(SUM(matched),0) FROM road_matches').fetchone()[0]>=new_matches+3
        assert any(start<=new_start+.2 and end>=new_start+29.8 for start,end in db.execute('SELECT start,end FROM coverage WHERE road_id=?',(public_id,)))
    tap('定位我');time.sleep(4);shot('new-section-after');pixels('new-section-after')
    meters_after=coverage()
    for i,d in enumerate([new_start+20,new_start+10,new_start]):check_saved('new_section_reverse_'+str(i),*at(d))
    assert abs(coverage()-meters_after)<.3
    shell('am','force-stop','cn.walkpaint');launch()
    assert foreground() and abs(coverage()-meters_after)<.3
    tap('定位我');time.sleep(3);shot('new-section-reopened');pixels('new-section-reopened')
    checks['new_gray_to_contiguous_confirmed_30m_reverse_restart']=True
    # A new service session must not reuse historic GPS counters or satellite observations.
    values=diagnostics()
    assert int(values.get('session_gps_count','0'))<=1 and int(values.get('session_saved_count','0'))==0
    assert int(values.get('gnss_at','0'))==0 or int(values['gnss_at'])>=int(values['service_start_at'])
    checks['session_restart_no_historic_callback_or_satellite_claim']=True
    # Restore the original public test position; do not bridge the discontinuity.
    check_saved('return_public_anchor',*at(18))
    forward=coverage();tap('定位我');time.sleep(7);home_clean();shot('map');pixels('map')
    checks['normal_home_clean_and_amap_rendered']=True
    print('NORMAL_MAP_RENDER_PASS',flush=True)
    for i,d in enumerate([46,32,18]):check_saved('reverse_duplicate_'+str(i),*at(d))
    assert abs(coverage()-forward)<.3;checks['repeat_deduplicated']=True
    shell('input','-d','0','keyevent','KEYCODE_HOME');check_saved('background_save',*at(32))
    shell('input','keyevent','KEYCODE_SLEEP');time.sleep(8);check_saved('screen_off_save',*at(46))
    assert foreground();launch();checks['background_screen_off_save']=True
    # A fix unsuitable for the recorder is a settings detail, not a permanent home card.
    check_saved('poor_fix_not_stored',*at(60),accuracy=80,expect=False)
    home_clean();shot('no-reliable-fix');checks['no_fix_without_permanent_status_card']=True
    tap('足迹',exact=True);shot('footprints');assert '累计已探索道路' in texts()
    assert '定位点' not in texts() and '记录轨迹距离' not in texts()
    ensure('查看今天走过的道路');time.sleep(2);shot('day-map');tap('返回全部探索')
    tap('我的',exact=True);shot('my-top')
    ensure('附近道路');assert '选择道路包文件' in texts();tap('选择道路包文件')
    assert 'com.android.documentsui' in shell('dumpsys','activity','activities')
    shell('input','keyevent','KEYCODE_BACK');tap('查看地图',exact=True)
    checks['road_management_actual_picker_entry']=True
    tap('我的',exact=True);ensure('导出备份')
    assert 'com.android.documentsui' in shell('dumpsys','activity','activities')
    shell('input','keyevent','KEYCODE_BACK')
    ensure('恢复备份');assert 'com.android.documentsui' in shell('dumpsys','activity','activities')
    shell('input','keyevent','KEYCODE_BACK');checks['backup_restore_real_picker_entry']=True
    ensure('记录与道路诊断');time.sleep(2)
    for i in range(5):
        if '道路匹配只读诊断' in texts():break
        scroll()
    assert '道路匹配只读诊断' in texts();shot('road-diagnosis')
    # Scroll back through the real diagnostic dialog, including new GNSS/session fields.
    found=False
    for i in range(12):
        text=texts()
        if '本次服务：GPS回调' in text and '卫星状态' in text:
            found=True;shot('gps-gnss-diagnosis');break
        scroll(up=True)
    assert found, 'New coordinate-free stage/GNSS diagnosis missing from actual UI'
    tap('关闭',exact=True)
    checks['coordinate_free_match_diagnosis_real_ui']=True
    top();ensure('省电与后台运行')
    resumed=[line for line in shell('dumpsys','activity','activities').splitlines() if 'topResumedActivity=' in line]
    assert any('com.android.settings/' in line for line in resumed), 'System battery settings not foreground'
    shell('input','keyevent','KEYCODE_BACK');checks['power_settings_entry']=True
    top();toggle();paused=count();tap('地图',exact=True);home_clean();shot('paused-home')
    check_saved('paused_no_write',*at(60),expect=False);assert count()==paused
    tap('我的',exact=True);resume();ensure('撤回自动记录授权');tap('撤回授权',exact=True)
    assert not foreground();checks['pause_resume_revoke_preserved_history']=True
    top();resume();tap('地图',exact=True)
    # Real system permission withdrawal; no app preferences/databases are cleared.
    permissions_changed=True
    for key in ['ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION']:shell('pm','revoke','cn.walkpaint','android.permission.'+key)
    launch();assert '定位权限已失效' in texts();shot('permission-expired');pixels('permission-expired')
    checks['no_permission_focuses_existing_painted_roads']=True
    tap('定位权限已失效');assert '定位权限' in texts();tap('地图',exact=True);tap('定位我')
    n=next(n for n in ui().iter('node') if n.attrib.get('resource-id','').endswith('permission_allow_foreground_only_button'))
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
    shell('input','-d','0','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(3);assert foreground()
    checks['permission_notice_settings_and_real_grant']=True
    check_saved('after_permission_new_fix',*at(18));tap('定位我')
    print('CALLBACK_STALL_CHECK_60_SECONDS',flush=True)
    for i in range(6):time.sleep(10)
    assert '未收到新定位' in texts();assert foreground();shot('recording-interrupted')
    tap('未收到新定位');assert '自动足迹' in texts();checks['interruption_notice_clicks_settings']=True
    tap('地图',exact=True);check_saved('after_gap_no_false_paint',*at(60));assert abs(coverage()-forward)<.3
    tap('定位我');home_clean();shot('map-final');pixels('map-final')
    shell('wm','size','720x1280');launch();tap('定位我');home_clean();shot('small-map')
    root=ui();loc=next(n for n in root.iter('node') if n.attrib.get('content-desc')=='定位我')
    nav=next(n for n in root.iter('node') if n.attrib.get('content-desc')=='我的')
    lb=list(map(int,re.findall(r'\d+',loc.attrib['bounds'])));nb=list(map(int,re.findall(r'\d+',nav.attrib['bounds'])))
    assert lb[3]<nb[1],'Location control overlaps navigation'
    tap('足迹',exact=True);shot('small-footprints');tap('我的',exact=True);shot('small-my')
    checks['small_320dp_navigation_no_overlap']=True
    checks['all_original_rows_retained']=all(initial[t]<=rows()[t] for t in initial)
    assert checks['all_original_rows_retained'] and coverage()>=before_coverage-.3
    checks['final_points']=count();checks['painted_meters']=coverage()
    checks['apk_sha256']=hashlib.sha256(Path(args.apk).read_bytes()).hexdigest()
    installed=run('exec-out','cat',shell('pm','path','cn.walkpaint').strip().split(':',1)[1],binary=True)
    assert hashlib.sha256(installed).hexdigest()==checks['apk_sha256'];checks['installed_exact_apk']=True
    checks['real_vivo']='NOT_TESTED';checks['doze_oem_reboot_battery']='NOT_TESTED'
    checks['backup_roundtrip']='ISOLATED_DATABASE_PASS; real UI picker opening/cancel only'
    checks['pass']=True;print('ISSUE6_V09_FINAL_REAL_UI_PASS',flush=True)
except BaseException as e:
    checks['error']=str(e);raise
finally:
    errors=[]
    actions=[]
    if provider:actions.append(('cmd','location','providers','remove-test-provider','gps'))
    if mock:actions.append(('appops','set','2000','android:mock_location',original_mode))
    if permissions_changed:
        for key in permissions:actions.append(('pm','grant' if granted[key] else 'revoke','cn.walkpaint','android.permission.'+key))
    actions.append(('wm','size',original_size))
    if len(rotation)>=2:actions.append(('wm','user-rotation','-d','0',*rotation))
    actions += [('input','keyevent','KEYCODE_WAKEUP'),('rm','-f','/sdcard/wp-v05-ui.xml')]
    for action in actions:
        try:shell(*action)
        except Exception as e:errors.append(str(e))
    checks['cleanup_errors']=errors
    checks['viewport_restored']=shell('wm','size')==size_state
    checks['mock_restored']=original_mode in shell('appops','get','2000','android:mock_location')
    (out/'acceptance.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    if errors:raise RuntimeError('Cleanup failed')

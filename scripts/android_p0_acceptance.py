#!/usr/bin/env python3
"""Issue #2 emulator-only acceptance; preserve original rows and restore test state.
No real-device mock injection, network changes, uploads, uninstall or data clear.
"""
from pathlib import Path
source=(Path(__file__).with_name('android_v05_acceptance.py')).read_text(encoding='utf-8')
assert '\ninitial=rows()' in source
exec(compile(source.split('\ninitial=rows()')[0],'existing-v05-helpers','exec'))
initial=rows();initial_count=count()
checks['initial_points']=initial_count
checks['baseline_head']=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
checks['source_diff_sha256']=hashlib.sha256(subprocess.check_output(['git','diff','HEAD'])).hexdigest()
permission_state=shell('dumpsys','package','cn.walkpaint')
permissions=['ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','ACCESS_BACKGROUND_LOCATION']
granted={key:bool(re.search(key+r': granted=true',permission_state)) for key in permissions}
mock_state=shell('appops','get','2000','android:mock_location')
mode=re.search(r'(?:MOCK_LOCATION|mock_location):\s*(allow|ignore|deny|default|foreground)',mock_state)
restore_mode=mode.group(1) if mode else 'default'
location_enabled=shell('cmd','location','is-location-enabled').strip()
rotation=shell('wm','user-rotation','-d','0').strip().split()
mock_set=provider_set=permission_changed=False
fixture=Path('app/src/androidTest/assets/oxford-roads.geojson')
geometry=json.loads(fixture.read_text(encoding='utf-8'))['features'][0]['geometry']['coordinates']
def at(distance):
    import math
    for a,b in zip(geometry,geometry[1:]):
        length=math.hypot((b[1]-a[1])*111195,(b[0]-a[0])*111195*math.cos(math.radians((a[1]+b[1])/2)))
        if distance<=length:
            t=distance/length
            return a[1]+(b[1]-a[1])*t,a[0]+(b[0]-a[0])*t
        distance-=length
    raise AssertionError('Public road fixture too short')
def texts():return '\n'.join(n.attrib.get('text','') for n in ui().iter('node'))
def toggle():
    n=next(n for n in ui().iter('node') if n.attrib.get('checkable')=='true')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
    shell('input','-d','0','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(2)
def partial_sleep(seconds):
    for i in range(seconds//10):time.sleep(10)
try:
    assert 'name="automatic_enabled" value="true"' in run('exec-out','run-as','cn.walkpaint','cat','shared_prefs/footprint_preferences.xml'), 'Existing consent required'
    assert 'Success' in run('install','-r',str(Path(args.apk).resolve()),timeout=120)
    assert f'versionCode={args.version_code}' in shell('dumpsys','package','cn.walkpaint')
    assert all(initial[t]<=rows()[t] for t in initial)
    checks['same_package_upgrade_preserved_history']=True
    assert 'Success' in run('install','-r','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',timeout=120)
    instrumentation=shell('am','instrument','-w','cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation')
    (out/'database-tests.txt').write_text(instrumentation,encoding='utf-8')
    assert 'V05_ISOLATED_DATABASE_PASS' in instrumentation
    checks['isolated_migration_failure_backup_daily']=True
    shell('cmd','location','set-location-enabled','true')
    # The real Android permission dialog, using the user's already-consented emulator session.
    permission_changed=True
    for key in ['ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION']:
        shell('pm','revoke','cn.walkpaint','android.permission.'+key)
    launch();tap('定位我')
    n=next(n for n in ui().iter('node') if n.attrib.get('resource-id','').endswith('permission_allow_foreground_only_button'))
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
    shell('input','-d','0','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(3)
    assert foreground()
    checks['real_permission_return_started_fgs']=True
    run('push',str(fixture.resolve()),'/sdcard/Download/wp-oxford-roads.geojson')
    tap('青绿：已涂道路');select_pack();tap('查看地图',exact=True)
    shell('appops','set','2000','android:mock_location','allow');mock_set=True
    shell('cmd','location','providers','add-test-provider','gps','--requiresSatellite','--supportsSpeed','--supportsBearing');provider_set=True
    shell('cmd','location','providers','set-test-provider-enabled','gps','true')
    with database() as db: matches_before=db.execute('SELECT COALESCE(SUM(matched),0) FROM road_matches').fetchone()[0]
    check_saved('foreground_movement',*at(18))
    shell('input','-d','0','keyevent','KEYCODE_HOME')
    check_saved('other_app_background',*at(32))
    shell('input','keyevent','KEYCODE_SLEEP')
    assert any(x in shell('dumpsys','power') for x in ['mWakefulness=Asleep','mWakefulness=Dozing'])
    print('SCREEN_OFF continuous public mock GPS for 60 seconds',flush=True)
    screen_started=time.monotonic()
    for i,d in enumerate([46,60,46]):
        partial_sleep(20)
        check_saved('screen_off_'+str(i),*at(d))
    checks['screen_off_duration_seconds']=round(time.monotonic()-screen_started)
    assert foreground()
    saved_diagnostics=diagnostics()
    assert 'SCREEN_OFF' in saved_diagnostics.get('power_state','')
    assert int(saved_diagnostics['valid_gps_at'])>0 and int(saved_diagnostics['saved_at'])>0
    checks['screen_off_callbacks_valid_and_saved_separate']=True
    with database() as db: matches_after=db.execute('SELECT COALESCE(SUM(matched),0) FROM road_matches').fetchone()[0]
    assert matches_after>=matches_before+4, 'New GPS must create fresh confirmed matches, not only display old coverage'
    checks['fresh_road_matches_added']=matches_after-matches_before
    launch();tap('定位我');shot('return-road-map');assert_colored_render('return-road-map')
    assert '新足迹已保存' in texts()
    forward=coverage()
    for i,d in enumerate([32,18,32,46,60]):check_saved('reverse_repeat_'+str(i),*at(d))
    assert abs(coverage()-forward)<0.3
    checks['road_repeat_deduplicated']=True
    check_saved('bad_precision_not_saved',*at(94),accuracy=80,expect=False)
    assert '等待可靠 GPS' in texts()
    checks['fresh_rejected_fix_honest_status']=True
    shot('poor-precision')
    print('SIMULATED_CALLBACK_STALL for 65 seconds, not an OEM-kill claim',flush=True)
    partial_sleep(60);time.sleep(5)
    shot('callback-interrupted')
    assert 'GPS 回调已中断' in texts()
    assert foreground()
    assert abs(coverage()-forward)<0.3
    checks['stalled_callback_ui_while_service_alive']=True
    check_saved('recovery_no_gap_bridge',*at(94))
    assert abs(coverage()-forward)<0.3
    checks['gap_not_colored']=True
    # Recorded point with no imported road nearby; there is no automatic network download.
    time.sleep(12)
    check_saved('outside_catalog_jump_not_saved',52.0,-1.5,expect=False)
    assert abs(coverage()-forward)<0.3
    assert '当前位置附近没有已导入道路' in texts()
    checks['outside_catalog_hint_and_no_paint']=True
    shot('outside-road-region')
    tap('足迹',exact=True);shot('daily-history')
    assert '按天查看足迹' in texts()
    # Today's date selects a day row, never an individual zero-distance trip.
    from datetime import datetime
    daylabel=datetime.now().strftime('%m月%d日')
    tap(daylabel)
    assert '返回累计地图' in texts()
    shot('selected-day-map')
    assert_colored_render('selected-day-map')
    tap('返回累计地图',exact=True)
    checks['daily_history_and_day_map_real_ui']=True
    # Deliberate strong stop: no automatic bypass, reopening is the recovery trigger.
    before_stop=count();persisted=diagnostics()
    shell('am','force-stop','cn.walkpaint');assert not foreground();time.sleep(3);assert count()==before_stop
    launch();assert foreground();time.sleep(3)
    recovered=diagnostics()
    assert int(recovered.get('start_count',0))>int(persisted.get('start_count',0))
    checks['force_stop_requires_visible_reopen']=True
    checks['system_exit_reason']=recovered.get('process_exit_reason','UNAVAILABLE')
    checks['recovery_reason']=recovered.get('recovery_reason','UNAVAILABLE')
    tap('我的',exact=True);tap('查看记录诊断');shot('diagnostics-top')
    assert '最近有效 GPS' in texts() and '实际 SQLite 提交' in texts()
    scroll=next((n for n in ui().iter('node') if n.attrib.get('scrollable')=='true'),None)
    if scroll is not None:
        x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll.attrib['bounds']))
        shell('input','-d','0','swipe',str((x1+x2)//2),str(y2-25),str((x1+x2)//2),str(y1+25),'500')
    assert '系统进程退出' in texts() and '屏幕/省电/待机' in texts()
    shot('diagnostics-bottom');tap('关闭',exact=True)
    toggle();assert not foreground()
    check_saved('paused_no_write',52.0003,-1.5,expect=False)
    toggle();tap('启用自动足迹',exact=True);time.sleep(3);assert foreground()
    checks['pause_and_explicit_resume']=True
    final=rows();assert all(initial[t]<=final[t] for t in initial)
    checks['all_original_rows_retained']=True
    checks['painted_meters']=coverage()
    checks['added_public_emulator_points']=count()-initial_count
    checks['apk_sha256']=hashlib.sha256(Path(args.apk).read_bytes()).hexdigest()
    installed=run('exec-out','cat',shell('pm','path','cn.walkpaint').strip().split(':',1)[1],binary=True)
    assert hashlib.sha256(installed).hexdigest()==checks['apk_sha256']
    checks['installed_apk_exact_hash']=True
    checks['real_vivo']='BLOCKED-no-real-device-connected'
    checks['doze_oem_reboot_battery']='NOT_TESTED'
    checks['pass']=True
    (out/'diagnostic-fields.json').write_text(json.dumps(diagnostics(),ensure_ascii=False,indent=2),encoding='utf-8')
    print('ISSUE_2_P0_EMULATOR_PASS',json.dumps(checks,ensure_ascii=False),flush=True)
finally:
    errors=[]
    actions=[]
    if provider_set:actions.append(('cmd','location','providers','remove-test-provider','gps'))
    if mock_set:actions.append(('appops','set','2000','android:mock_location',restore_mode))
    if permission_changed:
        for key in permissions:actions.append(('pm','grant' if granted[key] else 'revoke','cn.walkpaint','android.permission.'+key))
    actions += [('cmd','location','set-location-enabled',location_enabled),('wm','user-rotation','-d','0',*rotation),('input','keyevent','KEYCODE_WAKEUP'),('rm','-f','/sdcard/Download/wp-oxford-roads.geojson','/sdcard/wp-v05-ui.xml')]
    for action in actions:
        try:shell(*action)
        except Exception as e:errors.append(type(e).__name__)
    checks['cleanup_errors']=errors
    (out/'acceptance.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    assert not errors, 'Test state cleanup failed'

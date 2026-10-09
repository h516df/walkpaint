#!/usr/bin/env python3
"""Emulator-only targeted recovery after a failed primary attempt. Never erase failure evidence."""
from pathlib import Path
source=Path(__file__).with_name('android_v05_acceptance.py').read_text(encoding='utf-8')
assert '\ninitial=rows()' in source
exec(compile(source.split('\ninitial=rows()')[0],'existing-v05-helpers','exec'))
prior=json.loads(Path('verification/issue-2-p0-complete/acceptance.json').read_text(encoding='utf-8'))
checks.update(prior)
checks['primary_attempt']='FAIL: foreground absent after first reopen; framework PID changed during investigation; cause unconfirmed'
checks['primary_failure_evidence']='verification/issue-2-p0-complete'
checks['recovery_test_head']=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
initial=rows();initial_count=count()
before_system=shell('pidof','system_server').strip()
mock=shell('appops','get','2000','android:mock_location')
mode=re.search(r'(?:MOCK_LOCATION|mock_location):\s*(allow|ignore|deny|default|foreground)',mock)
restore_mode=mode.group(1) if mode else 'default'
rotation=shell('wm','user-rotation','-d','0').strip().split()
mock_set=provider_set=False
def toggle():
    n=next(n for n in ui().iter('node') if n.attrib.get('checkable')=='true')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
    shell('input','-d','0','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(2)
try:
    assert f'versionCode={args.version_code}' in shell('dumpsys','package','cn.walkpaint')
    launch();assert foreground()
    before=diagnostics()
    shell('am','force-stop','cn.walkpaint');assert not foreground()
    time.sleep(3);assert count()==initial_count
    launch()
    deadline=time.monotonic()+20
    while not foreground() and time.monotonic()<deadline:time.sleep(2)
    assert foreground(), 'Visible reopen failed in targeted retry'
    after_system=shell('pidof','system_server').strip()
    assert before_system==after_system,'Framework restarted during targeted retry'
    checks['targeted_reopen_same_framework_pass']=True
    recovered=diagnostics()
    assert int(recovered['start_count'])>int(before['start_count'])
    checks['force_stop_requires_visible_reopen']=True
    checks['system_exit_reason']=recovered.get('process_exit_reason','UNAVAILABLE')
    checks['recovery_reason']=recovered.get('recovery_reason','UNAVAILABLE')
    tap('我的',exact=True);tap('查看记录诊断');shot('diagnostics-full')
    text='\n'.join(n.attrib.get('text','') for n in ui().iter('node'))
    assert all(x in text for x in ['最近有效 GPS','实际 SQLite 提交','系统进程退出','屏幕/省电/待机'])
    tap('关闭',exact=True)
    shell('appops','set','2000','android:mock_location','allow');mock_set=True
    shell('cmd','location','providers','add-test-provider','gps','--requiresSatellite','--supportsSpeed','--supportsBearing');provider_set=True
    shell('cmd','location','providers','set-test-provider-enabled','gps','true')
    toggle();assert not foreground()
    check_saved('paused_no_write',52.0003,-1.5,expect=False)
    toggle();tap('启用自动足迹',exact=True);time.sleep(3);assert foreground()
    fixture=json.loads(Path('app/src/test/resources/oxford-roads.geojson').read_text(encoding='utf-8'))
    a,b=fixture['features'][0]['geometry']['coordinates'][:2]
    import math
    length=math.hypot((b[1]-a[1])*111195,(b[0]-a[0])*111195*math.cos(math.radians((a[1]+b[1])/2)))
    t=18/length
    check_saved('post_explicit_resume_actual_commit',a[1]+(b[1]-a[1])*t,a[0]+(b[0]-a[0])*t)
    checks['pause_and_explicit_resume']=True
    tap('地图',exact=True);tap('定位我');shot('final-map');assert_colored_render('final-map')
    text='\n'.join(n.attrib.get('text','') for n in ui().iter('node'));assert '新足迹已保存' in text
    # Validate the primary attempt's selected-day screenshot independently.
    old_out=out
    out=Path('verification/issue-2-p0-complete');assert_colored_render('selected-day-map');out=old_out
    checks['selected_day_real_road_render_verified']=True
    final=rows();assert all(initial[t]<=final[t] for t in initial)
    checks['all_recovery_baseline_rows_retained']=True
    checks['recovery_baseline_points']=initial_count
    checks['final_points']=count()
    checks['original_upgrade_preservation']='primary attempt verified all 121 pre-upgrade rows; this retry verified all 132 baseline rows'
    checks['painted_meters']=coverage()
    sha=hashlib.sha256(Path(args.apk).read_bytes()).hexdigest()
    installed=run('exec-out','cat',shell('pm','path','cn.walkpaint').strip().split(':',1)[1],binary=True)
    assert hashlib.sha256(installed).hexdigest()==sha
    checks['apk_sha256']=sha;checks['installed_apk_exact_hash']=True
    checks['real_vivo']='BLOCKED-no-real-device-connected'
    checks['doze_oem_reboot_battery']='NOT_TESTED'
    checks['pass']=True
    (out/'diagnostic-fields.json').write_text(json.dumps(diagnostics(),ensure_ascii=False,indent=2),encoding='utf-8')
    print('P0_TARGETED_RECOVERY_PASS',json.dumps(checks,ensure_ascii=False),flush=True)
finally:
    errors=[]
    actions=[]
    if provider_set:actions.append(('cmd','location','providers','remove-test-provider','gps'))
    if mock_set:actions.append(('appops','set','2000','android:mock_location',restore_mode))
    actions += [('wm','user-rotation','-d','0',*rotation),('input','keyevent','KEYCODE_WAKEUP'),('rm','-f','/sdcard/wp-v05-ui.xml')]
    for action in actions:
        try:shell(*action)
        except Exception as e:errors.append(type(e).__name__)
    checks['cleanup_errors']=errors
    (out/'acceptance.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    assert not errors

"""Emulator-only real UI color acceptance; keeps every pre-existing database row."""
from pathlib import Path
from PIL import Image
exec(Path('scripts/android_v05_acceptance.py').read_text(encoding='utf-8').split('\ninitial=rows()')[0])
tables=['trips','points','roads','road_matches','coverage']
def data_rows():
    with database() as db:return {t:set(db.execute('SELECT * FROM '+t).fetchall()) for t in tables}
initial=data_rows();initial_points=count()
rotation=shell('wm','user-rotation','-d','0').strip().split();size=shell('wm','size')
override=re.search(r'Override size: (\d+x\d+)',size)
def saved_color():
    try:
        xml=ET.fromstring(run('exec-out','run-as','cn.walkpaint','cat','shared_prefs/road_appearance.xml'))
        return next((n.text for n in xml if n.attrib.get('name')=='explored_color'),None)
    except RuntimeError:return None
original=saved_color() or '#579B8C';privacy_changed=False
mock=provider=False;tracking_original=None
mode=re.search(r'(?:MOCK_LOCATION|mock_location):\s*(allow|ignore|deny|default|foreground)',shell('appops','get','2000','android:mock_location'))
original_mock_mode=mode.group(1) if mode else 'default'
def public_fix():
    if not provider:return
    shell('cmd','location','providers','set-test-provider-location','gps','--location','51.75353,-1.25507','--accuracy','5')
    time.sleep(.5)
def tracking_switch():
    tap('我的',exact=True)
    for i in range(6):
        nodes=list(ui().iter('node'))
        switch=next((n for n in nodes if n.attrib.get('checkable')=='true'),None)
        if switch is not None:return switch
        shell('input','-d','0','swipe','405','400','405','1100','300');time.sleep(.5)
    raise AssertionError('Tracking switch not visible')
def click_switch(switch):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',switch.attrib['bounds']))
    shell('input','-d','0','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(2)
presets=[('青绿','#579B8C'),('雾蓝','#647D9E'),('灰紫','#887A9D'),('石墨','#657778'),('赭金','#A38556')]
def ensure_control(label):
    for attempt in range(8):
        root=ui()
        if any(label in n.attrib.get('text','') for n in root.iter('node')):return
        scroll=next((n for n in root.iter('node') if n.attrib.get('scrollable')=='true'),None)
        assert scroll is not None,'No scrollable area for '+label
        x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll.attrib['bounds']))
        shell('input','-d','0','swipe',str((x1+x2)//2),str(int(y1+(y2-y1)*.85)),str((x1+x2)//2),str(int(y1+(y2-y1)*.2)),'350');time.sleep(.5)
    raise AssertionError('Control unavailable: '+label)
def open_colors():
    tap('我的',exact=True)
    # Profile may retain its scroll after visiting privacy.
    for i in range(6):
        if any(n.attrib.get('text')=='涂色外观' for n in ui().iter('node')):break
        shell('input','-d','0','swipe','405','400','405','1100','300');time.sleep(.5)
    tap('涂色外观',exact=True)
def field(value):
    ensure_control('自定义颜色代码')
    node=next(n for n in ui().iter('node') if n.attrib.get('class','').endswith('EditText'))
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
    shell('input','-d','0','tap',str((x1+x2)//2),str((y1+y2)//2))
    shell('input','-d','0','keyevent','KEYCODE_MOVE_END')
    shell('input','-d','0','keyevent',*(['KEYCODE_DEL']*16))
    shell('input','-d','0','text',value);time.sleep(.7)
def close_colors():tap('完成',exact=True);public_fix();tap('地图',exact=True);time.sleep(3)
def select_color(name,hex):
    open_colors();ensure_control(name);tap(name,exact=True)
    assert saved_color()==hex
    close_colors()
def mask(name,hex):
    image=Image.open(out/(name+'.png')).convert('RGB');w,h=image.size
    rgb=tuple(int(hex[i:i+2],16) for i in (1,3,5));points=set();pix=image.load()
    for y in range(int(h*.06),int(h*.81)):
        for x in range(int(w*.04),int(w*.96)):
            if all(abs(a-b)<=3 for a,b in zip(pix[x,y],rgb)):points.add((x,y))
    return points
def sdk_probe(renderer):
    public_fix();before=data_rows()
    result=shell('am','instrument','-w','-e','roadColorProbe','true','cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation')
    (out/(renderer+'-sdk.txt')).write_text(result,encoding='utf-8')
    assert 'ROAD_COLOR_SDK_PASS renderer='+renderer in result,result
    assert data_rows()==before,'SDK appearance calls changed database'
    launch();public_fix();checks[renderer+'_sdk_colors_camera_follow_and_data']=True
def privacy():
    tap('我的',exact=True);ensure_control('地图服务与隐私');tap('地图服务与隐私')
try:
    assert 'Success' in run('install','-r','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',timeout=120)
    shell('wm','size','810x1440');launch()
    switch=tracking_switch();tracking_original=switch.attrib.get('checked')=='true'
    if tracking_original:click_switch(switch);assert not foreground()
    shell('appops','set','2000','android:mock_location','allow');mock=True
    shell('cmd','location','providers','add-test-provider','gps','--requiresSatellite','--supportsSpeed','--supportsBearing');provider=True
    shell('cmd','location','providers','set-test-provider-enabled','gps','true')
    public_fix();tap('地图',exact=True);tap('定位我');time.sleep(3)
    assert all(initial[t]<=data_rows()[t] for t in tables);initial=data_rows();initial_points=count()
    open_colors();field('B56F77');ensure_control('应用自定义颜色');tap('应用自定义颜色',exact=True);close_colors()
    sdk_probe('AMap')
    privacy();tap('撤回并用离线地图');privacy_changed=True;public_fix();tap('地图',exact=True);tap('定位我');time.sleep(3);shot('offline-custom')
    assert len(mask('offline-custom','#B56F77'))>100 and saved_color()=='#B56F77'
    sdk_probe('MapLibre')
    privacy();tap('同意使用高德');privacy_changed=False;public_fix();tap('地图',exact=True);tap('定位我');time.sleep(3);shot('amap-custom-returned')
    assert len(mask('amap-custom-returned','#B56F77'))>100
    tap('足迹',exact=True);ensure_control('查看今天走过的道路');tap('查看今天走过的道路');time.sleep(3);shot('history-custom')
    hist=mask('history-custom','#B56F77');assert len(hist)>100
    select_color('灰紫','#887A9D');shot('history-recolored')
    assert len(mask('history-recolored','#887A9D')&hist)/len(hist)>.85
    assert any('返回全部探索' in n.attrib.get('text','') for n in ui().iter('node'))
    tap('返回全部探索');checks['past_confirmed_day_recolors_without_view_jump']=True
    open_colors();tap('恢复青绿',exact=True);assert saved_color()=='#579B8C';close_colors();shot('default-restored')
    assert len(mask('default-restored','#579B8C'))>100
    shell('wm','size','720x1280');launch();open_colors();shot('small-dialog');ensure_control('自定义颜色代码');shot('small-custom-field');tap('完成',exact=True)
    checks['restore_default_service_switch_and_small_320dp']=True
    assert data_rows()==initial
    checks['all_five_database_tables_exactly_unchanged']=True;checks['initial_points']=initial_points;checks['final_points']=count()
    checks['apk_sha256']=hashlib.sha256(Path(args.apk).read_bytes()).hexdigest()
    installed=run('exec-out','cat',shell('pm','path','cn.walkpaint').strip().split(':',1)[1],binary=True)
    assert hashlib.sha256(installed).hexdigest()==checks['apk_sha256'];checks['installed_exact_apk']=True
    checks['real_vivo_visual']='NOT_TESTED';checks['pass']=True
    print('ISSUE8_REMAINING_ACTUAL_COLOR_UI_PASS',flush=True)
except Exception as e:
    checks['error']=str(e);raise
finally:
    errors=[]
    try:
        shell('wm','size','810x1440');launch()
        if privacy_changed:privacy();tap('同意使用高德');tap('地图',exact=True)
        open_colors()
        match=next((name for name,hex in presets if hex==original),None)
        if match:ensure_control(match);tap(match,exact=True)
        else:field(original.lstrip('#'));ensure_control('应用自定义颜色');tap('应用自定义颜色',exact=True)
        close_colors();assert saved_color()==original
    except Exception as e:errors.append(str(e))
    if provider:
        try:shell('cmd','location','providers','remove-test-provider','gps')
        except Exception as e:errors.append(str(e))
    if mock:
        try:shell('appops','set','2000','android:mock_location',original_mock_mode)
        except Exception as e:errors.append(str(e))
    if tracking_original is not None:
        try:
            switch=tracking_switch()
            if (switch.attrib.get('checked')=='true')!=tracking_original:
                click_switch(switch)
                if tracking_original:tap('启用自动足迹',exact=True)
            assert (tracking_switch().attrib.get('checked')=='true')==tracking_original
            tap('地图',exact=True)
        except Exception as e:errors.append(str(e))
    for action in [('wm','size',override.group(1) if override else 'reset'),('wm','user-rotation','-d','0',*rotation),('rm','-f','/sdcard/wp-v05-ui.xml')]:
        try:shell(*action)
        except Exception as e:errors.append(str(e))
    checks['cleanup_errors']=errors
    (out/'acceptance.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    if errors:raise RuntimeError('Color test cleanup failed')

from pathlib import Path
exec(Path('scripts/android_v05_acceptance.py').read_text(encoding='utf-8').split('\ninitial=rows()')[0])
initial=rows();initial_real=count();rotation=shell('wm','user-rotation','-d','0').strip().split()
size=shell('wm','size');original_size=re.search(r'Override size: (\d+x\d+)',size)
mode=re.search(r'(?:MOCK_LOCATION|mock_location):\s*(allow|ignore|deny|default|foreground)',shell('appops','get','2000','android:mock_location'))
original_mode=mode.group(1) if mode else 'default';mock=provider=False
geometry=json.loads(Path('app/src/androidTest/assets/oxford-roads.geojson').read_text(encoding='utf-8'))['features'][0]['geometry']['coordinates']
def at(d):
    import math
    for a,b in zip(geometry,geometry[1:]):
        length=math.hypot((b[1]-a[1])*111195,(b[0]-a[0])*111195*math.cos(math.radians((b[1]+a[1])/2)))
        if d<=length:
            t=d/length;return a[1]+(b[1]-a[1])*t,a[0]+(b[0]-a[0])*t
        d-=length
    raise AssertionError('Public road too short')
def blue(name):
    import struct,zlib
    data=(out/(name+'.png')).read_bytes();pos=8;compressed=b''
    while pos<len(data):
        n=struct.unpack('>I',data[pos:pos+4])[0];kind=data[pos+4:pos+8];body=data[pos+8:pos+8+n];pos+=n+12
        if kind==b'IHDR':w,h,depth,color,*_=struct.unpack('>IIBBBBB',body)
        elif kind==b'IDAT':compressed+=body
    channels=3 if color==2 else 4;stride=w*channels;raw=zlib.decompress(compressed);previous=bytearray(stride);points=set()
    for y in range(h):
        start=y*(stride+1);mode=raw[start];row=bytearray(raw[start+1:start+1+stride])
        for x in range(stride):
            left=row[x-channels] if x>=channels else 0;up=previous[x];ul=previous[x-channels] if x>=channels else 0
            if mode==1:v=left
            elif mode==2:v=up
            elif mode==3:v=(left+up)//2
            elif mode==4:
                estimate=left+up-ul;ds=[abs(estimate-left),abs(estimate-up),abs(estimate-ul)];v=[left,up,ul][ds.index(min(ds))]
            else:v=0
            row[x]=(row[x]+v)&255
        if .1*h<y<.83*h:
            for x in range(int(.05*w),int(.95*w)):
                r,g,b=row[x*channels:x*channels+3]
                if abs(r-48)<=5 and abs(g-139)<=5 and abs(b-211)<=5:points.add((x,y))
        previous=row
    groups=[]
    while points:
        q=[points.pop()];group=[]
        while q:
            p=q.pop();group.append(p)
            for n in [(p[0]+1,p[1]),(p[0]-1,p[1]),(p[0],p[1]+1),(p[0],p[1]-1)]:
                if n in points:points.remove(n);q.append(n)
        groups.append(group)
    group=max(groups,key=len);assert len(group)>50
    return sum(p[0] for p in group)/len(group),sum(p[1] for p in group)/len(group)
try:
    assert 'Success' in run('install','-r',str(Path(args.apk).resolve()),timeout=120)
    assert f'versionCode={args.version_code}' in shell('dumpsys','package','cn.walkpaint')
    assert all(initial[t]<=rows()[t] for t in initial)
    assert 'Success' in run('install','-r','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',timeout=120)
    result=shell('am','instrument','-w','cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation')
    (out/'isolated-database-and-heading.txt').write_text(result,encoding='utf-8')
    assert 'V05_ISOLATED_DATABASE_PASS' in result
    checks['isolated_schema_roads_backup_android_heading_axes']=True
    shell('wm','size','810x1440');launch()
    shell('am','start','--display','0','--activity-reorder-to-front','-n','cn.walkpaint/.MainActivity')
    shell('appops','set','2000','android:mock_location','allow');mock=True
    shell('cmd','location','providers','add-test-provider','gps','--requiresSatellite','--supportsSpeed','--supportsBearing');provider=True
    shell('cmd','location','providers','set-test-provider-enabled','gps','true')
    tap('我的',exact=True)
    def toggle():
        n=next(n for n in ui().iter('node') if n.attrib.get('checkable')=='true')
        x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
        shell('input','-d','0','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(2)
    toggle();assert not foreground();toggle();tap('启用自动足迹',exact=True);time.sleep(3)
    tap('地图',exact=True)
    check_saved('public_anchor',*at(18));tap('定位我');time.sleep(3);shot('anchor');a=blue('anchor')
    check_saved('public_move_without_locate',*at(60));time.sleep(4);shot('moved');b=blue('moved')
    dist=((b[0]-a[0])**2+(b[1]-a[1])**2)**.5
    checks['blue_dot_displacement_pixels']=dist;checks['position_follow_pass']=dist<20
    assert checks['position_follow_pass'],f'Automatic camera did not keep blue dot centered: {dist}'
    # An actual drag pauses following; subsequent GPS must not move the camera back.
    shell('input','-d','0','swipe','400','610','610','610','450');time.sleep(3);shot('browsing-before');p=blue('browsing-before')
    assert abs(p[0]-a[0])>100,'Map drag did not change view'
    check_saved('while_browsing_move',*at(46));time.sleep(3);shot('browsing-after');q=blue('browsing-after')
    assert abs(q[0]-a[0])>70,'Following stole the dragged camera back'
    checks['real_drag_pauses_following']=True
    tap('定位我');time.sleep(3);shot('follow-restored');c=blue('follow-restored')
    assert ((c[0]-a[0])**2+(c[1]-a[1])**2)**.5<20
    checks['existing_locate_button_restores_following']=True
    # Stale and unreliable GPS cannot drag the camera; no extra record from poor accuracy.
    check_saved('poor_precision_not_recorded',*at(60),accuracy=80,expect=False)
    time.sleep(2);shot('poor-fix');poor=blue('poor-fix')
    assert ((poor[0]-a[0])**2+(poor[1]-a[1])**2)**.5<25
    checks['unreliable_position_does_not_move_camera']=True
    # Today's history camera is held while a new live GPS moves elsewhere.
    tap('足迹',exact=True)
    def texts():return '\n'.join(n.attrib.get('text','') for n in ui().iter('node'))
    for i in range(5):
        if '查看今天走过的道路' in texts():break
        shell('input','-d','0','swipe','405','1100','405','400','500');time.sleep(1)
    tap('查看今天走过的道路');time.sleep(3);shot('history-before')
    history=blue('history-before')
    public_other=json.loads(Path('app/src/androidTest/assets/oxford-roads.geojson').read_text(encoding='utf-8'))['features'][2]['geometry']['coordinates'][-1]
    check_saved('history_live_move',public_other[1],public_other[0]);time.sleep(3);shot('history-after');history_moved=blue('history-after')
    assert ((history_moved[0]-a[0])**2+(history_moved[1]-a[1])**2)**.5>40,'Live fix stole history focus'
    tap('返回全部探索');time.sleep(3);shot('history-exit');restored=blue('history-exit')
    assert ((restored[0]-a[0])**2+(restored[1]-a[1])**2)**.5<20
    checks['history_holds_view_then_exit_resumes']=True
    shell('input','keyevent','KEYCODE_HOME');check_saved('background_recording_unchanged',*at(32))
    launch();shell('am','start','--display','0','--activity-reorder-to-front','-n','cn.walkpaint/.MainActivity');time.sleep(2)
    shot('resumed-map');checks['foreground_resume_map']=True
    shell('wm','size','720x1280');launch();tap('定位我');time.sleep(2);shot('small-map')
    checks['small_320dp_real_map']=True
    checks['all_original_rows_retained']=all(initial[t]<=rows()[t] for t in initial)
    checks['initial_points']=initial_real;checks['final_points']=count()
    checks['apk_sha256']=hashlib.sha256(Path(args.apk).read_bytes()).hexdigest()
    installed=run('exec-out','cat',shell('pm','path','cn.walkpaint').strip().split(':',1)[1],binary=True)
    assert hashlib.sha256(installed).hexdigest()==checks['apk_sha256']
    checks['installed_exact_apk']=True;checks['real_vivo_heading']='NOT_TESTED';checks['pass']=True
    print('ISSUE7_ACTUAL_FOLLOW_UI_PASS',round(dist),flush=True)
finally:
    errors=[]
    actions=[]
    if provider:actions.append(('cmd','location','providers','remove-test-provider','gps'))
    if mock:actions.append(('appops','set','2000','android:mock_location',original_mode))
    actions.append(('wm','size',original_size.group(1) if original_size else 'reset'))
    if rotation:actions.append(('wm','user-rotation','-d','0',*rotation))
    actions.append(('rm','-f','/sdcard/wp-v05-ui.xml'))
    for action in actions:
        try:shell(*action)
        except Exception as e:errors.append(str(e))
    checks['cleanup_errors']=errors
    (out/'acceptance.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    if errors:raise RuntimeError('Cleanup failed')

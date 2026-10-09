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
                if abs(r-23)<=5 and abs(g-119)<=5 and abs(b-205)<=5:points.add((x,y))
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
def privacy():
    tap('我的',exact=True)
    for i in range(8):
        if any('地图服务与隐私' in n.attrib.get('text','') for n in ui().iter('node')):break
        shell('input','-d','0','swipe','405','1100','405','400','450');time.sleep(.5)
    tap('地图服务与隐私')
changed=False
try:
    shell('wm','size','810x1440');launch()
    privacy();tap('撤回并用离线地图');changed=True;tap('地图',exact=True);time.sleep(3)
    shell('appops','set','2000','android:mock_location','allow');mock=True
    shell('cmd','location','providers','add-test-provider','gps','--requiresSatellite','--supportsSpeed','--supportsBearing');provider=True
    shell('cmd','location','providers','set-test-provider-enabled','gps','true')
    check_saved('offline_anchor',*at(18));tap('定位我');time.sleep(2);shot('offline-anchor');a=blue('offline-anchor')
    check_saved('offline_move',*at(60));time.sleep(2);shot('offline-moved');b=blue('offline-moved')
    distance=lambda x,y:((x[0]-y[0])**2+(x[1]-y[1])**2)**.5
    checks['offline_blue_displacement_pixels']=distance(a,b);assert distance(a,b)<20
    shell('input','-d','0','swipe','400','610','610','610','450');time.sleep(2)
    check_saved('offline_browsing',*at(46));shot('offline-browsing');c=blue('offline-browsing')
    checks['offline_browsing_offset_pixels']=distance(a,c);assert distance(a,c)>70
    tap('定位我');time.sleep(2);shot('offline-restored');assert distance(a,blue('offline-restored'))<20
    checks['offline_follow_drag_recover']=True
    checks['original_rows_retained']=all(initial[t]<=rows()[t] for t in initial)
    assert checks['original_rows_retained'];checks['pass']=True
    print('ISSUE7_OFFLINE_FOLLOW_UI_PASS',flush=True)
finally:
    errors=[]
    if changed:
        try:privacy();tap('同意使用高德');tap('地图',exact=True);time.sleep(2);shot('amap-restored')
        except Exception as e:errors.append(str(e))
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

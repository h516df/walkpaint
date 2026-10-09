from pathlib import Path
exec(Path('scripts/android_v05_acceptance.py').read_text(encoding='utf-8').split('\ninitial=rows()')[0])
initial=rows();rotation=shell('wm','user-rotation','-d','0').strip().split()
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
    checks['reproduced_missing_follow']=dist>60
    assert checks['reproduced_missing_follow'],'Expected baseline v0.9 missing follow'
    checks['all_original_rows_retained']=all(initial[t]<=rows()[t] for t in initial)
    print('V09_MISSING_POSITION_FOLLOW_REPRODUCED',round(dist),flush=True)
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
    (out/'before.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    if errors:raise RuntimeError('Cleanup failed')

"""History replay on the dedicated Google AVD: the day's trail grows while it plays.

Runs against the signed ARM64 release package on emulator-5560. The release package is not
debuggable, so state is read as root instead of through run-as. Verifies the picture, not the code:
raw framebuffer frames are compared inside the map area, so the assertion is "more of the route is
drawn", not "the progress number changed".
"""
import json, os, re, struct, subprocess, time
from pathlib import Path
import xml.etree.ElementTree as ET
from google_avd_guard import require_walkpaint_google_avd
os.chdir(Path(__file__).resolve().parents[1])

ADB=r'D:\Apps\Android\sdk\platform-tools\adb.exe'
SERIAL='emulator-5560'
PACKAGE='cn.walkpaint'
DB='/data/data/%s/databases/footprints.db' % PACKAGE
# A day whose real (non-demo) track is long enough to see grow. Chosen from the fixture database.
DAY='2026-10-19'
DAY_CELL='19'
require_walkpaint_google_avd(ADB,SERIAL)
base=[ADB,'-s',SERIAL]
out=Path('verification/v064-replay');out.mkdir(parents=True,exist_ok=True)
checks={};cleanup=[]

def run(*args,binary=False,timeout=90):
    p=subprocess.run(base+list(args),capture_output=True,timeout=timeout)
    if p.returncode:raise RuntimeError((p.stdout+p.stderr).decode(errors='replace')[-500:])
    return p.stdout if binary else p.stdout.decode(errors='replace')
def sh(*args):return run('shell',*args)
def ui():
    sh('uiautomator','dump','--compressed','/sdcard/wp-replay-ui.xml')
    return ET.fromstring(run('exec-out','cat','/sdcard/wp-replay-ui.xml'))
def labels():return [n.get('text') for n in ui().iter('node') if n.get('text')]
def tap_node(n):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    sh('input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.7)
def tap(*texts):
    for n in ui().iter('node'):
        if any(t in [n.get('text'),n.get('content-desc')] for t in texts):
            tap_node(n);return
    raise AssertionError('Missing control '+str(texts)+'; visible='+str(labels()))
def has(text):return any(text in (v or '') for v in labels())
def node_center(*texts):
    for n in ui().iter('node'):
        if any(t in [n.get('text'),n.get('content-desc')] for t in texts):
            x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
            return ((x1+x2)//2,(y1+y2)//2)
    raise AssertionError('Missing control '+str(texts)+'; visible='+str(labels()))
def raw_tap(spot):
    sh('input','tap',str(spot[0]),str(spot[1]));time.sleep(.25)

# Raw RGBA_8888 framebuffer, so the check needs no image library and no extra dependency.
def frame():
    raw=run('exec-out','screencap',binary=True)
    w,h,fmt,space=struct.unpack('<IIII',raw[:16])
    assert fmt==1,'unexpected screencap format %d' % fmt
    return w,h,raw[16:16+w*h*4]
# The route is stroked with an opaque outline (UiTokens.TrailOutlineArgb 0xFF23312D). Counting
# those pixels measures how much line is on screen, which the veil and the camera cannot fake.
OUTLINE=(0x23,0x31,0x2D)
def outline_pixels(fr,box,tolerance=14):
    w,h,px=fr;x0,y0,x1,y1=box
    hits=[]
    for y in range(y0,y1):
        row=px[y*w*4+x0*4:y*w*4+x1*4]
        r=row[0::4];g=row[1::4];b=row[2::4]
        for i,(cr,cg,cb) in enumerate(zip(r,g,b)):
            if abs(cr-OUTLINE[0])<=tolerance and abs(cg-OUTLINE[1])<=tolerance and abs(cb-OUTLINE[2])<=tolerance:
                hits.append((x0+i,y))
    return hits
def tight_box(points,pad=8):
    xs=[p[0] for p in points];ys=[p[1] for p in points]
    return (max(0,min(xs)-pad),max(0,min(ys)-pad),max(xs)+pad,max(ys)+pad)
def inside(point,box):
    return box[0]<=point[0]<box[2] and box[1]<=point[1]<box[3]
def changed(before,after,box):
    w,h,pa=before;_,_,pb=after
    x0,y0,x1,y1=box;total=0
    for y in range(y0,y1):
        off=y*w*4
        a=pa[off+x0*4:off+x1*4];b=pb[off+x0*4:off+x1*4]
        total+=sum(1 for i in range(0,len(a),4)
                   if abs(a[i]-b[i])>12 or abs(a[i+1]-b[i+1])>12 or abs(a[i+2]-b[i+2])>12)
    return total
def shot(name):
    out.joinpath(name+'.png').write_bytes(run('exec-out','screencap','-p',binary=True))
def rows():
    query=('sqlite3 %s "select (select count(*) from trips),(select count(*) from points),'
        '(select count(*) from roads),(select count(*) from coverage),'
        '(select count(*) from road_matches);"' % DB)
    return tuple(int(v) for v in sh(query).strip().split('|'))
def progress():
    for text in labels():
        m=re.fullmatch(r'(\d+)%',text or '')
        if m:return int(m.group(1))
    return None

run('root');time.sleep(2);run('wait-for-device')
original=rows()
try:
    sh('am','force-stop',PACKAGE)
    sh('am','start','-n',PACKAGE+'/.MainActivity');time.sleep(4)
    if '同意并启用地图' in labels():raise AssertionError('map consent was reset; enable the map first')
    tap('足迹');tap(DAY_CELL)
    assert has(DAY),'day page for %s did not open: %s' % (DAY,labels())
    tap('在地图查看这天的足迹')
    assert has('回放'),'replay entry missing on the day card: %s' % labels()
    checks['replay_entry_on_day_card']=True
    shot('day-on-map')

    baseline=frame()
    mapbox=(40,400,880,1900)
    w,h,_=baseline
    assert (w,h)==(1080,2400),'unexpected screen %dx%d' % (w,h)
    checks['fixture_points_on_day']=sh('sqlite3 %s "select count(*) from points p join trips t '
        "on t.id=p.trip_id where t.demo=0 and date(t.started/1000,'unixepoch','localtime')='%s';\"" % (DB,DAY)).strip()

    # Two things make this emulator hard to sample: a screenshot costs ~0.8s and an "input tap" can
    # cost seconds, so a ramp cannot be built by tapping between frames. The animation is started
    # from a known state, the paused start is measured once, and frames are then grabbed back to
    # back WHILE it plays. Growth is proven by those frames; the two ends are pinned by the bar.
    tap('回放')
    time.sleep(6.5)                      # let a first pass finish: the toggle is then "start over"
    toggle=node_center('暂停','继续')
    assert has('继续'),'replay did not finish its first pass: %s' % labels()
    raw_tap(toggle)                      # 继续 = start over from the first point
    raw_tap(toggle)                      # and freeze it while it is still near the start
    start=progress()
    assert start is not None and start<=60,'the replay was not frozen near the start: %s' % start
    marks=[outline_pixels(frame(),mapbox)]
    shot('replay-paused-%03d' % start)
    raw_tap(toggle)                      # resume: the line is now grabbed while it grows
    grabbed=[frame() for _ in range(7)]
    time.sleep(3.5)                      # one pass lasts about 5.4 seconds
    end=progress()
    assert end==100,'replay never reached the end: %s' % end
    grabbed.append(frame())
    marks+=[outline_pixels(f,mapbox) for f in grabbed]
    checks['replay_bar_shown']=True
    checks['sampled_percentages']=[start,100]
    # The corridor is where the line ended up: outline pixels the last frame has and the first does
    # not. The frames captured in between still have to fall in order, so the ramp cannot pass by
    # construction.
    box=tight_box([p for p in marks[-1] if p not in set(marks[0])])
    counts=[len([p for p in m if inside(p,box)]) for m in marks]
    checks['route_corridor_box']=list(box)
    checks['drawn_route_pixels']=counts
    # Points recorded while standing still add no length, so a plateau is normal; the line may never
    # go backwards, and a one-shot reveal would show a single value instead of a ramp.
    assert counts[-1]-counts[0]>=300,'the drawn route barely grew (%s)' % counts
    assert all(b>=a-15 for a,b in zip(counts,counts[1:])),'the drawn route shrank while playing (%s)' % counts
    assert len(set(counts))>=3,'the drawn route appeared in one step, not as a ramp (%s)' % counts
    checks['route_grows_while_playing']=True
    checks['replay_reaches_full_route']=True

    # Pause must freeze the picture, not just the number.
    raw_tap(toggle)                      # at the end this starts a fresh pass
    raw_tap(toggle)                      # and this freezes it again
    frozen_at=progress()
    assert frozen_at is not None and frozen_at<100,'the replay was not frozen: %s' % frozen_at
    first=frame();time.sleep(1.8);second=frame()
    frozen=changed(first,second,mapbox)
    checks['paused_frozen_pixels']=frozen
    assert frozen<=200,'the map kept changing while paused (%d pixels)' % frozen
    assert progress()==frozen_at,'progress moved while paused: %s -> %s' % (frozen_at,progress())
    checks['pause_freezes_replay']=True

    # 继续 at the end starts over instead of standing still, and draws less than the finished route.
    raw_tap(toggle);time.sleep(6.5)      # play the frozen pass out to the end
    assert progress()==100,'replay did not reach the end again: %s' % progress()
    raw_tap(toggle);time.sleep(.6)       # 继续 at the end = start over
    restarted=len([p for p in outline_pixels(frame(),mapbox) if inside(p,box)])
    checks['restarted_route_pixels']=restarted
    assert restarted<counts[-1],'replay did not start over (%d vs %d)' % (restarted,counts[-1])
    checks['replay_restarts_after_finishing']=True

    tap('关闭');time.sleep(1)
    assert has('回放') and not has('关闭'),'closing replay did not restore the day card: %s' % labels()
    checks['close_restores_day_card']=True
    shot('replay-closed')
    assert rows()==original,'replay wrote to the database: %s -> %s' % (original,rows())
    checks['replay_changes_no_database_rows']=True
except Exception as exc:
    checks['failure']=str(exc)
    try:shot('failure')
    except Exception:pass
    raise
finally:
    def restore(name,fn):
        try:fn()
        except Exception as e:cleanup.append(name+': '+str(e))
    restore('stop app',lambda:sh('am','force-stop',PACKAGE))
    restore('temporary UI',lambda:sh('rm','-f','/sdcard/wp-replay-ui.xml'))
    checks['database_rows_after']=rows()
    checks['cleanup_errors']=cleanup
    out.joinpath('acceptance.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(checks,ensure_ascii=True,indent=2),flush=True)

#!/usr/bin/env python3
"""Read back emulator cleanup and capture the actually visible GNSS diagnostic fields."""
from pathlib import Path
exec(Path('scripts/android_v05_acceptance.py').read_text(encoding='utf-8').split('\ninitial=rows()')[0])
rotation=shell('wm','user-rotation','-d','0').strip().split()
size_state=shell('wm','size')
try:
    launch()
    shell('am','start','--display','0','--activity-reorder-to-front','-n','cn.walkpaint/.MainActivity')
    tap('我的',exact=True)
    def scroll(up=False,half=False):
        n=next(n for n in ui().iter('node') if n.attrib.get('scrollable')=='true')
        x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
        a,b=(y1+70,y2-70) if up else (y2-70,y1+70)
        if half:b=(a+b)//2
        shell('input','-d','0','swipe',str((x1+x2)//2),str(a),str((x1+x2)//2),str(b),'500');time.sleep(1)
    def texts():return '\n'.join(n.attrib.get('text','') for n in ui().iter('node'))
    for i in range(6):
        if '记录与道路诊断' in texts():break
        scroll()
    tap('记录与道路诊断')
    time.sleep(2)
    # One Text node includes offscreen text, so screenshot its scrolled position explicitly.
    scroll(half=True);shot('gps-gnss-visible')
    scroll(half=True);shot('gps-gnss-visible-lower')
    tap('关闭',exact=True);tap('地图',exact=True)
    perms=shell('dumpsys','package','cn.walkpaint')
    checks['permissions_after']={k:bool(re.search(k+r': granted=true',perms)) for k in ['ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','ACCESS_BACKGROUND_LOCATION']}
    checks['installed_foreground_service']=foreground()
    checks['mock_shell_default']='default' in shell('appops','get','2000','android:mock_location')
    values=diagnostics()
    checks['session_counts']={k:int(values.get(k,'0')) for k in ['session_gps_count','session_valid_gps_count','session_saved_count','session_filtered_count']}
    checks['gnss_is_current_or_not_observed']=int(values.get('gnss_at','0'))==0 or int(values['gnss_at'])>=int(values['service_start_at'])
    with database() as db:checks['schema2']=db.execute('PRAGMA user_version').fetchone()[0]==2
    checks['pass']=checks['installed_foreground_service'] and checks['mock_shell_default'] and checks['gnss_is_current_or_not_observed'] and checks['schema2']
    assert checks['pass']
finally:
    if len(rotation)>=2:shell('wm','user-rotation','-d','0',*rotation)
    shell('rm','-f','/sdcard/wp-v05-ui.xml')
    checks['viewport_unchanged']=shell('wm','size')==size_state
    (out/'audit.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')

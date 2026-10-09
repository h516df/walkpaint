from pathlib import Path
exec(Path('scripts/android_v05_acceptance.py').read_text(encoding='utf-8').split('\ninitial=rows()')[0])
tables=['trips','points','roads','road_matches','coverage']
with database() as db:before={t:set(db.execute('SELECT * FROM '+t).fetchall()) for t in tables}
assert 'Success' in run('install','-r','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',timeout=120)
result=shell('am','instrument','-w','-e','cameraProbe','true','cn.walkpaint.test/cn.walkpaint.RoadAcceptanceInstrumentation')
(out/'camera-probe.txt').write_text(result,encoding='utf-8')
with database() as db:after={t:set(db.execute('SELECT * FROM '+t).fetchall()) for t in tables}
checks['all_database_rows_unchanged']=all(before[t]==after[t] for t in tables)
checks['public_sdk_bearing_zoom_and_activity_sensor_lifecycle']='PUBLIC_CAMERA_BEARING_ZOOM_SENSOR_LIFETIME_PASS' in result
checks['real_phone_heading']='NOT_TESTED'
checks['apk_sha256']=hashlib.sha256(Path(args.apk).read_bytes()).hexdigest()
checks['pass']=checks['all_database_rows_unchanged'] and checks['public_sdk_bearing_zoom_and_activity_sensor_lifecycle']
(out/'acceptance.json').write_text(json.dumps(checks,indent=2),encoding='utf-8')
print(result,flush=True)
assert checks['pass']
launch()
shell('rm','-f','/sdcard/wp-v05-ui.xml')

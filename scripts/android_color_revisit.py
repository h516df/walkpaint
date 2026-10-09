from pathlib import Path
exec(Path('scripts/android_follow_acceptance.py').read_text(encoding='utf-8').split('\ntry:\n')[0])
before_meters=coverage();before_rows=rows();before_points=count()
try:
    launch();assert foreground(),'Revisit test requires previously enabled recording'
    shell('appops','set','2000','android:mock_location','allow');mock=True
    shell('cmd','location','providers','add-test-provider','gps','--requiresSatellite','--supportsSpeed','--supportsBearing');provider=True
    shell('cmd','location','providers','set-test-provider-enabled','gps','true')
    for index,distance in enumerate([18,38,60,38,18]):
        check_saved('public_revisit_'+str(index),*at(distance));time.sleep(3)
    after_meters=coverage()
    checks['coverage_before_meters']=before_meters;checks['coverage_after_meters']=after_meters
    assert abs(after_meters-before_meters)<.01,'Revisiting already painted road increased coverage'
    with database() as db:
        matched=db.execute('SELECT COUNT(*) FROM road_matches WHERE matched=1').fetchone()[0]
    checks['deduplicated_forward_and_reverse_walk']=True
    checks['original_rows_retained']=all(before_rows[t]<=rows()[t] for t in before_rows)
    assert checks['original_rows_retained'];checks['initial_points']=before_points;checks['final_points']=count();checks['matched_total']=matched
    checks['apk_sha256']=hashlib.sha256(Path(args.apk).read_bytes()).hexdigest();checks['pass']=True
    print('ACTUAL_PUBLIC_FORWARD_REVERSE_DEDUP_PASS',round(before_meters,2),round(after_meters,2),flush=True)
except Exception as e:checks['error']=str(e);raise
finally:
    errors=[]
    actions=[]
    if provider:actions.append(('cmd','location','providers','remove-test-provider','gps'))
    if mock:actions.append(('appops','set','2000','android:mock_location',original_mode))
    for action in actions:
        try:shell(*action)
        except Exception as e:errors.append(str(e))
    checks['cleanup_errors']=errors
    (out/'acceptance.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    if errors:raise RuntimeError('Revisit cleanup failed')

from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1];task=root/'verification'
provided_apk=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'app/build/outputs/apk/debug/app-debug.apk'
sys.path.insert(0,str(root/'scripts'));sys.argv=[__file__];__file__=str(root/'scripts/android_profile_acceptance.py')
exec(compile((root/'scripts/android_profile_acceptance.py').read_text(encoding='utf-8').split('\npermissions=')[0],__file__,'exec'))
out=task/'feedback-google';out.mkdir(exist_ok=True)
original=rows();original_history=None
try:original_history=run('exec-out','run-as','cn.walkpaint','cat','shared_prefs/feedback_history.xml',binary=True)
except RuntimeError:pass
def scroll():
    n=next(n for n in ui().iter('node') if n.get('scrollable')=='true')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    sh('input','swipe',str((x1+x2)//2),str(y2-70),str((x1+x2)//2),str(y1+80),'350');time.sleep(1)
def ensure(text):
    for _ in range(8):
        if text in labels():tap(text);return
        scroll()
    raise AssertionError('Missing '+text)
def history_switch():return next(n for n in ui().iter('node') if n.get('content-desc')=='留存故障线索')
try:
    assert 'Success' in run('install','-r',str(provided_apk),timeout=150)
    checks['apk_sha256']=hashlib.sha256(provided_apk.read_bytes()).hexdigest()
    remote=sh('pm','path','cn.walkpaint').split('package:')[1].strip()
    checks['installed_apk_matches']=hashlib.sha256(run('exec-out','cat',remote,binary=True)).hexdigest()==checks['apk_sha256']
    assert checks['installed_apk_matches']
    checks['source_sha']=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root).decode().strip()
    launch();tap('我的');tap('更多设置');tap('隐私与权限');tap('地图服务与隐私');back()
    tap('问题反馈');shot('feedback-form')
    assert all(t in labels() for t in ['问题反馈','定位','道路涂色','地图显示','权限和后台','闪退','其他'])
    tap('权限和后台')
    field=next(n for n in ui().iter('node') if n.get('class')=='android.widget.EditText');tap_node(field)
    sh('input','text','Recording%sstops%safter%sscreen%soff');back()
    tap('预览反馈报告');time.sleep(3);shot('feedback-preview')
    visible='\n'.join(labels());assert '报告预览' in visible and 'Recording stops after screen off' in visible
    assert '问题类型：权限和后台' in visible
    checks['actual_report_category_description']=True
    tap('复制报告');time.sleep(1);shot('feedback-copied');checks['copy_action']=True
    tap('选择分享目标');shot('share-chooser');checks['system_share_chooser']=any('Sharing' in t or '分享' in t or 'Nearby' in t or 'Quick Share' in t for t in labels())
    assert checks['system_share_chooser'],labels();back();back()
    ensure('清空故障线索');shot('clear-history-confirm');assert any('足迹、行程、道路涂色和备份全部保留' in t for t in labels())
    tap('取消');ensure('清空故障线索');tap('确认清空线索');time.sleep(3)
    assert original==rows();checks['clear_keeps_database_rows']=True
    sh('am','force-stop','cn.walkpaint');launch();tap('我的');tap('更多设置');tap('问题反馈')
    assert '暂无线索，可填写问题并预览报告。' in labels();checks['clear_persisted_after_restart']=True
    tap_node(history_switch());time.sleep(3);sh('am','force-stop','cn.walkpaint');launch();tap('我的');tap('更多设置');tap('问题反馈')
    assert history_switch().get('checked')=='false';checks['disable_persisted_after_restart']=True
    assert original==rows();checks['disable_keeps_database_rows']=True;shot('feedback-collection-disabled')
finally:
    sh('am','force-stop','cn.walkpaint')
    if original_history is not None:
        restore=out/'restore-history.xml';restore.write_bytes(original_history)
        run('push',str(restore),'/data/local/tmp/wp-feedback-restore.xml')
        sh('run-as','cn.walkpaint','cp','/data/local/tmp/wp-feedback-restore.xml','shared_prefs/feedback_history.xml')
        sh('rm','-f','/data/local/tmp/wp-feedback-restore.xml');restore.unlink()
    else:sh('run-as','cn.walkpaint','rm','-f','shared_prefs/feedback_history.xml')
    sh('rm','-f','/sdcard/wp-profile-ui.xml')
    out.joinpath('acceptance.json').write_text(json.dumps(checks,indent=2),encoding='utf-8')
print(json.dumps(checks))

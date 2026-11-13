"""Black-box optimized-QA smoke checks. Never targets a phone or a normal app package."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess
import time
import traceback
import xml.etree.ElementTree as ET

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['adb','aapt2','serial','avd','qa-base-apk','qa-perf-apk','output']:
        p.add_argument('--'+name,required=True)
    a=p.parse_args();out=Path(a.output);out.mkdir(parents=True,exist_ok=True)
    package='com.chimafok.todo.qa'
    def adb(*args):
        r=subprocess.run([a.adb,'-s',a.serial,*args],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=60)
        if r.returncode:raise RuntimeError(r.stderr.strip() or r.stdout.strip())
        return r.stdout
    assert adb('emu','avd','name').splitlines()[0].strip()==a.avd,'Not the explicitly owned test emulator'
    assert adb('shell','getprop','sys.boot_completed').strip()=='1','Emulator not ready'
    launch={}
    for key in ['qa_base_apk','qa_perf_apk']:
        apk=getattr(a,key)
        text=subprocess.check_output([a.aapt2,'dump','badging',apk],text=True,encoding='utf-8')
        assert "name='"+package+"'" in text,'Only .qa packages are allowed'
        assert ('application-debuggable' in text)==(key=='qa_base_apk')
        launch[key]=re.search(r"launchable-activity: name='([^']+)'",text)[1]
    def probe(method,arg=None):
        cmd=['shell','content','call','--uri','content://'+package+'.system-test','--method',method]
        if arg is not None:cmd+=['--arg',str(arg)]
        text=adb(*cmd)
        # Android 8 may print a still-parcelled empty Bundle on a cold call.
        # Read back the result without repeating a mutating operation.
        for _ in range(4):
            marker=text.find('evidence=')
            if marker>=0:return json.JSONDecoder().raw_decode(text[marker+len('evidence='):])[0]
            time.sleep(.3)
            text=adb('shell','content','call','--uri','content://'+package+'.system-test','--method','inspect')
        raise AssertionError(text)
    def identity():
        text=adb('shell','dumpsys','package',package)
        return dict(uid=re.search(r'userId=(\d+)',text)[1],firstInstallTime=re.search(r'firstInstallTime=([^\r\n]+)',text)[1].strip())
    def start(variant='qa_perf_apk'):
        adb('shell','am','force-stop',package)
        result=adb('shell','am','start','-W','-n',package+'/'+launch[variant])
        assert 'Error' not in result,result
        time.sleep(1)
    def ui():
        last=''
        for _ in range(4):
            adb('shell','uiautomator','dump','/data/local/tmp/todo-perf-qa.xml')
            last=adb('exec-out','cat','/data/local/tmp/todo-perf-qa.xml')
            if '<hierarchy' in last:
                tree=ET.fromstring(last)
                if any(n.get('package')==package for n in tree.iter('node')):return tree
            time.sleep(.5)
        raise AssertionError('Optimized QA UI did not become active')
    def find(tree,labels,contains=False):
        nodes=[n for n in tree.iter('node') if n.get('package')==package]
        # A completion checkbox's speech label contains the task title as well.
        # Prefer visible exact text before considering broader speech labels.
        for attr in ['text','content-desc']:
            exact=next((n for n in nodes if n.get(attr,'') in labels),None)
            if exact is not None:return exact
        if contains:
            for attr in ['text','content-desc']:
                match=next((n for n in nodes if any(label in n.get(attr,'') for label in labels)),None)
                if match is not None:return match
        return None
    def tap(node):
        assert node is not None,'Expected QA control missing'
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
        adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.4)
    def search(text):
        tree=ui();tap(find(tree,['Search tasks','搜尋任務','搜索任务']))
        adb('shell','input','text',text.replace(' ','%s'));time.sleep(1)
        adb('shell','input','keyevent','KEYCODE_BACK');time.sleep(.3)
    dimensions=list(map(int,re.search(r'(\d+)x(\d+)',adb('shell','wm','size')).groups()))
    def swipe(up=True):
        width,height=dimensions;x=width//2;top=int(height*.45);bottom=int(height*.82)
        adb('shell','input','swipe',str(x),str(bottom if up else top),str(x),str(top if up else bottom),'280');time.sleep(.2)
    facts={};before={};cases=[];began=datetime.now(timezone.utc).isoformat()
    def run(name,action,dependent=True):
        start_time=time.monotonic();case={'name':name}
        if dependent and any(c.get('failure') for c in cases):case['skipped']='Earlier dependent check failed'
        else:
            try:action()
            except Exception:case['failure']=traceback.format_exc()
        case['seconds']=time.monotonic()-start_time;cases.append(case)
    def seed():
        assert 'Success' in adb('install','-r',a.qa_base_apk)
        start('qa_base_apk');probe('inspect')
        assert probe('seed-perf',20)['perfTaskCount']==20
        before.update(identity())
    def update():
        assert 'Success' in adb('install','-r',a.qa_perf_apk)
        assert identity()==before,'Installation identity changed'
        start()
        assert probe('inspect')['perfTaskCount']==20,'Synthetic tasks were not retained'
        facts['identityRetained']=True;facts['syntheticTasksRetained']=20
        search('QA Perf')
        assert find(ui(),['QA Perf'],True) is not None
    def details_scroll():
        tree=ui();tap(find(tree,['QA Perf 00'],True))
        assert find(ui(),['Synthetic performance note 0'],True) is not None
        tap(find(ui(),['QA Perf 00'],True))
        assert find(ui(),['Synthetic performance note 0'],True) is None
        for _ in range(5):swipe()
        assert find(ui(),['QA Perf'],True) is not None
        for _ in range(5):swipe(False)
        assert find(ui(),['QA Perf 00'],True) is not None
    def edit_restart():
        tap(find(ui(),['QA Perf 00'],True))
        for _ in range(4):
            tree=ui();edit=find(tree,['Edit','編輯','编辑'])
            if edit is not None:break
            swipe()
        tap(edit);tree=ui()
        title=next((n for n in tree.iter('node') if n.get('package')==package and n.get('class')=='android.widget.EditText' and n.get('text')=='QA Perf 00'),None)
        tap(title);adb('shell','input','keyevent','KEYCODE_MOVE_END');adb('shell','input','text','_R8')
        adb('shell','input','keyevent','KEYCODE_BACK');time.sleep(.4)
        tap(find(ui(),['Save','儲存','保存']))
        start();search('QA Perf 00_R8')
        assert find(ui(),['QA Perf 00_R8'],True) is not None,'Optimized Room edit did not persist'
        facts['editedTitleRetainedAfterRestart']=True
    def cleanup():
        assert probe('clear-perf')['perfTaskCount']==0
        adb('shell','am','force-stop',package)
        facts['fixtureRemoved']=True
    run('seedSyntheticTasksInRegularQa',seed)
    run('optimizedUpdateRetainsIdentityAndRoomData',update)
    run('detailsAndVerticalScrollingRemainUsable',details_scroll)
    run('editedTaskPersistsAcrossProcessRestart',edit_restart)
    run('removeOnlySyntheticPerformanceFixture',cleanup,False)
    root=ET.Element('testsuite',name='OptimizedQaSmoke',tests=str(len(cases)),failures=str(sum('failure' in c for c in cases)),errors='0',skipped=str(sum('skipped' in c for c in cases)),timestamp=began)
    for c in cases:
        node=ET.SubElement(root,'testcase',classname='OptimizedQaSmoke',name=c['name'],time=str(c['seconds']))
        for flag in ['failure','skipped']:
            if flag in c:ET.SubElement(node,flag).text=c[flag]
    ET.ElementTree(root).write(out/'TEST-optimized-qa-smoke.xml',encoding='utf-8',xml_declaration=True)
    result=dict(started=began,api=int(adb('shell','getprop','ro.build.version.sdk').strip()),targetPackage=package,kind='ADB black-box smoke of optimized QA APK, not connected instrumentation or FPS benchmarking',cases=cases,facts=facts)
    (out/'result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result))
    if any('failure' in c or 'skipped' in c for c in cases):raise SystemExit(1)

if __name__=='__main__':main()

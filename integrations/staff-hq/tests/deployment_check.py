#!/usr/bin/env python3
"""Exercise the exact root overlay and deployment checker with PHP and real local HTTP probes."""
from pathlib import Path
import argparse,base64,hashlib,json,os,re,socket,subprocess,tempfile,time,urllib.request,zipfile

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--php',type=Path,required=True)
p.add_argument('--archive',type=Path,default=ROOT/'releases/TownyReborn-StaffHQ-5.2.1-MERGED-DIRECT-UPLOAD.zip')
p.add_argument('--active-archive',type=Path,help='Optional private current-HQ ZIP; only allowlisted public files are read')
a=p.parse_args();checks=[]
def check(name,value):
    if not value:raise AssertionError(name)
    checks.append(name);print('PASS',name,flush=True)

with tempfile.TemporaryDirectory(prefix='hq-deployment-check-') as temp:
    work=Path(temp);web=work/'web';web.mkdir()
    with zipfile.ZipFile(a.archive) as archive:
        archive.extractall(web)
        check('API is directly at the archive root','api.php' in archive.namelist() and not any(n.startswith('WEBSPACE-UPDATE/') for n in archive.namelist()))
        check('No production data in root overlay',not any(n in archive.namelist() for n in ('config/config.php','config/notifications.php','storage/','pending-events.jsonl')))
        with zipfile.ZipFile(ROOT/'releases/TownyReborn-StaffHQ-5.2.1-API9.zip') as original:
            preserved={'.htaccess','assets/app.css','media.php','index.php','app/bootstrap.php'}
            check('Tested API and core retained outside the documented PDF restore',all(archive.read(n.removeprefix('WEBSPACE-UPDATE/'))==original.read(n) for n in original.namelist() if n.startswith('WEBSPACE-UPDATE/') and n.removeprefix('WEBSPACE-UPDATE/') not in preserved))
        if a.active_archive:
            with zipfile.ZipFile(a.active_archive) as active:
                for name in ('.htaccess','assets/app.css','media.php'):
                    check('Active PDF file preserved exactly: '+name,archive.read(name)==active.read(name))
                journey="    case 'journey': showJourneyInsights(); break;\n"
                check('Active index retained with only the Journey route added',archive.read('index.php').decode().replace(journey,'',1)==active.read('index.php').decode())
                boot=archive.read('app/bootstrap.php').decode()
                before=active.read('app/bootstrap.php').decode()
                # The active bootstrap only changed CSS cache versions; new Journey/heartbeat includes are retained.
                for version in ('5.1.1','5.1.2-pdf','5.2.1','5.2.1-pdf'):
                    boot=boot.replace('?v='+version+'"','?v=CACHE"');before=before.replace('?v='+version+'"','?v=CACHE"')
                boot=boot.replace("require_once APP_ROOT . '/app/connection-health.php';\n",'').replace("require_once APP_ROOT . '/app/journey.php';\n",'')
                check('Active bootstrap retained apart from cache and new module includes',boot==before)
                check('Input archive demonstrates the root cause',b'X-TR-API-Version: 7' in active.read('api.php') and b'STAFF_HQ_API_VERSION = 9' in active.read('WEBSPACE-UPDATE/api.php'))
    for file in sorted(web.rglob('*.php')):
        result=subprocess.run([str(a.php),'-n','-l',str(file)],capture_output=True,text=True)
        if result.returncode:raise AssertionError(str(file)+result.stdout+result.stderr)
    check('All merged PHP files pass syntax checks',True)
    (web/'config/config.php').write_text("<?php throw new RuntimeException('PRIVATE CONFIG MUST NOT LOAD');")
    (web/'storage').mkdir(exist_ok=True);(web/'storage/private-token.txt').write_text('PRIVATE TOKEN MUST NOT BE READ')
    phase=work/'phase.json';phase.write_text(json.dumps({'get':9,'post':9,'journey':True,'status':200}))
    router=work/'router.php'
    router.write_text("""<?php
if(parse_url($_SERVER['REQUEST_URI'],PHP_URL_PATH)!=='/api.php')return false;
$p=json_decode(file_get_contents(base64_decode('%s')),true);
$post=$_SERVER['REQUEST_METHOD']==='POST';$v=$p[$post?'post':'get'];
http_response_code($post?401:$p['status']);header('X-TR-API-Version: '.$v);header('Content-Type: application/json');
if(!empty($p['html'])){echo '<html>wrong route</html>';exit;}
$b=$post?['error'=>'unauthorized']:['status'=>'ok','service'=>'Towny Reborn Staff API','version'=>$v];
if($v>=9){$b['api_version']=$v;$b['capabilities']=['journey'=>$p['journey']];}
echo json_encode($b);
"""%base64.b64encode(str(phase).encode()).decode())
    with socket.socket() as probe:probe.bind(('127.0.0.1',0));port=probe.getsockname()[1]
    url=f'http://127.0.0.1:{port}/';log=(work/'php.log').open('w')
    server=subprocess.Popen([str(a.php),'-n','-S',f'127.0.0.1:{port}','-t',str(web),str(router)],stdout=log,stderr=log)
    try:
        def request(suffix):
            with urllib.request.urlopen(url+suffix,timeout=5) as response:return response.read().decode(),dict(response.headers)
        for _ in range(50):
            try:body,headers=request('tr-update-check-5-2-1.php?format=json');break
            except Exception:time.sleep(.05)
        report=json.loads(body)
        check('Complete upload verified without loading private config',report['files_match'] is True and report['api_on_disk']==9 and report['checked_files']>30)
        html,headers=request('tr-update-check-5-2-1.php');script=re.search(r'<script nonce="([^"]+)">([\s\S]*?)</script>',html)
        check('Page nonce is permitted by its CSP',script is not None and "'nonce-"+script[1]+"'" in headers['Content-Security-Policy'])
        check('No absolute host paths or private data disclosed',temp not in html and 'PRIVATE TOKEN' not in html and 'PRIVATE CONFIG' not in html)
        js=work/'client.js';js.write_text(script[2])
        runner=work/'run.cjs';runner.write_text(r"""
const fs=require('fs'),vm=require('vm');
(async()=>{
 const elements=new Map(),calls=[];
 const document={getElementById(id){if(!elements.has(id))elements.set(id,{textContent:'',className:'',disabled:false,addEventListener(){}});return elements.get(id);}};
 for(const id of ['advice','retry','get','post','journey','live'])document.getElementById(id);
 const hostname=process.argv[4]||'staff.townyreborn.com';
 const context=vm.createContext({document,location:{hostname},fetch:(target,options)=>{calls.push({target,options});return fetch(new URL(target,process.argv[2]),options);}});
 const source=fs.readFileSync(process.argv[3],'utf8').replace(/check\(\);\s*$/,'globalThis.finished=check();');
 vm.runInContext(source,context);await context.finished;
 if(hostname==='staff.townyreborn.com'){
  if(calls.length!==2||calls.some(c=>c.options.credentials!=='omit'||'Authorization' in c.options.headers||!c.target.startsWith('/api.php?')))throw new Error('Probe must stay anonymous and target the connector root');
  if(calls.filter(c=>c.options.method==='POST').length!==1)throw new Error('Expected one anonymous POST');
 }else if(calls.length)throw new Error('Another domain must not be probed');
 console.log(JSON.stringify({advice:elements.get('advice').textContent,get:elements.get('get').textContent,post:elements.get('post').textContent,live:elements.get('live').className}));
})().catch(error=>{console.error(error);process.exit(1)});
""")
        def client(label,state,expected,expected_class='card warn',source=js,host='staff.townyreborn.com'):
            phase.write_text(json.dumps(state))
            result=subprocess.run([os.environ.get('CODEX_PRIMARY_RUNTIME_NODE','node'),str(runner),url,str(source),host],capture_output=True,text=True)
            if result.returncode:raise RuntimeError(result.stderr)
            output=json.loads(result.stdout);check(label,expected in output['advice'] and output['live']==expected_class)
            return output
        ready={'get':9,'post':9,'journey':True,'status':200}
        result=client('API 9 GET and anonymous POST allow immediate connector retry',ready,'/trstaff reload','card ok')
        check('Anonymous POST 401 is presented as expected','HTTP 401' in result['post'])
        client('Old executed API with new files identifies PHP cache',{'get':7,'post':7,'journey':True,'status':200},'Restart PHP-FPM')
        client('Mixed GET/POST versions identify routing or worker mismatch',{'get':9,'post':7,'journey':True,'status':200},'different API versions')
        client('Missing Journey support does not show ready',{'get':9,'post':9,'journey':False,'status':200},'Journey is unavailable')
        client('API storage failure does not show ready',{'get':9,'post':9,'journey':True,'status':503},'not healthy')
        client('HTML instead of API JSON is diagnosed safely',{**ready,'html':True},'did not return readable JSON')
        client('Another domain is not probed',ready,'another domain','card warn',js,'townyreborn.com')
        nested=web/'nested';nested.mkdir()
        with zipfile.ZipFile(a.archive) as archive:archive.extractall(nested)
        nested_html=request('nested/tr-update-check-5-2-1.php')[0];nested_js=work/'nested-client.js';nested_js.write_text(re.search(r'<script nonce="[^"]+">([\s\S]*?)</script>',nested_html)[1])
        client('A complete upload in the wrong subfolder is diagnosed',{'get':7,'post':7,'journey':True,'status':200},'inside a subfolder','card warn',nested_js)
        api=(web/'api.php').read_bytes();(web/'api.php').write_text("<?php header('X-TR-API-Version: 7');")
        changed=json.loads(request('tr-update-check-5-2-1.php?format=json')[0])
        check('Skipped API upload is distinguished from stale PHP cache',changed['api_on_disk']==7 and changed['files_match'] is False and 'api.php' in changed['changed_files'])
        altered_script=work/'altered-client.js';altered_script.write_text(re.search(r'<script nonce="[^"]+">([\s\S]*?)</script>',request('tr-update-check-5-2-1.php')[0])[1])
        client('Skipped upload asks to overwrite all files',ready,'Disable “only upload newer files”','card ok',altered_script)
        (web/'api.php').write_bytes(api)
        target=web/'app/game-insights.php';saved=target.read_bytes();target.unlink()
        missing=json.loads(request('tr-update-check-5-2-1.php?format=json')[0])
        check('Missing game validator detected without bootstrap',missing['files_match'] is False and 'app/game-insights.php' in missing['missing_files'])
        target.write_bytes(saved)
        manifest=web/'tr-update-manifest-5-2-1.json';saved_manifest=manifest.read_bytes();data=json.loads(saved_manifest);data['files']['storage/private-token.txt']='0'*64;manifest.write_text(json.dumps(data))
        blocked=json.loads(request('tr-update-check-5-2-1.php?format=json')[0])
        check('Private file entries are rejected before hashing',blocked['manifest_valid'] is False and 'storage/private-token.txt' not in blocked['changed_files'])
        manifest.write_bytes(saved_manifest)
        manifest.unlink();absent=json.loads(request('tr-update-check-5-2-1.php?format=json')[0])
        check('Missing manifest never reports a complete update',absent['manifest_valid'] is False and absent['files_match'] is False)
    finally:
        server.terminate();server.wait(timeout=5);log.close()
print('TOTAL',len(checks),'passed')

<?php
declare(strict_types=1);

// Read-only deployment check. Does not load app config, use a token, clear caches or write files.
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
$manifestPath=__DIR__.'/tr-update-manifest-5-2-1.json';
$manifest=is_file($manifestPath)?json_decode((string)@file_get_contents($manifestPath),true):null;
$valid=is_array($manifest)&&($manifest['release']??'')==='5.2.1'&&is_array($manifest['files']??null)&&count($manifest['files'])>30;
$changed=[];$missing=[];$checked=0;
if($valid)foreach($manifest['files'] as $name=>$expected){
    if(!is_string($name)||!preg_match('~\A[a-zA-Z0-9_./-]+\z~',$name)||in_array('..',explode('/',$name),true)||in_array('.',explode('/',$name),true)
        ||str_starts_with($name,'storage/')||in_array($name,['config/config.php','config/notifications.php'],true)
        ||!is_string($expected)||!preg_match('/\A[a-f0-9]{64}\z/',$expected)){$valid=false;break;}
    $checked++;
    if(!is_file(__DIR__.'/'.$name))$missing[]=$name;
    elseif(!hash_equals($expected,(string)@hash_file('sha256',__DIR__.'/'.$name)))$changed[]=$name;
}
$apiSource=is_file(__DIR__.'/api.php')?(string)@file_get_contents(__DIR__.'/api.php'):'';
$onDisk=null;
if(preg_match('/STAFF_HQ_API_VERSION\s*=\s*([0-9]+)/',$apiSource,$match)
    ||preg_match('/X-TR-API-Version:\s*([0-9]+)/',$apiSource,$match))$onDisk=(int)$match[1];
$atRoot=($_SERVER['SCRIPT_NAME']??'')==='/tr-update-check-5-2-1.php';
$report=['release'=>'5.2.1','expected_api'=>9,'at_site_root'=>$atRoot,'manifest_valid'=>$valid,'files_match'=>$valid&&!$changed&&!$missing,
    'checked_files'=>$checked,'missing_files'=>$missing,'changed_files'=>$changed,'api_on_disk'=>$onDisk,
    'opcache_timestamps'=>ini_get('opcache.validate_timestamps')];
if(($_GET['format']??'')==='json'){header('Content-Type: application/json; charset=utf-8');echo json_encode($report,JSON_PRETTY_PRINT|JSON_UNESCAPED_SLASHES);exit;}
$nonce=base64_encode(random_bytes(18));
header("Content-Security-Policy: default-src 'none'; script-src 'nonce-$nonce'; style-src 'nonce-$nonce'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'");
header('Content-Type: text/html; charset=utf-8');
function trUpdateEscape(string $value): string{return htmlspecialchars($value,ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8');}
?><!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Towny Reborn · HQ update check</title>
<style nonce="<?= trUpdateEscape($nonce) ?>">*{box-sizing:border-box}body{margin:0;background:#141019;color:#f7eef6;font:16px/1.6 system-ui,sans-serif}main{max-width:800px;margin:32px auto;padding:24px}h1{font-size:1.65rem;color:#ff91cb}h2{font-size:1.1rem;margin:0 0 6px}p{margin:8px 0}.card{padding:18px;border:1px solid #594156;border-radius:14px;margin:16px 0;background:#201a25}.ok{border-color:#8cde84}.warn{border-color:#ffbd80}.subtle{color:#c9b7ca}code{overflow-wrap:anywhere;color:#b7f5a8}li{overflow-wrap:anywhere}a{color:#ffadd7}button{padding:10px 16px;color:#141019;background:#b7f5a8;border:0;border-radius:9px;font-weight:700;cursor:pointer}details{margin-top:14px}#advice{white-space:pre-line}@media(max-width:450px){main{margin:8px auto;padding:16px}}</style>
<main><p class="subtle">Towny Reborn · Staff HQ 5.2.1</p><h1>Website update check</h1><p>This checks the uploaded files and the public API response. No API token is needed.</p>
<?php if(!$atRoot): ?><section class="card warn"><h2>This checker is inside a subfolder</h2><p>The connector uses <code>https://staff.townyreborn.com/api.php</code>. Move the ZIP contents to that website root, then open <a href="/tr-update-check-5-2-1.php">the root update check</a>.</p></section><?php endif; ?>
<section id="files" class="card <?= $report['files_match']?'ok':'warn' ?>"><h2>Uploaded website files</h2><p><?= $report['files_match']?'All '.$checked.' website files match the update.':'The complete update has not been verified in this folder.' ?></p><p>API version in the uploaded file: <strong><?= $onDisk!==null?$onDisk:'unknown' ?></strong> · expected: <strong>9</strong></p>
<?php if(!$valid): ?><p>The update manifest is missing or invalid. Upload the complete ZIP contents.</p><?php endif; ?>
<?php if($changed||$missing): ?><details><summary>Missing or different public files</summary><ul><?php foreach($missing as $name): ?><li>Missing: <code><?= trUpdateEscape($name) ?></code></li><?php endforeach;foreach($changed as $name): ?><li>Different: <code><?= trUpdateEscape($name) ?></code></li><?php endforeach; ?></ul></details><?php endif; ?></section>
<section id="live" class="card"><h2>Executed API</h2><p id="get">Checking GET…</p><p id="post">Checking unauthenticated POST…</p><p id="journey">Checking Journey support…</p></section>
<section class="card"><h2>Next step</h2><p id="advice">Checking the server response…</p><p class="subtle">This page does not change settings, reset PHP, acknowledge events or touch the connector queue.</p><button id="retry" type="button">Check again</button></section>
<p><a href="/api.php">Open the connector's API response</a></p></main>
<script nonce="<?= trUpdateEscape($nonce) ?>">
const uploaded=<?= json_encode($report,JSON_HEX_TAG|JSON_HEX_AMP|JSON_HEX_APOS|JSON_HEX_QUOT|JSON_UNESCAPED_SLASHES) ?>;
async function apiProbe(method){
  const options={method,cache:'no-store',credentials:'omit',redirect:'error',headers:{Accept:'application/json'}};
  if(method==='POST'){options.headers['Content-Type']='application/json';options.body=JSON.stringify({operation:'compatibility.check'});}
  const response=await fetch('/api.php?tr_update_check='+Date.now(),options);
  const body=await response.json();
  return {status:response.status,version:Number(body.api_version||body.version||response.headers.get('X-TR-API-Version')||0),journey:body.capabilities?.journey??body.journey??null,service:body.service};
}
async function check(){
  const advice=document.getElementById('advice'),button=document.getElementById('retry');button.disabled=true;
  try{
    if(location.hostname!=='staff.townyreborn.com'){
      document.getElementById('live').className='card warn';
      document.getElementById('get').textContent='GET: not probed on this domain';document.getElementById('post').textContent='POST: not probed on this domain';document.getElementById('journey').textContent='Journey support: not checked';
      advice.textContent='Open this checker on staff.townyreborn.com. A copy on another domain does not update the connector endpoint.';return;
    }
    const [get,post]=await Promise.all([apiProbe('GET'),apiProbe('POST')]);
    document.getElementById('get').textContent='GET: API '+(get.version||'unknown')+' · HTTP '+get.status;
    document.getElementById('post').textContent='POST: API '+(post.version||'unknown')+' · HTTP '+post.status+' (401 is expected without a token)';
    document.getElementById('journey').textContent='Journey support: '+(get.journey===true?'available':get.journey===false?'unavailable':'unknown');
    const ready=get.status===200&&get.version>=9&&get.journey===true&&get.service==='Towny Reborn Staff API'&&post.status===401&&post.version>=9&&post.journey===true;
    document.getElementById('live').className='card '+(ready?'ok':'warn');
    if(!uploaded.at_site_root)advice.textContent='This checker is inside a subfolder. Move the ZIP contents into the active staff.townyreborn.com website root used by /api.php, then open the root check link.';
    else if(!uploaded.files_match||uploaded.api_on_disk!==9)advice.textContent='Upload every file from the direct-upload ZIP beside this checker and replace existing files. Disable “only upload newer files”. Keep your real config and storage. Then check again.';
    else if(ready)advice.textContent='The website update is active for GET and POST. Run /trstaff reload on the SMP/Hub to retry immediately, then check /trstaff status. Keep all queue files.';
    else if(get.version!==post.version)advice.textContent='GET and POST are reaching different API versions. Ask the host to check routing, PHP-FPM workers and cache for this domain. The uploaded API file is version 9.';
    else if(get.version>0&&get.version<9)advice.textContent='The uploaded files are current, but PHP is still executing an older API. Restart PHP-FPM / clear OPcache through the host. If it stays old, ask the host to check the /api.php route and document root. A browser refresh does not clear the PHP cache.';
    else advice.textContent='The uploaded files match, but the API is not healthy or Journey is unavailable. Check the hosting/PHP error log and database connection. Keep all connector queue files.';
  }catch(error){document.getElementById('live').className='card warn';document.getElementById('get').textContent='API response unavailable';document.getElementById('post').textContent='API response unavailable';document.getElementById('journey').textContent='Journey support: unknown';advice.textContent='The API did not return readable JSON. Check the website root, PHP routing and hosting errors. Open the API response link below. No queue data was changed.';}
  finally{button.disabled=false;}
}
document.getElementById('retry').addEventListener('click',check);check();
</script></html>

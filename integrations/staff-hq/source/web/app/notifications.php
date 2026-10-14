<?php
declare(strict_types=1);

/** Discord delivery is a transactional outbox. Network calls never hold an ingest transaction. */
function notificationDefaults(): array
{
    static $defaults=null;
    if($defaults!==null)return $defaults;
    $private=APP_ROOT.'/config/notifications.php';
    $supplied=is_file($private)?require $private:[];
    return $defaults=array_replace([
        'enabled'=>false,'webhook'=>'','servers'=>['smp','hub','proxy'],'role_id'=>'',
        'delivery_mode'=>'bot','bot_token'=>'','channel_id'=>'','bot_verified_context'=>'',
        'bot_user_id'=>'982787050828869672','discord_guild_id'=>'1541433346737967114',
        'cron_enabled'=>false,'cron_token'=>'',
        'vulcan'=>true,'minimum_severity'=>'low','minimum_vl'=>0,
        'chat_flags'=>true,'health'=>true,'errors'=>true,'performance'=>true,'bans'=>true,'recoveries'=>true,
        'down_minutes'=>5,'queue_threshold'=>1000,'tps_threshold'=>15,'mspt_threshold'=>100,
        'reminder_minutes'=>30,'group_seconds'=>60,
    ],is_array($supplied)?$supplied:[]);
}

function notificationSetting(PDO $pdo,string $key,string $value): void
{
    $pdo->prepare('INSERT INTO settings(setting_key,setting_value,updated_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value),updated_at=VALUES(updated_at)')->execute(['notification.'.$key,$value,now()]);
}

function notificationSettings(bool $reload=false): array
{
    if(!$reload&&isset($GLOBALS['notification_settings']))return $GLOBALS['notification_settings'];
    $s=notificationDefaults();
    foreach(db()->query("SELECT setting_key,setting_value FROM settings WHERE setting_key LIKE 'notification.option.%'")->fetchAll() as $row){
        $key=substr($row['setting_key'],20);
        if(in_array($key,['webhook_encrypted','cron_token_encrypted','bot_token_encrypted'],true)){
            $secret=['webhook_encrypted'=>'webhook','cron_token_encrypted'=>'cron_token','bot_token_encrypted'=>'bot_token'][$key];
            try{$s[$secret]=decryptValue((string)$row['setting_value']);}catch(Throwable){$s[$secret]='';}
        } elseif(array_key_exists($key,$s)){
            $value=json_decode((string)$row['setting_value'],true);
            if($value!==null)$s[$key]=$value;
        }
    }
    foreach(['enabled','cron_enabled','vulcan','chat_flags','health','errors','performance','bans','recoveries'] as $k)$s[$k]=(bool)$s[$k];
    $s['servers']=array_values(array_unique(array_filter(array_map(static fn($v)=>strtolower(trim(is_scalar($v)?(string)$v:'')),is_array($s['servers'])?array_slice($s['servers'],0,20):[]),static fn($v)=>preg_match('/^[a-z0-9_.-]{1,64}$/',$v))));
    $s['down_minutes']=max(5,min(120,(int)$s['down_minutes']));
    $s['queue_threshold']=max(1,min(50000,(int)$s['queue_threshold']));
    $s['tps_threshold']=max(1,min(20,(float)$s['tps_threshold']));
    $s['mspt_threshold']=max(1,min(10000,(float)$s['mspt_threshold']));
    $s['minimum_vl']=max(0,(float)$s['minimum_vl']);
    $s['group_seconds']=max(15,min(300,(int)$s['group_seconds']));
    $s['reminder_minutes']=max(5,min(1440,(int)$s['reminder_minutes']));
    $s['role_id']=preg_match('/^\d{17,20}$/',(string)$s['role_id'])?(string)$s['role_id']:'';
    $s['delivery_mode']=$s['delivery_mode']==='webhook'?'webhook':'bot';
    foreach(['channel_id','bot_user_id','discord_guild_id'] as $key)$s[$key]=preg_match('/^\d{17,20}$/D',(string)$s[$key])?(string)$s[$key]:'';
    if(!in_array($s['minimum_severity'],['low','medium','high','critical'],true))$s['minimum_severity']='low';
    return $GLOBALS['notification_settings']=$s;
}

function notificationWebhookValid(string $url): bool
{
    return preg_match('~^https://discord\.com/api/(?:v\d+/)?webhooks/\d{17,20}/[A-Za-z0-9_-]{20,200}$~D',$url)===1;
}

function notificationBotTokenValid(string $token): bool
{
    return preg_match('/^[A-Za-z0-9._-]{20,256}$/D',$token)===1;
}

function notificationBotContext(array $settings): string
{
    return hash('sha256',implode('|',[$settings['bot_token'],$settings['channel_id'],$settings['bot_user_id'],$settings['discord_guild_id']]));
}

function notificationDestinationValid(array $settings): bool
{
    if($settings['delivery_mode']==='webhook')return notificationWebhookValid((string)$settings['webhook']);
    return notificationBotTokenValid((string)$settings['bot_token'])&&preg_match('/^\d{17,20}$/D',(string)$settings['channel_id'])===1
        &&$settings['bot_user_id']!==''&&$settings['discord_guild_id']!==''
        &&hash_equals(notificationBotContext($settings),(string)$settings['bot_verified_context']);
}

/** Bot API routes are fixed; the credential never appears in a URL or log. No Gateway login. */
function notificationBotApi(string $method,string $path,string $token,?array $message=null): array
{
    $valid=($method==='GET'&&($path==='/users/@me'||preg_match('~^/channels/\d{17,20}$~D',$path)))
        ||($method==='POST'&&preg_match('~^/channels/\d{17,20}/messages$~D',$path));
    if(!$valid||!notificationBotTokenValid($token))return ['status'=>0,'headers'=>[],'body'=>'','error'=>'invalid_bot_request'];
    $url='https://discord.com/api/v10'.$path;$headers=[];$response='';$status=0;
    $requestHeaders=['Content-Type: application/json','User-Agent: TownyRebornStaffHQ/4.3.1','Authorization: Bot '.$token];
    if(isset($GLOBALS['tr_notification_bot_api']))return ($GLOBALS['tr_notification_bot_api'])($method,$path,$requestHeaders,$message);
    if(function_exists('curl_init')){
        $curl=curl_init($url);
        $options=[CURLOPT_CUSTOMREQUEST=>$method,CURLOPT_HTTPHEADER=>$requestHeaders,CURLOPT_RETURNTRANSFER=>true,CURLOPT_FOLLOWLOCATION=>false,CURLOPT_CONNECTTIMEOUT=>2,CURLOPT_TIMEOUT=>4,CURLOPT_SSL_VERIFYPEER=>true,CURLOPT_SSL_VERIFYHOST=>2,
            CURLOPT_HEADERFUNCTION=>static function($curl,string $line)use(&$headers):int{$parts=explode(':',$line,2);if(count($parts)===2)$headers[strtolower(trim($parts[0]))]=trim($parts[1]);return strlen($line);}];
        if($method==='POST')$options[CURLOPT_POSTFIELDS]=jsonEncode($message??[]);
        curl_setopt_array($curl,$options);$response=curl_exec($curl);$status=(int)curl_getinfo($curl,CURLINFO_RESPONSE_CODE);curl_close($curl);
    }elseif(ini_get('allow_url_fopen')){
        $http=['method'=>$method,'header'=>implode("\r\n",$requestHeaders)."\r\n",'timeout'=>4,'ignore_errors'=>true,'follow_location'=>0,'max_redirects'=>0];
        if($method==='POST')$http['content']=jsonEncode($message??[]);
        $context=stream_context_create(['http'=>$http,'ssl'=>['verify_peer'=>true,'verify_peer_name'=>true]]);
        $response=@file_get_contents($url,false,$context,0,65536);
        foreach($http_response_header??[] as $line){if(preg_match('~^HTTP/\S+\s+(\d+)~',$line,$m))$status=(int)$m[1];elseif(str_contains($line,':')){[$k,$v]=explode(':',$line,2);$headers[strtolower(trim($k))]=trim($v);}}
    }else return ['status'=>0,'headers'=>[],'body'=>'','error'=>'no_https_transport'];
    return ['status'=>$status,'headers'=>$headers,'body'=>is_string($response)?mb_substr($response,0,65536):'','error'=>$status===0?'connection_failed':''];
}

/** Read-only setup checks verify the existing bot account and prevent a wrong-server channel. */
function notificationVerifyBot(array $settings): void
{
    foreach(['/users/@me','/channels/'.$settings['channel_id']] as $index=>$path){
        try{$response=notificationBotApi('GET',$path,(string)$settings['bot_token']);}catch(Throwable){throw new InvalidArgumentException('Discord could not verify Freddie right now. Try again shortly.');}
        $status=(int)($response['status']??0);$data=json_decode((string)($response['body']??''),true);
        if($status===401)throw new InvalidArgumentException('Discord rejected the bot token. Use Freddie\'s existing bot token.');
        if($status===403||$status===404)throw new InvalidArgumentException('Freddie cannot access this channel. Check its channel ID and View Channel permission.');
        if($status!==200||!is_array($data))throw new InvalidArgumentException('Discord could not verify Freddie right now. Try again shortly.');
        if($index===0&&(($data['id']??'')!==$settings['bot_user_id']||empty($data['bot'])))throw new InvalidArgumentException('This token does not belong to Freddie the Frog.');
        if($index===1&&(($data['id']??'')!==$settings['channel_id']||($data['guild_id']??'')!==$settings['discord_guild_id']||!in_array($data['type']??null,[0,5],true)))throw new InvalidArgumentException('Choose a text or announcement channel in the Towny Reborn Discord server.');
    }
}

function notificationCronTokenValid(string $token): bool
{
    return preg_match('/^tr_cron_[a-f0-9]{64}$/D',$token)===1;
}

function notificationCronAuthorized(array $settings,string $submitted): bool
{
    $expected=(string)($settings['cron_token']??'');
    return !empty($settings['cron_enabled'])&&notificationCronTokenValid($expected)
        &&notificationCronTokenValid($submitted)&&hash_equals($expected,$submitted);
}

function notificationSchema(PDO $pdo): void
{
    $pdo->exec("CREATE TABLE IF NOT EXISTS notification_outbox (
        id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,dedupe_key CHAR(64) NOT NULL UNIQUE,
        kind VARCHAR(32) NOT NULL,server_name VARCHAR(64) NOT NULL,severity VARCHAR(16) NOT NULL,
        title VARCHAR(190) NOT NULL,details_json LONGTEXT NOT NULL,occurrences INT UNSIGNED NOT NULL DEFAULT 1,
        status VARCHAR(16) NOT NULL DEFAULT 'pending',attempts INT UNSIGNED NOT NULL DEFAULT 0,
        available_at DATETIME NOT NULL,claimed_at DATETIME NULL,depends_on_id BIGINT UNSIGNED NULL,
        first_at DATETIME NOT NULL,last_at DATETIME NOT NULL,sent_at DATETIME NULL,
        last_http SMALLINT UNSIGNED NULL,last_error VARCHAR(190) NULL,
        INDEX idx_notification_due(status,available_at),INDEX idx_notification_history(last_at),
        INDEX idx_notification_dependency(depends_on_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    $pdo->exec("CREATE TABLE IF NOT EXISTS notification_monitors (
        monitor_key VARCHAR(150) PRIMARY KEY,server_name VARCHAR(64) NOT NULL,kind VARCHAR(32) NOT NULL,
        title VARCHAR(190) NOT NULL,severity VARCHAR(16) NOT NULL DEFAULT 'high',
        state VARCHAR(16) NOT NULL DEFAULT 'ok',condition_since DATETIME NULL,last_fresh_at DATETIME NULL,
        incident_at DATETIME NULL,last_notice_at DATETIME NULL,cycle INT UNSIGNED NOT NULL DEFAULT 0,
        incident_notification_id BIGINT UNSIGNED NULL,details_json LONGTEXT NOT NULL,
        created_at DATETIME NOT NULL,updated_at DATETIME NOT NULL,INDEX idx_notification_monitor_state(state)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
}

function notificationRank(string $severity): int {return ['low'=>0,'medium'=>1,'high'=>2,'critical'=>3][$severity]??0;}
function notificationDate(int $epoch): string {return date('Y-m-d H:i:s',$epoch);}

/** A single player's closed/timed-out connection is not a staff emergency. */
function notificationRoutinePlayerDisconnect(string $plugin,string $message): bool
{
    $client=preg_match('/^(?:connected player|initial connection|client connection)$/i',trim($plugin))
        ||preg_match('/\[(?:connected player|initial connection|client connection)\]/i',$message);
    if(!$client)return false;
    // Preserve infrastructure faults even when their log also mentions a player.
    if(preg_match('/connector|integration|listener|webhook|database|sqlstate|mysql|mariadb|\bapi\b|outofmemory|out of memory|\b(?:backend|server)\s+(?:connection|unavailable|offline|crash)|\b(?:failed|unable|could not)\s+to\s+(?:connect|start|bind|load)\b/i',$message))return false;
    return preg_match('/\bread timed out\b|ReadTimeoutException|\bconnection reset(?: by peer)?\b|\bbroken pipe\b|\ban existing connection was forcibly closed by the remote host\b/i',$message)===1;
}

function notificationRoutineQueuedDiagnostic(array $row): bool
{
    if(($row['kind']??'')!=='diagnostic')return false;
    $details=json_decode((string)($row['details_json']??''),true);
    if(!is_array($details))return false;
    $plugin='';
    if(!is_array($details['fields']??[]))return false;
    foreach(($details['fields']??[]) as $field){
        if(!is_array($field))continue;
        if(($field['name']??'')==='Plugin'){$plugin=(string)($field['value']??'');break;}
    }
    return notificationRoutinePlayerDisconnect($plugin,(string)($details['description']??''));
}

/** Never place credentials, mentions or formatting supplied by a player in an alert. */
function notificationText(mixed $value,int $length=800): string
{
    $text=is_scalar($value)?(string)$value:'';
    if(preg_match('/password|passwd|api[ ._-]?token|bot[ ._-]?token|secret[ ._-]?key|authorization|bearer|tr_live_|tr_cron_|X-TR-Cron-Key/i',$text))return '[Sensitive detail omitted]';
    $text=preg_replace('~https?://(?:[^\s/]+\.)?discord(?:app)?\.com/api/(?:v\d+/)?webhooks/[^\s]+~i','[Webhook redacted]',$text)??'';
    $text=preg_replace('/[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/u','',$text)??'';
    return mb_substr(str_replace(['@','`','*','_','~','<','>'],['＠','ˋ','∗','＿','∼','‹','›'],$text),0,$length);
}

function notificationDetails(string $description,array $fields=[],string $page='attention',array $extra=[]): array
{
    $clean=[];
    foreach(array_slice($fields,0,8,true) as $name=>$value)$clean[]=['name'=>notificationText($name,80),'value'=>notificationText($value,400)?:'—','inline'=>true];
    return ['description'=>notificationText($description,1200),'fields'=>$clean,'page'=>preg_match('/^[a-z-]+$/',$page)?$page:'attention',...$extra];
}

function notificationEnqueue(PDO $pdo,string $key,string $kind,string $server,string $severity,string $title,array $details,?int $at=null,?int $available=null,?int $dependency=null): int
{
    $at??=time();$stamp=notificationDate($at);
    $pdo->prepare('INSERT IGNORE INTO notification_outbox(dedupe_key,kind,server_name,severity,title,details_json,available_at,depends_on_id,first_at,last_at) VALUES(?,?,?,?,?,?,?,?,?,?)')
        ->execute([hash('sha256',$key),obsText($kind,32),obsText($server,64),$severity,notificationText($title,190),jsonEncode($details),notificationDate($available??$at),$dependency,$stamp,$stamp]);
    $q=$pdo->prepare('SELECT id FROM notification_outbox WHERE dedupe_key=?');$q->execute([hash('sha256',$key)]);
    $GLOBALS['notification_queued']=true;
    return (int)$q->fetchColumn();
}

/** First alert is immediate; repeats after delivery get one delayed summary. */
function notificationCoalesce(PDO $pdo,string $group,string $kind,string $server,string $severity,string $title,array $details,?int $at=null): void
{
    $at??=time();$seconds=notificationSettings()['group_seconds'];$bucket=intdiv($at,$seconds);
    $key='group:'.$group.':'.$bucket;
    $q=$pdo->prepare('SELECT * FROM notification_outbox WHERE dedupe_key=? FOR UPDATE');$q->execute([hash('sha256',$key)]);$row=$q->fetch();
    if(!$row){notificationEnqueue($pdo,$key,$kind,$server,$severity,$title,$details,$at);return;}
    if($row['status']==='pending'&&(int)$row['attempts']===0){
        $stronger=notificationRank($severity)>notificationRank($row['severity'])?$severity:$row['severity'];
        $pdo->prepare('UPDATE notification_outbox SET occurrences=occurrences+1,last_at=?,severity=?,details_json=? WHERE id=?')->execute([notificationDate($at),$stronger,jsonEncode($details),$row['id']]);
        $GLOBALS['notification_queued']=true;return;
    }
    $summary=$key.':summary';$sq=$pdo->prepare('SELECT * FROM notification_outbox WHERE dedupe_key=? FOR UPDATE');$sq->execute([hash('sha256',$summary)]);$old=$sq->fetch();
    if(!$old){
        $details['summary']=true;
        notificationEnqueue($pdo,$summary,$kind,$server,$severity,$title.' · repeated alerts',$details,$at,($bucket+1)*$seconds,(int)$row['id']);
    } elseif($old['status']==='pending'){
        $details['summary']=true;$stronger=notificationRank($severity)>notificationRank($old['severity'])?$severity:$old['severity'];
        $pdo->prepare('UPDATE notification_outbox SET occurrences=occurrences+1,last_at=?,severity=?,details_json=? WHERE id=?')->execute([notificationDate($at),$stronger,jsonEncode($details),$old['id']]);
    }
}

/** Recoverable operational faults must persist for five minutes before Discord delivery. */
function notificationGrace(string $kind): int
{
    return in_array($kind,['connector','integration','queue','performance','diagnostic'],true)?300:0;
}

/** A resolved incident must not leave an unsent problem, escalation or reminder behind. */
function notificationSuppressCondition(PDO $pdo,array $monitor): void
{
    $parent=(int)($monitor['incident_notification_id']??0);
    $title=notificationText($monitor['title'],190);
    $q=$pdo->prepare("SELECT id,kind,details_json,title,depends_on_id FROM notification_outbox WHERE server_name=? AND status IN('pending','blocked') AND kind<>'recovery' AND (kind=? OR depends_on_id=?) FOR UPDATE");
    $q->execute([$monitor['server_name'],$monitor['kind'],$parent]);
    $ids=[];
    foreach($q->fetchAll() as $queued){
        $d=json_decode((string)$queued['details_json'],true);$d=is_array($d)?$d:[];
        $tagged=($d['condition_key']??null)===$monitor['monitor_key']&&($d['condition_cycle']??null)===(int)$monitor['cycle'];
        $legacy=empty($d['condition_key'])&&$monitor['kind']!=='diagnostic'&&in_array($queued['title'],[$title,notificationText($title.' · escalated',190),notificationText($title.' · still needs attention',190)],true);
        if($tagged||$legacy||($parent&&((int)$queued['id']===$parent||(int)$queued['depends_on_id']===$parent)))$ids[]=(int)$queued['id'];
    }
    if($ids){$marks=implode(',',array_fill(0,count($ids),'?'));$pdo->prepare("UPDATE notification_outbox SET status='suppressed',claimed_at=NULL,last_error='Condition cleared before delivery; Discord delivery skipped' WHERE id IN ($marks) AND status IN('pending','blocked')")->execute($ids);}
}

/** A durable transition gives one confirmed outage and recovery only after a delivered alert. */
function notificationCondition(PDO $pdo,string $key,string $server,string $kind,string $title,string $severity,bool $bad,int $delay,array $details,?int $at=null): void
{
    $at??=time();$stamp=notificationDate($at);$s=notificationSettings();$delay=max($delay,notificationGrace($kind));
    $pdo->prepare('INSERT IGNORE INTO notification_monitors(monitor_key,server_name,kind,title,severity,details_json,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)')->execute([$key,$server,$kind,$title,$severity,jsonEncode($details),$stamp,$stamp]);
    $q=$pdo->prepare('SELECT * FROM notification_monitors WHERE monitor_key=? FOR UPDATE');$q->execute([$key]);$row=$q->fetch();
    $recovery=null;
    if(!$bad&&$row['state']==='incident'&&in_array($kind,['heartbeat','connector','queue'],true)){
        $recovery=$row['recovery_since']??$stamp;
        if($at-strtotime((string)$recovery)<120){
            $pdo->prepare('UPDATE notification_monitors SET recovery_since=?,updated_at=? WHERE monitor_key=?')->execute([$recovery,$stamp,$key]);
            return;
        }
    }
    $since=$bad?($row['condition_since']??$stamp):null;
    $state=$row['state'];$cycle=(int)$row['cycle'];$incident=$row['incident_at'];$notice=$row['last_notice_at'];$parent=$row['incident_notification_id'];
    if($bad&&$state==='incident'&&$parent){
        $q=$pdo->prepare('SELECT status FROM notification_outbox WHERE id=? FOR UPDATE');$q->execute([$parent]);
        if($q->fetchColumn()==='suppressed'){
            // The sender may have observed healthy status between two worker runs.
            // A later failure must start a new watch rather than reuse the cancelled alert.
            $state='watching';$since=$stamp;$incident=null;$notice=null;$parent=null;
            if($kind==='diagnostic')$details['observed_occurrences']=1;
        }
    }
    if($bad&&notificationGrace($kind)&&$kind!=='diagnostic'&&$row['updated_at']<notificationDate($at-120)){
        $q=$pdo->prepare('SELECT status FROM notification_outbox WHERE id=? FOR UPDATE');$q->execute([$parent]);
        if($q->fetchColumn()!=='sent')$since=$stamp; // A missing observation breaks an unreported watch.
    }
    if($bad&&($delay===0||$at-strtotime((string)$since)>=$delay)){
        if($state!=='incident'){
            $state='incident';$cycle++;$incident=$stamp;$notice=$stamp;
            $parent=notificationEnqueue($pdo,'condition:'.$key.':'.$cycle,$kind,$server,$severity,$title,[...$details,'condition_key'=>$key,'condition_cycle'=>$cycle],$at);
        } elseif(notificationRank($severity)>notificationRank($row['severity'])){
            $q=$pdo->prepare('SELECT status FROM notification_outbox WHERE id=? FOR UPDATE');$q->execute([$parent]);$parentState=$q->fetchColumn();
            $tagged=[...$details,'condition_key'=>$key,'condition_cycle'=>$cycle];
            if(in_array($parentState,['pending','blocked'],true))$pdo->prepare('UPDATE notification_outbox SET severity=?,details_json=?,last_at=? WHERE id=?')->execute([$severity,jsonEncode($tagged),$stamp,$parent]);
            else notificationEnqueue($pdo,'condition:'.$key.':'.$cycle.':escalated:'.$severity,$kind,$server,$severity,$title.' · escalated',$tagged,$at,null,$parent?(int)$parent:null);
            $notice=$stamp;
        } elseif(!in_array($kind,['heartbeat','connector'],true)&&$notice&&$at-strtotime($notice)>=$s['reminder_minutes']*60){
            $q=$pdo->prepare('SELECT status FROM notification_outbox WHERE id=? FOR UPDATE');$q->execute([$parent]);
            if($q->fetchColumn()==='sent')notificationEnqueue($pdo,'condition:'.$key.':'.$cycle.':reminder:'.$stamp,$kind,$server,$severity,$title.' · still needs attention',[...$details,'condition_key'=>$key,'condition_cycle'=>$cycle],$at,null,$parent?(int)$parent:null);
            $notice=$stamp;
        }
    } elseif($bad){
        if($state!=='incident')$state='watching';
    } elseif(!$bad){
        $q=$pdo->prepare('SELECT status FROM notification_outbox WHERE id=? FOR UPDATE');$q->execute([$parent]);$parentState=$q->fetchColumn();
        notificationSuppressCondition($pdo,$row);
        // An in-flight alert may complete after this transaction; its recovery still waits for sent.
        // A quiet diagnostic log does not prove that a plugin has recovered.
        if($state==='incident'&&$kind!=='diagnostic'&&$s['recoveries']&&in_array($parentState,['sent','processing'],true)){
            notificationEnqueue($pdo,'condition:'.$key.':'.$cycle.':recovery','recovery',$server,'low',$title.' · recovered',notificationDetails('Fresh status data confirms this condition is healthy again.',['Server'=>strtoupper($server)],'attention',['condition_key'=>$key,'condition_cycle'=>$cycle]),$at,null,(int)$parent);
        }
        $state='ok';$incident=null;$notice=null;
    }
    $pdo->prepare('UPDATE notification_monitors SET title=?,severity=?,state=?,condition_since=?,incident_at=?,last_notice_at=?,cycle=?,incident_notification_id=?,details_json=?,updated_at=?,recovery_since=? WHERE monitor_key=?')
        ->execute([$title,$severity,$state,$since,$incident,$notice,$cycle,$parent,jsonEncode($details),$stamp,null,$key]);
}

/** Timestamps change on every log line; the underlying error must recur over five minutes. */
function notificationDiagnosticObserved(PDO $pdo,string $server,string $plugin,string $level,string $message,int $observedAt): void
{
    $fingerprint=preg_replace('/^(?:\[\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?\]\s*|\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(?:[,.]\d+)?\s*)/','',$message)??$message;
    $key='diagnostic:'.$server.':'.hash('sha256',$plugin.'|'.$level.'|'.$fingerprint);
    $q=$pdo->prepare('SELECT * FROM notification_monitors WHERE monitor_key=? FOR UPDATE');$q->execute([$key]);$row=$q->fetch();
    $last=$row&&$row['last_fresh_at']?strtotime($row['last_fresh_at']):null;
    if($last!==null&&$observedAt<$last)return; // An older queued log cannot keep a fault alive.
    $continuous=$last!==null&&$observedAt-$last<=120&&$row['condition_since']!==null&&in_array($row['state'],['watching','incident'],true);
    $old=$row?json_decode((string)$row['details_json'],true):[];
    $count=$continuous?max(0,(int)($old['observed_occurrences']??0))+1:1;
    $details=notificationDetails($message,['Server'=>strtoupper($server),'Plugin'=>$plugin,'Level'=>$level],'diagnostics',['observed_occurrences'=>$count]);
    if($row&&!$continuous)notificationCondition($pdo,$key,$server,'diagnostic',$plugin.' · '.$level,'high',false,300,$details,$observedAt);
    notificationCondition($pdo,$key,$server,'diagnostic',$plugin.' · '.$level,'high',true,300,$details,$observedAt);
    $pdo->prepare('UPDATE notification_monitors SET last_fresh_at=? WHERE monitor_key=?')->execute([notificationDate($observedAt),$key]);
    $pdo->prepare("UPDATE notification_outbox n JOIN notification_monitors m ON m.incident_notification_id=n.id SET n.occurrences=?,n.last_at=? WHERE m.monitor_key=? AND m.state='incident' AND n.status IN('pending','blocked')")->execute([$count,notificationDate($observedAt),$key]);
}

/** Called only for accepted, nonduplicate events, within the existing API transaction. */
function notificationOnEvent(PDO $pdo,array $payload,string $type,string $source,string $occurredAt,?int $at=null): void
{
    $at??=time();$s=notificationSettings();if(!$s['enabled'])return;
    $source=strtolower(obsText($source,64));$epoch=strtotime($occurredAt);
    if($epoch===false||$at-$epoch>300||$epoch>$at+30)return; // Queued historic data is not a new emergency.
    if($type==='chat.message' && $s['chat_flags']) {
        $rules=json_decode((string)$pdo->query("SELECT setting_value FROM settings WHERE setting_key='chat.review.rules'")->fetchColumn(),true);
        $match=chatReviewMatches((string)($payload['message']??''),is_array($rules)?$rules:[]);
        $urgent=array_intersect($match['categories'],['Duplication','Exploits']);
        if (!$urgent) return;
        $name=obsText($payload['username']??'',32);$channel=obsText($payload['channel']??'global',32);
        $excerpt=notificationText($payload['message']??'',650);
        $details=notificationDetails('Review this conversation. A matching phrase is not proof of an exploit.',[
            'Player'=>$name,'Server'=>strtoupper($source),'Channel'=>$channel,'Matched'=>implode(', ',$match['terms']),'Message'=>$excerpt
        ],'chat-review');
        notificationCoalesce($pdo,'chat:'.$source.':'.strtolower($name).':'.implode(',',$urgent),'chat_flag',$source,'high','Chat flag · '.$name.' · '.implode(', ',$urgent),$details,$at);
        return;
    }
    if($type==='server.snapshot'){
        // Remember every accepted fresh heartbeat, including one followed by an older queued snapshot.
        if($s['health']&&in_array($source,$s['servers'],true)){
            $stamp=notificationDate($at);$captured=notificationDate(min($epoch,$at));
            $pdo->prepare("INSERT INTO notification_monitors(monitor_key,server_name,kind,title,severity,last_fresh_at,details_json,created_at,updated_at) VALUES(?,?,'heartbeat',?,'critical',?,'{}',?,?) ON DUPLICATE KEY UPDATE last_fresh_at=GREATEST(COALESCE(last_fresh_at,VALUES(last_fresh_at)),VALUES(last_fresh_at))")
                ->execute(['heartbeat:'.$source,$source,'Status unknown · '.strtoupper($source),$captured,$stamp,$stamp]);
        }
        return;
    }
    if($type==='anticheat.alert'&&$s['vulcan']){
        $name=obsText($payload['username']??$payload['player_name']??'',32);$check=obsText($payload['check']??$payload['check_name']??'',96);$vl=isset($payload['vl'])?(float)$payload['vl']:null;
        $severity=strtolower((string)($payload['severity']??''));if(!in_array($severity,['low','medium','high','critical'],true))$severity=$vl!==null&&$vl>=15?'critical':($vl!==null&&$vl>=8?'high':($vl!==null&&$vl>=3?'medium':'low'));
        if(notificationRank($severity)<notificationRank($s['minimum_severity'])||($s['minimum_vl']>0&&($vl===null||$vl<$s['minimum_vl'])))return;
        $details=notificationDetails('Vulcan detected a check violation. Review the evidence before taking action.',['Server'=>strtoupper($source),'Player'=>$name,'Check'=>$check,'VL'=>$vl===null?'Not reported':obsNumber($vl),'Severity'=>ucfirst($severity)],'alerts');
        notificationCoalesce($pdo,'vulcan:'.$source.':'.strtolower($name).':'.$check,'vulcan',$source,$severity,'Vulcan · '.$name.' · '.$check,$details,$at);
    } elseif($type==='diagnostic.alert'&&$s['errors']){
        $message=(string)($payload['message']??'');$level=strtoupper((string)($payload['level']??'WARN'));
        $plugin=obsText($payload['plugin']??'Server',96);
        if(preg_match('/password|passwd|api[ ._-]?token|bot[ ._-]?token|secret[ ._-]?key|authorization|bearer|tr_live_|tr_cron_|X-TR-Cron-Key|discord(?:app)?\.com\/api\/(?:v\d+\/)?webhooks/i',$message))return;
        if(notificationRoutinePlayerDisconnect($plugin,$message))return;
        if(!in_array($level,['WARN','ERROR'],true)||($level!=='ERROR'&&!preg_match('/(?:hook|listener|connector|integration).*(?:failed|disabled|error|unavailable)|(?:failed|disabled|could not).*(?:hook|listener|connector|integration)/i',$message)))return;
        $message=preg_replace('/\b(?:\d{1,3}\.){3}\d{1,3}\b|[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}/i','[redacted]',$message)??'';
        notificationDiagnosticObserved($pdo,$source,$plugin,$level,$message,min($epoch,$at));
    } elseif($type==='punishment.created'&&$s['bans']&&in_array(strtolower((string)($payload['punishment_type']??'')),['ban','tempban'],true)){
        $name=(string)($payload['username']??$payload['player_name']??'');
        notificationEnqueue($pdo,'ban:'.$source.':'.($payload['event_id']??hash('sha256',jsonEncode($payload))),'punishment',$source,'high','Ban issued · '.$name,notificationDetails('A ban was recorded by the network.',['Server'=>strtoupper($source),'Player'=>$name,'Type'=>$payload['punishment_type'],'Reason'=>$payload['reason']??'','Issued by'=>$payload['issued_by']??$payload['staff']??'System'],'punishments'),$at);
    }
}

/** Snapshot age must advance: replaying old snapshots cannot hide a disconnected connector. */
function notificationCheckHealth(PDO $pdo,?int $at=null): void
{
    $at??=time();$s=notificationSettings();if(!$s['enabled'])return;
    $snapshots=[];foreach(hqServerStatuses($pdo) as $row)$snapshots[strtolower($row['server_name'])]=$row;
    $pdo->beginTransaction();
    try{
        $pdo->prepare("UPDATE notification_monitors SET state='ok',condition_since=NULL WHERE kind IN('connector','integration','queue','performance') AND state NOT IN('incident','disabled') AND updated_at<?")->execute([notificationDate($at-120)]);
        $disabled=[];
        if(!$s['health'])$disabled=['heartbeat','connector','queue'];
        if(!$s['performance'])$disabled[]='performance';
        if(!$s['errors'])$disabled=[...$disabled,'integration','diagnostic'];
        if($disabled){$marks=implode(',',array_fill(0,count($disabled),'?'));$pdo->prepare("UPDATE notification_monitors SET state='disabled',condition_since=NULL WHERE kind IN ($marks)")->execute($disabled);}
        if($s['errors']){
            foreach($pdo->query("SELECT * FROM notification_monitors WHERE kind='diagnostic' AND state IN('watching','incident')")->fetchAll() as $diagnostic){
                $fresh=$diagnostic['last_fresh_at']?strtotime($diagnostic['last_fresh_at']):false;
                $since=$diagnostic['condition_since']?strtotime($diagnostic['condition_since']):false;
                $d=json_decode((string)$diagnostic['details_json'],true);$d=is_array($d)?$d:[];
                if($fresh===false||$fresh>$at+30||$at-$fresh>120)notificationCondition($pdo,$diagnostic['monitor_key'],$diagnostic['server_name'],'diagnostic',$diagnostic['title'],'high',false,300,$d,$at);
                elseif($since!==false&&$fresh-$since>=300)notificationCondition($pdo,$diagnostic['monitor_key'],$diagnostic['server_name'],'diagnostic',$diagnostic['title'],'high',true,300,$d,$at);
                // Time passing without repeated errors cannot turn an isolated log into an incident.
            }
        }
        foreach($s['servers'] as $server){
            $row=$snapshots[$server]??null;$key='heartbeat:'.$server;$stamp=notificationDate($at);
            $q=$pdo->prepare('SELECT * FROM notification_monitors WHERE monitor_key=? FOR UPDATE');$q->execute([$key]);$monitor=$q->fetch();
            $fresh=$monitor&&$monitor['last_fresh_at']?strtotime($monitor['last_fresh_at']):null;
            $captured=$row?strtotime($row['captured_at']):false;$received=$row?strtotime($row['updated_at']):false;
            if($captured!==false&&$received!==false&&$captured<=$at+30&&$received<=$at+30)$fresh=max($fresh??0,min($captured,$received,$at));
            $reference=$fresh??($monitor?strtotime($monitor['created_at']):$at);$age=max(0,$at-$reference);
            $details=notificationDetails('No fresh server status has arrived for more than '.$s['down_minutes'].' minutes. This can be an HQ/API delivery problem and does not prove the game server is offline.',['Server'=>strtoupper($server),'Last fresh status'=>$fresh?gmdate('Y-m-d H:i:s',$fresh).' UTC':'Not received yet','Without fresh status'=>number_format($age/60,1).' minutes'],'plugins');
            if($s['health']){
                notificationCondition($pdo,$key,$server,'heartbeat','Status unknown · '.strtoupper($server),'high',$age>$s['down_minutes']*60,0,$details,$at);
                if($fresh!==null)$pdo->prepare('UPDATE notification_monitors SET last_fresh_at=? WHERE monitor_key=?')->execute([notificationDate($fresh),$key]);
            }
            // Do not label stale operational data as a current condition or recovery.
            if(!$row||$captured===false||$received===false||$captured>$at+30||$received>$at+30||$at-min($captured,$received)>120){
                $pdo->prepare("UPDATE notification_monitors SET state='ok',condition_since=NULL WHERE server_name=? AND kind IN('connector','integration','queue','performance') AND state NOT IN('incident','disabled')")->execute([$server]);
                continue;
            }
            $m=json_decode((string)$row['metadata_json'],true);$m=is_array($m)?$m:[];
            if($s['errors']){
                $plugins=$pdo->prepare("SELECT p.plugin_name,p.enabled,o.integration FROM server_plugins p JOIN plugin_observability o ON o.server_name=p.server_name AND o.plugin_name=p.plugin_name WHERE LOWER(p.server_name)=? AND o.last_seen_at>=? AND o.last_seen_at<=?");
                $plugins->execute([$server,notificationDate($at-120),notificationDate($at+30)]);
                foreach($plugins->fetchAll() as $plugin){
                    if(!in_array(strtolower($plugin['plugin_name']),['vulcan','litebans','premiumvanish','townyrebornstaffconnector','townyrebornstaffproxyconnector'],true))continue;
                    $integration=(string)$plugin['integration'];
                    $failed=!(bool)$plugin['enabled']||(!str_contains(strtolower($integration),'disabled in config')&&preg_match('/unavailable|fallback|failed|error/i',$integration));
                    notificationCondition($pdo,'integration:'.$server.':'.substr(hash('sha256',strtolower($plugin['plugin_name'])),0,32),$server,'integration','Important plugin integration · '.$plugin['plugin_name'],'high',(bool)$failed,300,notificationDetails('A moderation or Staff connector plugin reports an unavailable native integration or disabled plugin.',['Server'=>strtoupper($server),'Plugin'=>$plugin['plugin_name'],'Plugin enabled'=>$plugin['enabled']?'Yes':'No','Integration'=>$integration?:'Not reported'],'plugins'),$at);
                }
            }
            if($s['health']){
                $queue=max(0,(int)($m['queued_events']??0));$state=(string)($m['connection_state']??'');$http=(int)($m['last_http_status']??0);
                $connection=$http>=400||($state!==''&&preg_match('/fail|reject|unauthor|unsupported|does not accept|not connected|blocked|error/i',$state));
                if(array_key_exists('connection_state',$m)||array_key_exists('last_http_status',$m))notificationCondition($pdo,'connector:'.$server,$server,'connector','Connector reports a problem · '.strtoupper($server),'high',(bool)$connection,300,notificationDetails('The connector is sending status, but reports a delivery or compatibility problem.',['Server'=>strtoupper($server),'State'=>$state?:'Not reported','Last HTTP'=>$http?:'Not reported','Queue'=>$queue],'plugins'),$at);
                if(array_key_exists('queued_events',$m))notificationCondition($pdo,'queue:'.$server,$server,'queue','Connector backlog · '.strtoupper($server),$queue>=5000?'critical':'high',$queue>=$s['queue_threshold'],300,notificationDetails('Events are waiting to reach Staff HQ. Check API connectivity and the connector queue.',['Server'=>strtoupper($server),'Queued events'=>$queue,'Threshold'=>$s['queue_threshold']],'plugins'),$at);
                $lost=max(0,(int)($m['dropped_events']??0));$lostKey='notification.last_dropped.'.$server;
                $read=$pdo->prepare('SELECT setting_value FROM settings WHERE setting_key=?');$read->execute([$lostKey]);$previous=$read->fetchColumn();
                if(array_key_exists('dropped_events',$m)){
                    $read->execute(['notification.dropped_generation.'.$server]);$generation=(int)($read->fetchColumn()?:0);
                    if($previous!==false&&$lost<(int)$previous){$generation++;notificationSetting($pdo,'dropped_generation.'.$server,(string)$generation);}
                    if($previous!==false&&$lost>(int)$previous)notificationEnqueue($pdo,'dropped:'.$server.':'.$generation.':'.$lost,'data_loss',$server,'critical','Connector dropped events · '.strtoupper($server),notificationDetails('The reported dropped-event counter increased. Some events were not retained by the connector.',['Server'=>strtoupper($server),'Newly dropped'=>$lost-(int)$previous,'Total reported'=>$lost],'plugins'),$at);
                    $pdo->prepare('INSERT INTO settings(setting_key,setting_value,updated_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value),updated_at=VALUES(updated_at)')->execute([$lostKey,(string)$lost,$stamp]);
                }
            }
            if($s['performance']){
                $tps=$row['tps']!==null?(float)$row['tps']:null;$mspt=$row['mspt']!==null?(float)$row['mspt']:null;
                // Velocity does not report TPS/MSPT. Null measurements are not failures.
                if($tps!==null||$mspt!==null)notificationCondition($pdo,'performance:'.$server,$server,'performance','Sustained server lag · '.strtoupper($server),'high',($tps!==null&&$tps<$s['tps_threshold'])||($mspt!==null&&$mspt>$s['mspt_threshold']),300,notificationDetails('Poor server tick performance has persisted for more than five minutes.',['Server'=>strtoupper($server),'TPS'=>$tps===null?'Not reported':obsNumber($tps),'MSPT'=>$mspt===null?'Not reported':obsNumber($mspt)],'analytics'),$at);
                else $pdo->prepare("UPDATE notification_monitors SET condition_since=NULL WHERE monitor_key=? AND state<>'incident'")->execute(['performance:'.$server]);
            }
        }
        if($s['servers']){$marks=implode(',',array_fill(0,count($s['servers']),'?'));$pdo->prepare("UPDATE notification_monitors SET state='disabled',condition_since=NULL WHERE kind<>'diagnostic' AND server_name NOT IN ($marks)")->execute($s['servers']);}
        else $pdo->exec("UPDATE notification_monitors SET state='disabled',condition_since=NULL WHERE kind<>'diagnostic'");
        $pdo->commit();
    }catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();throw $e;}
}

function notificationMessage(array $row,array $settings): array
{
    $d=json_decode((string)$row['details_json'],true);$d=is_array($d)?$d:[];$rank=notificationRank($row['severity']);
    $colors=['low'=>0x65de9a,'medium'=>0xffd27e,'high'=>0xff934f,'critical'=>0xff4d70];
    $fields=is_array($d['fields']??null)?array_slice($d['fields'],0,8):[];
    if((int)$row['occurrences']>1||!empty($d['summary']))$fields[]=['name'=>!empty($d['summary'])?'Additional alerts':'Grouped alerts','value'=>(string)$row['occurrences'],'inline'=>true];
    $base=rtrim((string)config('app_url','https://staff.townyreborn.com'),'/');
    $url=$base.'/index.php?page='.rawurlencode((string)($d['page']??'attention'));
    $role=$rank>=2?$settings['role_id']:'';
    return ['username'=>'Towny Reborn · Staff HQ','allowed_mentions'=>['parse'=>[],'roles'=>$role!==''?[$role]:[]],'content'=>$role!==''?'<@&'.$role.'>':'',
        'embeds'=>[['title'=>notificationText($row['title'],190),'description'=>notificationText($d['description']??'',1200),'color'=>$colors[$row['severity']]??0x69b6ff,'url'=>$url,'fields'=>$fields,'timestamp'=>gmdate('Y-m-d\TH:i:s\Z',strtotime($row['last_at'])),'footer'=>['text'=>'Towny Reborn Staff HQ · '.strtoupper($row['server_name']).' · recorded event']]]];
}

/** Old queue entries, repeat summaries and recoveries must not create new role pings. */
function notificationPingEligible(array $row,int $at): bool
{
    $details=json_decode((string)$row['details_json'],true);
    $details=is_array($details)?$details:[];
    $first=strtotime((string)$row['first_at']);
    return notificationRank((string)$row['severity'])>=2&&!$row['depends_on_id']
        &&empty($details['summary'])&&!in_array($row['kind'],['test','recovery'],true)
        &&$first!==false&&$first>=$at-300&&$first<=$at+30;
}

/** One bounded digest can acknowledge many outbox rows; individual records stay in the HQ. */
function notificationBatchMessage(array $rows,array $settings,int $at,bool $allowPing): array
{
    $role='';
    if($allowPing&&$settings['role_id']!==''){
        foreach($rows as $row)if(notificationPingEligible($row,$at)){$role=$settings['role_id'];break;}
    }
    $settings['role_id']=$role;
    $message=notificationMessage($rows[0],$settings);
    // The leading row can be a recovery while another row is a fresh high-priority incident.
    $message['content']=$role!==''?'<@&'.$role.'>':'';
    $message['allowed_mentions']=['parse'=>[],'roles'=>$role!==''?[$role]:[]];
    if(count($rows)===1)return $message;
    $types=[];$events=0;$rank=0;$samples=[];$last=$rows[0]['last_at'];
    foreach($rows as $index=>$row){
        $count=max(1,(int)$row['occurrences']);$events+=$count;
        $types[$row['kind']]=($types[$row['kind']]??0)+$count;
        $rank=max($rank,notificationRank($row['severity']));$last=max($last,$row['last_at']);
        if($index>0&&count($samples)<4)$samples[]=notificationText(strtoupper($row['server_name']).' · '.$row['title'].' ('.$count.')',125);
    }
    $totals=[];foreach($types as $kind=>$count)$totals[]=notificationText($kind,32).': '.$count;
    $lead=$rows[0];$details=json_decode((string)$lead['details_json'],true);$details=is_array($details)?$details:[];
    $leadText=strtoupper($lead['server_name']).' · '.$lead['title']."\n".notificationText($details['description']??'',400);
    foreach(array_slice(is_array($details['fields']??null)?$details['fields']:[],0,3) as $field){
        if(is_array($field))$leadText.="\n".notificationText($field['name']??'',60).': '.notificationText($field['value']??'',100);
    }
    $fields=[['name'=>'Recorded events','value'=>notificationText(implode(' · ',$totals),500),'inline'=>false],
        ['name'=>'Lead notification','value'=>notificationText($leadText,950),'inline'=>false]];
    if($samples)$fields[]=['name'=>'More notifications · sample','value'=>notificationText(implode("\n",$samples),550),'inline'=>false];
    $message['embeds']=[['title'=>'Staff HQ · '.count($rows).' notifications',
        'description'=>$events.' recorded events grouped into one delivery. Full details remain in the HQ; open the link above. Infrastructure incidents are shown first.',
        'color'=>[0x65de9a,0xffd27e,0xff934f,0xff4d70][$rank],
        'url'=>rtrim((string)config('app_url','https://staff.townyreborn.com'),'/').'/index.php?page=attention',
        'fields'=>$fields,'timestamp'=>gmdate('Y-m-d\TH:i:s\Z',strtotime($last)),
        'footer'=>['text'=>'Towny Reborn Staff HQ · grouped delivery · at most one message per minute']]];
    return $message;
}

/** Restrict destinations to Discord. Do not follow redirects or log the secret URL. */
function notificationHttp(string $url,array $message): array
{
    if(!notificationWebhookValid($url))return ['status'=>0,'headers'=>[],'body'=>'','error'=>'invalid_webhook'];
    $body=jsonEncode($message);$headers=[];$response='';$status=0;
    if(function_exists('curl_init')){
        $curl=curl_init($url.'?wait=true');
        curl_setopt_array($curl,[CURLOPT_POST=>true,CURLOPT_POSTFIELDS=>$body,CURLOPT_HTTPHEADER=>['Content-Type: application/json','User-Agent: TownyRebornStaffHQ/4.3.1'],CURLOPT_RETURNTRANSFER=>true,CURLOPT_FOLLOWLOCATION=>false,CURLOPT_CONNECTTIMEOUT=>2,CURLOPT_TIMEOUT=>4,CURLOPT_SSL_VERIFYPEER=>true,CURLOPT_SSL_VERIFYHOST=>2,CURLOPT_HEADERFUNCTION=>static function($curl,string $line)use(&$headers):int{$parts=explode(':',$line,2);if(count($parts)===2)$headers[strtolower(trim($parts[0]))]=trim($parts[1]);return strlen($line);}]);
        $response=curl_exec($curl);$status=(int)curl_getinfo($curl,CURLINFO_RESPONSE_CODE);curl_close($curl);
    }elseif(ini_get('allow_url_fopen')){
        $context=stream_context_create(['http'=>['method'=>'POST','header'=>"Content-Type: application/json\r\nUser-Agent: TownyRebornStaffHQ/4.3.1\r\n",'content'=>$body,'timeout'=>4,'ignore_errors'=>true,'follow_location'=>0,'max_redirects'=>0],'ssl'=>['verify_peer'=>true,'verify_peer_name'=>true]]);
        $response=@file_get_contents($url.'?wait=true',false,$context,0,65536);
        foreach($http_response_header??[] as $line){if(preg_match('~^HTTP/\S+\s+(\d+)~',$line,$m))$status=(int)$m[1];elseif(str_contains($line,':')){[$k,$v]=explode(':',$line,2);$headers[strtolower(trim($k))]=trim($v);}}
    }else return ['status'=>0,'headers'=>[],'body'=>'','error'=>'no_https_transport'];
    return ['status'=>$status,'headers'=>$headers,'body'=>is_string($response)?mb_substr($response,0,65536):'','error'=>$status===0?'connection_failed':''];
}

/** Recheck accepted status before sending: the minute worker may not have run yet. */
function notificationCurrentCondition(PDO $pdo,array $monitor,array $settings,int $at): ?bool
{
    $kind=$monitor['kind'];$server=$monitor['server_name'];
    if($monitor['state']==='incident'&&in_array($kind,['heartbeat','connector','queue'],true)
        &&!empty($monitor['recovery_since'])&&$at-strtotime($monitor['recovery_since'])<120)return null;
    $snapshot=hqServerStatus($pdo,$server);
    $captured=$snapshot?strtotime($snapshot['captured_at']):false;$received=$snapshot?strtotime($snapshot['updated_at']):false;
    $valid=$captured!==false&&$received!==false&&$captured<=$at+30&&$received<=$at+30;
    if($kind==='heartbeat'){
        $fresh=$monitor['last_fresh_at']?strtotime($monitor['last_fresh_at']):strtotime($monitor['created_at']);
        if($valid)$fresh=max($fresh,min($captured,$received,$at));
        return $at-$fresh>$settings['down_minutes']*60;
    }
    if(!$valid||$at-min($captured,$received)>120)return null;
    $m=json_decode((string)$snapshot['metadata_json'],true);$m=is_array($m)?$m:[];
    if($kind==='connector'){
        if(!array_key_exists('connection_state',$m)&&!array_key_exists('last_http_status',$m))return null;
        return (int)($m['last_http_status']??0)>=400||preg_match('/fail|reject|unauthor|unsupported|does not accept|not connected|blocked|error/i',(string)($m['connection_state']??''))===1;
    }
    if($kind==='queue')return array_key_exists('queued_events',$m)?(int)$m['queued_events']>=$settings['queue_threshold']:null;
    if($kind==='performance'){
        if($snapshot['tps']===null&&$snapshot['mspt']===null)return null;
        return ($snapshot['tps']!==null&&(float)$snapshot['tps']<$settings['tps_threshold'])||($snapshot['mspt']!==null&&(float)$snapshot['mspt']>$settings['mspt_threshold']);
    }
    if($kind==='integration'){
        $q=$pdo->prepare('SELECT p.plugin_name,p.enabled,o.integration FROM server_plugins p JOIN plugin_observability o ON o.server_name=p.server_name AND o.plugin_name=p.plugin_name WHERE LOWER(p.server_name)=? AND o.last_seen_at>=? AND o.last_seen_at<=?');
        $q->execute([$server,notificationDate($at-120),notificationDate($at+30)]);
        foreach($q->fetchAll() as $plugin){
            if('integration:'.$server.':'.substr(hash('sha256',strtolower($plugin['plugin_name'])),0,32)!==$monitor['monitor_key'])continue;
            $integration=strtolower((string)$plugin['integration']);
            return !(bool)$plugin['enabled']||(!str_contains($integration,'disabled in config')&&preg_match('/unavailable|fallback|failed|error/i',$integration)===1);
        }
    }
    return null;
}

/** Skip cleared/legacy noise; defer faults until their observation window is confirmed. */
function notificationQueuedDecision(PDO $pdo,array $row,array $settings,int $at): array
{
    $skip=static fn(string $reason)=>['status'=>'suppressed','reason'=>$reason];
    $wait=static fn(int $until)=>['status'=>'pending','available_at'=>notificationDate(max($at+1,$until))];
    if(notificationRoutineQueuedDiagnostic($row))return $skip('Routine player disconnect; Discord delivery skipped');
    $kind=$row['kind'];$d=json_decode((string)$row['details_json'],true);$d=is_array($d)?$d:[];
    if($kind==='diagnostic'&&empty($d['condition_key']))return $skip('Legacy log burst has no confirmed five-minute recurrence');
    $parent=null;
    if($kind==='recovery'){
        if(!$settings['recoveries']||empty($row['depends_on_id']))return $skip('Recovery without a delivered incident; Discord delivery skipped');
        $q=$pdo->prepare('SELECT * FROM notification_outbox WHERE id=?');$q->execute([$row['depends_on_id']]);$parent=$q->fetch();
        if(!$parent||$parent['status']!=='sent')return $skip('Incident was never delivered; recovery skipped');
    }
    if(!notificationGrace($kind)&&!in_array($kind,['heartbeat','recovery'],true))return ['status'=>'send'];
    $monitor=null;
    if(is_string($d['condition_key']??null)){
        $q=$pdo->prepare('SELECT * FROM notification_monitors WHERE monitor_key=?');$q->execute([$d['condition_key']]);$monitor=$q->fetch();
        if(!$monitor||(int)($d['condition_cycle']??-1)!==(int)$monitor['cycle'])return $skip('Superseded incident; Discord delivery skipped');
    }else{
        $q=$pdo->prepare('SELECT * FROM notification_monitors WHERE incident_notification_id=?');$q->execute([$parent['id']??$row['depends_on_id']??$row['id']]);$monitor=$q->fetch();
        if(!$monitor&&$kind!=='recovery'){
            $base=preg_replace('/ · (?:escalated|still needs attention)$/u','',(string)$row['title']);
            $q=$pdo->prepare('SELECT * FROM notification_monitors WHERE server_name=? AND kind=?');$q->execute([$row['server_name'],$kind]);
            foreach($q->fetchAll() as $candidate)if(notificationText($candidate['title'],190)===$base){$monitor=$candidate;break;}
        }
    }
    if(!$monitor)return $kind==='heartbeat'?['status'=>'send']:$skip('No current confirmed incident; Discord delivery skipped');
    $category=$monitor['kind'];
    if((in_array($category,['heartbeat','connector','queue'],true)&&!$settings['health'])
        ||(in_array($category,['diagnostic','integration'],true)&&!$settings['errors'])
        ||($category==='performance'&&!$settings['performance']))return $skip('Monitoring disabled; Discord delivery skipped');
    if($kind==='recovery'){
        if($monitor['state']==='watching')return $wait($at+60);
        if($monitor['state']!=='ok'||$monitor['condition_since']!==null)return $skip('Condition is not currently healthy; recovery skipped');
        $pd=json_decode((string)$parent['details_json'],true);
        if(notificationGrace($parent['kind'])&&empty($pd['condition_key'])&&strtotime($row['first_at'])-strtotime($parent['first_at'])<300)return $skip('Legacy short-lived fault; recovery skipped');
        // A fault may have returned since the last worker check.
        $current=notificationCurrentCondition($pdo,$monitor,$settings,$at);
        if($current===true)return $wait($at+60);
        if($current===null)return $wait($at+60);
        return ['status'=>'send'];
    }
    if(!in_array($monitor['state'],['watching','incident'],true))return $skip('Condition cleared before delivery; Discord delivery skipped');
    if($kind==='diagnostic'){
        $fresh=$monitor['last_fresh_at']?strtotime($monitor['last_fresh_at']):false;
        $since=$monitor['condition_since']?strtotime($monitor['condition_since']):false;
        if($fresh===false||$fresh>$at+30||$at-$fresh>120)return $skip('Diagnostic stopped recurring; Discord delivery skipped');
        if($since===false||$fresh-$since<300)return $wait($at+60);
    }else{
        $current=notificationCurrentCondition($pdo,$monitor,$settings,$at);
        if($current===false)return $skip('Fresh status cleared the condition; Discord delivery skipped');
        if(notificationGrace($kind)&&($current===null||$monitor['updated_at']<notificationDate($at-120)))return $wait($at+60);
    }
    $since=$monitor['condition_since']?strtotime($monitor['condition_since']):false;
    if(notificationGrace($kind)&&($since===false||$at-$since<300))return $wait($since===false?$at+60:$since+300);
    return ['status'=>'send'];
}

function notificationDispatch(PDO $pdo,int $limit=5,?callable $transport=null,?int $at=null): array
{
    $at??=time();$s=notificationSettings();$result=['sent'=>0,'retried'=>0,'blocked'=>0,'messages'=>0];
    if(!$s['enabled']||!notificationDestinationValid($s))return $result;
    $bot=$s['delivery_mode']==='bot';$destination=$bot?'https://discord.com/api/v10/channels/'.$s['channel_id'].'/messages':(string)$s['webhook'];
    $transport??=$GLOBALS['tr_notification_transport']??($bot?static fn($url,$message)=>notificationBotApi('POST','/channels/'.$s['channel_id'].'/messages',(string)$s['bot_token'],$message):'notificationHttp');
    $lock='tr.hq.notify.'.substr(hash('sha256',(string)config('db.database')),0,20);
    $take=$pdo->prepare('SELECT GET_LOCK(?,0)');$take->execute([$lock]);if((int)$take->fetchColumn()!==1)return $result;
    try{
        $state=$pdo->query("SELECT setting_key,setting_value FROM settings WHERE setting_key IN('notification.discord_pause_until','notification.next_delivery_at','notification.role_ping_after','notification.destination_blocked')")->fetchAll(PDO::FETCH_KEY_PAIR);
        if(!empty($state['notification.destination_blocked'])||(int)($state['notification.discord_pause_until']??0)>$at
            ||(int)($state['notification.next_delivery_at']??0)>$at)return $result;
        $pdo->prepare("UPDATE notification_outbox SET status='pending',claimed_at=NULL WHERE status='processing' AND claimed_at<?")->execute([notificationDate($at-60)]);
        // The sender lock covers all API, cron and administrator requests. Commit the cooldown
        // before calling Discord so a crashed request or manual retry cannot create a burst.
        $pdo->beginTransaction();
        try{
            $q=$pdo->prepare("SELECT n.* FROM notification_outbox n LEFT JOIN notification_outbox p ON p.id=n.depends_on_id WHERE n.status='pending' AND n.available_at<=? AND (n.depends_on_id IS NULL OR p.status IN('sent','suppressed')) ORDER BY CASE WHEN n.kind IN('heartbeat','connector','integration','data_loss','queue','performance') THEN 0 WHEN n.kind='chat_flag' THEN -1 WHEN n.kind='test' THEN 1 WHEN n.kind='recovery' THEN 2 ELSE 3 END,FIELD(n.severity,'critical','high','medium','low'),n.id LIMIT 500 FOR UPDATE");
            $rows=[];
            // Rescan after filtering a legacy parent so its summary/recovery is also skipped.
            // Maintenance is bounded and does not consume a Discord delivery window.
            for($scan=0;$scan<3;$scan++){
                $q->execute([notificationDate($at)]);$changed=0;
                foreach($q->fetchAll() as $row){
                    $decision=notificationQueuedDecision($pdo,$row,$s,$at);
                    if($decision['status']==='suppressed'){
                        $pdo->prepare("UPDATE notification_outbox SET status='suppressed',claimed_at=NULL,last_error=? WHERE id=? AND status='pending'")->execute([$decision['reason'],$row['id']]);$changed++;
                    }elseif($decision['status']==='pending'){
                        $pdo->prepare("UPDATE notification_outbox SET available_at=? WHERE id=? AND status='pending'")->execute([$decision['available_at'],$row['id']]);$changed++;
                    }elseif(count($rows)<100){$rows[$row['id']]=$row;}
                }
                if(!$changed||count($rows)>=100)break;
            }
            $rows=array_values($rows);
            if(!$rows){$pdo->commit();return $result;}
            $message=notificationBatchMessage($rows,$s,$at,(int)($state['notification.role_ping_after']??0)<=$at);
            if($bot)unset($message['username']);
            $ids=array_column($rows,'id');$marks=implode(',',array_fill(0,count($ids),'?'));
            $pdo->prepare("UPDATE notification_outbox SET status='processing',claimed_at=?,attempts=attempts+1 WHERE id IN ($marks) AND status='pending'")->execute([notificationDate($at),...$ids]);
            notificationSetting($pdo,'next_delivery_at',(string)($at+60));
            if($message['content']!=='')notificationSetting($pdo,'role_ping_after',(string)($at+600));
            $pdo->commit();
        }catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();throw $e;}
        // The limit argument remains compatible with existing callers; every invocation makes
        // at most ONE network request regardless of its former per-request limit.
        try{$response=$transport($destination,$message);}catch(Throwable){$response=['status'=>0,'headers'=>[],'body'=>'','error'=>'connection_failed'];}
        $status=(int)($response['status']??0);$headers=$response['headers']??[];$raw=json_decode((string)($response['body']??''),true);$raw=is_array($raw)?$raw:[];
        if($status>=200&&$status<300){
            $pdo->prepare("UPDATE notification_outbox SET status='sent',sent_at=?,claimed_at=NULL,last_http=?,last_error=NULL WHERE id IN ($marks) AND status='processing'")->execute([notificationDate($at),$status,...$ids]);
            $result['sent']=count($ids);$result['messages']=1;
            notificationSetting($pdo,'last_success',notificationDate($at));notificationSetting($pdo,'last_error','');
            if(isset($headers['x-ratelimit-remaining'])&&(int)$headers['x-ratelimit-remaining']===0){
                $delay=max(1,(int)ceil((float)($headers['x-ratelimit-reset-after']??1)));notificationSetting($pdo,'discord_pause_until',(string)($at+$delay));
            }
        }elseif($status===429){
            $retry=max(1,(int)ceil(max((float)($raw['retry_after']??0),(float)($headers['retry-after']??0),(float)($headers['x-ratelimit-reset-after']??1))));
            $pdo->prepare("UPDATE notification_outbox SET status='pending',available_at=?,claimed_at=NULL,last_http=429,last_error='Discord rate limit; retry scheduled' WHERE id IN ($marks) AND status='processing'")->execute([notificationDate($at+$retry),...$ids]);
            notificationSetting($pdo,'discord_pause_until',(string)($at+$retry));$result['retried']=count($ids);
        }elseif(in_array($status,[400,401,403,404],true)||($response['error']??'')==='no_https_transport'){
            $error=match($status){400=>'Discord rejected the notification payload',401=>$bot?'Discord rejected Freddie\'s bot token':'Discord webhook access was denied',403=>$bot?'Freddie cannot send here; check View Channel, Send Messages and Embed Links':'Discord webhook access was denied',404=>$bot?'Discord alert channel no longer exists or Freddie cannot access it':'Discord webhook no longer exists',default=>'Enable PHP cURL or HTTPS streams for Discord delivery'};
            $pdo->prepare("UPDATE notification_outbox SET status='blocked',claimed_at=NULL,last_http=?,last_error=? WHERE id IN ($marks) AND status='processing'")->execute([$status?:null,$error,...$ids]);
            notificationSetting($pdo,'last_error',$error);notificationSetting($pdo,'discord_pause_until',(string)($at+300));$result['blocked']=count($ids);
            if(in_array($status,[401,403,404],true))notificationSetting($pdo,'destination_blocked','1');
        }else{
            $delay=min(900,15*2**min(6,max(array_column($rows,'attempts'))));$error=$status?'Discord temporarily unavailable (HTTP '.$status.')':'Discord connection failed; notification retained';
            $pdo->prepare("UPDATE notification_outbox SET status='pending',available_at=?,claimed_at=NULL,last_http=?,last_error=? WHERE id IN ($marks) AND status='processing'")->execute([notificationDate($at+$delay),$status?:null,$error,...$ids]);notificationSetting($pdo,'last_error',$error);$result['retried']=count($ids);
        }
    }finally{$release=$pdo->prepare('SELECT RELEASE_LOCK(?)');$release->execute([$lock]);}
    return $result;
}

function notificationWorker(PDO $pdo,?int $at=null): array
{
    $at??=time();$lock='tr.hq.health.'.substr(hash('sha256',(string)config('db.database')),0,20);
    $q=$pdo->prepare('SELECT GET_LOCK(?,0)');$q->execute([$lock]);if((int)$q->fetchColumn()!==1)return ['busy'=>true];
    try{notificationCheckHealth($pdo,$at);notificationSetting($pdo,'worker_last_run',notificationDate($at));}
    finally{$q=$pdo->prepare('SELECT RELEASE_LOCK(?)');$q->execute([$lock]);}
    $result=notificationDispatch($pdo,5,null,$at);
    $q=$pdo->prepare("SELECT n.id FROM notification_outbox n WHERE n.status IN('sent','suppressed') AND COALESCE(n.sent_at,n.last_at)<? AND NOT EXISTS(SELECT 1 FROM notification_outbox child WHERE child.depends_on_id=n.id AND child.status NOT IN('sent','suppressed')) AND NOT EXISTS(SELECT 1 FROM notification_monitors m WHERE m.incident_notification_id=n.id AND m.state='incident') LIMIT 1000");$q->execute([notificationDate($at-30*86400)]);$expired=$q->fetchAll(PDO::FETCH_COLUMN);
    if($expired){$marks=implode(',',array_fill(0,count($expired),'?'));$pdo->prepare("DELETE FROM notification_outbox WHERE id IN ($marks) AND status IN('sent','suppressed')")->execute($expired);}
    return $result;
}

function notificationBanner(): void
{
    if(!can('alerts.view'))return;
    $health=notificationSettings()['enabled']?(int)db()->query("SELECT COUNT(*) FROM notification_monitors WHERE state='incident'")->fetchColumn():0;
    $recent=(int)db()->query("SELECT COUNT(*) FROM alerts WHERE severity IN('high','critical') AND status IN('open','escalated') AND occurred_at>=DATE_SUB(NOW(),INTERVAL 5 MINUTE)")->fetchColumn();
    if($health||$recent)echo '<div class="attention-banner" role="status"><div><strong>Attention mode</strong><span>'.e($health.' network conditions · '.$recent.' recent high-priority Vulcan alerts').'</span></div><a class="button danger small" href="index.php?page=attention">Open Attention Centre</a></div>';
}

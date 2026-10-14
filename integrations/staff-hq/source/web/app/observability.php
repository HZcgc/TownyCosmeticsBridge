<?php
declare(strict_types=1);

/** Statistics are anonymous and permanent. Searchable activity expires after 30 days. */
function observabilitySchema(PDO $pdo): void
{
    $suffix = ' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci';
    $definitions = [
        "observability_receipts (server_name VARCHAR(64) NOT NULL, event_id VARCHAR(96) NOT NULL, received_at DATETIME NOT NULL, PRIMARY KEY(server_name,event_id))",
        "metric_hourly (hour_utc DATETIME NOT NULL, server_name VARCHAR(64) NOT NULL, plugin_name VARCHAR(96) NOT NULL,
         metric VARCHAR(64) NOT NULL, dimension VARCHAR(128) NOT NULL DEFAULT '', value_sum DOUBLE NOT NULL,
         sample_count BIGINT UNSIGNED NOT NULL, value_min DOUBLE NOT NULL, value_max DOUBLE NOT NULL,
         PRIMARY KEY(hour_utc,server_name,plugin_name,metric,dimension), INDEX idx_metric_lookup(metric,hour_utc))",
        "gameplay_activity (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, sample_key CHAR(64) NOT NULL UNIQUE,
         player_uuid CHAR(36) NOT NULL, player_name VARCHAR(32) NOT NULL, server_name VARCHAR(64) NOT NULL,
         plugin_name VARCHAR(96) NOT NULL, metric VARCHAR(64) NOT NULL, dimension VARCHAR(128) NOT NULL,
         world_name VARCHAR(96) NOT NULL, value_sum DOUBLE NOT NULL, sample_count BIGINT UNSIGNED NOT NULL,
         details_json TEXT NULL, first_at DATETIME NOT NULL, last_at DATETIME NOT NULL,
         INDEX idx_game_player(player_uuid,last_at), INDEX idx_game_time(last_at), INDEX idx_game_metric(metric,last_at))",
        "plugin_observability (server_name VARCHAR(64) NOT NULL, plugin_name VARCHAR(96) NOT NULL,
         category VARCHAR(32) NOT NULL DEFAULT 'Infrastructure', coverage VARCHAR(32) NOT NULL DEFAULT 'technical',
         hooks_json TEXT NULL, integration VARCHAR(190) NULL, installed_version VARCHAR(96) NULL,
         latest_version VARCHAR(96) NULL, update_state VARCHAR(32) NOT NULL DEFAULT 'unknown',
         update_source VARCHAR(190) NULL, release_url VARCHAR(512) NULL, compatibility VARCHAR(256) NULL,
         update_reason VARCHAR(256) NULL, checked_at DATETIME NULL, next_check_at DATETIME NULL,
         last_seen_at DATETIME NULL, PRIMARY KEY(server_name,plugin_name))",
        "daily_server_stats (day_utc DATE NOT NULL, server_name VARCHAR(64) NOT NULL,
         active_players BIGINT UNSIGNED NOT NULL DEFAULT 0, new_players BIGINT UNSIGNED NOT NULL DEFAULT 0,
         PRIMARY KEY(day_utc,server_name))",
        "daily_player_visits (day_utc DATE NOT NULL, server_name VARCHAR(64) NOT NULL, player_uuid CHAR(36) NOT NULL,
         PRIMARY KEY(day_utc,server_name,player_uuid))",
        "retention_cohorts (day_utc DATE PRIMARY KEY, new_players BIGINT UNSIGNED NOT NULL DEFAULT 0,
         d1 BIGINT UNSIGNED NOT NULL DEFAULT 0, d3 BIGINT UNSIGNED NOT NULL DEFAULT 0,
         d7 BIGINT UNSIGNED NOT NULL DEFAULT 0, d30 BIGINT UNSIGNED NOT NULL DEFAULT 0)",
        "chat_signals (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, message_id BIGINT UNSIGNED NOT NULL UNIQUE,
         player_uuid CHAR(36) NOT NULL, player_name VARCHAR(32) NOT NULL, server_name VARCHAR(64) NOT NULL,
         channel_name VARCHAR(32) NOT NULL, category VARCHAR(32) NOT NULL, matched_terms VARCHAR(512) NOT NULL,
         excerpt TEXT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'open', case_id BIGINT UNSIGNED NULL,
         reviewed_by BIGINT UNSIGNED NULL, created_at DATETIME NOT NULL, reviewed_at DATETIME NULL,
         INDEX idx_chat_signal_status(status,created_at), INDEX idx_chat_signal_time(created_at))",
        "server_diagnostics (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, fingerprint CHAR(64) NOT NULL UNIQUE,
         server_name VARCHAR(64) NOT NULL, plugin_name VARCHAR(96) NOT NULL, level VARCHAR(8) NOT NULL,
         message TEXT NOT NULL, occurrences INT UNSIGNED NOT NULL DEFAULT 1,
         status VARCHAR(20) NOT NULL DEFAULT 'open', case_id BIGINT UNSIGNED NULL,
         reviewed_by BIGINT UNSIGNED NULL, first_at DATETIME NOT NULL, last_at DATETIME NOT NULL,
         INDEX idx_diagnostic_status(status,last_at), INDEX idx_diagnostic_time(last_at))",
    ];
    foreach ($definitions as $definition) $pdo->exec('CREATE TABLE IF NOT EXISTS ' . $definition . $suffix);
    $pdo->exec('INSERT IGNORE INTO observability_receipts(server_name,event_id,received_at) SELECT source_server,event_id,received_at FROM ingest_events WHERE event_id IS NOT NULL');
    addColumnIfMissing($pdo, 'players', 'observability_cohort', 'DATE NULL');
    foreach (['admin' => ['plugins.view','activity.view','messages.review'], 'moderator' => ['plugins.view','activity.view','messages.review'], 'helper' => ['plugins.view']] as $role => $permissions) {
        foreach ($permissions as $permission) $pdo->prepare('INSERT IGNORE INTO role_permissions(role,permission,allowed,updated_at) VALUES(?,?,1,?)')->execute([$role,$permission,now()]);
    }
    $pdo->prepare("INSERT IGNORE INTO settings(setting_key,setting_value,updated_at) VALUES('chat.review.rules',?,?)")
        ->execute([jsonEncode(['Duplication' => ['dupe','duping','duplicated','duplication','duplizieren','verdoppeln'], 'Exploits' => ['exploit','bypass','infinite money','unendlich geld','free money'], 'Bugs' => ['bug','glitch','broken','kaputt','fehler','does not work','doesnt work','geht nicht','geht ne']]),now()]);
    observabilityBackfill($pdo);
}

function obsUtc(string $local): string { return gmdate('Y-m-d H:i:s', strtotime($local)); }
function obsHour(string $local): string { return gmdate('Y-m-d H:00:00', strtotime($local)); }
function obsRecent(string $local): bool { return strtotime($local) >= time()-30*86400; }
function obsText(mixed $value, int $length): string { return mb_substr(is_scalar($value) ? (string)$value : '',0,$length); }

/** Buffer writes across one API batch. Remote MySQL gets one upsert per chunk, not per sample. */
function obsMetric(string $server, string $plugin, string $metric, string $dimension, string $at, float $sum=1, int $count=1, ?float $min=null, ?float $max=null): void
{
    $row = [obsHour($at),obsText($server,64),obsText($plugin,96),obsText($metric,64),obsText($dimension,128),$sum,$count,$min??($count?$sum/$count:0),$max??($count?$sum/$count:0)];
    $key = jsonEncode(array_slice($row,0,5));
    if (isset($GLOBALS['obs_metrics'][$key])) {
        $old =& $GLOBALS['obs_metrics'][$key]; $old[5]+=$sum; $old[6]+=$count; $old[7]=min($old[7],$row[7]); $old[8]=max($old[8],$row[8]);
    } else $GLOBALS['obs_metrics'][$key] = $row;
}

function obsFlush(PDO $pdo): void
{
    $rows = array_values($GLOBALS['obs_metrics']??[]);
    foreach (array_chunk($rows,150) as $chunk) {
        $sql = 'INSERT INTO metric_hourly(hour_utc,server_name,plugin_name,metric,dimension,value_sum,sample_count,value_min,value_max) VALUES ' . implode(',',array_fill(0,count($chunk),'(?,?,?,?,?,?,?,?,?)'));
        $sql .= ' ON DUPLICATE KEY UPDATE value_sum=value_sum+VALUES(value_sum),sample_count=sample_count+VALUES(sample_count),value_min=LEAST(value_min,VALUES(value_min)),value_max=GREATEST(value_max,VALUES(value_max))';
        $pdo->prepare($sql)->execute(array_merge(...$chunk));
    }
    $GLOBALS['obs_metrics']=[];
}

function obsTelemetry(PDO $pdo,array $payload,string $source): void
{
    $samples=$payload['samples']??null;
    if (!is_array($samples)||!array_is_list($samples)||count($samples)>120||count($samples)<1) throw new InvalidArgumentException('Invalid telemetry samples.');
    $raw=[];
    foreach ($samples as $sample) {
        if (!is_array($sample)) throw new InvalidArgumentException('Invalid telemetry row.');
        $metric=obsText($sample['metric']??'',64); $plugin=obsText($sample['plugin']??'',96);
        if ($plugin===''||!preg_match('/^[a-z0-9_.-]{1,64}$/',$metric)) throw new InvalidArgumentException('Invalid metric.');
        foreach (['value','min','max'] as $key) if (isset($sample[$key])&&(!is_numeric($sample[$key])||!is_finite((float)$sample[$key])||abs((float)$sample[$key])>1e15)) throw new InvalidArgumentException('Invalid numeric metric.');
        $value=(float)($sample['value']??1); $count=(int)($sample['count']??1);
        if ($count<1||$count>100000000) throw new InvalidArgumentException('Invalid metric count.');
        $at=apiDate((string)($sample['minute']??'')); $dimension=obsText($sample['dimension']??'',128);
        $min=(float)($sample['min']??$value/$count); $max=(float)($sample['max']??$value/$count);
        if ($min>$max) throw new InvalidArgumentException('Invalid metric range.');
        obsMetric($source,$plugin,$metric,$dimension,$at,$value,$count,$min,$max);
        $uuid=normalizeUuid((string)($sample['uuid']??''));
        if (!$uuid && obsRecent($at) && !empty($sample['username'])) { $known=apiResolvePlayer($pdo,(string)$sample['username']); $uuid=$known ? normalizeUuid((string)$known['uuid']) : null; }
        if (!$uuid||!obsRecent($at)) continue;
        $name=obsText($sample['username']??'',32); $world=obsText($sample['world']??'',96);
        $key=hash('sha256',jsonEncode([gmdate('Y-m-d H:i',strtotime($at)),$source,$plugin,$metric,$dimension,$uuid,$world]));
        $details=is_array($sample['details']??null)?obsText(jsonEncode($sample['details']),12000):null;
        $raw[]=[$key,$uuid,$name,$source,$plugin,$metric,$dimension,$world,$value,$count,$details,apiDate((string)($sample['first_at']??$at)),apiDate((string)($sample['last_at']??$at))];
    }
    foreach(array_chunk($raw,100) as $chunk) $pdo->prepare('INSERT INTO gameplay_activity(sample_key,player_uuid,player_name,server_name,plugin_name,metric,dimension,world_name,value_sum,sample_count,details_json,first_at,last_at) VALUES '.implode(',',array_fill(0,count($chunk),'(?,?,?,?,?,?,?,?,?,?,?,?,?)')).' ON DUPLICATE KEY UPDATE value_sum=value_sum+VALUES(value_sum),sample_count=sample_count+VALUES(sample_count),first_at=LEAST(first_at,VALUES(first_at)),last_at=GREATEST(last_at,VALUES(last_at))')->execute(array_merge(...$chunk));
}

function obsSnapshot(PDO $pdo,array $payload,string $source,string $at): void
{
    foreach (['online_players','tps','mspt','memory_used_mb','memory_max_mb','uptime_seconds'] as $metric) {
        if(isset($payload[$metric])&&is_numeric($payload[$metric])&&is_finite((float)$payload[$metric])) obsMetric($source,'Server',$metric,'',$at,(float)$payload[$metric]);
    }
    $rows=[]; $base=[];
    foreach(array_slice((array)($payload['plugins']??[]),0,500) as $p) {
        if(!is_array($p)||empty($p['name']))continue;
        $name=obsText($p['name'],96); $version=obsText($p['version']??'',96);
        $base[$name]=[$source,$name,$version,!empty($p['enabled'])?1:0,now()];
        $hooks=$p['hook_names']??[];
        if(isset($p['insights_hooks']))$hooks=['legacy'=>$hooks,'insights'=>$p['insights_hooks'],'failed_hooks'=>$p['failed_hooks']??[],'buffered'=>$p['insights_buffered']??0,'rejected'=>$p['insights_rejected']??0];
        $rows[$name]=[$source,$name,obsText($p['category']??'Infrastructure',32),obsText($p['coverage']??'technical',32),jsonEncode($hooks),obsText($p['integration']??'',190),$version,$at];
    }
    $pdo->prepare('DELETE FROM server_plugins WHERE server_name=?')->execute([$source]);
    foreach(array_chunk(array_values($base),100) as $chunk) $pdo->prepare('INSERT INTO server_plugins(server_name,plugin_name,plugin_version,enabled,updated_at) VALUES '.implode(',',array_fill(0,count($chunk),'(?,?,?,?,?)')))->execute(array_merge(...$chunk));
    foreach(array_chunk(array_values($rows),100) as $chunk) $pdo->prepare('INSERT INTO plugin_observability(server_name,plugin_name,category,coverage,hooks_json,integration,installed_version,last_seen_at) VALUES '.implode(',',array_fill(0,count($chunk),'(?,?,?,?,?,?,?,?)')).' ON DUPLICATE KEY UPDATE category=VALUES(category),coverage=VALUES(coverage),hooks_json=VALUES(hooks_json),integration=VALUES(integration),installed_version=VALUES(installed_version),last_seen_at=VALUES(last_seen_at)')->execute(array_merge(...$chunk));
}

function obsPluginUpdates(PDO $pdo,array $payload,string $source): void
{
    $plugins=$payload['plugins']??null;
    if(!is_array($plugins)||count($plugins)>100)throw new InvalidArgumentException('Invalid release list.');
    $rows=[];
    foreach($plugins as $p) {
        if(!is_array($p)||empty($p['name']))throw new InvalidArgumentException('Missing plugin name.');
        $state=(string)($p['state']??'unknown');
        if(!in_array($state,['unknown','custom','current','update_available','newer_build','unverified','check_failed'],true))$state='unverified';
        $url=(string)($p['url']??''); $host=parse_url($url,PHP_URL_HOST);
        if(parse_url($url,PHP_URL_SCHEME)!=='https'||!in_array($host,['github.com','modrinth.com','www.spigotmc.org','spigotmc.org'],true))$url='';
        $rows[]=[$source,obsText($p['name'],96),obsText($p['installed_version']??'',96),obsText($p['latest_version']??'',96),$state,obsText($p['source']??'',190),obsText($url,512),obsText($p['compatibility']??'',256),obsText($p['reason']??'',256),apiDate((string)($p['checked_at']??'')),apiFutureDate((string)($p['next_check_at']??''))];
    }
    if($rows) $pdo->prepare('INSERT INTO plugin_observability(server_name,plugin_name,installed_version,latest_version,update_state,update_source,release_url,compatibility,update_reason,checked_at,next_check_at) VALUES '.implode(',',array_fill(0,count($rows),'(?,?,?,?,?,?,?,?,?,?,?)')).' ON DUPLICATE KEY UPDATE installed_version=VALUES(installed_version),latest_version=VALUES(latest_version),update_state=VALUES(update_state),update_source=VALUES(update_source),release_url=VALUES(release_url),compatibility=VALUES(compatibility),update_reason=VALUES(update_reason),checked_at=VALUES(checked_at),next_check_at=VALUES(next_check_at)')->execute(array_merge(...$rows));
}

function apiFutureDate(string $value): ?string { $ts=strtotime($value); return $ts!==false&&$ts<time()+7*86400 ? date('Y-m-d H:i:s',$ts):null; }

function obsVisit(PDO $pdo,string $uuid,string $source,string $at): void
{
    if(!obsRecent($at))return;
    $day=gmdate('Y-m-d',strtotime($at));
    foreach(array_unique([$source,'network']) as $server) {
        $insert=$pdo->prepare('INSERT IGNORE INTO daily_player_visits(day_utc,server_name,player_uuid) VALUES(?,?,?)');$insert->execute([$day,$server,$uuid]);
        if($insert->rowCount())$pdo->prepare('INSERT INTO daily_server_stats(day_utc,server_name,active_players) VALUES(?,?,1) ON DUPLICATE KEY UPDATE active_players=active_players+1')->execute([$day,$server]);
    }
    analyticsRetentionVisit($pdo,$uuid,$day);
}

function obsChatSignal(PDO $pdo,int $messageId,array $payload,string $source,string $at): void
{
    $rules=json_decode((string)$pdo->query("SELECT setting_value FROM settings WHERE setting_key='chat.review.rules'")->fetchColumn(),true);
    $text=(string)($payload['message']??'');$match=chatReviewMatches($text,is_array($rules)?$rules:[]);
    if(!$match['terms'])return;
    $pdo->prepare('INSERT IGNORE INTO chat_signals(message_id,player_uuid,player_name,server_name,channel_name,category,matched_terms,excerpt,created_at) VALUES(?,?,?,?,?,?,?,?,?)')
        ->execute([$messageId,normalizeUuid((string)$payload['uuid']),obsText($payload['username'],32),$source,obsText($payload['channel']??'global',32),obsText(implode(', ',$match['categories']),32),obsText(implode(', ',$match['terms']),512),obsText($text,4096),$at]);
    obsMetric($source,'Chat review','review_hints',obsText($match['categories'][0],128),$at);
}

function obsDiagnostic(PDO $pdo,array $payload,string $source,string $at): void
{
    $message=obsText($payload['message']??'',1600);$plugin=obsText($payload['plugin']??'Server',96);$level=strtoupper((string)($payload['level']??'WARN'));
    if($message===''||!in_array($level,['WARN','ERROR'],true))throw new InvalidArgumentException('Invalid diagnostic.');
    if(preg_match('/password|passwd|api[ ._-]?token|bot[ ._-]?token|secret[ ._-]?key|authorization|bearer|tr_live_|tr_cron_|X-TR-Cron-Key/i',$message))return;
    $message=preg_replace('/\b(?:\d{1,3}\.){3}\d{1,3}\b|[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}/i','[redacted]',$message);
    if(!obsRecent($at))return;
    $fingerprint=hash('sha256',jsonEncode([$source,$plugin,$level,$message,gmdate('Y-m-d H:i',strtotime($at))]));
    $pdo->prepare('INSERT INTO server_diagnostics(fingerprint,server_name,plugin_name,level,message,first_at,last_at) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE occurrences=occurrences+1,last_at=GREATEST(last_at,VALUES(last_at))')->execute([$fingerprint,$source,$plugin,$level,$message,$at,$at]);
}

/** Existing totals are seeded once without assuming historical gameplay or retention. */
function observabilityBackfill(PDO $pdo): void
{
    if($pdo->query("SELECT setting_value FROM settings WHERE setting_key='observability.imported'")->fetchColumn())return;
    $pdo->beginTransaction();
    try {
        foreach ([['player_sessions','joined_at','server_name','Server','player_joins'],['player_messages','sent_at','source_server','Chat','messages'],['alerts','occurred_at','server_name','Vulcan','anticheat_alerts']] as [$table,$time,$source,$plugin,$metric]) {
            $sql="SELECT DATE_FORMAT($time,'%Y-%m-%d %H:00:00') at_time,COALESCE($source,'network') server,COUNT(*) total FROM $table GROUP BY at_time,server";
            foreach($pdo->query($sql)->fetchAll() as $row) obsMetric($row['server'],$plugin,$metric,'',$row['at_time'],(float)$row['total'],(int)$row['total'],1,1);
        }
        obsFlush($pdo);
        $pdo->exec("INSERT IGNORE INTO daily_player_visits(day_utc,server_name,player_uuid) SELECT DATE(CONVERT_TZ(joined_at,@@session.time_zone,'+00:00')),COALESCE(server_name,'network'),player_uuid FROM player_sessions WHERE joined_at>=DATE_SUB(NOW(),INTERVAL 30 DAY)");
        $pdo->exec("INSERT IGNORE INTO daily_player_visits(day_utc,server_name,player_uuid) SELECT day_utc,'network',player_uuid FROM daily_player_visits");
        $pdo->exec('INSERT INTO daily_server_stats(day_utc,server_name,active_players) SELECT day_utc,server_name,COUNT(*) FROM daily_player_visits GROUP BY day_utc,server_name ON DUPLICATE KEY UPDATE active_players=GREATEST(active_players,VALUES(active_players))');
        $pdo->prepare("INSERT INTO settings(setting_key,setting_value,updated_at) VALUES('observability.imported','1',?)")->execute([now()]);$pdo->commit();
    } catch(Throwable $e) { if($pdo->inTransaction())$pdo->rollBack();$GLOBALS['obs_metrics']=[];throw $e; }
}

function obsRetention(PDO $pdo,int $limit=5000): int
{
    $limit=max(1,min(5000,$limit)); $count=0;
    foreach(['gameplay_activity'=>'last_at','player_messages'=>'sent_at','chat_signals'=>'created_at','server_diagnostics'=>'last_at','player_sessions'=>'joined_at','alerts'=>'occurred_at'] as $table=>$field) $count+=$pdo->exec("DELETE FROM $table WHERE $field < DATE_SUB(NOW(),INTERVAL 30 DAY) LIMIT $limit");
    $count+=$pdo->exec("DELETE FROM audit_logs WHERE source_server IS NOT NULL AND created_at<DATE_SUB(NOW(),INTERVAL 30 DAY) LIMIT $limit");
    $count+=$pdo->exec("DELETE FROM daily_player_visits WHERE day_utc<DATE_SUB(UTC_DATE(),INTERVAL 30 DAY) LIMIT $limit");
    $count+=$pdo->exec("UPDATE players SET metadata_json='{}',balance=NULL,town_name=NULL,online=0,risk_score=0 WHERE last_seen_at<DATE_SUB(NOW(),INTERVAL 30 DAY) AND (metadata_json<>'{}' OR balance IS NOT NULL OR town_name IS NOT NULL OR online<>0 OR risk_score<>0) LIMIT $limit");
    $count+=$pdo->exec("UPDATE players SET observability_cohort=NULL WHERE observability_cohort<DATE_SUB(UTC_DATE(),INTERVAL 30 DAY) LIMIT $limit");
    $count+=$pdo->exec("DELETE FROM retention_members WHERE cohort_day<DATE_SUB(UTC_DATE(),INTERVAL 30 DAY) LIMIT $limit");
    // Receipts stay for deduplication, including queues replayed more than 30 days later.
    $count+=$pdo->exec("UPDATE ingest_events SET payload_json='{}' WHERE received_at<DATE_SUB(NOW(),INTERVAL 30 DAY) AND payload_json<>'{}' LIMIT $limit");
    return $count;
}

function obsRetentionSummary(int $days=30): array
{
    return analyticsRetention(analyticsRange(['days'=>(string)$days]))['summary'];
}

function analyticsSnapshot(int $days=30): array
{
    $data=legacyAnalyticsSnapshot($days);$days=$data['days'];$ret=obsRetentionSummary($days);
    foreach([1,3,7,30] as $d)$data['overview']['d'.$d.'_retention']=$ret[$d]['percent'];
    $data['overview']['online']=obsOnline();
    $data['engagement']['sessions']=(int)statScalar("SELECT COALESCE(SUM(value_sum),0) FROM metric_hourly WHERE metric='player_joins' AND hour_utc>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL $days DAY)");
    $data['moderation']['alerts']=(int)statScalar("SELECT COALESCE(SUM(value_sum),0) FROM metric_hourly WHERE metric='anticheat_alerts' AND hour_utc>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL $days DAY)");
    foreach(['players','sessions','alerts'] as $key){foreach($data['trends'][$key] as &$v)$v=0;unset($v);
        $sql=$key==='players'?"SELECT day_utc chart_date,active_players total FROM daily_server_stats WHERE server_name='network'":("SELECT DATE(hour_utc) chart_date,SUM(value_sum) total FROM metric_hourly WHERE metric='".($key==='sessions'?'player_joins':'anticheat_alerts')."' GROUP BY chart_date");
        foreach(db()->query($sql)->fetchAll() as $r)if(isset($data['trends'][$key][$r['chart_date']]))$data['trends'][$key][$r['chart_date']]=(int)$r['total'];
    }
    foreach($data['daily'] as $k=>&$v)$v=(int)($data['trends']['players'][$k]??0);unset($v);
    $data['join_hours_line']=array_fill_keys(array_map(fn($h)=>sprintf('%02d:00',$h),range(0,23)),0);
    foreach(db()->query("SELECT HOUR(hour_utc) h,SUM(value_sum) n FROM metric_hourly WHERE metric='player_joins' AND hour_utc>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL $days DAY) GROUP BY h")->fetchAll() as $r)$data['join_hours_line'][sprintf('%02d:00',$r['h'])]=(int)$r['n'];
    return $data;
}
function obsOnline(): int
{
    $total=0;
    foreach(hqServerStatuses() as $row){
        if(!hqStatusFresh($row))continue;
        if(strtolower($row['server_name'])==='proxy')return (int)$row['online_players'];
        $total+=(int)$row['online_players'];
    }
    return $total;
}

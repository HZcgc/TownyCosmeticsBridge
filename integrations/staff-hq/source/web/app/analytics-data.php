<?php
declare(strict_types=1);

/** Convert legacy local DATETIME values using PHP's timezone rules, even without MySQL tz tables. */
function analyticsUtcSql(string $field,array $range): string
{
    $tz=new DateTimeZone((string)config('timezone','Europe/Berlin'));
    $start=strtotime($range['from'].' UTC')-172800;$end=strtotime($range['until'].' UTC')+172800;
    $transitions=$tz->getTransitions($start,$end);
    if(!$transitions){$offset=(new DateTimeImmutable('@'.$start))->setTimezone($tz)->getOffset();return "DATE_SUB($field,INTERVAL $offset SECOND)";}
    $base=(int)$transitions[0]['offset'];$cases=[];
    foreach(array_reverse(array_slice($transitions,1)) as $t){$offset=(int)$t['offset'];$local=gmdate('Y-m-d H:i:s',(int)$t['ts']+$offset);$cases[]="WHEN $field >= '$local' THEN DATE_SUB($field,INTERVAL $offset SECOND)";}
    return $cases?'(CASE '.implode(' ',$cases)." ELSE DATE_SUB($field,INTERVAL $base SECOND) END)":"DATE_SUB($field,INTERVAL $base SECOND)";
}

/** Version 7: recover observed cohorts and keep temporary membership for exactly-once returns. */
function analyticsSchema(PDO $pdo): void
{
    $pdo->exec("CREATE TABLE IF NOT EXISTS retention_members (
        player_uuid CHAR(36) PRIMARY KEY, cohort_day DATE NOT NULL,
        d1 TINYINT UNSIGNED NOT NULL DEFAULT 0, d3 TINYINT UNSIGNED NOT NULL DEFAULT 0,
        d7 TINYINT UNSIGNED NOT NULL DEFAULT 0, d30 TINYINT UNSIGNED NOT NULL DEFAULT 0,
        INDEX idx_retention_member_day(cohort_day)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    if($pdo->query("SELECT setting_value FROM settings WHERE setting_key='analytics.cohorts.repaired'")->fetchColumn())return;
    addIndexIfMissing($pdo,'metric_hourly','idx_metric_plugin_time','INDEX idx_metric_plugin_time (plugin_name,hour_utc)');
    $pdo->beginTransaction();
    try {
        // Recover days proved by sessions, including sessions spanning midnight. Never use last_seen
        // alone as proof of a return on an arbitrary earlier day.
        $days=[];for($i=0;$i<=30;$i++)$days[]=gmdate('Y-m-d',time()-$i*86400);
        $calendar=implode(' UNION ALL ',array_map(fn($d)=>"SELECT '$d' day_utc",$days));
        $window=['from'=>gmdate('Y-m-d',time()-31*86400).' 00:00:00','until'=>gmdate('Y-m-d',time()+86400).' 00:00:00'];
        $firstUtc=analyticsUtcSql('p.first_seen_at',$window);$joinedUtc=analyticsUtcSql('s.joined_at',$window);
        $leftUtc=analyticsUtcSql("IF(s.left_at IS NULL OR EXISTS(SELECT 1 FROM player_sessions n WHERE n.player_uuid=s.player_uuid AND n.server_name=s.server_name AND n.id<>s.id AND n.joined_at=s.left_at),s.joined_at,s.left_at)",$window);

        $pdo->exec("INSERT IGNORE INTO daily_player_visits(day_utc,server_name,player_uuid)
          SELECT c.day_utc,COALESCE(s.server_name,'network'),s.player_uuid FROM player_sessions s
          JOIN players p ON p.uuid=s.player_uuid JOIN ($calendar) c
          ON c.day_utc BETWEEN DATE($joinedUtc)
          AND DATE($leftUtc)
          WHERE COALESCE(s.left_at,p.last_seen_at,s.joined_at)>=DATE_SUB(NOW(),INTERVAL 31 DAY)");
        $pdo->exec("INSERT IGNORE INTO daily_player_visits(day_utc,server_name,player_uuid) SELECT day_utc,'network',player_uuid FROM daily_player_visits WHERE day_utc>=DATE_SUB(UTC_DATE(),INTERVAL 30 DAY)");
        // Keep old anonymous cohorts. Rebuild only the 30-day window whose individual history exists.
        $pdo->exec("INSERT IGNORE INTO retention_members(player_uuid,cohort_day)
          SELECT p.uuid,DATE($firstUtc) FROM players p
          WHERE DATE($firstUtc) BETWEEN DATE_SUB(UTC_DATE(),INTERVAL 30 DAY) AND UTC_DATE()
          AND (p.observability_cohort IS NOT NULL OR EXISTS(SELECT 1 FROM daily_player_visits v WHERE v.player_uuid=p.uuid AND v.server_name='network' AND v.day_utc=DATE($firstUtc)))");
        foreach([1,3,7,30] as $d)$pdo->exec("UPDATE retention_members m SET d$d=EXISTS(SELECT 1 FROM daily_player_visits v WHERE v.player_uuid=m.player_uuid AND v.server_name='network' AND v.day_utc=DATE_ADD(m.cohort_day,INTERVAL $d DAY))");
        $pdo->exec("UPDATE retention_cohorts SET new_players=0,d1=0,d3=0,d7=0,d30=0 WHERE day_utc>=DATE_SUB(UTC_DATE(),INTERVAL 30 DAY)");
        $pdo->exec("INSERT INTO retention_cohorts(day_utc,new_players,d1,d3,d7,d30) SELECT cohort_day,COUNT(*),SUM(d1),SUM(d3),SUM(d7),SUM(d30) FROM retention_members GROUP BY cohort_day ON DUPLICATE KEY UPDATE new_players=VALUES(new_players),d1=VALUES(d1),d3=VALUES(d3),d7=VALUES(d7),d30=VALUES(d30)");
        $pdo->exec("UPDATE players p JOIN retention_members m ON m.player_uuid=p.uuid SET p.observability_cohort=m.cohort_day");
        $pdo->exec("INSERT INTO daily_server_stats(day_utc,server_name,active_players) SELECT day_utc,server_name,COUNT(*) FROM daily_player_visits GROUP BY day_utc,server_name ON DUPLICATE KEY UPDATE active_players=GREATEST(active_players,VALUES(active_players))");
        $pdo->exec("UPDATE daily_server_stats s LEFT JOIN retention_cohorts c ON c.day_utc=s.day_utc SET s.new_players=COALESCE(c.new_players,0) WHERE s.server_name='network' AND s.day_utc>=DATE_SUB(UTC_DATE(),INTERVAL 30 DAY)");
        $pdo->prepare("INSERT INTO settings(setting_key,setting_value,updated_at) VALUES('analytics.cohorts.repaired','1',?) ON DUPLICATE KEY UPDATE setting_value='1',updated_at=VALUES(updated_at)")->execute([now()]);
        $pdo->commit();
    } catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();throw $e;}
}

function analyticsRetentionVisit(PDO $pdo,string $uuid,string $day): void
{
    $q=$pdo->prepare('SELECT first_seen_at,observability_cohort FROM players WHERE uuid=?');$q->execute([$uuid]);$p=$q->fetch();if(!$p)return;
    $first=gmdate('Y-m-d',strtotime((string)$p['first_seen_at']));
    $m=$pdo->prepare('SELECT * FROM retention_members WHERE player_uuid=? FOR UPDATE');$m->execute([$uuid]);$member=$m->fetch();
    if($member&&$member['cohort_day']!==$first) {
        // A late Paper payload may correct a proxy's provisional first-seen timestamp.
        $pdo->prepare('UPDATE retention_cohorts SET new_players=GREATEST(0,new_players-1),d1=GREATEST(0,d1-?),d3=GREATEST(0,d3-?),d7=GREATEST(0,d7-?),d30=GREATEST(0,d30-?) WHERE day_utc=?')->execute([$member['d1'],$member['d3'],$member['d7'],$member['d30'],$member['cohort_day']]);
        $pdo->prepare("UPDATE daily_server_stats SET new_players=GREATEST(0,new_players-1) WHERE day_utc=? AND server_name='network'")->execute([$member['cohort_day']]);
        $pdo->prepare('DELETE FROM retention_members WHERE player_uuid=?')->execute([$uuid]);
        $pdo->prepare('UPDATE players SET observability_cohort=NULL WHERE uuid=?')->execute([$uuid]);$member=false;
    }
    if($first<gmdate('Y-m-d',time()-30*86400)||$first>$day)return;
    if(!$member) {
        $proof=$pdo->prepare("SELECT 1 FROM daily_player_visits WHERE player_uuid=? AND server_name='network' AND day_utc=?");$proof->execute([$uuid,$first]);if(!$proof->fetchColumn())return;
        $ins=$pdo->prepare('INSERT IGNORE INTO retention_members(player_uuid,cohort_day) VALUES(?,?)');$ins->execute([$uuid,$first]);
        if($ins->rowCount()) {
            $pdo->prepare('INSERT INTO retention_cohorts(day_utc,new_players) VALUES(?,1) ON DUPLICATE KEY UPDATE new_players=new_players+1')->execute([$first]);
            $pdo->prepare("INSERT INTO daily_server_stats(day_utc,server_name,new_players) VALUES(?,'network',1) ON DUPLICATE KEY UPDATE new_players=new_players+1")->execute([$first]);
            $pdo->prepare('UPDATE players SET observability_cohort=? WHERE uuid=?')->execute([$first,$uuid]);
        }
    }
    // Test every observed target day so queued / out-of-order joins and prefilled daily visits work.
    foreach([1,3,7,30] as $d) {
        $target=gmdate('Y-m-d',strtotime($first.' UTC')+$d*86400);if($target>$day)continue;
        $up=$pdo->prepare("UPDATE retention_members m SET d$d=1 WHERE m.player_uuid=? AND m.d$d=0 AND EXISTS(SELECT 1 FROM daily_player_visits v WHERE v.player_uuid=m.player_uuid AND v.server_name='network' AND v.day_utc=?)");$up->execute([$uuid,$target]);
        if($up->rowCount())$pdo->prepare("UPDATE retention_cohorts SET d$d=d$d+1 WHERE day_utc=?")->execute([$first]);
    }
}

function analyticsExtent(): array
{
    static $extent=null;if($extent!==null)return $extent;
    $row=db()->query("SELECT MIN(first_at) first_at,MAX(last_at) last_at FROM (
        SELECT MIN(hour_utc) first_at,MAX(hour_utc) last_at FROM metric_hourly UNION ALL
        SELECT MIN(CAST(day_utc AS DATETIME)),MAX(CAST(day_utc AS DATETIME)) FROM daily_server_stats
    ) coverage")->fetch();
    return $extent=['first'=>$row['first_at']?:null,'last'=>$row['last_at']?:null];
}

function analyticsRange(?array $input=null): array
{
    $input??=$_GET;$today=gmdate('Y-m-d');$tz=new DateTimeZone('UTC');$extent=analyticsExtent();
    $preset=(string)($input['days']??'30');if(!in_array($preset,['1','7','30','90','180','365','730','0','custom'],true))$preset='30';
    $valid=static function($s)use($tz):bool{if(!is_string($s)||!preg_match('/^\d{4}-\d{2}-\d{2}$/',$s))return false;$d=DateTimeImmutable::createFromFormat('!Y-m-d',$s,$tz);return $d&&$d->format('Y-m-d')===$s&&$s>='1970-01-01'&&$s<='9998-12-31';};
    $error=null;
    if($preset==='custom'&&$valid($input['start']??null)&&$valid($input['end']??null)){$start=$input['start'];$end=min($today,$input['end']);if($start>$end){[$start,$end]=[$end,$start];$end=min($end,$today);$error='The start and end dates were reordered.';}}
    else{$end=$today;$start=$preset==='0'?substr((string)($extent['first']??$today),0,10):gmdate('Y-m-d',strtotime($today.' UTC')-(max(1,(int)$preset)-1)*86400);if($preset==='custom'){$preset='30';$start=gmdate('Y-m-d',strtotime($today.' UTC')-29*86400);$error='Choose valid start and end dates for a custom range.';}}
    $start=min($start,$today);$start=max('1970-01-01',$start);$length=(int)((strtotime($end.' UTC')-strtotime($start.' UTC'))/86400)+1;
    $auto=$length<=2?'hour':($length<=180?'day':($length<=1095?'week':'month'));
    $requested=(string)($input['group']??'auto');if(!in_array($requested,['auto','hour','day','week','month'],true))$requested='auto';
    $group=$requested==='auto'?$auto:$requested;
    $estimated=match($group){'hour'=>$length*24,'day'=>$length,'week'=>(int)ceil($length/7)+1,default=>(int)ceil($length/28)+1};
    if($estimated>800){$group=$auto;$error='This range is grouped into '.$group.'s to keep the graph readable.';}
    return ['preset'=>$preset,'start'=>$start,'end'=>$end,'from'=>$start.' 00:00:00','until'=>gmdate('Y-m-d H:i:s',strtotime($end.' UTC')+86400),'days'=>$length,'group'=>$group,'requested_group'=>$requested,'partial'=>$end===$today,'extent'=>$extent,'error'=>$error];
}

function analyticsQuery(array $r,array $extra=[]): array
{
    return array_merge(['days'=>$r['preset'],'start'=>$r['start'],'end'=>$r['end'],'group'=>$r['requested_group']],$extra);
}
function analyticsScope(string $field,string $source,array &$params): string
{
    if($source==='')return '';$params[]=$source;return " AND $field=?";
}
function analyticsLocalBounds(array $r): array
{
    return [date('Y-m-d H:i:s',strtotime($r['from'].' UTC')),date('Y-m-d H:i:s',strtotime($r['until'].' UTC'))];
}
function analyticsSqlBucket(string $field,string $group): string
{
    return match($group){'hour'=>"DATE_FORMAT($field,'%Y-%m-%d %H:00:00')",'day'=>"DATE($field)",'week'=>"DATE_SUB(DATE($field),INTERVAL WEEKDAY($field) DAY)",default=>"DATE_FORMAT($field,'%Y-%m-01')"};
}
function analyticsBucket(string $value,string $group): string
{
    $d=new DateTimeImmutable($value,new DateTimeZone('UTC'));
    return match($group){'hour'=>$d->format('Y-m-d H:00:00'),'day'=>$d->format('Y-m-d'),'week'=>$d->modify('monday this week')->format('Y-m-d'),default=>$d->format('Y-m-01')};
}
function analyticsLabels(array $r): array
{
    $cursor=new DateTimeImmutable(analyticsBucket($r['from'],$r['group']),new DateTimeZone('UTC'));$until=new DateTimeImmutable($r['until'],new DateTimeZone('UTC'));$out=[];$increment='1 '.$r['group'];
    while($cursor<$until&&count($out)<800){$out[]=$cursor->format($r['group']==='hour'?'Y-m-d H:00:00':'Y-m-d');$cursor=$cursor->modify('+'.$increment);}
    return $out;
}
function analyticsRows(string $sql,array $params=[]): array {$s=db()->prepare($sql);$s->execute($params);return $s->fetchAll();}
function analyticsGauge(string $metric): bool {return in_array($metric,['online_players','tps','mspt','memory_used_mb','memory_max_mb','uptime_seconds'],true);}
function analyticsMetricUnit(string $metric): string
{
    return match($metric){'tps'=>'TPS','mspt'=>'ms','memory_used_mb','memory_max_mb'=>'MB','uptime_seconds','session_seconds'=>'seconds','money_paid','trade_value'=>'$',default=>''};
}
function analyticsMetricSeries(array $r,string $source,array $metrics,string $plugin='',bool $byServer=false,string $dimension=''): array
{
    if(!$metrics)return [];
    static $coverageCache=[];
    $params=[$r['from'],$r['until']];$where='hour_utc>=? AND hour_utc<?'.analyticsScope('server_name',$source,$params);
    if($plugin!==''){$where.=' AND plugin_name=?';$params[]=$plugin;}
    if($dimension!==''){$where.=' AND dimension=?';$params[]=$dimension;}
    $bucket=analyticsSqlBucket('hour_utc',$r['group']);$coverageKey=jsonEncode([$bucket,$where,$params]);
    if(!isset($coverageCache[$coverageKey])){$coverageCache[$coverageKey]=[];foreach(analyticsRows("SELECT $bucket bucket,server_name FROM metric_hourly WHERE $where GROUP BY bucket,server_name",$params) as $row)$coverageCache[$coverageKey][$row['server_name']][$row['bucket']]=true;}
    $coverage=$coverageCache[$coverageKey];
    $where.=' AND metric IN('.implode(',',array_fill(0,count($metrics),'?')).')';array_push($params,...$metrics);
    $rows=analyticsRows("SELECT $bucket bucket,metric,".($byServer?'server_name':'\'all\'')." server,SUM(value_sum) total,SUM(sample_count) samples FROM metric_hourly WHERE $where GROUP BY bucket,metric,server ORDER BY bucket",$params);
    $out=[];$labels=analyticsLabels($r);$colors=['#65de9a','#c58dff','#69b6ff','#ff76d8','#ffd27e','#63e0e4'];
    foreach($rows as $row){$key=$row['metric'].'|'.$row['server'];if(!isset($out[$key]))$out[$key]=['key'=>$key,'label'=>obsMetricLabel($row['metric']).($byServer?' · '.strtoupper($row['server']):''),'metric'=>$row['metric'],'server'=>$row['server'],'unit'=>analyticsMetricUnit($row['metric']),'color'=>$colors[count($out)%count($colors)],'values'=>array_fill_keys($labels,null)];$out[$key]['values'][$row['bucket']]=analyticsGauge($row['metric'])?(float)$row['total']/max(1,(int)$row['samples']):(float)$row['total'];}
    foreach($out as &$series){if(analyticsGauge($series['metric']))continue;foreach($series['values'] as $label=>&$value){if($value!==null)continue;$observed=$byServer?isset($coverage[$series['server']][$label]):(bool)array_filter($coverage,fn($v)=>isset($v[$label]));if($observed)$value=0;}unset($value);}unset($series);
    return array_values($out);
}
function analyticsDailySeries(array $r,string $source=''): array
{
    // Weekly/monthly values are averages of daily unique counts, never "unique players per month".
    $group=$r['group']==='hour'?'day':$r['group'];$dailyRange=[...$r,'group'=>$group];$labels=analyticsLabels($dailyRange);$bucket=analyticsSqlBucket('day_utc',$group);
    $rows=analyticsRows("SELECT $bucket bucket,AVG(active_players) active,AVG(new_players) newcomers FROM daily_server_stats WHERE day_utc>=? AND day_utc<=? AND server_name=? GROUP BY bucket ORDER BY bucket",[$r['start'],$r['end'],$source?:'network']);
    $active=$new=array_fill_keys($labels,null);foreach($rows as $row){$active[$row['bucket']]=(float)$row['active'];$new[$row['bucket']]=(float)$row['newcomers'];}
    $out=[['key'=>'active','label'=>'Daily active players','color'=>'#69b6ff','values'=>$active,'unit'=>'players']];
    if($source==='')$out[]=['key'=>'new','label'=>'New players per day','color'=>'#65de9a','values'=>$new,'unit'=>'players'];return $out;
}
function analyticsRetention(array $r): array
{
    $rows=analyticsRows('SELECT * FROM retention_cohorts WHERE day_utc>=? AND day_utc<=? AND new_players>0 ORDER BY day_utc',[$r['start'],$r['end']]);$out=[];$today=gmdate('Y-m-d');
    foreach([1,3,7,30] as $d){$eligible=0;$returned=0;$pending=0;$next=null;foreach($rows as $row){$ready=gmdate('Y-m-d',strtotime($row['day_utc'].' UTC')+($d+1)*86400);if($ready<=$today){$eligible+=(int)$row['new_players'];$returned+=min((int)$row['new_players'],(int)$row['d'.$d]);}else{$pending+=(int)$row['new_players'];$next=$next===null?$ready:min($next,$ready);}}
        $out[$d]=['eligible'=>$eligible,'returned'=>$returned,'percent'=>$eligible?percent($returned,$eligible):null,'pending'=>$pending,'next'=>$next,'state'=>$eligible?'measured':($pending?'waiting':'no-cohort')];}
    return ['summary'=>$out,'cohorts'=>$rows];
}
function analyticsRetentionSeries(array $r,array $cohorts): array
{
    $group=$r['group']==='hour'?'day':$r['group'];$rr=[...$r,'group'=>$group];$labels=analyticsLabels($rr);$out=[];$today=gmdate('Y-m-d');
    foreach([1=>'#65de9a',3=>'#69b6ff',7=>'#c58dff',30=>'#ff76d8'] as $d=>$color){$groups=[];foreach($cohorts as $c){if(gmdate('Y-m-d',strtotime($c['day_utc'].' UTC')+($d+1)*86400)>$today)continue;$b=analyticsBucket($c['day_utc'],$group);$groups[$b]['eligible']=($groups[$b]['eligible']??0)+(int)$c['new_players'];$groups[$b]['returned']=($groups[$b]['returned']??0)+min((int)$c['new_players'],(int)$c['d'.$d]);}$v=array_fill_keys($labels,null);foreach($groups as $b=>$g)$v[$b]=percent($g['returned'],$g['eligible']);$out[]=['key'=>'d'.$d,'label'=>'D'.$d,'color'=>$color,'values'=>$v,'unit'=>'%'];}return $out;
}
function analyticsStaffRows(array $r,string $source=''): array
{
    [$from,$until]=analyticsLocalBounds($r);$params=[$from,$until];$scope=analyticsScope('a.source_server',$source,$params);
    return analyticsRows("SELECT u.id,u.display_name,u.minecraft_uuid,u.minecraft_username,u.role,COUNT(*) total,
        SUM(a.action LIKE 'command.%') commands,SUM(a.source_server IS NULL) portal_actions
        FROM audit_logs a JOIN users u ON u.active=1 AND (
          (a.actor_user_id=u.id AND (a.actor_minecraft_uuid=u.minecraft_uuid OR (a.actor_minecraft_uuid IS NULL AND a.source_server IS NULL) OR (a.actor_minecraft_uuid IS NULL AND LOWER(a.actor_label)=LOWER(u.minecraft_username)))) OR (a.actor_user_id IS NULL AND u.minecraft_uuid IS NOT NULL AND a.actor_minecraft_uuid=u.minecraft_uuid)
          OR (a.actor_user_id IS NULL AND a.actor_minecraft_uuid IS NULL AND u.minecraft_username IS NOT NULL AND LOWER(a.actor_label)=LOWER(u.minecraft_username)))
        WHERE a.created_at>=? AND a.created_at<? $scope GROUP BY u.id,u.display_name,u.minecraft_uuid,u.minecraft_username,u.role ORDER BY total DESC LIMIT 100",$params);
}
function analyticsRankedMetrics(array $r,string $source,string $metric,string $field='dimension',string $plugin=''): array
{
    if(!in_array($field,['dimension','server_name','plugin_name'],true))$field='dimension';$params=[$r['from'],$r['until'],$metric];$where='hour_utc>=? AND hour_utc<? AND metric=?'.analyticsScope('server_name',$source,$params);if($plugin!==''){$where.=' AND plugin_name=?';$params[]=$plugin;}
    return analyticsRows("SELECT $field label,SUM(value_sum) total FROM metric_hourly WHERE $where GROUP BY $field ORDER BY total DESC LIMIT 30",$params);
}
function analyticsCounterTotal(array $r,string $source,string $metric): float
{
    $p=[$r['from'],$r['until'],$metric];return statScalar('SELECT COALESCE(SUM(value_sum),0) FROM metric_hourly WHERE hour_utc>=? AND hour_utc<? AND metric=?'.analyticsScope('server_name',$source,$p),$p);
}
function analyticsDateSeries(array $r,string $table,string $field,string $label,string $color): array
{
    if(!in_array($table,['cases','punishments'],true)||!in_array($field,['created_at','issued_at','resolved_at'],true))return [];
    [$from,$until]=analyticsLocalBounds($r);$bucket=analyticsSqlBucket(analyticsUtcSql($field,$r),$r['group']);$v=array_fill_keys(analyticsLabels($r),0);
    foreach(analyticsRows("SELECT $bucket bucket,COUNT(*) total FROM $table WHERE $field>=? AND $field<? GROUP BY bucket",[$from,$until]) as $row)$v[$row['bucket']]=(int)$row['total'];
    return ['key'=>$field,'label'=>$label,'color'=>$color,'unit'=>'','values'=>$v];
}

function analyticsJoinClock(array $r,string $source): array
{
    $params=[$r['from'],$r['until']];$where="metric='player_joins' AND hour_utc>=? AND hour_utc<?".analyticsScope('server_name',$source,$params);
    $rows=analyticsRows("SELECT HOUR(hour_utc) hour,SUM(value_sum) total FROM metric_hourly WHERE $where GROUP BY hour ORDER BY hour",$params);if(!$rows)return [];
    $v=[];for($h=0;$h<24;$h++)$v[sprintf('%02d:00',$h)]=0;foreach($rows as $row)$v[sprintf('%02d:00',$row['hour'])]=(float)$row['total'];
    return [['key'=>'join-hours','label'=>'Recorded joins','color'=>'#69b6ff','unit'=>'','values'=>$v]];
}

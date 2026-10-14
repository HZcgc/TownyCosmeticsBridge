<?php
declare(strict_types=1);

function connectorHealthSchema(PDO $pdo): void
{
    $pdo->exec("CREATE TABLE IF NOT EXISTS connector_heartbeats (
        server_name VARCHAR(64) PRIMARY KEY, captured_at DATETIME NOT NULL, received_at DATETIME NOT NULL,
        online_players INT UNSIGNED NOT NULL DEFAULT 0, tps DOUBLE NULL, mspt DOUBLE NULL,
        uptime_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0, metadata_json TEXT NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    $columns=$pdo->query("SHOW COLUMNS FROM notification_monitors LIKE 'recovery_since'")->fetch();
    if(!$columns)$pdo->exec('ALTER TABLE notification_monitors ADD recovery_since DATETIME NULL');
    $pdo->prepare("INSERT INTO settings(setting_key,setting_value,updated_at) VALUES('schema.version','11',?) ON DUPLICATE KEY UPDATE setting_value='11',updated_at=VALUES(updated_at)")->execute([now()]);
}

/** Small priority request: independent of the ordinary ingest transaction and its retry backlog. */
function apiConnectorHeartbeat(PDO $pdo,array $client,array $payload): never
{
    $server=strtolower(trim((string)($payload['server']??'')));
    $captured=strtotime((string)($payload['occurred_at']??''));
    $metadata=$payload['metadata']??[];
    if(!preg_match('/^[a-z0-9_.-]{1,64}$/',$server)||$captured===false||$captured>time()+30
        ||!is_array($metadata)||strlen(jsonEncode($metadata))>12000)apiResponse(422,['error'=>'invalid_heartbeat']);
    $online=$payload['online_players']??0;
    if(!is_numeric($online)||(int)$online<0||(int)$online>1000000)apiResponse(422,['error'=>'invalid_heartbeat']);
    $tps=isset($payload['tps'])&&is_numeric($payload['tps'])&&is_finite((float)$payload['tps'])?(float)$payload['tps']:null;
    $mspt=isset($payload['mspt'])&&is_numeric($payload['mspt'])&&is_finite((float)$payload['mspt'])?(float)$payload['mspt']:null;
    try{
        $pdo->beginTransaction();
        $pdo->prepare("INSERT INTO connector_heartbeats(server_name,captured_at,received_at,online_players,tps,mspt,uptime_seconds,metadata_json) VALUES(?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE online_players=IF(VALUES(captured_at)>=captured_at,VALUES(online_players),online_players),
            tps=IF(VALUES(captured_at)>=captured_at,VALUES(tps),tps),mspt=IF(VALUES(captured_at)>=captured_at,VALUES(mspt),mspt),
            uptime_seconds=IF(VALUES(captured_at)>=captured_at,VALUES(uptime_seconds),uptime_seconds),
            metadata_json=IF(VALUES(captured_at)>=captured_at,VALUES(metadata_json),metadata_json),
            received_at=IF(VALUES(captured_at)>=captured_at,VALUES(received_at),received_at),captured_at=GREATEST(captured_at,VALUES(captured_at))")
            ->execute([$server,date('Y-m-d H:i:s',$captured),now(),(int)$online,$tps,$mspt,max(0,(int)($payload['uptime_seconds']??0)),jsonEncode($metadata)]);
        $pdo->prepare('UPDATE api_clients SET last_seen_at=? WHERE id=?')->execute([now(),$client['id']]);
        $pdo->commit();
        apiResponse(202,['accepted'=>true,'heartbeat'=>true,'server'=>$server]);
    }catch(Throwable $error){if($pdo->inTransaction())$pdo->rollBack();apiResponse(503,['error'=>'heartbeat_storage_unavailable']);}
}

/** Liveness uses captured time as well as receipt time; an old queued report cannot look current. */
function hqServerStatuses(?PDO $pdo=null): array
{
    $pdo??=db();$rows=[];
    foreach($pdo->query('SELECT * FROM server_status ORDER BY server_name')->fetchAll() as $row){$row['snapshot_captured_at']=$row['captured_at'];$rows[strtolower($row['server_name'])]=$row;}
    foreach($pdo->query('SELECT * FROM connector_heartbeats ORDER BY server_name')->fetchAll() as $live){
        $key=strtolower($live['server_name']);$old=$rows[$key]??null;
        if($old&&strtotime($old['captured_at'])>strtotime($live['captured_at']))continue;
        $row=$old??['server_name'=>$key,'plugin_count'=>0,'world_count'=>0,'minecraft_version'=>'Not reported','java_version'=>'Not reported','memory_used_mb'=>null,'memory_max_mb'=>null,'snapshot_captured_at'=>null];
        foreach(['captured_at','online_players','tps','mspt','uptime_seconds','metadata_json'] as $field)$row[$field]=$live[$field];
        $row['updated_at']=$live['received_at'];$row['status_source']='priority heartbeat';$rows[$key]=$row;
    }
    return array_values($rows);
}

function hqServerStatus(PDO $pdo,string $server): ?array
{
    foreach(hqServerStatuses($pdo) as $row)if(strtolower($row['server_name'])===strtolower($server))return $row;
    return null;
}

function hqStatusFresh(array $row,?int $at=null,int $window=180): bool
{
    $at??=time();$captured=strtotime((string)$row['captured_at']);$received=strtotime((string)$row['updated_at']);
    return $captured!==false&&$received!==false&&$captured<=$at+30&&$received<=$at+30&&$at-min($captured,$received)<=$window;
}

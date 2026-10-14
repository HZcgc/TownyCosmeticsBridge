<?php
declare(strict_types=1);

try {
    require_once __DIR__ . '/app/bootstrap.php';
} catch (Throwable $error) {
    $reference=bin2hex(random_bytes(6));
    error_log('[StaffHQ API '.$reference.'] bootstrap_unavailable '.get_class($error).' at '.basename($error->getFile()).':'.$error->getLine());
    http_response_code(503);header('Content-Type: application/json; charset=utf-8');header('Cache-Control: no-store');header('Retry-After: 60');
    echo json_encode(['error'=>'portal_unavailable','reference'=>$reference]);exit;
}

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-TR-API-Version: 8');

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    try {
        db()->query('SELECT 1');
        apiResponse(200, ['status' => 'ok', 'service' => 'Towny Reborn Staff API', 'version' => 8, 'batch' => true, 'remote_actions' => true, 'validation_details' => true, 'priority_heartbeat'=>true, 'journey'=>true]);
    } catch (Throwable) {
        apiResponse(503, ['status' => 'unavailable']);
    }
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    apiResponse(405, ['error' => 'method_not_allowed']);
}

$token = apiBearerToken();
if ($token === '' || !str_starts_with($token, 'tr_live_')) {
    apiResponse(401, ['error' => 'unauthorized']);
}

$pdo = db();
$statement = $pdo->prepare('SELECT * FROM api_clients WHERE token_hash = ? AND active = 1 LIMIT 1');
$statement->execute([hash('sha256', $token)]);
$client = $statement->fetch();
if (!$client) {
    apiResponse(401, ['error' => 'unauthorized']);
}

$length = (int) ($_SERVER['CONTENT_LENGTH'] ?? 0);
if ($length > 1_048_576) {
    apiResponse(413, ['error' => 'payload_too_large']);
}

$raw = file_get_contents('php://input');
if ($raw === false || $raw === '') {
    apiResponse(400, ['error' => 'empty_payload']);
}

try {
    $payload = json_decode($raw, true, 64, JSON_THROW_ON_ERROR);
} catch (JsonException) {
    apiResponse(400, ['error' => 'invalid_json']);
}
if (!is_array($payload)) {
    apiResponse(400, ['error' => 'invalid_payload']);
}

$operation = strtolower(trim((string) ($payload['operation'] ?? '')));
if ($operation === 'connector.heartbeat') apiConnectorHeartbeat($pdo,$client,$payload);
if ($operation === 'actions.pull') apiPullActions($pdo, $client, $payload);
if ($operation === 'actions.result') apiActionResult($pdo, $client, $payload);

$isBatch = array_key_exists('events', $payload);
if ($isBatch) {
    $events = $payload['events'];
    if (!is_array($events) || !array_is_list($events) || count($events) < 1 || count($events) > 25) {
        apiResponse(422, ['error' => 'invalid_batch', 'maximum_events' => 25]);
    }
    foreach ($events as $event) {
        if (!is_array($event)) {
            apiResponse(422, ['error' => 'invalid_batch_event']);
        }
    }
} else {
    $events = [$payload];
}

$pdo->beginTransaction();

try {
    $accepted = 0;
    $duplicates = 0;
    $types = [];
    foreach ($events as $eventIndex => $event) {
        $result = apiProcessEvent($pdo, $client, $event);
        $types[] = $result['type'];
        if ($result['duplicate']) {
            $duplicates++;
        } else {
            $accepted++;
        }
    }

    obsFlush($pdo);
    $pdo->prepare('UPDATE api_clients SET last_seen_at = ? WHERE id = ?')->execute([now(), (int) $client['id']]);
    $pdo->commit();
    apiRunRetention($pdo);

    if ($isBatch) {
        apiResponse(202, [
            'accepted' => true,
            'accepted_count' => $accepted,
            'duplicate_count' => $duplicates,
            'event_count' => count($events),
        ]);
    }
    $duplicate = $duplicates === 1;
    apiResponse($duplicate ? 200 : 202, [
        'accepted' => true,
        'duplicate' => $duplicate,
        'event_type' => $types[0],
    ]);
} catch (InvalidArgumentException $exception) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    // Only fixed validation messages become public error codes. Keep the entire
    // transaction rolled back so the connector can safely retry valid events.
    $validationCode = trim((string) preg_replace('/[^a-z0-9]+/', '_', strtolower($exception->getMessage())), '_');
    apiResponse(422, [
        'error' => 'invalid_event_data',
        'validation_error' => $validationCode,
        'event_index' => (int) ($eventIndex ?? 0),
        'event_id' => is_scalar($event['event_id'] ?? null) ? (string) $event['event_id'] : '',
        'event_type' => is_scalar($event['type'] ?? null) ? (string) $event['type'] : '',
    ]);
} catch (PDOException $exception) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    $reference=bin2hex(random_bytes(6));
    error_log('[StaffHQ API '.$reference.'] storage_error SQLSTATE '.$exception->getCode().' driver '.(string)($exception->errorInfo[1]??'unknown').' at '.basename($exception->getFile()).':'.$exception->getLine());
    apiResponse(500, ['error' => 'storage_error','reference'=>$reference]);
} catch (Throwable $exception) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    $reference=bin2hex(random_bytes(6));
    error_log('[StaffHQ API '.$reference.'] processing_error '.get_class($exception).' at '.basename($exception->getFile()).':'.$exception->getLine());
    apiResponse(500, ['error' => 'processing_error','reference'=>$reference]);
}

/** @return array{type:string,duplicate:bool} */
function apiProcessEvent(PDO $pdo, array $client, array $payload): array
{
    $type = strtolower(trim((string) ($payload['type'] ?? '')));
    $eventId = trim((string) ($payload['event_id'] ?? '')) ?: null;
    $source = trim((string) ($payload['server'] ?? ($_SERVER['HTTP_X_TR_SOURCE'] ?? 'network'))) ?: 'network';
    $allowedTypes = ['player.update', 'player.join', 'player.quit', 'anticheat.alert', 'punishment.created', 'staff.action', 'command.action', 'chat.message', 'server.snapshot','telemetry.batch','plugin.updates','diagnostic.alert','game.event','integration.snapshot'];
    if (!in_array($type, $allowedTypes, true)) {
        throw new InvalidArgumentException('Unsupported event type.');
    }

    $occurredAt = apiDate((string) ($payload['occurred_at'] ?? ''));
    $insert = $pdo->prepare(
        'INSERT INTO ingest_events (api_client_id, event_type, source_server, event_id, payload_json, received_at)
         VALUES (?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE id = id'
    );
    $storedPayload = ['type'=>$type,'event_id'=>$eventId];
    $insert->execute([
        (int) $client['id'],
        $type,
        mb_substr($source, 0, 64),
        $eventId !== null ? mb_substr($eventId, 0, 96) : null,
        jsonEncode($storedPayload),
        now(),
    ]);

    if ($eventId !== null && $insert->rowCount() === 0) {
        return ['type' => $type, 'duplicate' => true];
    }

    if ($eventId !== null) {
        $receipt=$pdo->prepare('INSERT IGNORE INTO observability_receipts(server_name,event_id,received_at) VALUES(?,?,?)');
        $receipt->execute([mb_substr($source,0,64),mb_substr($eventId,0,96),now()]);
        if ($receipt->rowCount()===0) return ['type'=>$type,'duplicate'=>true];
    }

    if ($type==='player.join') obsMetric($source,'Server','player_joins','',$occurredAt);
    if ($type==='anticheat.alert') obsMetric($source,'Vulcan','anticheat_alerts',obsText($payload['check']??'',128),$occurredAt);
    if ($type==='punishment.created') obsMetric($source,'LiteBans','punishments',obsText($payload['punishment_type']??'',128),$occurredAt);
    if ($type==='command.action') obsMetric($source,'Commands','commands',obsText($payload['action']??'',128),$occurredAt);
    if ($type==='chat.message') obsMetric($source,'Chat','messages',obsText($payload['channel']??'global',128),$occurredAt);
    switch ($type) {
        case 'player.update':
        case 'player.join':
        case 'player.quit':
            apiUpsertPlayer($pdo, $payload, $type, $source, $occurredAt);
            break;
        case 'anticheat.alert':
            apiCreateAlert($pdo, $payload, $source, $occurredAt);
            break;
        case 'punishment.created':
            apiCreatePunishment($pdo, $payload, $occurredAt);
            break;
        case 'staff.action':
            apiCreateStaffAudit($pdo, $payload, $source, $occurredAt);
            break;
        case 'command.action':
            apiCreateCommandAudit($pdo, $payload, $source, $occurredAt);
            break;
        case 'chat.message':
            apiCreateChatMessage($pdo, $payload, $source, $occurredAt, $eventId);
            break;
        case 'game.event': gameIngest($pdo,$payload,$source,$occurredAt); break;
        case 'integration.snapshot': gameSnapshot($pdo,$payload,$source,$occurredAt); break;
        case 'telemetry.batch': obsTelemetry($pdo,$payload,$source); break;
        case 'plugin.updates': obsPluginUpdates($pdo,$payload,$source); break;
        case 'diagnostic.alert': obsDiagnostic($pdo,$payload,$source,$occurredAt); break;
        case 'server.snapshot':
            apiServerSnapshot($pdo, $payload, $source, $occurredAt);
            break;
    }

    notificationOnEvent($pdo,$payload,$type,$source,$occurredAt);
    return ['type' => $type, 'duplicate' => false];
}

function apiPullActions(PDO $pdo, array $client, array $payload): never
{
    $server = trim((string) ($payload['server'] ?? 'smp')) ?: 'smp';
    if (!preg_match('/^[A-Za-z0-9_.-]{1,64}$/', $server)) apiResponse(422, ['error' => 'invalid_server']);
    $pdo->beginTransaction();
    try {
        $pdo->prepare(
            "UPDATE action_queue SET status = 'pending', claimed_by = NULL, claimed_at = NULL
             WHERE target_server = ? AND status = 'claimed' AND claimed_at < DATE_SUB(NOW(), INTERVAL 15 MINUTE)"
        )->execute([$server]);
        $select = $pdo->prepare(
            "SELECT id, action_type, player_uuid, player_name, reason, duration_seconds
             FROM action_queue WHERE target_server = ? AND status = 'pending' ORDER BY requested_at ASC LIMIT 5 FOR UPDATE"
        );
        $select->execute([$server]);
        $actions = $select->fetchAll();
        $claim = $pdo->prepare("UPDATE action_queue SET status = 'claimed', claimed_by = ?, claimed_at = ? WHERE id = ? AND status = 'pending'");
        $punishment = $pdo->prepare("UPDATE punishments p JOIN action_queue q ON q.punishment_id = p.id SET p.execution_status = 'claimed' WHERE q.id = ?");
        foreach ($actions as $action) {
            $claim->execute([(int) $client['id'], now(), (int) $action['id']]);
            $punishment->execute([(int) $action['id']]);
        }
        $pdo->prepare('UPDATE api_clients SET last_seen_at = ? WHERE id = ?')->execute([now(), (int) $client['id']]);
        $pdo->commit();
        apiResponse(200, ['ok' => true, 'actions' => $actions]);
    } catch (Throwable) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        apiResponse(500, ['error' => 'action_queue_error']);
    }
}

function apiActionResult(PDO $pdo, array $client, array $payload): never
{
    $id = (int) ($payload['action_id'] ?? 0);
    $status = strtolower(trim((string) ($payload['status'] ?? 'failed')));
    if ($id <= 0 || !in_array($status, ['succeeded', 'failed'], true)) apiResponse(422, ['error' => 'invalid_action_result']);
    $message = mb_substr(trim((string) ($payload['message'] ?? '')), 0, 1000);
    $pdo->beginTransaction();
    try {
        $find = $pdo->prepare('SELECT * FROM action_queue WHERE id = ? AND claimed_by = ? AND status = ? FOR UPDATE');
        $find->execute([$id, (int) $client['id'], 'claimed']);
        $action = $find->fetch();
        if (!$action) {
            $pdo->rollBack();
            apiResponse(409, ['error' => 'action_not_claimed']);
        }
        $pdo->prepare('UPDATE action_queue SET status = ?, completed_at = ?, result_message = ? WHERE id = ?')
            ->execute([$status, now(), $message ?: null, $id]);
        if (!empty($action['punishment_id'])) {
            $pdo->prepare('UPDATE punishments SET execution_status = ? WHERE id = ?')->execute([$status, (int) $action['punishment_id']]);
        }
        $pdo->prepare(
            'INSERT INTO audit_logs (actor_user_id, actor_label, action, target_type, target_id, metadata_json, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)'
        )->execute([
            (int) $action['requested_by'], 'Staff HQ', 'punishment.' . $status, 'player', $action['player_uuid'] ?: $action['player_name'],
            jsonEncode(['queue_id' => $id, 'type' => $action['action_type'], 'connector_message' => $message]), now(),
        ]);
        if($status==='failed'&&notificationSettings()['enabled']&&notificationSettings()['errors'])notificationEnqueue($pdo,'action-failed:'.$id,'action_failed',(string)$action['target_server'],'high','Staff action failed',notificationDetails('The connector could not carry out a requested moderation action.',['Player'=>$action['player_name'],'Action'=>$action['action_type'],'Result'=>$message],'punishments'));
        $pdo->prepare('UPDATE api_clients SET last_seen_at = ? WHERE id = ?')->execute([now(), (int) $client['id']]);
        $pdo->commit();
        apiResponse(200, ['ok' => true]);
    } catch (Throwable) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        apiResponse(500, ['error' => 'action_result_error']);
    }
}

function apiServerSnapshot(PDO $pdo, array $payload, string $source, string $occurredAt): void
{
    $plugins = $payload['plugins'] ?? [];
    if (!is_array($plugins)) $plugins = [];
    $statement = $pdo->prepare(
        'INSERT INTO server_status (server_name, online_players, max_players, tps, mspt, memory_used_mb, memory_max_mb, uptime_seconds, world_count, plugin_count, minecraft_version, java_version, metadata_json, captured_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE online_players = VALUES(online_players), max_players = VALUES(max_players), tps = VALUES(tps), mspt = VALUES(mspt), memory_used_mb = VALUES(memory_used_mb), memory_max_mb = VALUES(memory_max_mb), uptime_seconds = VALUES(uptime_seconds), world_count = VALUES(world_count), plugin_count = VALUES(plugin_count), minecraft_version = VALUES(minecraft_version), java_version = VALUES(java_version), metadata_json = VALUES(metadata_json), captured_at = VALUES(captured_at), updated_at = VALUES(updated_at)'
    );
    $statement->execute([
        mb_substr($source, 0, 64), max(0, (int) ($payload['online_players'] ?? 0)), max(0, (int) ($payload['max_players'] ?? 0)),
        isset($payload['tps']) ? max(0, min(100, (float) $payload['tps'])) : null,
        isset($payload['mspt']) ? max(0, min(10000, (float) $payload['mspt'])) : null,
        isset($payload['memory_used_mb']) ? max(0, (int) $payload['memory_used_mb']) : null,
        isset($payload['memory_max_mb']) ? max(0, (int) $payload['memory_max_mb']) : null,
        isset($payload['uptime_seconds']) ? max(0, (int) $payload['uptime_seconds']) : null,
        isset($payload['world_count']) ? max(0, (int) $payload['world_count']) : null,
        count($plugins), mb_substr((string) ($payload['minecraft_version'] ?? ''), 0, 96) ?: null,
        mb_substr((string) ($payload['java_version'] ?? ''), 0, 96) ?: null,
        isset($payload['metadata']) ? jsonEncode($payload['metadata']) : null, $occurredAt, now(),
    ]);
    obsSnapshot($pdo,$payload,$source,$occurredAt);
}

function apiBearerToken(): string
{
    $authorization = (string) ($_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '');
    if ($authorization === '' && function_exists('apache_request_headers')) {
        $headers = apache_request_headers();
        $authorization = (string) ($headers['Authorization'] ?? $headers['authorization'] ?? '');
    }
    if (preg_match('/^Bearer\s+(.+)$/i', trim($authorization), $matches)) {
        return trim($matches[1]);
    }
    return trim((string) ($_SERVER['HTTP_X_API_TOKEN'] ?? ''));
}

function apiUpsertPlayer(PDO $pdo,array $payload,string $type,string $source,string $at): void
{
    $uuid=normalizeUuid((string)($payload['uuid']??''));$name=obsText($payload['username']??'',32);
    if(!$uuid||$name==='')throw new InvalidArgumentException('Invalid player identity.');
    $online=$type==='player.join'?1:($type==='player.quit'?0:(int)!empty($payload['online']));
    $first=apiDate((string)($payload['first_seen_at']??$at));$platform=($payload['platform']??'java')==='bedrock'?'bedrock':'java';
    $query=$pdo->prepare('SELECT * FROM players WHERE uuid=?');$query->execute([$uuid]);$old=$query->fetch();
    $newer=!$old||strtotime($at)>=strtotime((string)$old['last_seen_at']);
    if($old && $type==='player.quit' && $source!=='proxy' && $old['last_server']!==$source)$online=(int)$old['online'];
    if(!$old) {
        $pdo->prepare('INSERT INTO players(uuid,username,platform,first_seen_at,last_seen_at,last_server,online,playtime_seconds,balance,town_name,risk_score,metadata_json,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)')
          ->execute([$uuid,$name,$platform,$first,$at,$source,$online,max(0,(int)($payload['playtime_seconds']??0)),$payload['balance']??null,obsText($payload['town']??'',96)?:null,0,isset($payload['metadata'])?jsonEncode($payload['metadata']):null,now()]);
    } elseif($newer) {
        $pdo->prepare('UPDATE players SET username=?,platform=?,first_seen_at=LEAST(COALESCE(first_seen_at,?),?),last_seen_at=?,last_server=?,online=?,playtime_seconds=GREATEST(playtime_seconds,?),balance=COALESCE(?,balance),town_name=COALESCE(?,town_name),metadata_json=COALESCE(?,metadata_json),updated_at=? WHERE uuid=?')
          ->execute([$name,$platform,$first,$first,$at,$type==='player.quit'?$old['last_server']:$source,$online,max(0,(int)($payload['playtime_seconds']??0)),$payload['balance']??null,isset($payload['town'])?obsText($payload['town'],96):null,isset($payload['metadata'])?jsonEncode($payload['metadata']):null,now(),$uuid]);
    }
    if($old&&!$newer)$pdo->prepare('UPDATE players SET first_seen_at=LEAST(COALESCE(first_seen_at,?),?) WHERE uuid=?')->execute([$first,$first,$uuid]);
    if(!obsRecent($at))return;
    if($type!=='player.quit')obsVisit($pdo,$uuid,$source,$at);
    if($type==='player.join') {
        $pdo->prepare('UPDATE player_sessions SET left_at=?,duration_seconds=GREATEST(0,TIMESTAMPDIFF(SECOND,joined_at,?)) WHERE player_uuid=? AND server_name=? AND left_at IS NULL AND joined_at<=?')->execute([$at,$at,$uuid,$source,$at]);
        $pdo->prepare('INSERT INTO player_sessions(player_uuid,player_name,server_name,joined_at,created_at) VALUES(?,?,?,?,?)')->execute([$uuid,$name,$source,$at,now()]);
    } elseif($type==='player.quit') {
        $q=$pdo->prepare('SELECT id,joined_at FROM player_sessions WHERE player_uuid=? AND server_name=? AND left_at IS NULL AND joined_at<=? ORDER BY joined_at DESC LIMIT 1');$q->execute([$uuid,$source,$at]);$session=$q->fetch();
        if($session){$duration=max(0,strtotime($at)-strtotime($session['joined_at']));$pdo->prepare('UPDATE player_sessions SET left_at=?,duration_seconds=? WHERE id=?')->execute([$at,$duration,$session['id']]);obsMetric($source,'Server','session_seconds','',$at,(float)$duration);}
    }
}

function apiCreateAlert(PDO $pdo, array $payload, string $source, string $occurredAt): void
{
    if (!obsRecent($occurredAt)) return;
    $playerName = trim((string) ($payload['username'] ?? $payload['player_name'] ?? ''));
    $check = trim((string) ($payload['check'] ?? $payload['check_name'] ?? ''));
    if ($playerName === '' || $check === '') {
        throw new InvalidArgumentException('Invalid alert.');
    }
    $vl = isset($payload['vl']) ? (float) $payload['vl'] : null;
    $severity = strtolower((string) ($payload['severity'] ?? ''));
    if (!in_array($severity, ['low', 'medium', 'high', 'critical'], true)) {
        $severity = $vl !== null && $vl >= 15 ? 'critical' : ($vl !== null && $vl >= 8 ? 'high' : ($vl !== null && $vl >= 3 ? 'medium' : 'low'));
    }
    $uuid = normalizeUuid((string) ($payload['uuid'] ?? ''));
    if ($uuid === null) {
        $knownPlayer = apiResolvePlayer($pdo, $playerName);
        $uuid = $knownPlayer['uuid'] ?? null;
    }
    $statement = $pdo->prepare('INSERT INTO alerts (player_uuid, player_name, check_name, violation_level, severity, server_name, message, evidence_json, status, occurred_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
    $statement->execute([
        $uuid,
        mb_substr($playerName, 0, 32),
        mb_substr($check, 0, 96),
        $vl,
        $severity,
        mb_substr($source, 0, 64),
        isset($payload['message']) ? mb_substr((string) $payload['message'], 0, 2000) : null,
        isset($payload['evidence']) ? jsonEncode($payload['evidence']) : null,
        'open',
        $occurredAt,
        now(),
    ]);
    if ($uuid) {
        $increment = match ($severity) { 'critical' => 15, 'high' => 9, 'medium' => 4, default => 1 };
        $pdo->prepare('UPDATE players SET risk_score = LEAST(100, risk_score + ?), updated_at = ? WHERE uuid = ?')->execute([$increment, now(), $uuid]);
    }
}

function apiCreatePunishment(PDO $pdo, array $payload, string $occurredAt): void
{
    $playerName = trim((string) ($payload['username'] ?? $payload['player_name'] ?? ''));
    $type = strtolower(trim((string) ($payload['punishment_type'] ?? $payload['action'] ?? '')));
    $reason = trim((string) ($payload['reason'] ?? ''));
    if ($playerName === '' || $reason === '' || !in_array($type, ['warning', 'kick', 'mute', 'tempban', 'ban'], true)) {
        throw new InvalidArgumentException('Invalid punishment.');
    }
    $duration = isset($payload['duration_seconds']) ? max(0, (int) $payload['duration_seconds']) : null;
    $expiresAt = $duration ? date('Y-m-d H:i:s', strtotime($occurredAt) + $duration) : null;
    $uuid = normalizeUuid((string) ($payload['uuid'] ?? ''));
    if ($uuid === null) {
        $knownPlayer = apiResolvePlayer($pdo, $playerName);
        $uuid = $knownPlayer['uuid'] ?? null;
        if ($knownPlayer) $playerName = (string) $knownPlayer['username'];
    }
    $issuerLabel = trim((string) ($payload['issued_by'] ?? $payload['staff'] ?? 'System')) ?: 'System';
    $issuerId = apiResolveStaffUserId($pdo, $issuerLabel);
    $statement = $pdo->prepare('INSERT INTO punishments (case_id, player_uuid, player_name, punishment_type, reason, duration_seconds, source, external_id, issued_by, issued_by_label, issued_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
    $statement->execute([
        isset($payload['case_id']) ? (int) $payload['case_id'] : null,
        $uuid,
        mb_substr($playerName, 0, 32),
        $type,
        $reason,
        $duration,
        mb_substr((string) ($payload['source'] ?? 'server'), 0, 32),
        isset($payload['external_id']) ? mb_substr((string) $payload['external_id'], 0, 96) : null,
        $issuerId,
        mb_substr($issuerLabel, 0, 190),
        $occurredAt,
        $expiresAt,
    ]);
}

function apiCreateStaffAudit(PDO $pdo, array $payload, string $source, string $occurredAt): void
{
    if (!obsRecent($occurredAt)) return;
    $action = trim((string) ($payload['action'] ?? ''));
    if ($action === '') {
        throw new InvalidArgumentException('Invalid staff action.');
    }
    // PremiumVanish uses /pv <number> for internal/settings commands. Those are
    // noise, not moderation evidence. /pva and actual vanish events remain.
    if (strtolower($action) === 'command.pv') return;
    $actorLabel = mb_substr((string) ($payload['staff'] ?? 'Server staff'), 0, 190);
    $actorUuid = normalizeUuid((string) ($payload['actor_uuid'] ?? ''));
    $actorId = apiResolveStaffUserId($pdo, $actorLabel, $actorUuid);
    if($actorId!==null)obsMetric($source,'Staff','staff_actions',obsText($action,128),$occurredAt);
    $statement = $pdo->prepare('INSERT INTO audit_logs (actor_user_id, actor_label, actor_minecraft_uuid, actor_kind, action, target_type, target_id, source_server, metadata_json, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
    $statement->execute([
        $actorId,
        $actorLabel,
        $actorUuid,
        'staff',
        mb_substr($action, 0, 96),
        isset($payload['target_type']) ? mb_substr((string) $payload['target_type'], 0, 48) : 'network',
        isset($payload['target_id']) ? mb_substr((string) $payload['target_id'], 0, 96) : null,
        mb_substr($source, 0, 64),
        jsonEncode(['server' => $source, 'details' => $payload['details'] ?? null]),
        $occurredAt,
    ]);
}

function apiCreateCommandAudit(PDO $pdo, array $payload, string $source, string $occurredAt): void
{
    if (!obsRecent($occurredAt)) return;
    $action = strtolower(trim((string) ($payload['action'] ?? '')));
    $actorLabel = trim((string) ($payload['staff'] ?? $payload['username'] ?? ''));
    $actorUuid = normalizeUuid((string) ($payload['actor_uuid'] ?? $payload['uuid'] ?? ''));
    if ($actorLabel === '' || !str_starts_with($action, 'command.')) {
        throw new InvalidArgumentException('Invalid command action.');
    }
    $details = $payload['details'] ?? [];
    if (!is_array($details)) $details = [];
    $command = apiSafeCommandText($action, (string) ($details['command'] ?? substr($action, 8)));
    $details['command'] = $command;
    $actorId = apiResolveStaffUserId($pdo, $actorLabel, $actorUuid);
    $statement = $pdo->prepare(
        'INSERT INTO audit_logs (actor_user_id, actor_label, actor_minecraft_uuid, actor_kind, action, target_type, target_id, source_server, command_text, metadata_json, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
    );
    $statement->execute([
        $actorId,
        mb_substr($actorLabel, 0, 190),
        $actorUuid,
        'player',
        mb_substr($action, 0, 96),
        isset($payload['target_type']) ? mb_substr((string) $payload['target_type'], 0, 48) : 'network',
        isset($payload['target_id']) ? mb_substr((string) $payload['target_id'], 0, 96) : null,
        mb_substr($source, 0, 64),
        mb_substr($command, 0, 4096),
        jsonEncode(['server' => $source, 'details' => $details]),
        $occurredAt,
    ]);
}

function apiCreateChatMessage(PDO $pdo, array $payload, string $source, string $occurredAt, ?string $eventId): void
{
    if (!obsRecent($occurredAt)) return;
    $uuid = normalizeUuid((string) ($payload['uuid'] ?? ''));
    $username = trim((string) ($payload['username'] ?? ''));
    $message = trim((string) ($payload['message'] ?? ''));
    if ($uuid === null || $username === '' || $message === '') {
        throw new InvalidArgumentException('Invalid player message.');
    }
    $channel = strtolower(trim((string) ($payload['channel'] ?? 'global')));
    $channel = preg_replace('/[^a-z0-9_.-]+/', '-', $channel) ?: 'global';
    $recipientName = trim((string) ($payload['recipient_name'] ?? '')) ?: null;
    $recipientUuid = normalizeUuid((string) ($payload['recipient_uuid'] ?? ''));
    if ($recipientUuid === null && $recipientName !== null) {
        $knownRecipient = apiResolvePlayer($pdo, $recipientName);
        $recipientUuid = $knownRecipient['uuid'] ?? null;
        if ($knownRecipient) $recipientName = (string) $knownRecipient['username'];
    }
    $statement = $pdo->prepare(
        'INSERT INTO player_messages (event_id, player_uuid, player_name, channel_name, recipient_uuid, recipient_name, message_text, source_server, origin, delivered, sent_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
    );
    $statement->execute([
        $eventId !== null ? mb_substr($eventId, 0, 96) : null,
        $uuid,
        mb_substr($username, 0, 32),
        mb_substr($channel, 0, 32),
        $recipientUuid,
        $recipientName !== null ? mb_substr($recipientName, 0, 96) : null,
        mb_substr($message, 0, 4096),
        mb_substr($source, 0, 64),
        mb_substr((string) ($payload['origin'] ?? 'chat'), 0, 24),
        !array_key_exists('delivered', $payload) || !empty($payload['delivered']) ? 1 : 0,
        $occurredAt,
    ]);
    obsChatSignal($pdo,(int)$pdo->lastInsertId(),$payload,$source,$occurredAt);
}

function apiSafeCommandText(string $action, string $command): string
{
    $label = strtolower(substr($action, strlen('command.')));
    $sensitive = ['login','l','register','reg','changepassword','changepass','password','passwd','setpassword','auth','2fa','otp','totp','pin','link','verify','discord','discordsrv'];
    if (in_array($label, $sensitive, true)) return $label . ' [REDACTED]';
    $command = trim($command) ?: $label;
    if (str_contains($command, '[message logged separately]')) return $command;
    $private = ['msg','tell','w','whisper','message','pm','m','emsg','etell','ewhisper'];
    if (in_array($label, $private, true)) {
        $parts = preg_split('/\s+/', $command) ?: [];
        return $label . (isset($parts[1]) ? ' ' . mb_substr($parts[1], 0, 96) : '') . ' [message logged separately]';
    }
    if (in_array($label, ['r','reply','ereply','g','global','gc','lc','local','tc','townchat','nc','nationchat','ac','alliancechat','sc','staffchat','adc','adminchat'], true)) {
        return $label . (str_contains($command, ' ') ? ' [message logged separately]' : '');
    }
    return $command;
}

/** @return array<string,mixed>|null */
function apiResolvePlayer(PDO $pdo, string $username): ?array
{
    $statement = $pdo->prepare('SELECT uuid, username FROM players WHERE LOWER(username) = LOWER(?) ORDER BY updated_at DESC LIMIT 1');
    $statement->execute([$username]);
    return $statement->fetch() ?: null;
}

function apiResolveStaffUserId(PDO $pdo, string $label, ?string $uuid = null): ?int
{
    $uuid = normalizeUuid($uuid);
    if ($uuid !== null) {
        $statement=$pdo->prepare('SELECT id FROM users WHERE active=1 AND minecraft_uuid=? LIMIT 1');$statement->execute([$uuid]);
    } else {
        $statement=$pdo->prepare('SELECT id FROM users WHERE active=1 AND minecraft_username IS NOT NULL AND LOWER(minecraft_username)=LOWER(?) LIMIT 1');$statement->execute([$label]);
    }
    $id=$statement->fetchColumn();return $id===false?null:(int)$id;
}

function apiRunRetention(PDO $pdo): void
{
    try {
        $last=$pdo->query("SELECT setting_value FROM settings WHERE setting_key='activity.retention.last'")->fetchColumn();
        if($last&&strtotime((string)$last)>time()-3600)return;
        obsRetention($pdo,500);
        $pdo->prepare("INSERT INTO settings(setting_key,setting_value,updated_at) VALUES('activity.retention.last',?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value),updated_at=VALUES(updated_at)")->execute([now(),now()]);
    } catch(Throwable) { /* An accepted API event must not retry because maintenance failed. */ }
}

function apiDate(string $value): string
{
    if ($value === '') {
        return now();
    }
    $timestamp = strtotime($value);
    if ($timestamp === false || $timestamp < 1577836800 || $timestamp > time()+300) {
        return now();
    }
    return date('Y-m-d H:i:s', $timestamp);
}

function apiResponse(int $status, array $body): never
{
    http_response_code($status);
    $encoded=json_encode($body, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    header('Content-Length: '.strlen((string)$encoded));
    echo $encoded;
    if($status>=200&&$status<300&&!empty($GLOBALS['notification_queued'])){
        ignore_user_abort(true);
        if(function_exists('fastcgi_finish_request'))fastcgi_finish_request();
        else flush();
        try{notificationDispatch(db(),1);}catch(Throwable){/* The accepted event and notification remain committed. */}
    }
    exit;
}

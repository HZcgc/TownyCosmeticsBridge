<?php
declare(strict_types=1);

/** Additive Staff HQ 5 migration. Player dossiers outlive the rolling chat log. */
function workspaceSchema(PDO $pdo): void
{
    $suffix = ' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci';
    $tables = [
        "player_dossiers (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, player_uuid CHAR(36) NOT NULL UNIQUE, created_at DATETIME NOT NULL)",
        "player_notes (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, dossier_id BIGINT UNSIGNED NOT NULL,
         note_type VARCHAR(12) NOT NULL DEFAULT 'note', priority VARCHAR(12) NOT NULL DEFAULT 'normal',
         body TEXT NOT NULL, created_by BIGINT UNSIGNED NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
         archived_at DATETIME NULL, INDEX idx_notes_dossier(dossier_id,archived_at,created_at),
         FOREIGN KEY(dossier_id) REFERENCES player_dossiers(id) ON DELETE CASCADE,
         FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE SET NULL)",
        "player_pins (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, dossier_id BIGINT UNSIGNED NOT NULL,
         message_key CHAR(64) NOT NULL, message_id BIGINT UNSIGNED NULL, player_uuid CHAR(36) NULL,
         player_name VARCHAR(32) NOT NULL, recipient_name VARCHAR(96) NULL, channel_name VARCHAR(32) NOT NULL,
         source_server VARCHAR(64) NOT NULL, message_text TEXT NOT NULL, sent_at DATETIME NOT NULL,
         staff_note TEXT NULL, created_by BIGINT UNSIGNED NULL, created_at DATETIME NOT NULL,
         UNIQUE KEY uq_pin(dossier_id,message_key), INDEX idx_pins_dossier(dossier_id,sent_at),
         FOREIGN KEY(dossier_id) REFERENCES player_dossiers(id) ON DELETE CASCADE,
         FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE SET NULL)",
        "case_origins (source_key CHAR(64) PRIMARY KEY, case_id BIGINT UNSIGNED NOT NULL,
         FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE)",
        "integration_snapshots (server_name VARCHAR(64) NOT NULL, plugin_name VARCHAR(96) NOT NULL,
         snapshot_key VARCHAR(96) NOT NULL, data_json MEDIUMTEXT NOT NULL, captured_at DATETIME NOT NULL,
         PRIMARY KEY(server_name,plugin_name,snapshot_key))",
        "game_events (event_key CHAR(64) PRIMARY KEY, server_name VARCHAR(64) NOT NULL,
         plugin_name VARCHAR(96) NOT NULL, metric VARCHAR(64) NOT NULL, dimension VARCHAR(128) NOT NULL,
         actor_key VARCHAR(96) NOT NULL, player_uuid CHAR(36) NULL, player_name VARCHAR(32) NOT NULL,
         value_sum DOUBLE NOT NULL, sample_count BIGINT UNSIGNED NOT NULL, details_json TEXT NULL,
         occurred_at DATETIME NOT NULL, imported TINYINT(1) NOT NULL DEFAULT 0,
         INDEX idx_game_event_filter(plugin_name,metric,occurred_at), INDEX idx_game_event_player(actor_key,occurred_at))",
        "game_player_daily (day_utc DATE NOT NULL, server_name VARCHAR(64) NOT NULL, plugin_name VARCHAR(96) NOT NULL,
         metric VARCHAR(64) NOT NULL, dimension VARCHAR(128) NOT NULL, actor_key VARCHAR(96) NOT NULL,
         player_uuid CHAR(36) NULL, player_name VARCHAR(32) NOT NULL, value_sum DOUBLE NOT NULL,
         sample_count BIGINT UNSIGNED NOT NULL, PRIMARY KEY(day_utc,server_name,plugin_name,metric,dimension,actor_key),
         INDEX idx_game_daily_lookup(plugin_name,metric,day_utc))",
    ];
    foreach ($tables as $table) $pdo->exec('CREATE TABLE IF NOT EXISTS ' . $table . $suffix);
    $insert = $pdo->prepare('INSERT IGNORE INTO role_permissions(role,permission,allowed,updated_at) VALUES(?,?,?,NOW())');
    foreach (['admin','moderator','helper'] as $role) {
        $q=$pdo->prepare('SELECT permission,allowed FROM role_permissions WHERE role=?');$q->execute([$role]);$existing=$q->fetchAll(PDO::FETCH_ASSOC);
        $old=$existing?array_column(array_filter($existing,static fn($r)=>(bool)$r['allowed']),'permission'):defaultRolePermissions($role);
        foreach (workspacePermissionCatalog() as $permission => $details) {
            $allowed = str_starts_with($permission, 'profile.')
                ? in_array('players.view', $old, true)
                : (str_starts_with($permission, 'statistics.') ? in_array('analytics.view', $old, true) : false);
            if (str_ends_with($permission, '.manage')) $allowed = in_array('cases.manage', $old, true);
            if ($permission === 'messages.private') $allowed = in_array('messages.view', $old, true);
            $insert->execute([$role,$permission,(int)$allowed]);
        }
    }
}

function workspacePermissionCatalog(): array
{
    return [
        'profile.notes.view'=>['group'=>'Player profiles','label'=>'Read player notes and watch instructions'],
        'profile.notes.create'=>['group'=>'Player profiles','label'=>'Add notes and watch instructions without a case'],
        'profile.notes.manage'=>['group'=>'Player profiles','label'=>'Archive and restore player notes'],
        'profile.pins.view'=>['group'=>'Player profiles','label'=>'Read permanent message pins'],
        'profile.pins.create'=>['group'=>'Player profiles','label'=>'Pin a message permanently'],
        'profile.pins.manage'=>['group'=>'Player profiles','label'=>'Remove permanent message pins'],
        'profile.files.view'=>['group'=>'Player profiles','label'=>'Read player profile attachments'],
        'profile.files.create'=>['group'=>'Player profiles','label'=>'Upload player profile attachments'],
        'profile.files.manage'=>['group'=>'Player profiles','label'=>'Delete player profile attachments'],
        'messages.private'=>['group'=>'Activity logs','label'=>'Read private messages and private message pins'],
        'statistics.crates'=>['group'=>'Game statistics','label'=>'View crate openings, rewards and player rankings'],
        'statistics.economy'=>['group'=>'Game statistics','label'=>'View QuickShop transactions and pet purchases'],
        'statistics.leaderboards'=>['group'=>'Game statistics','label'=>'View voting, Dragon, Piñata and fishing rankings'],
    ];
}

function dossierFor(string $uuid): int
{
    $uuid = normalizeUuid($uuid) ?? throw new RuntimeException('Select a recorded player.');
    db()->prepare('INSERT IGNORE INTO player_dossiers(player_uuid,created_at) VALUES(?,?)')->execute([$uuid,now()]);
    $q=db()->prepare('SELECT id FROM player_dossiers WHERE player_uuid=?'); $q->execute([$uuid]);
    return (int)$q->fetchColumn();
}

function dossierPlayer(int $id): array
{
    $q=db()->prepare('SELECT p.* FROM player_dossiers d JOIN players p ON p.uuid=d.player_uuid WHERE d.id=?');
    $q->execute([$id]);
    return $q->fetch() ?: throw new RuntimeException('Player profile no longer exists.');
}

function messageReadable(array $message): bool
{
    return can('messages.view') && ($message['channel_name'] !== 'private' || can('messages.private'));
}

function profileSource(array $player): ?array
{
    $id=(int)($_POST['message_id']??0); $pin=(int)($_POST['pin_id']??0);
    if (!$id && !$pin) return null;
    if ($pin) {
        requirePermission('profile.pins.view');
        $q=db()->prepare('SELECT p.*,d.player_uuid AS subject_uuid FROM player_pins p JOIN player_dossiers d ON d.id=p.dossier_id WHERE p.id=?');
        $q->execute([$pin]);$row=$q->fetch();
        if (!$row || $row['subject_uuid']!==$player['uuid']) throw new RuntimeException('Pin does not belong to this profile.');
        if ($row['channel_name']==='private') requirePermission('messages.private');
        return $row;
    }
    requirePermission('messages.view');
    $q=db()->prepare('SELECT * FROM player_messages WHERE id=?');$q->execute([$id]);$row=$q->fetch();
    if (!$row || !messageReadable($row)) throw new RuntimeException('Message is unavailable to your role.');
    $related=normalizeUuid((string)$row['player_uuid'])===$player['uuid'] || strcasecmp($row['player_name'],$player['username'])===0;
    if ($row['channel_name']==='private') $related=$related || normalizeUuid((string)($row['recipient_uuid']??''))===$player['uuid'] || strcasecmp((string)($row['recipient_name']??''),$player['username'])===0;
    if (!$related) throw new RuntimeException('Message does not belong to this profile.');
    $row['message_key']=hash('sha256',$row['source_server'].':message:'.$row['id']);
    return $row;
}

function postProfileAction(string $action): never
{
    $user=requirePermission('players.view');
    $uuid=normalizeUuid((string)($_POST['player_uuid']??''));
    $player=$uuid ? resolvePlayer('', $uuid) : null;
    if (!$player) throw new RuntimeException('Select a recorded player.');
    $pdo=db();$dossier=dossierFor($uuid);
    if ($action==='profile-note') {
        requirePermission('profile.notes.create');
        $body=trim((string)($_POST['body']??''));
        if ($body==='' || mb_strlen($body)>10000) throw new RuntimeException('Enter a note of up to 10,000 characters.');
        $type=($_POST['note_type']??'')==='watch'?'watch':'note';
        $priority=in_array($_POST['priority']??'', ['normal','high'],true)?$_POST['priority']:'normal';
        $pdo->prepare('INSERT INTO player_notes(dossier_id,note_type,priority,body,created_by,created_at,updated_at) VALUES(?,?,?,?,?,?,?)')
            ->execute([$dossier,$type,$priority,$body,$user['id'],now(),now()]);
        audit('profile.note.added','player',$uuid,['note_id'=>(int)$pdo->lastInsertId(),'type'=>$type]);
    } elseif ($action==='profile-note-archive') {
        requirePermission('profile.notes.manage');
        $q=$pdo->prepare('UPDATE player_notes SET archived_at=?,updated_at=? WHERE id=? AND dossier_id=?');
        $q->execute([empty($_POST['restore'])?now():null,now(),(int)($_POST['note_id']??0),$dossier]);
        audit('profile.note.archived','player',$uuid,['note_id'=>(int)($_POST['note_id']??0),'restore'=>!empty($_POST['restore'])]);
    } elseif ($action==='profile-pin') {
        requirePermission('profile.pins.create');
        $message=profileSource($player) ?? throw new RuntimeException('Select a message to pin.');
        $staffNote=trim((string)($_POST['staff_note']??''));
        if (mb_strlen($staffNote)>2000) throw new RuntimeException('Keep the pin note under 2,000 characters.');
        $q=$pdo->prepare('INSERT IGNORE INTO player_pins(dossier_id,message_key,message_id,player_uuid,player_name,recipient_name,channel_name,source_server,message_text,sent_at,staff_note,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)');
        $q->execute([$dossier,$message['message_key'],$message['id'],normalizeUuid((string)$message['player_uuid']),$message['player_name'],$message['recipient_name'],$message['channel_name'],$message['source_server'],$message['message_text'],$message['sent_at'],$staffNote,$user['id'],now()]);
        audit('profile.message.pinned','player',$uuid,['message_key'=>$message['message_key']]);
    } elseif ($action==='profile-unpin') {
        requirePermission('profile.pins.manage');
        $pdo->prepare('DELETE FROM player_pins WHERE id=? AND dossier_id=?')->execute([(int)($_POST['pin_id']??0),$dossier]);
        audit('profile.message.unpinned','player',$uuid,['pin_id'=>(int)($_POST['pin_id']??0)]);
    } elseif ($action==='profile-upload') {
        requirePermission('profile.files.create');
        storeUploadedAttachments('player',$dossier,(int)$user['id']);
        audit('profile.files.added','player',$uuid);
        if (wantsJsonResponse()) jsonActionSuccess('player',$dossier,'index.php?page=player&uuid='.rawurlencode($uuid).'#profile-files');
    } elseif ($action==='profile-case') {
        requirePermission('cases.create');
        $message=profileSource($player);
        $nonce=(string)($_POST['request_key']??'');
        if (!preg_match('/^[a-f0-9]{32}$/',$nonce)) throw new RuntimeException('Reload the profile before creating a case.');
        $sourceKey=hash('sha256',$message ? 'message:'.$message['message_key'] : 'profile:'.$uuid.':'.$user['id'].':'.$nonce);
        $title=trim((string)($_POST['title']??'')) ?: ($message?'Review message from ':'Review player: ').$player['username'];
        $description=trim((string)($_POST['description']??''));
        if (mb_strlen($title)>190 || mb_strlen($description)>10000) throw new RuntimeException('Case title or description is too long.');
        $pdo->beginTransaction();
        try {
            $q=$pdo->prepare('SELECT case_id FROM case_origins WHERE source_key=?');$q->execute([$sourceKey]);$caseId=(int)$q->fetchColumn();
            if (!$caseId) {
                $number='TR-'.date('ymd').'-'.strtoupper(bin2hex(random_bytes(3)));
                $pdo->prepare('INSERT INTO cases(case_number,player_uuid,player_name,title,description,status,priority,created_by,assigned_user_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)')
                    ->execute([$number,$uuid,$player['username'],$title,$description,'open','normal',$user['id'],$user['id'],now(),now()]);
                $caseId=(int)$pdo->lastInsertId();
                $pdo->prepare('INSERT INTO case_origins(source_key,case_id) VALUES(?,?)')->execute([$sourceKey,$caseId]);
                addPlayersToCase($caseId,[$player],true);
                $body=$message ? '['.$message['sent_at'].' / '.$message['source_server'].' / '.$message['channel_name'].'] '.$message['player_name'].($message['recipient_name']?' → '.$message['recipient_name']:'').":\n".$message['message_text'] : 'Created from the player profile.';
                $pdo->prepare('INSERT INTO case_events(case_id,actor_user_id,event_type,body,metadata_json,created_at) VALUES(?,?,?,?,?,?)')
                    ->execute([$caseId,$user['id'],'profile_source',$body,jsonEncode(['private'=>$message && $message['channel_name']==='private','message_key'=>$message['message_key']??null]),now()]);
                audit('case.created_from_profile','case',(string)$caseId,['uuid'=>$uuid]);
            }
            $pdo->commit();
        } catch (PDOException $e) {
            if ($pdo->inTransaction()) $pdo->rollBack();
            if ((string)$e->getCode()!=='23000') throw $e;
            $q=$pdo->prepare('SELECT case_id FROM case_origins WHERE source_key=?');$q->execute([$sourceKey]);$caseId=(int)$q->fetchColumn();
            if (!$caseId) throw $e;
        } catch (Throwable $e) { if ($pdo->inTransaction()) $pdo->rollBack(); throw $e; }
        setFlash('success','Case created; the source message is saved permanently.');
        if (can('cases.view')) redirect('cases',['case_id'=>$caseId]);
    } else throw new RuntimeException('Unknown profile action.');
    setFlash('success','Player profile updated.');
    redirect('player',['uuid'=>$uuid]);
}

function profileFields(array $player, string $action): void
{
    echo csrfField().'<input type="hidden" name="action" value="'.e($action).'"><input type="hidden" name="return_page" value="player"><input type="hidden" name="player_uuid" value="'.e($player['uuid']).'">';
}

function profileMessageCard(array $message,array $player,bool $pin=false): void
{
    echo '<article class="message-card"><div class="message-meta"><strong>'.e($message['player_name']).'</strong><span class="badge">'.e($message['channel_name']).'</span><time>'.e(displayDate($message['sent_at'])).'</time><span>'.e(strtoupper($message['source_server'])).'</span></div>';
    if (!empty($message['recipient_name'])) echo '<span class="cell-sub">To '.e($message['recipient_name']).'</span>';
    echo '<blockquote>'.nl2br(e($message['message_text'])).'</blockquote>';
    if ($pin && !empty($message['staff_note'])) echo '<p class="subtle">'.nl2br(e($message['staff_note'])).'</p>';
    echo '<div class="button-row">';
    if (!$pin && can('profile.pins.create')) {
        echo '<form method="post">';profileFields($player,'profile-pin');
        echo '<input type="hidden" name="message_id" value="'.(int)$message['id'].'"><button class="button secondary small" type="submit">Pin to profile</button></form>';
    }
    if (can('cases.create')) {
        echo '<details class="case-from-message"><summary class="button secondary small">Create case</summary><form method="post" class="form-stack">';profileFields($player,'profile-case');
        echo '<input type="hidden" name="'.($pin?'pin_id':'message_id').'" value="'.(int)$message['id'].'"><input type="hidden" name="request_key" value="'.bin2hex(random_bytes(16)).'"><label>Case title<input name="title" maxlength="190" placeholder="What needs investigation?"></label><label>Staff context<textarea name="description" maxlength="10000"></textarea></label><button class="button small">Create with message evidence</button></form></details>';
    }
    if ($pin && can('profile.pins.manage')) {
        echo '<form method="post">';profileFields($player,'profile-unpin');
        echo '<input type="hidden" name="pin_id" value="'.(int)$message['id'].'"><button class="button danger small" data-confirm="Permanently remove this saved pin?">Unpin</button></form>';
    }
    echo '</div></article>';
}

function showPlayerWorkspace(): void
{
    requirePermission('players.view');
    $player=resolvePlayer(trim((string)($_GET['name']??'')),normalizeUuid((string)($_GET['uuid']??'')));
    if (!$player) { renderErrorPage('Player not found','The connector has not recorded this player yet.');return; }
    $uuid=$player['uuid'];$name=$player['username'];$dossier=dossierFor($uuid);$pdo=db();
    $notes=[];$pins=[];$messages=[];$cases=[];$punishments=[];$alerts=[];
    if (can('profile.notes.view')) {
        $q=$pdo->prepare('SELECT n.*,u.display_name FROM player_notes n LEFT JOIN users u ON u.id=n.created_by WHERE dossier_id=? ORDER BY archived_at IS NOT NULL,priority DESC,created_at DESC LIMIT 200');$q->execute([$dossier]);$notes=$q->fetchAll();
    }
    if (can('profile.pins.view')) {
        $q=$pdo->prepare("SELECT * FROM player_pins WHERE dossier_id=?".(can('messages.private')?'':" AND channel_name<>'private'").' ORDER BY sent_at DESC LIMIT 200');$q->execute([$dossier]);$pins=$q->fetchAll();
    }
    if (can('messages.view')) {
        $where="(player_uuid=? OR LOWER(player_name)=LOWER(?) OR (channel_name='private' AND (recipient_uuid=? OR LOWER(recipient_name)=LOWER(?))))";
        if (!can('messages.private')) $where.=" AND channel_name<>'private'";
        $q=$pdo->prepare('SELECT * FROM player_messages WHERE '.$where.' ORDER BY sent_at DESC LIMIT 50');$q->execute([$uuid,$name,$uuid,$name]);$messages=$q->fetchAll();
    }
    if (can('cases.view')) {
        $q=$pdo->prepare('SELECT DISTINCT c.*,u.display_name assigned_name FROM cases c JOIN case_players cp ON cp.case_id=c.id LEFT JOIN users u ON u.id=c.assigned_user_id WHERE cp.player_uuid=? OR LOWER(cp.player_name)=LOWER(?) ORDER BY c.updated_at DESC LIMIT 100');$q->execute([$uuid,$name]);$cases=$q->fetchAll();
    }
    if (can('punishments.view')) {
        $q=$pdo->prepare('SELECT p.*,u.display_name FROM punishments p LEFT JOIN users u ON u.id=p.issued_by WHERE player_uuid=? OR LOWER(player_name)=LOWER(?) ORDER BY issued_at DESC LIMIT 100');$q->execute([$uuid,$name]);$punishments=$q->fetchAll();
    }
    if (can('alerts.view')) {$q=$pdo->prepare('SELECT * FROM alerts WHERE player_uuid=? OR LOWER(player_name)=LOWER(?) ORDER BY occurred_at DESC LIMIT 100');$q->execute([$uuid,$name]);$alerts=$q->fetchAll();}
    $q=$pdo->prepare('SELECT * FROM player_sessions WHERE player_uuid=? ORDER BY joined_at DESC LIMIT 20');$q->execute([$uuid]);$sessions=$q->fetchAll();
    $metadata=json_decode((string)($player['metadata_json']??''),true)?:[];
    ob_start(); ?>
    <div class="player-profile-header"><?= renderPlayerIdentity($name,$uuid,ucfirst($player['platform']).' player',false) ?><a class="button secondary small" href="index.php?page=players">← Players</a></div>
    <nav class="profile-jumps" aria-label="Player sections"><a href="#profile-notes">Notes & watch</a><a href="#profile-pins">Saved messages</a><a href="#profile-files">Attachments</a><a href="#profile-history">History</a><a href="#profile-messages">Chat</a></nav>
    <section class="stats-grid"><article class="stat-card accent-lime"><span>Status</span><strong><?= $player['online']?'Online':'Offline' ?></strong><small><?= e($player['last_server']?:'Unknown server') ?></small></article><article class="stat-card accent-pink"><span>Balance</span><strong><?= $player['balance']===null?'—':'$'.number_format((float)$player['balance']) ?></strong><small><?= e($player['town_name']?:'No town') ?></small></article><article class="stat-card accent-blue"><span>Playtime</span><strong><?= number_format((int)$player['playtime_seconds']/3600,1) ?> h</strong><small>Last seen <?= e(displayDate($player['last_seen_at'])) ?></small></article><article class="stat-card accent-amber"><span>Staff watch instructions</span><strong><?= count(array_filter($notes,fn($r)=>$r['note_type']==='watch' && !$r['archived_at'])) ?></strong><small>Read context before taking action</small></article></section>
    <section class="content-grid profile-grid"><div>
    <?php if (can('profile.notes.view')): ?><article class="panel" id="profile-notes"><div class="panel-header"><h2>Notes & watch instructions</h2><span class="badge">Persistent</span></div><div class="panel-body">
    <?php foreach($notes as $note): ?><details class="profile-note <?= $note['note_type']==='watch' && !$note['archived_at']?'watch-note':'' ?>" <?= !$note['archived_at']?'open':'' ?>><summary><span class="badge <?= $note['priority']==='high'?'high':'' ?>"><?= e($note['archived_at']?'Archived':ucfirst($note['note_type'])) ?></span> <?= e($note['display_name']?:'Former staff') ?> · <?= e(displayDate($note['created_at'])) ?></summary><p><?= nl2br(e($note['body'])) ?></p><?php if(can('profile.notes.manage')): ?><form method="post"><?php profileFields($player,'profile-note-archive'); ?><input type="hidden" name="note_id" value="<?= (int)$note['id'] ?>"><input type="hidden" name="restore" value="<?= $note['archived_at']?'1':'' ?>"><button class="button secondary small"><?= $note['archived_at']?'Restore':'Archive note' ?></button></form><?php endif; ?></details><?php endforeach; ?>
    <?php if(!$notes): ?><p class="subtle">No staff notes yet. A note can be saved without opening a case.</p><?php endif; ?>
    <?php if(can('profile.notes.create')): ?><form method="post" class="form-stack"><?php profileFields($player,'profile-note'); ?><div class="split-form"><label>Type<select name="note_type"><option value="note">Staff note</option><option value="watch">Watch instruction</option></select></label><label>Priority<select name="priority"><option value="normal">Normal</option><option value="high">Important</option></select></label></div><label>What should staff know?<textarea name="body" maxlength="10000" rows="4" required placeholder="Facts, context and what to look out for…"></textarea></label><button class="button">Save note</button></form><?php endif; ?></div></article><?php endif; ?>
    <?php if(can('profile.pins.view')): ?><article class="panel" id="profile-pins"><div class="panel-header"><h2>Saved messages</h2><span class="badge"><?= count($pins) ?> pins</span></div><div class="panel-body"><p class="subtle">These copies remain when the 30-day chat log expires.</p><?php foreach($pins as $pin) profileMessageCard($pin,$player,true); ?><?php if(!$pins): ?><p>No saved messages yet. Use “Pin to profile” in the chat below.</p><?php endif; ?></div></article><?php endif; ?>
    <?php if(can('profile.files.view')): ?><article class="panel" id="profile-files"><div class="panel-header"><h2>Profile attachments</h2><span class="badge">No case needed</span></div><div class="panel-body"><?php renderAttachmentGallery(attachmentsFor('player',$dossier),can('profile.files.manage'),'player',['player_uuid'=>$uuid]); ?><?php if(can('profile.files.create')): ?><form class="form-stack upload-form" method="post" enctype="multipart/form-data" data-chunk-redirect="index.php?page=player&amp;uuid=<?= e($uuid) ?>#profile-files"><?php profileFields($player,'profile-upload'); ?><input type="hidden" name="owner_type" value="player"><input type="hidden" name="owner_id" value="<?= $dossier ?>"><label>Screenshot or supporting file<input type="file" name="attachments[]" multiple required></label><p class="field-hint">Up to 100 MB per file. Keep only information relevant to staff.</p><button class="button secondary">Attach to profile</button></form><?php endif; ?></div></article><?php endif; ?>
    <article class="panel" id="profile-history"><div class="panel-header"><h2>Moderation history</h2></div><div class="panel-body">
    <?php if(can('cases.view')): ?><details open><summary>Cases · <?= count($cases) ?> most recent</summary><?php renderCasesTable($cases); ?></details><?php endif; ?>
    <?php if(can('punishments.view')): ?><details><summary>Punishments · <?= count($punishments) ?> most recent</summary><?php foreach($punishments as $p): ?><div class="review-item"><strong><?= e(ucfirst($p['punishment_type'])) ?></strong><span class="badge"><?= e($p['execution_status']??'recorded') ?></span><p><?= nl2br(e($p['reason'])) ?></p><time><?= e(displayDate($p['issued_at'])) ?></time></div><?php endforeach; ?></details><?php endif; ?>
    <?php if(can('alerts.view')): ?><details><summary>Anti-cheat · <?= count($alerts) ?> most recent</summary><?php renderAlertsTable($alerts,false); ?></details><?php endif; ?></div></article>
    <?php if(can('messages.view')): ?><article class="panel" id="profile-messages"><div class="panel-header"><h2>Recent chat</h2><a class="button secondary small" href="index.php?page=messages&q=<?= rawurlencode($name) ?>&range=30">Full chat log</a></div><div class="panel-body"><?php foreach($messages as $message) profileMessageCard($message,$player); ?><?php if(!$messages): ?><p class="subtle">No readable messages in the rolling log.</p><?php endif; ?></div></article><?php endif; ?>
    <?php if(can('commands.view')): $q=$pdo->prepare("SELECT * FROM audit_logs WHERE action LIKE 'command.%' AND (actor_minecraft_uuid=? OR LOWER(actor_label)=LOWER(?)) ORDER BY created_at DESC LIMIT 30");$q->execute([$uuid,$name]); ?><article class="panel"><div class="panel-header"><h2>Recent commands</h2></div><div class="panel-body"><?php foreach($q->fetchAll() as $cmd): ?><div class="review-item"><code class="command-text"><?= e($cmd['command_text']?:substr($cmd['action'],8)) ?></code><time class="cell-sub"><?= e(displayDate($cmd['created_at'])) ?></time></div><?php endforeach; ?></div></article><?php endif; ?>
    <?php if(can('activity.view')) obsPlayerActivity($uuid); ?>
    </div><aside>
    <?php if(can('cases.create')): ?><article class="panel"><div class="panel-header"><h2>Open a case</h2></div><div class="panel-body"><form method="post" class="form-stack"><?php profileFields($player,'profile-case'); ?><input type="hidden" name="request_key" value="<?= bin2hex(random_bytes(16)) ?>"><label>Title<input name="title" maxlength="190" required></label><label>Context<textarea name="description" rows="4" maxlength="10000"></textarea></label><button class="button">Create case for <?= e($name) ?></button></form></div></article><?php endif; ?>
    <?php if(can('punishments.create')): ?><article class="panel"><div class="panel-header"><h2>Staff decision</h2></div><div class="panel-body"><form class="form-stack" method="post"><?= csrfField() ?><input type="hidden" name="action" value="add-punishment"><input type="hidden" name="return_page" value="player"><input type="hidden" name="player_uuid" value="<?= e($uuid) ?>"><input type="hidden" name="player_name" value="<?= e($name) ?>"><label>Action<select name="punishment_type"><?php foreach(['warning','kick','mute','tempban','ban'] as $kind): ?><option value="<?= $kind ?>"><?= e(ucfirst($kind)) ?></option><?php endforeach; ?></select></label><label>Reason<textarea name="reason" maxlength="1000" required></textarea></label><label>Duration (seconds)<input name="duration_seconds" type="number" min="1"></label><?php if(can('punishments.execute')): ?><label class="check-row"><input type="checkbox" name="execute_on_server" value="1"> Execute on SMP</label><?php endif; ?><button class="button secondary">Record decision</button></form></div></article><?php endif; ?>
    <article class="panel"><div class="panel-header"><h2>Player details</h2></div><div class="panel-body metric-list"><div><span>UUID</span><code class="wrapping-cell"><?= e($uuid) ?></code></div><div><span>First seen</span><strong><?= e(displayDate($player['first_seen_at'])) ?></strong></div><?php foreach(['world'=>'World','ping'=>'Ping','client_brand'=>'Client','vanished'=>'Vanished'] as $key=>$label): if(!array_key_exists($key,$metadata))continue; ?><div><span><?= $label ?></span><strong><?= e(is_bool($metadata[$key])?($metadata[$key]?'Yes':'No'):(string)$metadata[$key]) ?></strong></div><?php endforeach; ?></div></article>
    <article class="panel"><div class="panel-header"><h2>Recent sessions</h2></div><div class="panel-body timeline"><?php foreach($sessions as $s): ?><div class="timeline-item"><strong><?= e($s['server_name']) ?></strong><time><?= e(displayDate($s['joined_at'])) ?></time><p><?= $s['duration_seconds']===null?'Still open':number_format((int)$s['duration_seconds']/60,1).' min' ?></p></div><?php endforeach; ?></div></article>
    </aside></section>
    <?php renderAppPage($name,'players',(string)ob_get_clean());
}

/** Normalise visual obfuscation, then match literal staff phrases and dupe word forms. */
function chatReviewMatches(string $text,array $rules): array
{
    $normal=preg_replace('/§[0-9a-fk-orx]|[\x{200B}-\x{200F}\x{2060}\x{FEFF}]/iu','',$text)??$text;
    $normal=preg_replace('/\s+/u',' ',mb_strtolower($normal))??$normal;
    $hits=[];$categories=[];
    if (preg_match('/(?<![\p{L}\p{N}_])(?:dupe(?:d|s|r|rs)?|duping|duplication|duplicate(?:d|s)?|duplizier\p{L}*|dupe[\s-]*(?:machine|maschine)|item[\s-]*dupe)(?![\p{L}\p{N}_])/iu',$normal,$m)) {
        $hits[]=$m[0];$categories[]='Duplication';
    }
    foreach(array_slice($rules,0,20,true) as $category=>$terms) foreach(array_slice(is_array($terms)?$terms:[],0,50) as $term) {
        if(!is_string($term)) continue;
        $term=trim(mb_substr($term,0,80));if($term==='')continue;
        $pattern=preg_quote(mb_strtolower($term),'/');$pattern=str_replace(' ','\s+',$pattern);
        if(preg_match('/(?<![\p{L}\p{N}_])'.$pattern.'(?![\p{L}\p{N}_])/iu',$normal)) {$hits[]=$term;$categories[]=mb_substr((string)$category,0,32);}
    }
    return ['terms'=>array_values(array_unique($hits)),'categories'=>array_values(array_unique($categories))];
}

function groupedDiagnostics(array $rows): array
{
    $groups=[];
    foreach($rows as $row) {
        $normal=preg_replace('/\b\d{4}-\d\d-\d\d[T ][\d:.+-]+\b/','[time]',$row['message'])??$row['message'];
        $key=hash('sha256',jsonEncode([$row['server_name'],$row['plugin_name'],$row['level'],$normal]));
        if(!isset($groups[$key])) $groups[$key]=['sample'=>$row,'rows'=>[],'occurrences'=>0,'first_at'=>$row['first_at'],'last_at'=>$row['last_at']];
        $groups[$key]['rows'][]=$row;$groups[$key]['occurrences']+=(int)$row['occurrences'];
        $groups[$key]['first_at']=min($groups[$key]['first_at'],$row['first_at']);$groups[$key]['last_at']=max($groups[$key]['last_at'],$row['last_at']);
    }
    return array_values($groups);
}

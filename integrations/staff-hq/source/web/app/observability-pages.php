<?php
declare(strict_types=1);

function obsServers(): array { return array_values(array_unique(array_merge(['smp','hub','proxy'],db()->query('SELECT server_name FROM server_status ORDER BY server_name')->fetchAll(PDO::FETCH_COLUMN)))); }
function obsServerTabs(string $page,string $active): void
{
    echo '<nav class="filter-tabs" aria-label="Server selection">';
    foreach(['smp'=>'SMP','hub'=>'Hub',''=>'All servers'] as $server=>$label)echo '<a class="'.($active===$server?'active':'').'" href="index.php?'.e(http_build_query(['page'=>$page,'server'=>$server])).'">'.e($label).'</a>';
    echo '</nav>';
}
function obsNumber(float|int|null $value): string { if($value===null)return '—';return number_format($value,abs($value-round($value))<0.001?0:2); }
function obsMetricLabel(string $metric): string { return ucwords(str_replace('_',' ',$metric)); }
function obsStatsFilter(): array
{
    $r=analyticsRange();[$source,$plugin,$section,$where,$params]=analyticsStatisticsFilter($r);
    return [$r['days'],$source,$plugin,$where,$params];
}

function showGameStatistics(): void
{
    showStatisticsWorkspace();
}

function exportStatistics(): never
{
    requirePermission('analytics.view');[,,, $where,$params]=obsStatsFilter();
    header('Content-Type: text/csv; charset=utf-8');header('Content-Disposition: attachment; filename="townyreborn-statistics.csv"');$out=fopen('php://output','w');
    fputcsv($out,['Hour UTC','Server','Plugin','Metric','Dimension','Total','Samples','Minimum','Maximum']);
    $q=db()->prepare('SELECT * FROM metric_hourly WHERE '.$where.' ORDER BY hour_utc,server_name,plugin_name');$q->execute($params);
    while($r=$q->fetch()){ $data=[];foreach(['hour_utc','server_name','plugin_name','metric','dimension','value_sum','sample_count','value_min','value_max'] as $k){$v=(string)$r[$k];if(in_array($k,['server_name','plugin_name','metric','dimension'],true)&&preg_match('/^[=+@\-\t\r]/',$v))$v="'".$v;$data[]=$v;}fputcsv($out,$data); }fclose($out);exit;
}

function showPluginOverview(): void
{
    requirePermission('plugins.view');$source=obsText($_GET['server']??'',64);$search=obsText($_GET['q']??'',96);$state=obsText($_GET['state']??'',32);
    $where=['1=1'];$params=[];if($source!==''){$where[]='p.server_name=?';$params[]=$source;}if($search!==''){$where[]='p.plugin_name LIKE ?';$params[]='%'.$search.'%';}if($state!==''){$where[]='COALESCE(o.update_state,\'unknown\')=?';$params[]=$state;}
    $q=db()->prepare('SELECT p.*,o.category,o.coverage,o.hooks_json,o.integration,o.latest_version,o.update_state,o.release_url,o.compatibility,o.update_reason,o.checked_at,o.next_check_at,o.update_source FROM server_plugins p LEFT JOIN plugin_observability o ON o.server_name=p.server_name AND o.plugin_name=p.plugin_name WHERE '.implode(' AND ',$where).' ORDER BY p.server_name,o.category,p.plugin_name');$q->execute($params);$rows=$q->fetchAll();$servers=db()->query('SELECT * FROM server_status ORDER BY server_name')->fetchAll();
    ob_start(); ?>
    <div class="page-intro"><div><strong>Plugin coverage and public release checks</strong><p>Every installed plugin appears here. Native hooks record the events an API exposes; technical coverage records version, status, command usage and diagnostics. Public release checks run every 6 hours.</p></div><span class="badge">Refreshes every 60 seconds</span></div>
    <form class="toolbar" method="get"><input type="hidden" name="page" value="plugins"><div class="field"><label>Search</label><input name="q" value="<?= e($search) ?>" placeholder="Plugin name"></div><div class="field"><label>Server</label><select name="server"><option value="">All servers</option><?php foreach(obsServers() as $s): ?><option <?= $source===$s?'selected':'' ?> value="<?= e($s) ?>"><?= e(strtoupper($s)) ?></option><?php endforeach; ?></select></div><div class="field"><label>Release state</label><select name="state"><option value="">All states</option><?php foreach(['update_available','current','newer_build','custom','unknown','unverified','check_failed'] as $s): ?><option <?= $state===$s?'selected':'' ?> value="<?= e($s) ?>"><?= e(ucwords(str_replace('_',' ',$s))) ?></option><?php endforeach; ?></select></div><button class="button" type="submit">Filter</button></form>
    <div data-plugin-live><section class="server-health-grid"><?php foreach($servers as $s): $m=json_decode((string)$s['metadata_json'],true)?:[];$fresh=strtotime($s['updated_at'])>time()-180; ?><article class="server-health-card"><div><strong><?= e(strtoupper($s['server_name'])) ?></strong><span class="badge <?= $fresh?'online':'failed' ?>"><?= $fresh?'Receiving':'No recent heartbeat' ?></span></div><dl><dt>Connector</dt><dd><?= e($m['connector_version']??'Legacy') ?></dd><dt>Connection</dt><dd><?= e($m['connection_state']??'Not reported') ?></dd><dt>Queued / buffered</dt><dd><?= obsNumber((int)($m['queued_events']??0)) ?> / <?= obsNumber((int)($m['telemetry_buffered']??0)) ?></dd><dt>HTTP / last success</dt><dd><?= e($m['last_http_status']??'—') ?> · <?= e($m['last_success']??'—') ?></dd><dt>Rejected events / samples</dt><dd><?= obsNumber((int)($m['dropped_events']??0)) ?> / <?= obsNumber((int)($m['telemetry_rejected']??0)) ?></dd><dt>HQ last received</dt><dd><?= e(displayDate($s['updated_at'])) ?></dd><dt>Snapshot captured</dt><dd><?= e(displayDate($s['captured_at'])) ?></dd><?php if(!empty($m['duplicate_plugin_names'])): ?><dt>Duplicate plugins</dt><dd><?= e(implode(', ',(array)$m['duplicate_plugin_names'])) ?></dd><?php endif; ?><dt>Players / plugins</dt><dd><?= (int)$s['online_players'] ?> / <?= (int)$s['plugin_count'] ?></dd></dl></article><?php endforeach; ?></section>
    <article class="panel"><div class="panel-header"><h2>Installed plugins</h2><span class="badge"><?= count($rows) ?> shown</span></div><div class="panel-body flush"><div class="table-wrap"><table class="plugin-table"><thead><tr><th>Plugin / server</th><th>Installed</th><th>Coverage</th><th>Latest public release</th><th>Checked / next</th></tr></thead><tbody><?php foreach($rows as $r): $hooks=json_decode((string)$r['hooks_json'],true)?:[];if(!array_is_list($hooks))$hooks=array_values(array_unique(array_merge($hooks['legacy']??[],$hooks['insights']??[]))); ?><tr><td><strong><?= e($r['plugin_name']) ?></strong><span class="cell-sub"><?= e(strtoupper($r['server_name']).' · '.($r['category']?:'Infrastructure')) ?> · <?= $r['enabled']?'Enabled':'Disabled' ?></span></td><td><?= e($r['plugin_version']) ?></td><td><span class="badge <?= $r['coverage']==='native'?'online':'' ?>"><?= e($r['coverage']?:'technical') ?></span><?php if(is_array($hooks)&&$hooks): ?><details><summary><?= count($hooks) ?> event hooks</summary><?php foreach($hooks as $h): ?><span class="cell-sub wrapping-cell"><?= e($h) ?></span><?php endforeach; ?></details><?php endif; ?><span class="cell-sub"><?= e($r['integration']??'') ?></span></td><td><span class="badge <?= $r['update_state']==='update_available'?'high':'' ?>"><?= e(str_replace('_',' ',$r['update_state']??'unknown')) ?></span><?php if($r['release_url']): ?><a class="cell-main" href="<?= e($r['release_url']) ?>" rel="noopener noreferrer" target="_blank"><?= e($r['latest_version']?:'Release page') ?></a><?php endif; ?><span class="cell-sub wrapping-cell"><?= e($r['compatibility']?:$r['update_reason']) ?></span><span class="cell-sub"><?= e($r['update_source']) ?></span></td><td><?= e(displayDate($r['checked_at'],'Not checked yet')) ?><span class="cell-sub"><?= e(displayDate($r['next_check_at'],'—')) ?></span></td></tr><?php endforeach; ?></tbody></table></div></div></article></div>
    <div class="notice notice-info">Custom, paid and unlisted plugins can have no reliable public release feed. A newer version is a notification; check Minecraft and dependency compatibility before replacing a JAR. Run <code>/trstaff updates</code> on Paper or <code>/trstaffproxy updates</code> on Velocity for a fresh check.</div>
    <?php renderAppPage('Plugins & Connections','plugins',(string)ob_get_clean());
}

function obsActivityRows(array $rows): void
{
    echo '<div class="table-wrap"><table><thead><tr><th>Last action</th><th>Player</th><th>Action / type</th><th>Count / value</th><th>Server / world</th></tr></thead><tbody>';
    foreach($rows as $r)echo '<tr><td>'.e(displayDate($r['last_at'])).'</td><td>'.renderPlayerIdentity($r['player_name'],$r['player_uuid'],$r['plugin_name'],true).'</td><td>'.e(obsMetricLabel($r['metric'])).'<span class="cell-sub wrapping-cell">'.e(str_replace('|',' · ',$r['dimension'])).'</span></td><td>'.obsNumber((int)$r['sample_count']).' observations<span class="cell-sub">'.obsNumber((float)$r['value_sum']).' total</span></td><td>'.e(strtoupper($r['server_name'])).'<span class="cell-sub">'.e($r['world_name']).'</span></td></tr>';
    if(!$rows)echo '<tr><td colspan="5">No matching activity collected.</td></tr>';echo '</tbody></table></div>';
}
function showGameplayActivity(): void
{
    requirePermission('activity.view');$search=obsText($_GET['q']??'',96);$server=obsText($_GET['server']??'',64);$metric=obsText($_GET['metric']??'',64);$where=['last_at>=DATE_SUB(NOW(),INTERVAL 30 DAY)'];$params=[];
    if($search!==''){$where[]='(player_name LIKE ? OR player_uuid=? OR dimension LIKE ? OR world_name LIKE ? OR plugin_name LIKE ?)';array_push($params,'%'.$search.'%',$search,'%'.$search.'%','%'.$search.'%','%'.$search.'%');}if($server!==''){$where[]='server_name=?';$params[]=$server;}if($metric!==''){$where[]='metric=?';$params[]=$metric;}
    $q=db()->prepare('SELECT * FROM gameplay_activity WHERE '.implode(' AND ',$where).' ORDER BY last_at DESC LIMIT 500');$q->execute($params);
    ob_start(); ?><div class="page-intro"><div><strong>Who did what</strong><p>Actions are grouped by player, type, world and minute. This is a searchable activity record; counts survive permanently in Game Statistics after individual rows expire.</p></div><span class="badge">30-day player detail</span></div><form class="toolbar" method="get"><input type="hidden" name="page" value="activity"><div class="field"><label>Player, UUID, type, world or plugin</label><input name="q" value="<?= e($search) ?>"></div><div class="field"><label>Server</label><select name="server"><option value="">All servers</option><?php foreach(obsServers() as $s): ?><option <?= $server===$s?'selected':'' ?> value="<?= e($s) ?>"><?= e(strtoupper($s)) ?></option><?php endforeach; ?></select></div><div class="field"><label>Metric</label><input name="metric" value="<?= e($metric) ?>" placeholder="e.g. mob_kills"></div><button class="button" type="submit">Search</button></form><article class="panel"><div class="panel-header"><h2>Latest matching activity</h2><span class="badge">Up to 500 minute groups</span></div><div class="panel-body flush"><?php obsActivityRows($q->fetchAll()); ?></div></article><?php renderAppPage('Player Activity','activity',(string)ob_get_clean());
}
function obsPlayerActivity(string $uuid): void
{
    $q=db()->prepare('SELECT * FROM gameplay_activity WHERE player_uuid=? AND last_at>=DATE_SUB(NOW(),INTERVAL 30 DAY) ORDER BY last_at DESC LIMIT 25');$q->execute([$uuid]);
    echo '<article class="panel"><div class="panel-header"><h2>Gameplay · last 30 days</h2><a class="button secondary small" href="index.php?page=activity&q='.e($uuid).'">All activity</a></div><div class="panel-body flush">';obsActivityRows($q->fetchAll());echo '</div></article>';
}

function obsReviewControls(string $kind,int $id,string $status,?int $case): void
{
    echo '<div class="button-row">';
    foreach([$status==='dismissed'?'reopen':'dismiss'=>$status==='dismissed'?'Reopen':'Dismiss','case'=>'Create case'] as $action=>$label) {
        if($action==='case'&&(!can('cases.create')||$case))continue;
        echo '<form method="post">'.csrfField().'<input type="hidden" name="action" value="review-'.$kind.'-'.$action.'"><input type="hidden" name="id" value="'.$id.'"><button class="button secondary small" type="submit">'.$label.'</button></form>';
    }
    if($case)echo '<a href="index.php?page=cases&case_id='.$case.'">Open case</a>';echo '</div>';
}
function showChatReview(): void
{
    requirePermission('messages.review');requirePermission('messages.view');$server=obsText($_GET['server']??'smp',64);$status=(string)($_GET['status']??'open');if(!in_array($status,['open','dismissed','escalated','all'],true))$status='open';$params=[];$where=['created_at>=DATE_SUB(NOW(),INTERVAL 30 DAY)'];if($server!==''){$where[]='server_name=?';$params[]=$server;}if($status!=='all'){$where[]='status=?';$params[]=$status;}
    if(!can('messages.private'))$where[]="channel_name<>'private'";
    $q=db()->prepare('SELECT * FROM chat_signals WHERE '.implode(' AND ',$where).' ORDER BY created_at DESC LIMIT 200');$q->execute($params);$rows=$q->fetchAll();$signalId=(int)($_GET['id']??0);$selected=null;
    if($signalId){$q=db()->prepare('SELECT * FROM chat_signals WHERE id=?');$q->execute([$signalId]);$selected=$q->fetch()?:null;if($selected&&$selected['channel_name']==='private'&&!can('messages.private'))$selected=null;}
    ob_start();obsServerTabs('chat-review',$server); ?>
    <div class="page-intro"><div><strong>Bug and exploit review hints</strong><p>Matching words flag conversations for a person to review. A hint is not proof and never triggers a punishment.</p></div><form method="post"><?= csrfField() ?><input type="hidden" name="action" value="review-chat-dismiss-all"><input type="hidden" name="server" value="<?= e($server) ?>"><button class="button secondary" data-confirm="Dismiss all open chat hints for this server?" type="submit">Dismiss all open hints</button></form></div>
    <nav class="filter-tabs"><?php foreach(['open','dismissed','escalated','all'] as $s): ?><a class="<?= $status===$s?'active':'' ?>" href="index.php?<?= e(http_build_query(['page'=>'chat-review','server'=>$server,'status'=>$s])) ?>"><?= e(ucfirst($s)) ?></a><?php endforeach; ?></nav>
    <?php if($selected): $q=db()->prepare('SELECT * FROM player_messages WHERE id=?');$q->execute([$selected['message_id']]);$message=$q->fetch();$context=[];if($message){$conditions=['source_server=?','channel_name=?','sent_at BETWEEN DATE_SUB(?,INTERVAL 3 MINUTE) AND DATE_ADD(?,INTERVAL 3 MINUTE)'];$args=[$message['source_server'],$message['channel_name'],$message['sent_at'],$message['sent_at']];if($message['channel_name']==='private'){$conditions[]='player_name IN (?,?) AND recipient_name IN (?,?)';array_push($args,$message['player_name'],$message['recipient_name'],$message['player_name'],$message['recipient_name']);}$q=db()->prepare('SELECT * FROM player_messages WHERE '.implode(' AND ',$conditions).' ORDER BY sent_at,id LIMIT 100');$q->execute($args);$context=$q->fetchAll();} ?>
    <article class="panel"><div class="panel-header"><h2>Conversation context · ±3 minutes</h2><span class="badge"><?= e(strtoupper($selected['server_name']).' · '.$selected['channel_name']) ?></span></div><div class="panel-body"><?php foreach($context as $c): ?><div class="chat-context <?= (int)$c['id']===(int)$selected['message_id']?'highlight':'' ?>"><?= renderPlayerIdentity($c['player_name'],$c['player_uuid'],displayDate($c['sent_at']),true) ?><p><?= e($c['message_text']) ?></p></div><?php endforeach; ?><?php obsReviewControls('chat',(int)$selected['id'],$selected['status'],$selected['case_id']?(int)$selected['case_id']:null); ?></div></article><?php endif; ?>
    <article class="panel"><div class="panel-header"><h2>Review queue</h2><span class="badge"><?= count($rows) ?> shown</span></div><div class="panel-body"><?php foreach($rows as $r): ?><div class="review-item"><div><?= renderPlayerIdentity($r['player_name'],$r['player_uuid'],strtoupper($r['server_name']).' · '.$r['category'],true) ?><span class="badge"><?= e($r['status']) ?></span></div><p><?= e($r['excerpt']) ?></p><span class="cell-sub">Matched: <?= e($r['matched_terms']) ?> · <?= e(displayDate($r['created_at'])) ?></span><a href="index.php?page=chat-review&id=<?= (int)$r['id'] ?>&server=<?= e($server) ?>">View conversation</a><?php obsReviewControls('chat',(int)$r['id'],$r['status'],$r['case_id']?(int)$r['case_id']:null); ?></div><?php endforeach; ?><?php if(!$rows): ?><div class="empty-state"><strong>No matching hints</strong><p>New matching messages will appear here.</p></div><?php endif; ?></div></article>
    <?php if(can('api.manage')): $rules=db()->query("SELECT setting_value FROM settings WHERE setting_key='chat.review.rules'")->fetchColumn(); ?><details class="panel"><summary class="panel-header">Edit review phrases</summary><form class="form-stack panel-body" method="post"><?= csrfField() ?><input type="hidden" name="action" value="chat-rules"><label for="chat-rules">JSON categories and phrases</label><textarea id="chat-rules" name="rules" rows="8"><?= e(json_encode(json_decode((string)$rules,true),JSON_PRETTY_PRINT|JSON_UNESCAPED_UNICODE)) ?></textarea><button class="button" type="submit">Save phrases</button><span class="field-hint">Changes apply to new incoming messages. Custom categories apply to new messages. Built-in dupe detection remains enabled.</span></form></details><?php endif; ?>
    <?php renderAppPage('Chat Review','chat-review',(string)ob_get_clean());
}

function showServerDiagnostics(): void
{
    requirePermission('plugins.view');$server=obsText($_GET['server']??'',64);$status=(string)($_GET['status']??'open');
    $params=[];$where=['last_at>=DATE_SUB(NOW(),INTERVAL 30 DAY)'];
    if($server!==''){$where[]='server_name=?';$params[]=$server;}
    if($status!=='all'){$where[]='status=?';$params[]=in_array($status,['open','dismissed','escalated'],true)?$status:'open';}
    $q=db()->prepare('SELECT * FROM server_diagnostics WHERE '.implode(' AND ',$where).' ORDER BY last_at DESC LIMIT 2000');$q->execute($params);$rows=$q->fetchAll();$groups=groupedDiagnostics($rows);
    ob_start(); ?>
    <div class="page-intro"><div><strong>Server diagnostics</strong><p>Identical warnings and errors appear once. Expand a group for individual timestamps, statuses and review actions.</p></div><span class="badge">Last 30 days</span></div>
    <form class="toolbar" method="get"><input type="hidden" name="page" value="diagnostics"><label>Server<select name="server"><option value="">All servers</option><?php foreach(obsServers() as $s): ?><option value="<?= e($s) ?>" <?= $server===$s?'selected':'' ?>><?= e(strtoupper($s)) ?></option><?php endforeach; ?></select></label><label>Status<select name="status"><?php foreach(['open','dismissed','escalated','all'] as $s): ?><option value="<?= $s ?>" <?= $status===$s?'selected':'' ?>><?= ucfirst($s) ?></option><?php endforeach; ?></select></label><button class="button secondary">Filter</button></form>
    <article class="panel"><div class="panel-header"><h2>Grouped warnings & errors</h2><span class="badge"><?= count($groups) ?> groups · <?= array_sum(array_column($groups,'occurrences')) ?> occurrences shown</span></div><div class="panel-body">
    <?php foreach($groups as $group):$r=$group['sample']; ?><details class="diagnostic-group"><summary><strong><?= e(strtoupper($r['server_name']).' · '.$r['plugin_name']) ?></strong> <span class="badge <?= $r['level']==='ERROR'?'high':'' ?>"><?= e($r['level']) ?> ×<?= $group['occurrences'] ?></span><code><?= e($r['message']) ?></code><span class="cell-sub"><?= e(displayDate($group['first_at']).' → '.displayDate($group['last_at'])) ?></span></summary><div class="diagnostic-occurrences"><?php foreach($group['rows'] as $item): ?><div class="review-item"><time><?= e(displayDate($item['first_at']).' → '.displayDate($item['last_at'])) ?></time><span class="badge"><?= e($item['status']) ?> ×<?= (int)$item['occurrences'] ?></span><?php if(can('alerts.manage')) obsReviewControls('diagnostic',(int)$item['id'],$item['status'],$item['case_id']?(int)$item['case_id']:null); ?></div><?php endforeach; ?></div></details><?php endforeach; ?>
    <?php if(!$groups): ?><p class="subtle">No matching diagnostics.</p><?php endif; ?><?php if(count($rows)===2000): ?><p class="subtle">Showing the latest 2,000 minute records. Filter by server and status to narrow the view.</p><?php endif; ?></div></article>
    <?php renderAppPage('Server Diagnostics','diagnostics',(string)ob_get_clean());
}

function postObservability(string $action): never
{
    if($action==='chat-rules') {
        requirePermission('api.manage');$rules=json_decode((string)($_POST['rules']??''),true,16,JSON_THROW_ON_ERROR);
        if(!is_array($rules)||count($rules)>20)throw new RuntimeException('Use a JSON object with at most 20 categories.');
        foreach($rules as $category=>$terms){if(!is_string($category)||!is_array($terms)||count($terms)>50)throw new RuntimeException('Each category needs a list of up to 50 phrases.');foreach($terms as $term)if(!is_string($term)||mb_strlen($term)>80)throw new RuntimeException('Phrases must be strings of up to 80 characters.');}
        db()->prepare("UPDATE settings SET setting_value=?,updated_at=? WHERE setting_key='chat.review.rules'")->execute([jsonEncode($rules),now()]);audit('chat.rules.updated');setFlash('success','Review phrases saved.');redirect('chat-review');
    }
    if(!preg_match('/^review-(chat|diagnostic)-(dismiss|reopen|case|dismiss-all)$/',$action,$match))throw new RuntimeException('Unknown review action.');
    [,$kind,$operation]=$match;$user=requirePermission($kind==='chat'?'messages.review':'alerts.manage');requirePermission($kind==='chat'?'messages.view':'plugins.view');$table=$kind==='chat'?'chat_signals':'server_diagnostics';$page=$kind==='chat'?'chat-review':'diagnostics';$pdo=db();
    if($operation==='dismiss-all') {
        $server=obsText($_POST['server']??'',64);$sql="UPDATE $table SET status='dismissed',reviewed_by=?".($kind==='chat'?',reviewed_at=?':'')." WHERE status='open'".($server!==''?' AND server_name=?':'').($kind==='chat'&&!can('messages.private')?" AND channel_name<>'private'":'');$params=[$user['id']];if($kind==='chat')$params[]=now();if($server!=='')$params[]=$server;$q=$pdo->prepare($sql);$q->execute($params);audit('review.'.$kind.'.dismiss_all',null,null,['server'=>$server,'count'=>$q->rowCount()]);setFlash('success','Open items dismissed.');redirect($page,['server'=>$server]);
    }
    $id=(int)($_POST['id']??0);$pdo->beginTransaction();
    try {
        $q=$pdo->prepare("SELECT * FROM $table WHERE id=? FOR UPDATE");$q->execute([$id]);$r=$q->fetch();if(!$r)throw new RuntimeException('Review item not found.');if($kind==='chat'&&$r['channel_name']==='private')requirePermission('messages.private');
        if($operation==='case') {
            requirePermission('cases.create');if($r['case_id']){$pdo->commit();redirect('cases',['case_id'=>(int)$r['case_id']]);}
            $name=$kind==='chat'?$r['player_name']:'Server';$uuid=$kind==='chat'?$r['player_uuid']:null;
            $body=$kind==='chat'?'Chat hint · '.$r['category']."\nMatched phrases: ".$r['matched_terms']."\nServer / channel: ".$r['server_name'].' / '.$r['channel_name']."\n".$r['excerpt']:'Server diagnostic · '.$r['server_name'].' / '.$r['plugin_name']."\n".$r['message'];
            $number='TR-'.date('ymd').'-'.strtoupper(bin2hex(random_bytes(3)));
            $private=$kind==='chat'&&$r['channel_name']==='private';
            $pdo->prepare("INSERT INTO cases(case_number,player_uuid,player_name,title,description,status,priority,created_by,created_at,updated_at) VALUES(?,?,?,?,?,'open','normal',?,?,?)")->execute([$number,$uuid,$name,obsText(($kind==='chat'?'Chat review: ':'Server issue: ').$name,190),$private?'Private chat evidence is saved in the permission-protected timeline.':$body,$user['id'],now(),now()]);$case=(int)$pdo->lastInsertId();
            if($uuid)$pdo->prepare('INSERT INTO case_players(case_id,player_uuid,player_name,primary_subject,added_at) VALUES(?,?,?,1,?)')->execute([$case,$uuid,$name,now()]);
            $pdo->prepare("INSERT INTO case_events(case_id,actor_user_id,event_type,body,metadata_json,created_at) VALUES(?,?,'evidence',?,?,?)")->execute([$case,$user['id'],$body,jsonEncode(['private'=>$private,'review_kind'=>$kind,'review_id'=>$id,'server'=>$r['server_name']]),now()]);
            $pdo->prepare("UPDATE $table SET status='escalated',case_id=?,reviewed_by=? WHERE id=?")->execute([$case,$user['id'],$id]);audit('review.'.$kind.'.case','case',(string)$case);$pdo->commit();setFlash('success','Case created with the selected evidence.');redirect('cases',['case_id'=>$case]);
        }
        $pdo->prepare("UPDATE $table SET status=?,reviewed_by=?".($kind==='chat'?',reviewed_at=?':'').' WHERE id=?')->execute(array_merge([$operation==='dismiss'?'dismissed':'open',$user['id']],$kind==='chat'?[now()]:[],[$id]));audit('review.'.$kind.'.'.$operation,$kind,(string)$id);$pdo->commit();setFlash('success','Review item updated.');redirect($page,['server'=>$r['server_name']]);
    }catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();throw $e;}
}

<?php
declare(strict_types=1);

function gameDate(string $value): string
{
    try { $date=new DateTimeImmutable($value ?: 'now'); }
    catch(Throwable) { throw new InvalidArgumentException('Invalid game event timestamp.'); }
    return $date->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s');
}

function gameDisplayDate(?string $value): string
{
    if(!$value)return 'Never';
    try{return (new DateTimeImmutable($value,new DateTimeZone('UTC')))->setTimezone(new DateTimeZone((string)config('timezone','Europe/Berlin')))->format('M j, Y · H:i');}
    catch(Throwable){return 'Unknown time';}
}

/** Stable event IDs are retained independently of the short-lived ingest receipts. */
function gameIngest(PDO $pdo,array $payload,string $server,string $at): void
{
    $eventId=obsText($payload['event_id']??'',96);
    if ($eventId==='') throw new InvalidArgumentException('Game events require a stable event_id.');
    $plugin=obsText($payload['plugin']??'',96);
    $metric=obsText($payload['metric']??'',64);
    if ($plugin==='' || !preg_match('/^[a-z0-9_.-]{1,64}$/',$metric)) throw new InvalidArgumentException('Invalid game metric.');
    $value=$payload['value']??1;$count=$payload['count']??1;
    if (!is_numeric($value) || !is_finite((float)$value) || abs((float)$value)>1e12 || !is_numeric($count) || (int)$count<1 || (int)$count>1000000) throw new InvalidArgumentException('Invalid game event value.');
    $uuid=normalizeUuid((string)($payload['uuid']??''));$name=obsText($payload['username']??'',32);
    if (!$uuid && $name!=='') { $q=$pdo->prepare('SELECT uuid FROM players WHERE LOWER(username)=LOWER(?) ORDER BY updated_at DESC LIMIT 1');$q->execute([$name]);$uuid=normalizeUuid((string)$q->fetchColumn()); }
    $actor=$uuid?'uuid:'.$uuid:($name!==''?'name:'.mb_strtolower($name):'server');
    $dimension=obsText($payload['dimension']??'',128);
    $details=$payload['details']??[];
    if (!is_array($details) || strlen(jsonEncode($details))>12000) throw new InvalidArgumentException('Game details exceed the maximum size.');
    $stamp=gameDate($at);$key=hash('sha256',$server.':'.$eventId);
    $q=$pdo->prepare('INSERT IGNORE INTO game_events(event_key,server_name,plugin_name,metric,dimension,actor_key,player_uuid,player_name,value_sum,sample_count,details_json,occurred_at,imported) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)');
    $q->execute([$key,$server,$plugin,$metric,$dimension,$actor,$uuid,$name,(float)$value,(int)$count,jsonEncode($details),$stamp,empty($payload['imported'])?0:1]);
    if (!$q->rowCount()) return;
    $pdo->prepare('INSERT INTO game_player_daily(day_utc,server_name,plugin_name,metric,dimension,actor_key,player_uuid,player_name,value_sum,sample_count) VALUES(?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE value_sum=value_sum+VALUES(value_sum),sample_count=sample_count+VALUES(sample_count),player_name=IF(VALUES(player_name)<>\'\',VALUES(player_name),player_name)')
        ->execute([substr($stamp,0,10),$server,$plugin,$metric,$dimension,$actor,$uuid,$name,(float)$value,(int)$count]);
}

function gameSnapshot(PDO $pdo,array $payload,string $server,string $at): void
{
    $plugin=obsText($payload['plugin']??'',96);$key=obsText($payload['snapshot_key']??'catalog',96);$data=$payload['data']??null;
    if (!in_array($plugin,['ExcellentCrates','QuickShop-Hikari','MCPets','TownyRebornPetShops','TownyDragonEvent','LuckPerms','PinataParty','PyroFishingPro','Votifier'],true)
        || !preg_match('/^[a-zA-Z0-9_.:-]{1,96}$/',$key) || !is_array($data) || strlen(jsonEncode($data))>240000) throw new InvalidArgumentException('Invalid integration snapshot.');
    $pdo->prepare('INSERT INTO integration_snapshots(server_name,plugin_name,snapshot_key,data_json,captured_at) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE data_json=IF(VALUES(captured_at)>=captured_at,VALUES(data_json),data_json),captured_at=GREATEST(captured_at,VALUES(captured_at))')
        ->execute([$server,$plugin,$key,jsonEncode($data),gameDate($at)]);
    if(str_ends_with($key,'-manifest')&&isset($data['keys'],$data['prefix'])&&is_array($data['keys'])&&preg_match('/^[a-zA-Z0-9_-]+\.$/',(string)$data['prefix'])) {
        $keys=array_values(array_filter($data['keys'],static fn($v)=>is_string($v)&&str_starts_with($v,$data['prefix'])));
        $sql='DELETE FROM integration_snapshots WHERE server_name=? AND plugin_name=? AND snapshot_key LIKE ? AND captured_at<=?';
        $args=[$server,$plugin,$data['prefix'].'%',gameDate($at)];
        if($keys){$sql.=' AND snapshot_key NOT IN ('.implode(',',array_fill(0,count($keys),'?')).')';$args=[...$args,...$keys];}
        $pdo->prepare($sql)->execute($args);
    }
}

function gameRange(string $page): array
{
    $days=(string)($_GET['days']??'30');if(!in_array($days,['7','30','90','0'],true))$days='30';
    $server=obsText($_GET['server']??'',64);$params=[];$where='1=1';
    if($days!=='0'){$where.=' AND day_utc>=?';$params[]=gmdate('Y-m-d',time()-(int)$days*86400);}
    if($server!==''){$where.=' AND server_name=?';$params[]=$server;}
    return [$days,$server,$where,$params];
}

function gameToolbar(string $page): void
{
    [$days,$server]=gameRange($page); ?>
    <form class="toolbar" method="get"><input type="hidden" name="page" value="<?= e($page) ?>"><label>Period<select name="days"><?php foreach(['7'=>'7 days','30'=>'30 days','90'=>'90 days','0'=>'All recorded history'] as $key=>$label): ?><option value="<?= $key ?>" <?= $days===(string)$key?'selected':'' ?>><?= $label ?></option><?php endforeach; ?></select></label><label>Server<select name="server"><option value="">All servers</option><?php foreach(obsServers() as $s): ?><option value="<?= e($s) ?>" <?= $s===$server?'selected':'' ?>><?= e(strtoupper($s)) ?></option><?php endforeach; ?></select></label><button class="button secondary">Apply</button></form>
    <?php
}

function gameSubnav(string $active): void
{
    echo '<nav class="filter-tabs" aria-label="Game insights">';
    foreach(['statistics'=>['Overview','analytics.view'],'crates'=>['Crates','statistics.crates'],'economy'=>['Shops & pets','statistics.economy'],'leaderboards'=>['Leaderboards','statistics.leaderboards']] as $page=>[$label,$permission]) if(can($permission)) echo '<a class="'.($active===$page?'active':'').'" href="index.php?page='.e($page).'">'.e($label).'</a>';
    echo '</nav>';
}

function gameSnapshots(string $plugin, string $server=''): array
{
    $q=db()->prepare('SELECT * FROM integration_snapshots WHERE plugin_name=?'.($server!==''?' AND server_name=?':'').' ORDER BY server_name,snapshot_key');$q->execute($server!==''?[$plugin,$server]:[$plugin]);
    return array_map(static function($r){$r['data']=json_decode($r['data_json'],true)?:[];return $r;},$q->fetchAll());
}

function gameCoverage(string $plugin,string $server=''): void
{
    $q=db()->prepare('SELECT server_name,coverage,hooks_json,last_seen_at FROM plugin_observability WHERE plugin_name=?'.($server!==''?' AND server_name=?':''));$q->execute($server!==''?[$plugin,$server]:[$plugin]);$rows=$q->fetchAll();
    echo '<div class="coverage-strip"><strong>'.e($plugin).'</strong>';
    if(!$rows)echo '<span class="badge">No connector report yet</span>';
    foreach($rows as $row) {
        $hooks=json_decode((string)($row['hooks_json']??''),true)?:[];
        echo '<span class="badge '.($row['coverage']==='native'?'online':'').'">'.e(strtoupper($row['server_name']).' · '.$row['coverage']).'</span><span class="cell-sub">'.e(displayDate($row['last_seen_at'])).'</span>';
        if(!empty($hooks['failed_hooks']))echo '<span class="badge high">'.count($hooks['failed_hooks']).' unavailable hooks</span>';
    }
    echo '</div>';
}

function gameLeaders(string $plugin,string $metric,string $where,array $params,string $dimension=''): array
{
    $sql='SELECT actor_key,MAX(player_uuid) player_uuid,MAX(player_name) player_name,SUM(value_sum) total,SUM(sample_count) samples FROM game_player_daily WHERE '.$where.' AND plugin_name=? AND metric=? AND actor_key<>\'server\'';
    $args=[...$params,$plugin,$metric];if($dimension!==''){$sql.=' AND dimension=?';$args[]=$dimension;}
    $q=db()->prepare($sql.' GROUP BY actor_key ORDER BY total DESC,actor_key LIMIT 25');$q->execute($args);return $q->fetchAll();
}

function gameLeaderboardPanel(string $title,string $plugin,string $metric,string $unit='',string $dimension=''): void
{
    [,, $where,$params]=gameRange('leaderboards');$rows=gameLeaders($plugin,$metric,$where,$params,$dimension);
    echo '<article class="panel leaderboard-panel"><div class="panel-header"><h2>'.e($title).'</h2><span class="badge">Top 25</span></div><div class="panel-body">';
    if(!$rows)echo '<p class="subtle">No player events recorded for this period yet.</p>';
    foreach($rows as $i=>$row) echo '<div class="leaderboard-row"><span class="rank">'.($i+1).'</span>'.renderPlayerIdentity($row['player_name']?:'Unknown',$row['player_uuid']?:'', '',true).'<strong>'.e(obsNumber((float)$row['total']).($unit?' '.$unit:'')).'</strong></div>';
    echo '</div></article>';
}

function showLeaderboards(): void
{
    requirePermission('statistics.leaderboards');ob_start();gameSubnav('leaderboards');gameToolbar('leaderboards'); ?>
    <div class="page-intro"><div><strong>Player rankings</strong><p>Counts remain beyond the 30-day activity window. Rankings use recorded events; existing Dragon statistics are shown separately as lifetime totals.</p></div></div><section class="content-grid">
    <?php gameLeaderboardPanel('Votes','Votifier','votes');gameLeaderboardPanel('Dragon damage','TownyDragonEvent','dragon_damage','damage');gameLeaderboardPanel('Dragon wins','TownyDragonEvent','dragon_wins');gameLeaderboardPanel('Piñata hits','PinataParty','pinata_hits');gameLeaderboardPanel('Fishing catches','PyroFishingPro','fish_caught');gameLeaderboardPanel('Shiny fishing catches','PyroFishingPro','shiny_fish_caught'); ?></section>
    <?php foreach(gameSnapshots('TownyDragonEvent') as $snapshot): if($snapshot['snapshot_key']!=='lifetime')continue;$entries=$snapshot['data']['players']??[];usort($entries,static fn($a,$b)=>(float)($b['total-damage']??0)<=>(float)($a['total-damage']??0)); ?><article class="panel"><div class="panel-header"><h2>Existing Dragon totals · <?= e(strtoupper($snapshot['server_name'])) ?></h2><span class="badge">Lifetime snapshot</span></div><div class="panel-body"><p class="subtle">Captured <?= e(gameDisplayDate($snapshot['captured_at'])) ?>. These totals include older events and are not added a second time to event rankings.</p><?php foreach(array_slice($entries,0,25) as $i=>$r): ?><div class="leaderboard-row"><span class="rank"><?= $i+1 ?></span><?= renderPlayerIdentity($r['name']??$r['uuid'],$r['uuid']??'','',true) ?><strong><?= e(obsNumber((float)($r['total-damage']??0))) ?> damage · <?= (int)($r['wins']??0) ?> wins</strong></div><?php endforeach; ?></div></article><?php endforeach; ?>
    <?php foreach(['Votifier','TownyDragonEvent','PinataParty','PyroFishingPro'] as $p)gameCoverage($p);renderAppPage('Leaderboards','statistics',(string)ob_get_clean());
}

function showCrateInsights(): void
{
    requirePermission('statistics.crates');[$days,$server,$where,$params]=gameRange('crates');$crate=obsText($_GET['crate']??'',128);
    if($crate!==''){$where.=' AND dimension=?';$params[]=$crate;}
    $q=db()->prepare('SELECT dimension crate,SUM(value_sum) openings,SUM(sample_count) rewards,COUNT(DISTINCT actor_key) players FROM game_player_daily WHERE '.$where." AND plugin_name='ExcellentCrates' AND metric='crate_openings' GROUP BY dimension ORDER BY openings DESC");$q->execute($params);$totals=$q->fetchAll();
    $eventWhere=str_replace('day_utc','DATE(occurred_at)',$where);
    $q=db()->prepare('SELECT * FROM game_events WHERE '.$eventWhere." AND plugin_name='ExcellentCrates' AND metric='crate_openings' ORDER BY occurred_at DESC LIMIT 200");$q->execute($params);$events=$q->fetchAll();
    $q=db()->prepare("SELECT dimension label,SUM(value_sum) total FROM game_player_daily WHERE $where AND plugin_name='ExcellentCrates' AND metric='crate_reward' GROUP BY dimension ORDER BY total DESC LIMIT 100");$q->execute($params);$rewardTotals=$q->fetchAll();
    // Reward dimensions contain crate|reward, so apply a crate prefix rather than exact dimension for rewards.
    if($crate!==''){$rw=str_replace(' AND dimension=?','',$where);$rp=$params;array_pop($rp);$q=db()->prepare("SELECT dimension label,SUM(value_sum) total FROM game_player_daily WHERE $rw AND plugin_name='ExcellentCrates' AND metric='crate_reward' AND dimension LIKE ? GROUP BY dimension ORDER BY total DESC LIMIT 100");$q->execute([...$rp,str_replace(['\\','%','_'],['\\\\','\\%','\\_'],$crate).'|%']);$rewardTotals=$q->fetchAll();}
    ob_start();gameSubnav('crates');gameToolbar('crates'); ?>
    <div class="page-intro"><div><strong>Crate analytics</strong><p>Actual openings, players, reward frequencies and recent batches. A batch of seven rewards counts as seven openings. No keys or balances are changed by statistics.</p></div><span class="badge">Permanent history</span></div>
    <section class="stats-grid"><article class="stat-card accent-pink"><span>Openings</span><strong><?= number_format(array_sum(array_column($totals,'openings'))) ?></strong><small>Completed opening batches / imported history</small></article><article class="stat-card accent-blue"><span>Crates used</span><strong><?= count($totals) ?></strong><small>In the selected period</small></article><article class="stat-card accent-lime"><span>Reward types</span><strong><?= count($rewardTotals) ?></strong><small>Up to 100 most frequent</small></article></section>
    <article class="panel"><div class="panel-header"><h2>Openings by crate</h2></div><div class="panel-body"><div class="table-wrap"><table><thead><tr><th>Crate</th><th>Openings</th><th>Players</th><th>Details</th></tr></thead><tbody><?php foreach($totals as $r): ?><tr><td><?= e($r['crate']) ?></td><td><?= number_format((float)$r['openings']) ?></td><td><?= (int)$r['players'] ?></td><td><a href="index.php?<?= e(http_build_query(['page'=>'crates','days'=>$days,'server'=>$server,'crate'=>$r['crate']])) ?>">Inspect</a></td></tr><?php endforeach; ?></tbody></table></div><?php if(!$totals): ?><p class="subtle">No openings recorded. Import the supplied history once, then let the connector collect new rewards.</p><?php endif; ?></div></article>
    <section class="content-grid"><?php analyticsTablePanel('Observed rewards',$rewardTotals,'','Observed counts; these are not guaranteed drop probabilities.');gameLeaderboardPanel('Most crates opened','ExcellentCrates','crate_openings','openings',$crate); ?></section>
    <?php foreach(gameSnapshots('ExcellentCrates',$server) as $snapshot): if(!str_starts_with($snapshot['snapshot_key'],'crate.'))continue;$data=$snapshot['data'];if($crate!==''&&($data['id']??'')!==$crate)continue; ?><details class="panel"><summary class="panel-header">Configured crate · <?= e($data['name']??$data['id']??$snapshot['snapshot_key']) ?></summary><div class="panel-body"><p class="subtle"><?= e($data['id']??'') ?> · <?= e(strtoupper($snapshot['server_name'])) ?> · Captured <?= e(gameDisplayDate($snapshot['captured_at'])) ?></p><div class="table-wrap"><table><thead><tr><th>Reward</th><th>ID</th><th>Weight</th><th>Rarity</th></tr></thead><tbody><?php foreach($data['rewards']??[] as $r): ?><tr><td><?= e($r['name']??$r['id']??'') ?></td><td><code><?= e($r['id']??'') ?></code></td><td><?= e((string)($r['weight']??'—')) ?></td><td><?= e($r['rarity']??'—') ?></td></tr><?php endforeach; ?></tbody></table></div></div></details><?php endforeach; ?>
    <article class="panel"><div class="panel-header"><h2>Recent opening evidence</h2><span class="badge">Latest 200 records</span></div><div class="panel-body"><?php foreach($events as $r):$details=json_decode((string)$r['details_json'],true)?:[]; ?><details class="opening-record"><summary><strong><?= e($r['player_name']?:'Unknown') ?></strong> · <?= e($r['dimension']) ?> · <?= e(obsNumber((float)$r['value_sum'])) ?> opening(s)<time><?= e(gameDisplayDate($r['occurred_at'])) ?></time></summary><p><?= e(implode(', ',is_array($details['rewards']??null)?$details['rewards']:[(string)($details['reward']??'Not reported')])) ?></p><span class="badge"><?= $r['imported']?'Imported log':'Native event' ?></span><?php if(isset($details['selected_amount'])): ?><span class="badge">Batch <?= (int)$details['selected_amount'] ?></span><?php endif; ?></details><?php endforeach; ?></div></article>
    <?php gameCoverage('ExcellentCrates',$server);renderAppPage('Crate analytics','statistics',(string)ob_get_clean());
}

function showEconomyInsights(): void
{
    requirePermission('statistics.economy');[, $server,$where,$params]=gameRange('economy');
    ob_start();gameSubnav('economy');gameToolbar('economy'); ?>
    <div class="page-intro"><div><strong>Shops & MCPets</strong><p>Successful transactions and recorded pet activity. Pet shop purchase grants and pet summons are shown as separate actions.</p></div></div><section class="content-grid">
    <?php gameLeaderboardPanel('QuickShop trade value','QuickShop-Hikari','trade_value','$');gameLeaderboardPanel('QuickShop items traded','QuickShop-Hikari','items_traded');gameLeaderboardPanel('Pet purchases / grants','TownyRebornPetShops','pet_purchases');gameLeaderboardPanel('Pets summoned','MCPets','pet_summons'); ?></section>
    <?php foreach(['QuickShop-Hikari','TownyRebornPetShops','MCPets'] as $plugin):gameCoverage($plugin,$server);foreach(gameSnapshots($plugin,$server) as $snapshot): if(str_ends_with($snapshot['snapshot_key'],'-manifest'))continue; ?><details class="panel"><summary class="panel-header"><?= e($plugin.' · '.$snapshot['snapshot_key'].' · '.strtoupper($snapshot['server_name'])) ?></summary><div class="panel-body"><span class="cell-sub">Snapshot <?= e(gameDisplayDate($snapshot['captured_at'])) ?></span><div class="table-wrap"><table><thead><tr><th>Name / ID</th><th>Details</th></tr></thead><tbody><?php foreach($snapshot['data']['entries']??[] as $r): ?><tr><td><?= e((string)($r['name']??$r['id']??'Unknown')) ?></td><td><div class="detail-chips"><?php foreach($r as $key=>$value):if(in_array($key,['id','name'],true)||!is_scalar($value))continue; ?><span><strong><?= e(str_replace('_',' ',(string)$key)) ?></strong> <?= e((string)$value) ?></span><?php endforeach; ?></div></td></tr><?php endforeach; ?></tbody></table></div></div></details><?php endforeach;endforeach; ?>
    <article class="panel"><div class="panel-header"><h2>Recent shop and pet events</h2></div><div class="panel-body"><?php $ew=str_replace('day_utc','DATE(occurred_at)',$where);$q=db()->prepare("SELECT * FROM game_events WHERE $ew AND plugin_name IN('QuickShop-Hikari','MCPets','TownyRebornPetShops') ORDER BY occurred_at DESC LIMIT 100");$q->execute($params);foreach($q->fetchAll() as $r): ?><div class="review-item"><strong><?= e($r['player_name']?:'Server') ?></strong><span class="badge"><?= e($r['plugin_name'].' · '.str_replace('_',' ',$r['metric'])) ?></span><p><?= e($r['dimension']) ?> · <?= e(obsNumber((float)$r['value_sum'])) ?></p><time><?= e(gameDisplayDate($r['occurred_at'])) ?></time></div><?php endforeach; ?></div></article>
    <?php renderAppPage('Shops & pets','statistics',(string)ob_get_clean());
}

function showPermissionView(): void
{
    $user=requirePermission('team.view');$catalog=permissionCatalog();ob_start(); ?>
    <div class="page-intro"><div><strong>Who can see and do what?</strong><p>The website roles and Minecraft LuckPerms groups are independent. This page reads the current rules; it never impersonates another staff member.</p></div><a class="button secondary small" href="index.php?page=team">Manage roles</a></div>
    <article class="panel"><div class="panel-header"><h2>Staff HQ permissions</h2></div><div class="panel-body"><div class="table-wrap permission-matrix"><table><thead><tr><th>Permission</th><?php foreach(['owner','admin','moderator','helper'] as $role): ?><th class="role-accent" style="--role-accent:<?= e(rolePresentation($role)['accent']) ?>"><?= e(roleLabel($role)) ?><span class="cell-sub"><?= e(rolePresentation($role)['description']) ?></span></th><?php endforeach; ?></tr></thead><tbody><?php foreach($catalog as $permission=>$d): ?><tr><td><strong><?= e($d['label']) ?></strong><code class="cell-sub"><?= e($permission) ?></code></td><?php foreach(['owner','admin','moderator','helper'] as $role):$allowed=$role==='owner'||in_array($permission,permissionsForRole($role),true); ?><td><span class="badge <?= $allowed?'online':'' ?>"><?= $allowed?'Allowed':'Denied' ?></span></td><?php endforeach; ?></tr><?php endforeach; ?></tbody></table></div></div></article>
    <p class="notice">Only the Owner can add staff accounts and change role permissions. Assigning “Manage staff” does not grant account creation.</p>
    <?php if($user['role']==='owner'):foreach(['admin','moderator','helper'] as $role):$presentation=rolePresentation($role); ?><details class="panel"><summary class="panel-header">Customise <?= e(roleLabel($role)) ?></summary><form method="post" class="panel-body form-stack"><?= csrfField() ?><input type="hidden" name="action" value="role-presentation"><input type="hidden" name="role" value="<?= $role ?>"><input type="hidden" name="return_page" value="permissions"><label>Display name<input name="label" maxlength="40" value="<?= e($presentation['label']) ?>" required></label><label>Role description<textarea name="description" maxlength="500"><?= e($presentation['description']) ?></textarea></label><label>Accent colour<input type="color" name="accent" value="<?= e($presentation['accent']) ?>"></label><button class="button secondary">Save role presentation</button></form></details><?php endforeach;endif; ?>
    <?php if(can('plugins.view')): ?><article class="panel"><div class="panel-header"><h2>Minecraft · LuckPerms group nodes</h2><span class="badge">Read only</span></div><div class="panel-body"><p class="subtle">Direct nodes and inheritance from the latest SMP snapshot. Contexts and parent groups are shown explicitly; a player's effective permissions also depend on their world, server and personal nodes.</p><?php $snapshots=gameSnapshots('LuckPerms');foreach($snapshots as $snapshot):$d=$snapshot['data']; ?><details class="permission-group"><summary><strong><?= e($d['name']??$snapshot['snapshot_key']) ?></strong> · <?= e(strtoupper($snapshot['server_name'])) ?> · <?= count($d['nodes']??[]) ?> nodes</summary><span class="cell-sub">Captured <?= e(gameDisplayDate($snapshot['captured_at'])) ?></span><?php foreach($d['nodes']??[] as $node): ?><div class="permission-node"><span class="badge <?= !empty($node['value'])?'online':'high' ?>"><?= !empty($node['value'])?'Allow':'Deny' ?></span><code><?= e($node['key']??'') ?></code><span class="cell-sub"><?= e(jsonEncode($node['contexts']??[])) ?></span></div><?php endforeach; ?><?php if(!empty($d['truncated'])): ?><p>Additional nodes are omitted from this snapshot; use LuckPerms Editor for the complete group.</p><?php endif; ?></details><?php endforeach; ?><?php if(!$snapshots): ?><p>No LuckPerms snapshots yet. Install the updated connector on the SMP.</p><?php endif; ?></div></article><?php endif; ?>
    <?php renderAppPage('Permission view','team',(string)ob_get_clean());
}

function rolePresentation(string $role): array
{
    $default=['label'=>match($role){'owner'=>'Owner','admin'=>'Administrator','moderator'=>'Moderator',default=>'Helper'},'description'=>'','accent'=>'#a42580'];
    static $cache=[];if(isset($cache[$role]))return $cache[$role];
    try{$q=db()->prepare('SELECT setting_value FROM settings WHERE setting_key=?');$q->execute(['staff.role.'.$role]);$v=json_decode((string)$q->fetchColumn(),true);return $cache[$role]=array_replace($default,is_array($v)?$v:[]);}catch(Throwable){return $default;}
}

function postRolePresentation(): never
{
    $user=requireAuth();if($user['role']!=='owner'){http_response_code(403);exit('Only the Owner can customise roles.');}
    $role=(string)($_POST['role']??'');$label=trim((string)($_POST['label']??''));$description=trim((string)($_POST['description']??''));$accent=(string)($_POST['accent']??'');
    if(!in_array($role,['admin','moderator','helper'],true)||$label===''||mb_strlen($label)>40||mb_strlen($description)>500||!preg_match('/^#[a-fA-F0-9]{6}$/',$accent))throw new RuntimeException('Enter a valid role name, description and colour.');
    db()->prepare('INSERT INTO settings(setting_key,setting_value,updated_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value),updated_at=VALUES(updated_at)')->execute(['staff.role.'.$role,jsonEncode(['label'=>$label,'description'=>$description,'accent'=>$accent]),now()]);
    audit('role.presentation.updated','role',$role);setFlash('success','Role presentation updated.');redirect('permissions');
}

function showStaffGuide(): void
{
    requirePermission('documents.view');ob_start(); ?>
    <div class="page-intro"><div><strong>Staff field guide</strong><p>A quick route from a player report to a documented decision.</p></div><span class="badge">Towny Reborn</span></div>
    <section class="guide-grid"><article class="panel"><div class="panel-header"><h2>1 · Check the player</h2></div><div class="panel-body"><p>Find their profile in Players. Read active watch instructions before acting. Notes describe context; they do not prove misconduct.</p><p>Save a short factual note when the next staff member should know something. Choose “Watch instruction” for a specific thing to observe, then archive it when it no longer applies.</p></div></article><article class="panel"><div class="panel-header"><h2>2 · Preserve useful evidence</h2></div><div class="panel-body"><p>Use “Pin to profile” on a chat message to keep a permanent copy. Raw chat and commands expire after 30 days. Screenshots and supporting files can be attached to the profile without a case.</p><p>Include the time, place and what happened. Avoid irrelevant personal information. Private chat is visible only to staff with the additional permission.</p></div></article><article class="panel"><div class="panel-header"><h2>3 · Open a case when needed</h2></div><div class="panel-body"><p>Open a case directly from a message to preserve its exact wording and context, or use “Open a case” on the profile. Give it a clear title, add evidence and record what was checked.</p><p>A repeated click on the same source message opens the existing case. Use the general profile form for a separate investigation.</p></div></article><article class="panel"><div class="panel-header"><h2>4 · Review flags carefully</h2></div><div class="panel-body"><p>Chat Review flags dupe and exploit phrases across public, staff and captured private channels. The Discord alert is a request to inspect the conversation, never an automatic punishment.</p><p>Use conversation context, CoreProtect and the server logs to check what happened. Dismiss harmless reports; preserve evidence and open a case for a real incident.</p></div></article><article class="panel"><div class="panel-header"><h2>5 · Understand the statistics</h2></div><div class="panel-body"><p>Crates shows confirmed rewards and opening batches. Seven openings in one batch count as seven. Configured reward weights and observed frequencies answer different questions.</p><p>Shop value comes from successful QuickShop transactions. Pet summons are separate from purchases. Native event rankings start when the updated connector is installed; supplied history is marked “Imported”. A missing hook means missing coverage, not zero activity.</p></div></article><article class="panel"><div class="panel-header"><h2>6 · Diagnose and hand over</h2></div><div class="panel-body"><p>Diagnostics groups the same error across timestamps. Expand a group for individual occurrences and review actions. Attention shows delivery problems and server health.</p><p>Explain what you checked and the next step in the player note or case. Only the Owner can add staff and change group permissions. Website roles and Minecraft LuckPerms groups are configured independently.</p></div></article></section>
    <article class="panel"><div class="panel-header"><h2>Dragon event quick reference</h2></div><div class="panel-body"><p><code>/dragon status</code> shows the phase. The five-minute lobby displays a countdown and live boards near the arena portal. <code>/dragon stop</code> evacuates players, unloads the runtime world and removes only a marked event clone.</p><p>If cleanup reports an unowned old folder, the plugin preserves it and selects a separate managed clone for the next event. Never delete the template or Earth to solve a runtime problem. Stop and start the server to replace plugin JARs; avoid PlugMan reloads for world events.</p></div></article>
    <?php renderAppPage('Staff field guide','documents',(string)ob_get_clean());
}

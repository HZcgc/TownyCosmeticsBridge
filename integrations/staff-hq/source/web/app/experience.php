<?php
declare(strict_types=1);

/** One catalogue drives direct navigation, overview cards and guarded detail pages. */
function hqTopics(): array
{
    return [
        'crates'=>['Crates','statistics.crates','ExcellentCrates','crate_openings','Openings','Rewards, opening batches and player history.','box'],
        'dragon'=>['Dragon event','statistics.leaderboards','TownyDragonEvent','dragon_damage','Damage','Fight results, winners and lifetime records.','dragon'],
        'votes'=>['Voting','statistics.leaderboards','Votifier','votes','Votes','Player rankings and voting services.','vote'],
        'pinata'=>['Piñata','statistics.leaderboards','PinataParty','pinata_hits','Hits','Hits, spawns and recorded event activity.','spark'],
        'fishing'=>['Fishing','statistics.leaderboards','PyroFishingPro','fish_caught','Catches','Catch tiers, shiny fish and sea creatures.','fish'],
        'shops'=>['Player shops','statistics.economy','QuickShop-Hikari','trade_value','Trade value','Sales, traded items and the shop catalogue.','shop'],
        'pets'=>['MCPets','statistics.economy','TownyRebornPetShops','pet_purchases','Purchases / grants','Ownership grants, summons and pet offers.','paw'],
    ];
}

function hqIcon(string $name): string
{
    $path=match($name) {
        'box'=>'<path d="m3 7 9-4 9 4-9 4-9-4Zm0 0v10l9 4 9-4V7M12 11v10M7 5l10 4"/>',
        'dragon'=>'<path d="m3 16 5-2-2-6 6 2 3-7 2 5 4 2-2 6-6 3-4-2-6 3M16 11h.01M7 8 4 4l7 3"/>',
        'vote'=>'<path d="m7 12 3 3 7-7M4 5h5m6 0h5v15H4V5"/>',
        'spark'=>'<path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3ZM19 3v4m-2-2h4"/>',
        'fish'=>'<path d="M19 12c-4-7-10-7-13 0 3 7 9 7 13 0Zm0 0 3-4v8l-3-4M6 12 2 8v8l4-4M14 10h.01M10 6l2-3"/>',
        'shop'=>'<path d="M4 10v11h16V10M3 4h18l1 6c-2 3-4 3-6 0-2 3-4 3-6 0-2 3-4 3-8 0l1-6ZM9 21v-7h6v7"/>',
        'paw'=>'<ellipse cx="5" cy="8" rx="2" ry="3"/><ellipse cx="10" cy="5" rx="2" ry="3"/><ellipse cx="16" cy="5" rx="2" ry="3"/><ellipse cx="21" cy="9" rx="2" ry="3"/><path d="M6 18c0-4 4-7 7-7s7 3 7 7c0 4-4 3-7 2-3 1-7 2-7-2Z"/>',
        'players'=>'<circle cx="9" cy="7" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3m1-17a3 3 0 0 1 0 6m3 11v-4a5 5 0 0 0-3-4"/>',
        'alert'=>'<path d="m12 3 10 18H2L12 3Zm0 6v5m0 3h.01"/>',
        'case'=>'<path d="M3 6h7l2 3h9v12H3V6Zm5 7h8m-8 4h5"/>',
        'chart'=>'<path d="M3 3v18h18M7 15l4-5 4 3 6-8"/>',
        'home'=>'<path d="m3 10 9-7 9 7M5 9v12h14V9M9 21v-8h6v8"/>',
        'server'=>'<rect x="3" y="3" width="18" height="7" rx="2"/><rect x="3" y="14" width="18" height="7" rx="2"/><path d="M7 6.5h.01M7 17.5h.01m4-11h6m-6 11h6"/>',
        'message'=>'<path d="M3 4h18v13H8l-5 4V4Zm4 5h10M7 13h6"/>',
        default=>'<path d="M4 4h16v16H4V4Zm4 4h8m-8 4h8m-8 4h5"/>',
    };
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">'.$path.'</svg>';
}

function hqNavigation(): array
{
    $games=[['statistics','Game overview','analytics.view','chart']];
    foreach(hqTopics() as $page=>$d)$games[]=[$page,$d[0],$d[1],$d[6]];
    return [
        'Workspace'=>[['dashboard','Dashboard','dashboard.view','home'],['attention','Needs attention','alerts.view','alert']],
        'Games & economy'=>$games,
        'Players & moderation'=>[['players','Players','players.view','players'],['alerts','Anti-cheat alerts','alerts.view','alert'],['cases','Cases','cases.view','case'],['punishments','Punishments','punishments.view','case'],['chat-review','Chat review','messages.review','message'],['messages','Messages','messages.view','message'],['commands','Commands','commands.view','server'],['activity','Player activity','activity.view','chart']],
        'Server & analysis'=>[['analytics','Server analytics','analytics.view','chart'],['metrics','Metric explorer','analytics.view','chart'],['plugins','Connections','plugins.view','server'],['diagnostics','Diagnostics','plugins.view','alert'],['leaderboards','All rankings','statistics.leaderboards','vote']],
        'Team & guidance'=>[['permissions','Permissions','team.view','players'],['team','Staff team','team.view','players'],['staff-guide','Staff guide','documents.view','case'],['documents','Documents','documents.view','case'],['audit','Staff audit','audit.view','case'],['settings','System','api.manage','server']],
    ];
}

function hqGameTotals(string $where,array $params): array
{
    $q=db()->prepare('SELECT plugin_name,metric,SUM(value_sum) total,SUM(sample_count) samples,COUNT(DISTINCT CASE WHEN actor_key<>\'server\' THEN actor_key END) players FROM game_player_daily WHERE '.$where.' GROUP BY plugin_name,metric');$q->execute($params);
    $out=[];foreach($q->fetchAll() as $r)$out[$r['plugin_name'].'|'.$r['metric']]=$r;
    return $out;
}

function hqValue(?array $row,string $metric=''): string
{
    if(!$row)return 'No data yet';
    return ($metric==='trade_value'?'$':'').obsNumber((float)$row['total']);
}

function hqTopicCards(bool $dashboard=false): void
{
    [$days,$server,$where,$params]=gameRange('statistics');$totals=hqGameTotals($where,$params);
    $allowed=array_filter(hqTopics(),static fn($d)=>can($d[1]));if(!$allowed)return;
    echo '<section class="hq-section"><div class="hq-section-heading"><div><span class="eyebrow">Games & economy</span><h2>'.($dashboard?'The parts of your server':'Choose a game or system').'</h2></div><span class="scope-label">'.($days==='0'?'All recorded history':$days.' days').' · '.e($server?:'All servers').'</span></div><div class="topic-grid">';
    foreach($allowed as $page=>$d) {
        $row=$totals[$d[2].'|'.$d[3]]??null;
        echo '<a class="topic-card" data-game-card="'.e($page).'" href="index.php?'.e(http_build_query(['page'=>$page,'days'=>$days,'server'=>$server])).'"><span class="topic-icon">'.hqIcon($d[6]).'</span><span class="topic-title">'.e($d[0]).'<span aria-hidden="true">↗</span></span><strong class="topic-value '.($row?'':'no-data').'">'.e(hqValue($row,$d[3])).'</strong><span class="topic-metric">'.e($d[4]).'</span><p>'.e($d[5]).'</p></a>';
    }
    echo '</div></section>';
}

function showGameOverview(): void
{
    requirePermission('analytics.view');ob_start(); ?>
    <div class="hq-hero"><div><span class="eyebrow">Every system, one clear view</span><h2>Games & economy</h2><p>See what is happening, then open a card for rankings, trends and individual records.</p></div><?= hqIcon('chart') ?></div>
    <?php gameToolbar('statistics');hqTopicCards(); ?>
    <div class="hq-footer-note"><strong>Want a different measurement?</strong><p>The Metric explorer has all other plugin measurements, custom dates, breakdowns and exports.</p><a class="button secondary" href="index.php?page=metrics">Open Metric explorer</a></div>
    <?php renderAppPage('Games & economy','statistics',(string)ob_get_clean());
}

function hqDashboardIntro(): void
{
    ?><section class="hq-hero"><div><span class="eyebrow">Your daily staff workspace</span><h2>Your server, at a glance.</h2><p>Start with anything that needs attention, or open a game below for the complete picture.</p></div><?php if(can('players.view')): ?><form class="hq-player-search" action="index.php" method="get"><input type="hidden" name="page" value="players"><label for="hq-player-query">Open a player profile</label><div><input id="hq-player-query" name="q" placeholder="Player name or UUID" aria-label="Player name or UUID" required><button class="button" type="submit">Find player</button></div></form><?php endif; ?></section>
    <?php if(can('plugins.view')): $rows=db()->query('SELECT * FROM server_status ORDER BY server_name')->fetchAll(); ?><section class="server-strip" aria-label="Server connection status"><?php foreach($rows as $r):$fresh=strtotime($r['updated_at'])>=time()-180; ?><a class="server-chip" href="index.php?page=plugins"><span class="status-dot <?= $fresh?'':'stale' ?>"></span><strong><?= e(strtoupper($r['server_name'])) ?></strong><span><?= $fresh?(int)$r['online_players'].' players':'Report delayed' ?></span><?php if($fresh&&$r['tps']!==null): ?><span><?= e(obsNumber((float)$r['tps'])) ?> TPS</span><?php endif; ?><small>Reported <?= e(displayDate($r['captured_at'])) ?></small></a><?php endforeach; ?><?php if(!$rows): ?><div class="server-chip"><strong>Waiting for a server report</strong><span>Connections shows the latest connector status.</span></div><?php endif; ?></section><?php endif;
}

function showHqDashboard(): void
{
    requirePermission('dashboard.view');
    $players=(int)db()->query('SELECT COUNT(*) FROM players')->fetchColumn();
    $alerts=can('alerts.view')?db()->query("SELECT * FROM alerts WHERE status IN ('open','escalated') ORDER BY occurred_at DESC LIMIT 7")->fetchAll():[];
    $cases=can('cases.view')?db()->query("SELECT c.*,u.display_name assigned_name FROM cases c LEFT JOIN users u ON u.id=c.assigned_user_id WHERE c.status NOT IN ('resolved','closed') ORDER BY c.updated_at DESC LIMIT 6")->fetchAll():[];
    $subjects=casePlayersMap(array_column($cases,'id'));foreach($cases as &$c)$c['subjects']=$subjects[(int)$c['id']]??[];unset($c);
    ob_start();hqDashboardIntro(); ?>
    <section class="hq-task-grid" aria-label="Staff priorities">
        <?php if(can('players.view')): ?><a class="hq-task-card" href="index.php?page=players"><?= hqIcon('players') ?><span><strong><?= obsOnline() ?> players online</strong><small><?= $players ?> known profiles · notes, pins & files</small></span><span aria-hidden="true">→</span></a><?php endif; ?>
        <?php if(can('messages.review')&&can('messages.view')):$where=can('messages.private')?'':" AND channel_name<>'private'";$n=(int)db()->query("SELECT COUNT(*) FROM chat_signals WHERE status='open'".$where)->fetchColumn(); ?><a class="hq-task-card" href="index.php?page=chat-review"><?= hqIcon('message') ?><span><strong><?= $n ?> chat flags</strong><small>Review dupe and exploit discussions</small></span><span aria-hidden="true">→</span></a><?php endif; ?>
        <?php if(can('alerts.view')):$n=(int)db()->query("SELECT COUNT(*) FROM alerts WHERE status IN ('open','escalated')")->fetchColumn(); ?><a class="hq-task-card" href="index.php?page=attention"><?= hqIcon('alert') ?><span><strong><?= $n ?> open alerts</strong><small>Review server issues and new signals</small></span><span aria-hidden="true">→</span></a><?php endif; ?>
        <?php if(can('cases.view')):$n=(int)db()->query("SELECT COUNT(*) FROM cases WHERE status NOT IN ('resolved','closed')")->fetchColumn(); ?><a class="hq-task-card" href="index.php?page=cases"><?= hqIcon('case') ?><span><strong><?= $n ?> active cases</strong><small>Decisions, evidence and assignments</small></span><span aria-hidden="true">→</span></a><?php endif; ?>
    </section>
    <?php hqTopicCards(true); ?>
    <section class="analysis-two-col">
        <?php if(can('alerts.view')): ?><article class="panel"><div class="panel-header"><h2>Alerts to review</h2><a class="button secondary small" href="index.php?page=alerts">All alerts</a></div><div class="panel-body"><?php if(!$alerts): ?><div class="hq-empty"><strong>No open anti-cheat alerts</strong><p>New alerts awaiting review appear here.</p></div><?php endif; foreach($alerts as $i=>$a):if($i===3): ?><details class="detail-disclosure"><summary>Show <?= count($alerts)-3 ?> more alerts</summary><?php endif; ?><a class="work-record" href="index.php?page=alerts"><span><strong><?= e($a['player_name']?:'Unknown player') ?></strong><small><?= e($a['check_name']??$a['alert_type']??'Anti-cheat alert') ?> · <?= e(displayDate($a['occurred_at'])) ?></small></span><span class="badge <?= e($a['severity']) ?>"><?= e(ucfirst($a['severity'])) ?></span></a><?php endforeach;if(count($alerts)>3)echo '</details>'; ?></div></article><?php endif; ?>
        <?php if(can('cases.view')): ?><article class="panel"><div class="panel-header"><h2>Cases in progress</h2><a class="button secondary small" href="index.php?page=cases">All cases</a></div><div class="panel-body"><?php if(!$cases): ?><div class="hq-empty"><strong>No active cases</strong><p>Unresolved investigations appear here.</p></div><?php endif;foreach($cases as $i=>$c):if($i===3): ?><details class="detail-disclosure"><summary>Show <?= count($cases)-3 ?> more cases</summary><?php endif; ?><a class="work-record" href="index.php?page=cases&amp;case_id=<?= (int)$c['id'] ?>"><span><strong><?= e($c['title']) ?></strong><small><?= e($c['case_number']) ?> · <?= e(implode(', ',array_column($c['subjects'],'player_name'))) ?></small><small><?= e($c['assigned_name']?:'Unassigned') ?> · updated <?= e(displayDate($c['updated_at'])) ?></small></span><span class="badge <?= e($c['status']) ?>"><?= e(ucwords(str_replace('_',' ',$c['status']))) ?></span></a><?php endforeach;if(count($cases)>3)echo '</details>'; ?></div></article><?php endif; ?>
    </section>
    <?php if(can('analytics.view')):$ret=obsRetentionSummary(90); ?><details class="panel"><summary class="panel-header"><span><strong>Player growth & retention</strong><span class="cell-sub">Returning players, new arrivals and longer-term trends</span></span></summary><div class="panel-body"><section class="stats-grid"><?php analyticsKpi('New players · 7d',(int)db()->query('SELECT COUNT(*) FROM players WHERE first_seen_at>=DATE_SUB(NOW(),INTERVAL 7 DAY)')->fetchColumn(),'Recently joined profiles','blue');analyticsKpi('Active players · 7d',(int)db()->query('SELECT COUNT(*) FROM players WHERE last_seen_at>=DATE_SUB(NOW(),INTERVAL 7 DAY)')->fetchColumn(),'Recently seen profiles','pink'); ?></section><section class="stats-grid retention-cards"><?php foreach([1,3,7,30] as $d)renderRetentionCard($d,$ret[$d]); ?></section><a class="button secondary" href="index.php?page=analytics&amp;view=retention">Explore retention & player growth</a></div></details><?php endif; ?>
    <?php if(can('audit.view')):$audit=db()->query("SELECT * FROM audit_logs WHERE action<>'command.pv' AND (action NOT LIKE 'command.%' OR actor_user_id IS NOT NULL) ORDER BY created_at DESC LIMIT 7")->fetchAll(); ?><details class="panel"><summary class="panel-header"><strong>Recent staff activity</strong><span class="badge">Latest 7 actions</span></summary><div class="panel-body timeline"><?php foreach($audit as $a): ?><div class="timeline-item"><strong><?= e(str_replace('.',' · ',$a['action'])) ?></strong><time><?= e(displayDate($a['created_at'])) ?></time></div><?php endforeach; ?><a class="button secondary" href="index.php?page=audit">Open staff audit</a></div></details><?php endif; ?>
    <?php renderAppPage('Dashboard','dashboard',(string)ob_get_clean());
}

function hqLeaderboard(string $title,string $plugin,string $metric,string $where,array $params,string $unit='',string $dimension=''): void
{
    $rows=gameLeaders($plugin,$metric,$where,$params,$dimension);echo '<article class="panel"><div class="panel-header"><h2>'.e($title).'</h2><span class="badge">Player ranking</span></div><div class="panel-body">';
    if(!$rows)echo '<div class="hq-empty"><strong>No ranking yet</strong><p>Players appear after a recorded action in this period.</p></div>';
    foreach($rows as $i=>$r){if($i===5)echo '<details class="detail-disclosure"><summary>Show all '.count($rows).' ranked players</summary>';
        echo '<div class="leaderboard-row"><span class="rank">'.($i+1).'</span>'.renderPlayerIdentity($r['player_name']?:'Unknown',$r['player_uuid']?:'', '',can('players.view')).'<strong>'.($unit==='$'?'$':'').e(obsNumber((float)$r['total']).($unit&&$unit!=='$'?' '.$unit:'')).'</strong></div>';}
    if(count($rows)>5)echo '</details>';echo '</div></article>';
}

function hqGameChart(string $plugin,string $metric,string $title,string $days,string $server,string $dimension=''): void
{
    $q=db()->prepare('SELECT MIN(day_utc) FROM game_player_daily WHERE plugin_name=? AND metric=?'.($server!==''?' AND server_name=?':'').($dimension!==''?' AND dimension=?':''));$q->execute(array_merge([$plugin,$metric],$server!==''?[$server]:[],$dimension!==''?[$dimension]:[]));$first=$q->fetchColumn();
    $r=$days==='0'?analyticsRange(['days'=>'custom','start'=>$first?:gmdate('Y-m-d'),'end'=>gmdate('Y-m-d'),'group'=>'day']):analyticsRange(['days'=>$days,'group'=>'day']);
    if($r['group']==='hour')$r['group']='day';$labels=analyticsLabels($r);$values=array_fill_keys($labels,null);
    $bucket=analyticsSqlBucket('day_utc',$r['group']);$params=[$plugin,$metric,$r['start'],$r['end']];$scope=$server!==''?' AND server_name=?':'';if($server!=='')$params[]=$server;if($dimension!==''){$scope.=' AND dimension=?';$params[]=$dimension;}
    $q=db()->prepare("SELECT $bucket bucket,SUM(value_sum) total FROM game_player_daily WHERE plugin_name=? AND metric=? AND day_utc>=? AND day_utc<=?$scope GROUP BY bucket ORDER BY bucket");$q->execute($params);
    foreach($q->fetchAll() as $row)if(array_key_exists($row['bucket'],$values))$values[$row['bucket']]=(float)$row['total'];
    analyticsChartPanel($title,'Recorded daily totals. Open exact values below the chart.',[['label'=>$title,'values'=>$values,'unit'=>$metric==='trade_value'?'$':'']],$r,['cumulative'=>true]);
}

function hqRecentGameEvents(array $plugins,string $where,array $params): void
{
    $ew=str_replace('day_utc','DATE(occurred_at)',$where);$q=db()->prepare("SELECT game_events.*,COALESCE(NULLIF(game_events.player_name,''),players.username) actor_name FROM game_events LEFT JOIN players ON players.uuid=game_events.player_uuid WHERE $ew AND plugin_name IN(".implode(',',array_fill(0,count($plugins),'?')).') ORDER BY occurred_at DESC LIMIT 100');$q->execute([...$params,...$plugins]);$rows=$q->fetchAll();
    echo '<article class="panel"><div class="panel-header"><div><h2>Recent activity</h2><span class="cell-sub">Latest 100 matching records · expand a row for its evidence</span></div></div><div class="panel-body">';
    if(!$rows)echo '<div class="hq-empty"><strong>No recorded actions in this period</strong><p>Try All recorded history, or check the connection status below.</p></div>';
    foreach($rows as $i=>$r){if($i===8)echo '<details class="detail-disclosure"><summary>Show '.(count($rows)-8).' more records</summary>';$d=json_decode((string)$r['details_json'],true)?:[]; ?>
        <details class="event-record"><summary><span><strong><?= e($r['actor_name']?:($r['player_uuid']?'Player '.substr($r['player_uuid'],0,8):'Server event')) ?></strong><span class="cell-sub"><?= e(ucwords(str_replace('_',' ',$r['metric']))) ?> · <?= e(str_replace('|',' · ',$r['dimension'])) ?></span></span><strong><?= e(($r['metric']==='trade_value'?'$':'').obsNumber((float)$r['value_sum'])) ?></strong><time><?= e(gameDisplayDate($r['occurred_at'])) ?></time></summary><div class="event-evidence"><span class="badge"><?= $r['imported']?'Imported history':'Recorded event' ?></span><span><?= e(strtoupper($r['server_name'])) ?> · <?= e($r['plugin_name']) ?></span><?php if($r['player_uuid']&&can('players.view')): ?><a href="index.php?page=player&amp;uuid=<?= e($r['player_uuid']) ?>">Open player profile</a><?php endif; ?><dl><?php foreach($d as $key=>$value): ?><div><dt><?= e(ucwords(str_replace('_',' ',(string)$key))) ?></dt><dd><?= e(is_scalar($value)?(string)$value:jsonEncode($value)) ?></dd></div><?php endforeach; ?></dl></div></details>
    <?php }if(count($rows)>8)echo '</details>';echo '</div></article>';
}

function hqCatalog(array $plugins,string $server): void
{
    foreach($plugins as $plugin){$snapshots=gameSnapshots($plugin,$server);$cards=[];foreach($snapshots as $s){if(str_ends_with($s['snapshot_key'],'-manifest'))continue;foreach($s['data']['entries']??[] as $row)$cards[]=[$row,$s];}
        if(!$cards)continue;$total=count($cards);$current=max(1,min((int)ceil($total/24),(int)($_GET['catalog_page']??1)));$visible=array_slice($cards,($current-1)*24,24); ?><article class="panel"><div class="panel-header"><div><h2><?= $plugin==='QuickShop-Hikari'?'Shop catalogue':($plugin==='MCPets'?'Available MCPets':'Pet shop offers & ownership') ?></h2><span class="cell-sub"><?= e($plugin) ?> · latest reported catalogue</span></div><span class="badge"><?= count($cards) ?> entries</span></div><div class="panel-body"><div class="catalog-grid"><?php foreach($visible as [$row,$s]): ?><details class="catalog-entry"><summary><strong><?= e((string)($row['name']??$row['id']??$row['pet']??'Entry')) ?></strong><span><?= e(strtoupper($s['server_name'])) ?> · details</span></summary><dl><?php foreach($row as $k=>$v): ?><div><dt><?= e(ucwords(str_replace(['_','-'], ' ',(string)$k))) ?></dt><dd><?= e(is_scalar($v)?(string)$v:jsonEncode($v)) ?></dd></div><?php endforeach; ?></dl><small>Reported <?= e(gameDisplayDate($s['captured_at'])) ?></small></details><?php endforeach; ?></div><?php if($total>24): ?><nav class="analysis-pagination" aria-label="Catalogue pages"><?php foreach(['Previous'=>$current-1,'Next'=>$current+1] as $label=>$target):if($target<1||$target>ceil($total/24))continue; ?><a href="index.php?<?= e(http_build_query(['page'=>in_array('QuickShop-Hikari',$plugins,true)?'shops':'pets','days'=>gameRange('shops')[0],'server'=>$server,'catalog_page'=>$target])) ?>"><?= e($label) ?></a><?php endforeach; ?><span>Page <?= $current ?> of <?= (int)ceil($total/24) ?> · 24 entries per page</span></nav><?php endif; ?></div></article><?php }
}

function hqDragonLifetime(string $server): void
{
    foreach(gameSnapshots('TownyDragonEvent',$server) as $s){if($s['snapshot_key']!=='lifetime')continue;$rows=$s['data']['players']??[];usort($rows,static fn($a,$b)=>(float)($b['total-damage']??0)<=>(float)($a['total-damage']??0)); ?>
        <article class="panel"><div class="panel-header"><div><h2>Lifetime Dragon records</h2><span class="cell-sub">Includes earlier events · <?= e(strtoupper($s['server_name'])) ?> · reported <?= e(gameDisplayDate($s['captured_at'])) ?></span></div><span class="badge">Existing Dragon totals</span></div><div class="panel-body"><section class="stats-grid lifetime-kpis"><?php analyticsKpi('Lifetime damage',array_sum(array_column($rows,'total-damage')),'Reported by the Dragon plugin','pink');analyticsKpi('Lifetime wins',array_sum(array_column($rows,'wins')),'Kept separate from new event totals','blue');analyticsKpi('Recorded players',count($rows),'Across recorded lifetime history','lime'); ?></section><div class="table-wrap"><table><thead><tr><th>Player</th><th>Damage</th><th>Wins</th><th>Best fight</th><th>Deaths</th><th>Crystals</th></tr></thead><tbody><?php foreach(array_slice($rows,0,100) as $row): ?><tr><td><?= renderPlayerIdentity($row['name']??$row['uuid'],$row['uuid']??'', '',can('players.view')) ?></td><td><?= e(obsNumber((float)($row['total-damage']??0))) ?></td><td><?= (int)($row['wins']??0) ?></td><td><?= e(obsNumber((float)($row['personal-best']??0))) ?></td><td><?= (int)($row['deaths']??0) ?></td><td><?= (int)($row['crystals']??0) ?></td></tr><?php endforeach; ?></tbody></table></div><p class="subtle">These lifetime values are a snapshot, not extra events. They are never added twice to the period rankings. Showing up to 100 players by damage.</p></div></article>
    <?php }
}

function showTopicInsights(string $page): void
{
    $topic=hqTopics()[$page]??null;if(!$topic||$page==='crates'){http_response_code(404);exit;}requirePermission($topic[1]);[$days,$server,$where,$params]=gameRange($page);$totals=hqGameTotals($where,$params);
    $metrics=match($page) {
        'dragon'=>[['dragon_damage','Damage dealt','TownyDragonEvent'],['dragon_wins','Fight wins','TownyDragonEvent']],
        'votes'=>[['votes','Votes received','Votifier']],
        'pinata'=>[['pinata_hits','Piñata hits','PinataParty'],['pinatas_spawned','Piñatas spawned','PinataParty'],['pinata_kills','Recorded kills','PinataParty']],
        'fishing'=>[['fish_caught','Fish caught','PyroFishingPro'],['shiny_fish_caught','Shiny catches','PyroFishingPro'],['sea_creatures_caught','Sea creatures','PyroFishingPro'],['tournaments_completed','Tournament completions','PyroFishingPro']],
        'shops'=>[['trade_value','Successful trade value','QuickShop-Hikari'],['items_traded','Items traded','QuickShop-Hikari'],['shop_transactions','Transactions','QuickShop-Hikari']],
        default=>[['pet_purchases','Pet purchases / grants','TownyRebornPetShops'],['pet_summons','Pets summoned','MCPets'],['pet_name_changes','Pet name changes','MCPets']],
    };
    $plugins=array_values(array_unique(array_column($metrics,2)));ob_start(); ?>
    <div class="hq-topic-intro"><span class="topic-icon"><?= hqIcon($topic[6]) ?></span><div><h2><?= e($topic[0]) ?> at a glance</h2><p><?= e($topic[5]) ?> Choose a period, then expand a record for the details.</p></div><?php if(can('analytics.view')): ?><a class="button secondary small" href="index.php?page=statistics">All games & economy</a><?php endif; ?></div>
    <?php gameToolbar($page);echo '<section class="stats-grid topic-kpis">';foreach($metrics as $i=>[$metric,$label,$plugin]){$row=$totals[$plugin.'|'.$metric]??null;analyticsKpi($label,$row?(float)$row['total']:null,$row?'Recorded events in this period':'No matching events reported yet',['pink','blue','lime','amber'][$i%4],$metric==='trade_value'?'$':'');}echo '</section>';
    if($page==='dragon')hqDragonLifetime($server);
    ?><section class="analysis-two-col"><?php hqGameChart($topic[2],$topic[3],$topic[4].' over time',$days,$server);hqLeaderboard($page==='dragon'?'Dragon damage ranking':$topic[0].' ranking',$topic[2],$topic[3],$where,$params,$page==='shops'?'$':''); ?></section>
    <?php if($page==='dragon')hqLeaderboard('Dragon wins ranking','TownyDragonEvent','dragon_wins',$where,$params);
    if(in_array($page,['votes','fishing'],true)){$q=db()->prepare('SELECT dimension label,SUM(value_sum) total FROM game_player_daily WHERE '.$where.' AND plugin_name=? AND metric=? GROUP BY dimension ORDER BY total DESC LIMIT 100');$q->execute([...$params,$topic[2],$topic[3]]);analyticsTablePanel($page==='votes'?'Voting services':'Catch tiers & fish',$q->fetchAll());}
    if(in_array($page,['shops','pets'],true))hqCatalog($page==='pets'?['MCPets','TownyRebornPetShops']:['QuickShop-Hikari'],$server);
    hqRecentGameEvents($plugins,$where,$params); ?>
    <details class="panel connection-details"><summary class="panel-header"><span><strong>Data sources & connection status</strong><span class="cell-sub">Check the source when a value is missing.</span></span></summary><div class="panel-body"><p>Event totals begin when the updated connector observes an action. No matching events is shown as missing data, not an invented zero. Historical Dragon snapshots and new events remain separate.</p><?php foreach($plugins as $plugin)gameCoverage($plugin,$server);if(can('plugins.view')): ?><a class="button secondary" href="index.php?page=plugins">Open connections</a><?php endif; ?></div></details>
    <?php renderAppPage($topic[0],$page,(string)ob_get_clean());
}

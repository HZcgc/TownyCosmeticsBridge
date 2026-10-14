<?php
declare(strict_types=1);

function journeyProfiles(string $server='',string $uuid=''): array
{
    $rows=[];
    foreach(gameSnapshots('TownyRebornProgression',$server) as $snapshot){
        if(!str_starts_with($snapshot['snapshot_key'],'player.'))continue;
        $data=$snapshot['data'];
        if($uuid!==''&&($data['uuid']??'')!==$uuid)continue;
        $rows[]=['server'=>$snapshot['server_name'],'captured_at'=>$snapshot['captured_at'],'data'=>$data];
    }
    usort($rows,static fn($a,$b)=>(int)($b['data']['points']??0)<=>(int)($a['data']['points']??0));
    return $rows;
}

function journeyCatalogue(string $server): array
{
    static $cache=[];if(array_key_exists($server,$cache))return $cache[$server];
    foreach(gameSnapshots('TownyRebornProgression',$server) as $snapshot)if($snapshot['snapshot_key']==='catalog')return $cache[$server]=$snapshot['data'];
    return $cache[$server]=[];
}

function journeyMetricName(string $metric): string
{
    return match($metric){
        'journey_jp'=>'JP earned','journey_adjustments'=>'JP adjustment','journey_history_restored'=>'Historical JP restored',
        'journey_levels'=>'Journey level change','journey_levels_restored'=>'Journey level restored',
        'journey_unlocks'=>'Purchase option unlocked','journey_perk_purchases'=>'Permanent perk purchased',
        'journey_money_spent'=>'Confirmed perk payment','journey_ownership_restored'=>'Permanent ownership retained / restored',
        'journey_ownership_removed'=>'Permanent ownership removed','journey_rewards_claimed'=>'Journey reward claimed',
        'journey_reward_receipts_restored'=>'Earlier reward receipt retained / restored',
        'journey_reward_money'=>'Reward money delivered','journey_reward_chunks'=>'Town claim bonus delivered',
        'journey_purchase_receipt'=>'Purchase delivery state','journey_profile_commit'=>'Profile saved',
        default=>ucwords(str_replace('_',' ',$metric))};
}

function journeySourceName(string $source): string
{
    return match($source){'playtime'=>'Active playtime','mcmmo'=>'mcMMO','jobs'=>'Jobs','fishing'=>'Fishing','biomes'=>'Biomes','advancements'=>'Advancements','quests'=>'Quests',default=>ucfirst($source)};
}

function journeyPlayerCard(array $row,bool $expanded=false): void
{
    $data=$row['data'];$catalog=journeyCatalogue($row['server']);$perks=$catalog['perks']??[];
    $owned=is_array($data['owned-perks']??null)?$data['owned-perks']:[];
    $unlocked=is_array($data['unlocked-perks']??null)?$data['unlocked-perks']:[];
    $claims=is_array($data['claimed-gifts']??null)?$data['claimed-gifts']:[];
    ?>
    <details class="panel journey-player" <?= $expanded?'open':'' ?> id="journey-<?= e($data['uuid']??'unknown') ?>">
        <summary class="panel-header"><span><strong><?= e($data['name']??$data['uuid']??'Unknown player') ?></strong><span class="cell-sub"><?= e(strtoupper($row['server'])) ?> · <?= e($data['rank']??'Unknown rank') ?></span></span><span class="badge online">Level <?= (int)($data['level']??1) ?></span><span><?= number_format((float)($data['points']??0)) ?> JP</span></summary>
        <div class="panel-body">
            <div class="journey-personal-stats"><span><strong><?= count($owned) ?></strong> owned perks</span><span><strong><?= count($unlocked) ?></strong> purchase options unlocked</span><span><strong><?= count($claims) ?></strong> rewards claimed</span><span><strong><?= number_format((float)($data['history-credit-points']??0)) ?></strong> historical JP retained</span></div>
            <p class="subtle">Profile last saved <?= e(gameDisplayDate(isset($data['profile_saved_at'])?gameDate((string)$data['profile_saved_at']):null)) ?> · HQ observed <?= e(gameDisplayDate($row['captured_at'])) ?>. Ownership is separate from access supplied by a monthly or staff rank.</p>
            <?php if((int)($data['history-credit-points']??0)>0&&!empty($data['history-credit-source'])): ?><p><strong>Historical evidence:</strong> <?= e($data['history-credit-source']) ?>. Retained points are part of the current total; they are not new gameplay earnings.</p><?php endif; ?>
            <?php if(!empty($data['pending-purchase'])):$receipt=$data['pending-purchase']; ?><div class="notice notice-warning"><strong>Open purchase receipt: <?= e($receipt['perk']??'Unknown') ?></strong><p><?= e($receipt['phase']??'Unknown state') ?> · $<?= number_format((float)($receipt['price']??0)) ?>. A DEBITED receipt means the payment is saved and delivery still needs confirmation. Inspect the receipt before granting or refunding anything.</p></div><?php endif; ?>
            <?php if(!empty($data['pending-gift'])): ?><p class="notice notice-warning">Reward delivery pending: <?= e($data['pending-gift']) ?>. Check the existing receipt before reissuing a reward.</p><?php endif; ?>
            <details class="journey-detail"><summary>Permanent ownership and retained purchase unlocks</summary><div class="journey-perk-list">
            <?php foreach(array_unique([...$owned,...$unlocked]) as $id):$perk=$perks[$id]??[]; ?><div><span class="badge <?= in_array($id,$owned,true)?'online':'' ?>"><?= in_array($id,$owned,true)?'Owned permanently':'Available to purchase' ?></span><strong><?= e($perk['name']??$id) ?></strong><span class="cell-sub"><?= e($perk['rank-tier']??'') ?> · Level <?= (int)($perk['level']??0) ?> · Current catalogue price $<?= number_format((float)($perk['price']??0)) ?></span></div><?php endforeach; ?>
            <?php if(!$owned&&!$unlocked): ?><p>No permanent ownership or purchase unlocks recorded.</p><?php endif; ?></div><p class="subtle">Catalogue prices explain current offers. Only a confirmed payment receipt is included in spending totals.</p></details>
            <details class="journey-detail"><summary>Daily JP budgets · reset 00:00 UTC</summary><div class="table-wrap"><table><thead><tr><th>Activity</th><th>Earned today</th><th>Daily cap</th></tr></thead><tbody><?php foreach(($catalog['sources']??[]) as $source=>$settings):if(!is_array($settings)||empty($settings['enabled']))continue;$today=($data['day']??'')===gmdate('Y-m-d')?(int)($data['daily-awards'][$source]??0):0; ?><tr><td><?= e(journeySourceName((string)$source)) ?></td><td><?= number_format($today) ?> JP</td><td><?= number_format((float)($settings['daily-cap']??0)) ?> JP</td></tr><?php endforeach; ?></tbody></table></div></details>
            <details class="journey-detail"><summary>Claimed reward IDs (<?= count($claims) ?>)</summary><div class="detail-chips"><?php foreach($claims as $claim): ?><span><?= e($claim) ?></span><?php endforeach; ?></div></details>
            <a class="button secondary small" href="index.php?page=journey&amp;server=<?= rawurlencode($row['server']) ?>&amp;uuid=<?= rawurlencode($data['uuid']??'') ?>">Journey history</a>
            <?php if(can('players.view')): ?><a class="button secondary small" href="index.php?page=player&amp;uuid=<?= rawurlencode($data['uuid']??'') ?>#profile-journey">Player profile</a><?php endif; ?>
        </div>
    </details>
    <?php
}

function renderJourneyPlayer(string $uuid): void
{
    if(!can('analytics.view'))return;
    echo '<section id="profile-journey" class="profile-journey"><h2>Reborn Journey</h2>';
    $rows=journeyProfiles('',$uuid);
    foreach($rows as $row)journeyPlayerCard($row,true);
    if(!$rows)echo '<p class="subtle">No Journey profile received yet. Existing progress is not assumed to be zero.</p>';
    echo '</section>';
}

function journeyEventGroups(array $events): array
{
    $groups=[];
    foreach($events as $event){
        $details=json_decode((string)$event['details_json'],true)?:[];
        $key=$event['server_name'].'|'.$event['actor_key'].'|'.($details['commit']??$event['event_key']);
        if(!isset($groups[$key]))$groups[$key]=['name'=>$event['player_name'],'at'=>$event['occurred_at'],'sequence'=>(int)($details['sequence']??0),'events'=>[],'label'=>'Journey profile change'];
        $groups[$key]['events'][]=$event;
    }
    foreach($groups as &$group){
        foreach(['journey_perk_purchases','journey_money_spent','journey_rewards_claimed','journey_history_restored','journey_levels','journey_jp','journey_unlocks','journey_purchase_receipt'] as $metric){
            foreach($group['events'] as $event)if($event['metric']===$metric){$group['label']=journeyMetricName($metric).' · '.$event['dimension'];break 2;}
        }
    }unset($group);
    $groups=array_values($groups);usort($groups,static fn($a,$b)=>strcmp($b['at'],$a['at'])?:($b['sequence']<=>$a['sequence']));return $groups;
}

function showJourneyInsights(): void
{
    requirePermission('analytics.view');[$days,$server,$where,$params]=gameRange('journey');
    $uuid=normalizeUuid((string)($_GET['uuid']??''))??'';
    if($uuid!==''){$where.=' AND player_uuid=?';$params[]=$uuid;}
    $q=db()->prepare("SELECT metric,dimension,SUM(value_sum) total FROM game_player_daily WHERE $where AND plugin_name='TownyRebornProgression' GROUP BY metric,dimension");$q->execute($params);$totals=$q->fetchAll();$byMetric=[];
    foreach($totals as $row)$byMetric[$row['metric']]=($byMetric[$row['metric']]??0)+(float)$row['total'];
    $profiles=journeyProfiles($server,$uuid);$eventWhere=str_replace('day_utc','DATE(occurred_at)',$where);
    $q=db()->prepare("SELECT * FROM game_events WHERE $eventWhere AND plugin_name='TownyRebornProgression' AND metric<>'journey_profile_commit' ORDER BY occurred_at DESC,event_key DESC LIMIT 200");$q->execute($params);$events=$q->fetchAll();
    ob_start();gameSubnav('journey');gameToolbar('journey'); ?>
    <div class="page-intro"><div><strong>Reborn Journey</strong><p>Current player progress, retained purchases and unlocks, new JP by activity and confirmed transaction history.</p></div><a class="button secondary small" href="https://townyreborn.com/wiki/#journey" target="_blank" rel="noopener noreferrer">Player guide ↗</a></div>
    <section class="stats-grid"><article class="stat-card accent-pink"><span>Recorded profiles</span><strong><?= count($profiles) ?></strong><small>Current snapshots, including offline players</small></article><article class="stat-card accent-lime"><span>JP earned in this period</span><strong><?= number_format($byMetric['journey_jp']??0) ?></strong><small>New gameplay JP; historical restoration is separate</small></article><article class="stat-card accent-blue"><span>Confirmed perk purchases</span><strong><?= number_format($byMetric['journey_perk_purchases']??0) ?></strong><small>Saved payment and ownership delivery receipts</small></article><article class="stat-card accent-amber"><span>Confirmed perk spending</span><strong>$<?= number_format($byMetric['journey_money_spent']??0) ?></strong><small>Actual saved payment amounts, not catalogue estimates</small></article></section>
    <p class="notice notice-info">Existing JP, owned perks, retained purchase unlocks and claimed rewards are imported as current state. They are not counted again as new earnings or purchases. Individual event details use the existing 30-day activity window; aggregate statistics remain permanent.</p>
    <?php if($uuid!==''): ?><p><strong>Player filter:</strong> <?= e($uuid) ?> · <a href="index.php?page=journey&amp;server=<?= rawurlencode($server) ?>">Show all players</a></p><?php endif; ?>
    <section class="analysis-two-col"><article class="panel"><div class="panel-header"><h2>JP by activity</h2></div><div class="panel-body"><div class="table-wrap"><table><thead><tr><th>Activity</th><th>Earned JP</th></tr></thead><tbody><?php foreach($totals as $row):if($row['metric']!=='journey_jp')continue; ?><tr><td><?= e(journeySourceName($row['dimension'])) ?></td><td><?= number_format((float)$row['total']) ?></td></tr><?php endforeach; ?></tbody></table></div><p class="subtle">Historical JP restored in this period: <?= number_format($byMetric['journey_history_restored']??0) ?>. Manual adjustments: <?= number_format($byMetric['journey_adjustments']??0) ?>.</p></div></article>
    <?php if($uuid==='')hqGameChart('TownyRebornProgression','journey_jp','New JP over time',$days,$server); ?></section>
    <article class="panel"><div class="panel-header"><h2>Player Journey profiles</h2><span class="badge"><?= count($profiles) ?> recorded</span></div><div class="panel-body"><p class="subtle">Expand a player to inspect permanent ownership, purchase eligibility, claimed rewards, daily limits and migration evidence.</p><?php foreach($profiles as $row)journeyPlayerCard($row,$uuid!=='');if(!$profiles): ?><p>No Journey profiles received yet. Install the matching connector and Journey audit update; no existing player progress is removed.</p><?php endif; ?></div></article>
    <article class="panel"><div class="panel-header"><h2>Recent Journey evidence</h2><span class="badge">Latest 200 entries · grouped by saved change</span></div><div class="panel-body journey-events"><?php foreach(journeyEventGroups($events) as $group): ?><details class="opening-record journey-commit"><summary><span><strong><?= e($group['name']?:'Unknown player') ?></strong> · <?= e($group['label']) ?></span><span class="badge"><?= count($group['events']) ?> details</span><time><?= e(gameDisplayDate($group['at'])) ?></time></summary><?php foreach($group['events'] as $event):$details=json_decode((string)$event['details_json'],true)?:[]; ?><details class="journey-event-detail"><summary><?= e(journeyMetricName($event['metric'])) ?> · <?= e($event['dimension']) ?> · <?= e(obsNumber((float)$event['value_sum'])) ?></summary><dl class="journey-evidence"><?php foreach($details as $key=>$value):if(!is_scalar($value)&&!is_array($value))continue; ?><dt><?= e(ucwords(str_replace('_',' ',$key))) ?></dt><dd><?php if(is_array($value)): ?><ul><?php foreach($value as $item):if(!is_scalar($item))continue; ?><li><code><?= e((string)$item) ?></code></li><?php endforeach; ?></ul><?php else: ?><?= e(is_bool($value)?($value?'Yes':'No'):(string)$value) ?><?php endif; ?></dd><?php endforeach; ?></dl></details><?php endforeach; ?></details><?php endforeach;if(!$events): ?><p class="subtle">No matching event evidence in the retained activity window. Current snapshots and permanent aggregate totals remain available.</p><?php endif; ?></div></article>
    <?php gameCoverage('TownyRebornProgression',$server);renderAppPage('Reborn Journey','journey',(string)ob_get_clean());
}

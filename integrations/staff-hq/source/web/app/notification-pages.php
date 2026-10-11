<?php
declare(strict_types=1);

function showNotifications(): void
{
    requirePermission('alerts.view');
    $pdo=db();$s=notificationSettings();$manage=can('api.manage');
    header('Cache-Control: no-store, private');
    $cronReady=$s['cron_enabled']&&notificationCronTokenValid((string)$s['cron_token']);
    $cronUrl=rtrim((string)config('app_url'),'/').'/notification-cron.php';
    $status=$pdo->query("SELECT setting_key,setting_value FROM settings WHERE setting_key IN('notification.worker_last_run','notification.last_success','notification.last_error')")->fetchAll(PDO::FETCH_KEY_PAIR);
    $worker=$status['notification.worker_last_run']??null;
    $running=$worker&&time()-strtotime($worker)<180;
    $valid=notificationDestinationValid($s);$bot=$s['delivery_mode']==='bot';
    $counts=$pdo->query("SELECT status,COUNT(*) AS total FROM notification_outbox GROUP BY status")->fetchAll(PDO::FETCH_KEY_PAIR);
    $monitors=$pdo->query("SELECT * FROM notification_monitors WHERE state<>'disabled' AND (kind<>'diagnostic' OR condition_since IS NOT NULL) ORDER BY state='incident' DESC,server_name,kind")->fetchAll();
    $incidents=$s['enabled']?count(array_filter($monitors,static fn($r)=>$r['state']==='incident')):0;
    $rows=$pdo->query('SELECT * FROM notification_outbox ORDER BY id DESC LIMIT 50')->fetchAll();
    ob_start();
    ?>
    <div class="analysis-heading"><div><span class="eyebrow">Network response</span><h2>Attention Centre</h2><p>Discord alerts, current network conditions and delivery status in one place.</p></div><span class="badge <?= $incidents?'critical':'online' ?>"><?= $incidents?'Attention required':'No active network incident' ?></span></div>
    <?php if(!$running): ?><div class="notice notice-warning"><strong>Background checks need a minute schedule</strong><p>An administrator can configure free external checks below, or run <code>notification-worker.php</code> every minute in Plesk. This detects missing server heartbeats even when the servers stop sending events.</p></div><?php endif; ?>
    <?php if(!empty($status['notification.last_error'])): ?><div class="notice notice-error"><strong>Discord delivery needs attention</strong><p><?= e($status['notification.last_error']) ?>. Messages are retained in the queue. An administrator can update the Discord credentials or channel and retry delivery.</p></div><?php endif; ?>
    <section class="stats-grid notification-stats">
        <article class="stat-card"><span>Discord notifications</span><strong class="metric-status"><?= !$s['enabled']?'Paused':($valid?'Enabled':'Setup required') ?></strong><small><?= $bot?($valid?'Freddie the Frog · channel verified':'Configure Freddie and the alert channel below'):($valid?'Webhook configured privately':'Configure the Discord destination below') ?></small></article>
        <article class="stat-card"><span>Background checks</span><strong class="metric-status"><?= $running?'Running':'Needs setup' ?></strong><small>Last run: <?= e($worker?displayDate($worker):'Not run yet') ?></small></article>
        <article class="stat-card"><span>Delivery queue</span><strong><?= number_format((int)($counts['pending']??0)+(int)($counts['processing']??0)) ?></strong><small><?= number_format((int)($counts['blocked']??0)) ?> blocked · retained until delivery</small></article>
        <article class="stat-card"><span>Active conditions</span><strong><?= $incidents ?></strong><small>Last delivery: <?= e(isset($status['notification.last_success'])?displayDate($status['notification.last_success']):'Not sent yet') ?></small></article>
    </section>
    <p class="subtle notification-delivery-policy">Spam protection: at most one Discord message per minute across all alerts and tests. Waiting notifications are grouped; individual events remain in the HQ. Staff role pings have a ten-minute cooldown and exclude old queue entries, repeat summaries and recovery notices.</p>
    <p class="subtle notification-confirmation-policy">Temporary faults wait five minutes: connector failures, plugin integrations, backlog and server lag are sent only while still confirmed by fresh status. Faults that clear before delivery stay silent, including their recovery. Plugin log errors must keep recurring over five minutes; isolated errors and short bursts remain in HQ diagnostics. Recovery notices require a previously delivered incident. Vulcan alerts, bans, failed staff actions and newly reported event loss remain immediate.</p>
    <article class="panel notification-panel"><div class="panel-header"><h2>Server and connector monitoring</h2><span class="badge">More than <?= (int)$s['down_minutes'] ?> minutes without status</span></div><div class="panel-body flush"><div class="table-wrap"><table><thead><tr><th>Server</th><th>Condition</th><th>Status</th><th>Details</th><th>Updated</th></tr></thead><tbody>
        <?php if(!$monitors): ?><tr><td colspan="5"><div class="empty-state"><strong>Waiting for the first background check</strong><p>Monitored servers: <?= e(implode(', ',$s['servers'])) ?>.</p></div></td></tr><?php endif; ?>
        <?php foreach($monitors as $m): $details=json_decode($m['details_json'],true)?:[];$waiting=$m['kind']==='heartbeat'&&!$m['last_fresh_at']; ?>
        <tr><td><?= e(strtoupper($m['server_name'])) ?></td><td><?= e($m['title']) ?></td><td><span class="badge <?= $m['state']==='incident'?'critical':($m['condition_since']||$waiting?'pending':'online') ?>"><?= !$s['enabled']?'Paused':($m['state']==='incident'?'Incident':($waiting?'Waiting for status':($m['condition_since']?'Watching':'Healthy'))) ?></span></td><td class="wrapping-cell"><?php foreach(($details['fields']??[]) as $field): ?><span class="cell-sub"><?= e($field['name'].': '.$field['value']) ?></span><?php endforeach; ?></td><td><?= e(displayDate($m['updated_at'])) ?></td></tr>
        <?php endforeach; ?>
    </tbody></table></div></div></article>
    <?php if($manage): ?>
    <section class="notification-settings">
        <article class="panel"><div class="panel-header"><h2>Discord alert settings</h2><span class="badge">Administrator</span></div><div class="panel-body">
            <form method="post" action="index.php?page=attention" class="notification-form">
                <?= csrfField() ?><input type="hidden" name="action" value="notification-save"><input type="hidden" name="return_page" value="attention">
                <label class="notification-toggle"><input type="checkbox" name="enabled" value="1" <?= $s['enabled']?'checked':'' ?>><span><strong>Enable Discord notifications</strong><small>Pause outgoing notifications by turning this off.</small></span></label>
                <div class="split-form">
                    <div class="field"><label for="notification-method">Send Discord alerts with</label><select id="notification-method" name="delivery_mode"><option value="bot" <?= $bot?'selected':'' ?>>Freddie the Frog</option><option value="webhook" <?= !$bot?'selected':'' ?>>Discord webhook</option></select><small class="cell-sub">Freddie sends using his existing Discord bot account. No additional bot process is required.</small></div>
                    <div class="field"><label for="notification-bot-token">Freddie bot token · replace</label><input id="notification-bot-token" type="password" name="bot_token" maxlength="256" autocomplete="new-password" spellcheck="false" placeholder="<?= notificationBotTokenValid((string)$s['bot_token'])?'Configured · leave blank to keep':'Paste Freddie’s existing bot token' ?>"><small class="cell-sub">Stored encrypted and never displayed. Save verifies the Freddie account and the selected channel.</small></div>
                    <div class="field"><label for="notification-channel">Discord alert channel ID</label><input id="notification-channel" name="channel_id" inputmode="numeric" maxlength="20" value="<?= e($s['channel_id']) ?>" placeholder="Right-click the staff alert channel → Copy Channel ID"><small class="cell-sub">Enable Discord Developer Mode to copy the ID. Freddie needs View Channel, Send Messages and Embed Links in this channel.</small></div>
                    <div class="field"><label for="notification-webhook">Webhook address · only for webhook mode</label><input id="notification-webhook" type="password" name="webhook" autocomplete="new-password" spellcheck="false" placeholder="<?= notificationWebhookValid((string)$s['webhook'])?'Configured · leave blank to keep':'https://discord.com/api/webhooks/…' ?>"><small class="cell-sub">Leave blank when using Freddie. The stored webhook is not used in bot mode.</small></div>
                    <div class="field"><label for="notification-role">Optional Discord role ID</label><input id="notification-role" name="role_id" inputmode="numeric" maxlength="20" value="<?= e($s['role_id']) ?>" placeholder="No role mention"><small class="cell-sub">Fresh high and critical alerts can mention this role, at most once every ten minutes. Enable role mentions in Discord.</small></div>
                    <div class="field"><label for="notification-servers">Monitored server names</label><input id="notification-servers" name="servers" value="<?= e(implode(', ',$s['servers'])) ?>" required><small class="cell-sub">Use the names reported by your connectors, separated by commas.</small></div>
                    <div class="field"><label for="notification-down">Missing heartbeat threshold (minutes)</label><input id="notification-down" type="number" name="down_minutes" min="5" max="120" value="<?= (int)$s['down_minutes'] ?>"></div>
                    <div class="field"><label for="notification-severity">Minimum Vulcan severity</label><select id="notification-severity" name="minimum_severity"><?php foreach(['low','medium','high','critical'] as $level): ?><option value="<?= $level ?>" <?= $s['minimum_severity']===$level?'selected':'' ?>><?= ucfirst($level) ?></option><?php endforeach; ?></select></div>
                    <div class="field"><label for="notification-vl">Minimum Vulcan VL</label><input id="notification-vl" type="number" name="minimum_vl" min="0" max="100000" step="0.1" value="<?= e($s['minimum_vl']) ?>"></div>
                    <div class="field"><label for="notification-queue">Backlog threshold (events)</label><input id="notification-queue" type="number" name="queue_threshold" min="1" max="50000" value="<?= (int)$s['queue_threshold'] ?>"></div>
                    <div class="field"><label for="notification-reminder">Incident reminder (minutes)</label><input id="notification-reminder" type="number" name="reminder_minutes" min="5" max="1440" value="<?= (int)$s['reminder_minutes'] ?>"></div>
                </div>
                <div class="notification-options"><?php foreach(['chat_flags'=>['Dupe and exploit chat flags','Fresh dupe/exploit matches are sent immediately. Repeated hints are grouped; historic queue replay does not alert again.'],'vulcan'=>['Vulcan alerts','Fresh alerts enter the queue immediately; repeats and simultaneous alerts are grouped within the global minute limit.'],'health'=>['Server and connector health','Missing status, delivery failures and backlog wait at least five minutes. Actual newly reported event loss remains immediate.'],'errors'=>['Important errors','Plugin integration failures and recurring ERROR logs wait five minutes. Single log errors stay in HQ. Failed staff actions remain immediate.'],'performance'=>['Sustained server lag','TPS below 15 or MSPT above 100 for at least five minutes.'],'bans'=>['Bans and temporary bans','Notify when a new ban is reported.'],'recoveries'=>['Recovery notices','Send one notice when a previously delivered incident returns to normal. Short faults clear silently.']] as $key=>[$label,$hint]): ?><label class="notification-toggle"><input type="checkbox" name="<?= $key ?>" value="1" <?= $s[$key]?'checked':'' ?>><span><strong><?= e($label) ?></strong><small><?= e($hint) ?></small></span></label><?php endforeach; ?></div>
                <button class="button" type="submit">Save alert settings</button>
            </form>
        </div></article>
        <article class="panel"><div class="panel-header"><h2>Check delivery</h2></div><div class="panel-body"><p class="subtle">Send a clearly labelled test message to the configured Discord channel. This does not create a server incident.</p><div class="button-row">
            <form method="post" action="index.php?page=attention"><?= csrfField() ?><input type="hidden" name="action" value="notification-test"><input type="hidden" name="return_page" value="attention"><button class="button secondary" type="submit">Send Discord test</button></form>
            <form method="post" action="index.php?page=attention"><?= csrfField() ?><input type="hidden" name="action" value="notification-retry"><input type="hidden" name="return_page" value="attention"><button class="button secondary" type="submit">Retry blocked deliveries</button></form>
        </div><p class="subtle">Background checks send queued messages and detect missing heartbeats. Configure a minute schedule using the external checks below.</p></div></article>
    </section>
    <article class="panel notification-panel notification-cron"><div class="panel-header"><h2>External background checks</h2><span class="badge <?= $cronReady?'online':'' ?>"><?= $cronReady?'Access enabled':'Access disabled' ?></span></div><div class="panel-body">
        <p class="subtle">Use a free minute schedule at <a href="https://cron-job.org/en/" target="_blank" rel="noopener noreferrer">cron-job.org</a>. The scheduler calls this website; the HQ sends alerts using your configured Discord delivery method.</p>
        <?php if($cronReady): ?>
        <ol class="notification-cron-steps"><li>Create an account at cron-job.org and choose <strong>Create cronjob</strong>.</li><li>Copy the URL below. Set the schedule to <strong>Every minute</strong>.</li><li>Under <strong>Advanced</strong>, select request method <strong>POST</strong> and add the HTTP header <strong>X-TR-Cron-Key</strong> with the copied key as its value. Leave the request body empty.</li><li>Save the job and use <strong>Test run</strong>. A successful call returns HTTP <strong>200</strong>; the Background checks card above changes to <strong>Running</strong>.</li></ol>
        <div class="split-form">
            <div class="field"><label for="notification-cron-url">Cronjob URL</label><input id="notification-cron-url" type="text" value="<?= e($cronUrl) ?>" readonly spellcheck="false"><button class="button secondary small" type="button" data-copy="#notification-cron-url">Copy URL</button></div>
            <div class="field"><label for="notification-cron-key">HTTP header value · X-TR-Cron-Key</label><input id="notification-cron-key" type="password" value="<?= e($s['cron_token']) ?>" readonly autocomplete="off" spellcheck="false"><button class="button secondary small" type="button" data-copy="#notification-cron-key">Copy key</button><small class="cell-sub">Keep this key private. It authorizes background checks only. Never add it to the URL.</small></div>
        </div>
        <div class="button-row">
            <form method="post" action="index.php?page=attention"><?= csrfField() ?><input type="hidden" name="action" value="notification-cron-rotate"><input type="hidden" name="return_page" value="attention"><button class="button secondary" type="submit" data-confirm="The current scheduler key will stop working. Update the cronjob header with the new key. Continue?">Rotate key</button></form>
            <form method="post" action="index.php?page=attention"><?= csrfField() ?><input type="hidden" name="action" value="notification-cron-disable"><input type="hidden" name="return_page" value="attention"><button class="button secondary" type="submit">Disable external access</button></form>
        </div>
        <?php else: ?>
        <p class="subtle">Enable access to generate your private scheduler key and show the setup fields. No Discord bot or Plesk scheduled task is required.</p>
        <form method="post" action="index.php?page=attention"><?= csrfField() ?><input type="hidden" name="action" value="notification-cron-enable"><input type="hidden" name="return_page" value="attention"><button class="button" type="submit">Enable external checks</button></form>
        <?php endif; ?>
        <p class="subtle">Enable failure and recovery notifications in cron-job.org to receive an email if this website cannot be reached. The HQ can send Discord alerts while the website is available.</p>
        <details><summary>Optional: use a Plesk scheduled task instead</summary><p class="subtle">Plesk → Scheduled Tasks → Run a PHP script → <code>notification-worker.php</code>. Schedule: <code>* * * * *</code>. Select the PHP version used by this website.</p></details>
    </div></article>
    <?php endif; ?>
    <article class="panel notification-panel"><div class="panel-header"><h2>Recent notifications</h2><span class="badge">Latest 50</span></div><div class="panel-body flush"><div class="table-wrap"><table><thead><tr><th>Recorded</th><th>Notification</th><th>Server</th><th>Events</th><th>Delivery</th></tr></thead><tbody>
        <?php if(!$rows): ?><tr><td colspan="5"><div class="empty-state"><strong>No notifications yet</strong><p>Fresh Vulcan alerts and important network events will appear here.</p></div></td></tr><?php endif; ?>
        <?php foreach($rows as $row): ?><tr><td><?= e(displayDate($row['last_at'])) ?></td><td class="wrapping-cell"><strong><?= e($row['title']) ?></strong><span class="cell-sub"><?= e($row['kind']) ?> · <?= e($row['severity']) ?></span></td><td><?= e(strtoupper($row['server_name'])) ?></td><td><?= (int)$row['occurrences'] ?></td><td><span class="badge <?= $row['status']==='sent'?'online':($row['status']==='blocked'?'failed':($row['status']==='suppressed'?'':'pending')) ?>"><?= e($row['status']==='suppressed'?'Skipped':$row['status']) ?></span><?php if($row['last_error']): ?><span class="cell-sub"><?= e($row['last_error']) ?></span><?php endif; ?></td></tr><?php endforeach; ?>
    </tbody></table></div></div></article>
    <?php
    renderAppPage('Attention Centre','attention',(string)ob_get_clean());
}

function postNotifications(string $action): void
{
    requirePermission('api.manage');
    $pdo=db();$s=notificationSettings();
    if(in_array($action,['notification-cron-enable','notification-cron-rotate','notification-cron-disable'],true)){
        $enabled=$action!=='notification-cron-disable';
        $rotate=$enabled&&($action==='notification-cron-rotate'||!notificationCronTokenValid((string)$s['cron_token']));
        $pdo->beginTransaction();
        try{
            if($rotate)notificationSetting($pdo,'option.cron_token_encrypted',encryptValue('tr_cron_'.bin2hex(random_bytes(32))));
            notificationSetting($pdo,'option.cron_enabled',jsonEncode($enabled));
            audit($action,'system','external_cron',['enabled'=>$enabled,'key_rotated'=>$rotate]);
            $pdo->commit();
        }catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();throw $e;}
        notificationSettings(true);
        setFlash('success',!$enabled?'External background access disabled.':($rotate?'External access enabled with a new key. Copy it into the cronjob HTTP header.':'External background access enabled. Your existing scheduler key is unchanged.'));
    }elseif($action==='notification-save'){
        $mode=(string)($_POST['delivery_mode']??$s['delivery_mode']);
        if(!in_array($mode,['bot','webhook'],true))throw new InvalidArgumentException('Choose a valid Discord delivery method.');
        $botToken=trim((string)($_POST['bot_token']??''));
        if($botToken!==''&&!notificationBotTokenValid($botToken))throw new InvalidArgumentException('Enter Freddie\'s bot token without a prefix or spaces.');
        $channel=trim((string)($_POST['channel_id']??$s['channel_id']));
        if($channel!==''&&!preg_match('/^\d{17,20}$/D',$channel))throw new InvalidArgumentException('The alert channel ID must contain 17–20 digits.');
        $webhook=trim((string)($_POST['webhook']??''));
        if($webhook!==''&&!notificationWebhookValid($webhook))throw new InvalidArgumentException('Enter a valid HTTPS Discord webhook address.');
        $role=trim((string)($_POST['role_id']??''));
        if($role!==''&&!preg_match('/^\d{17,20}$/',$role))throw new InvalidArgumentException('The Discord role ID must contain 17–20 digits.');
        $servers=array_values(array_unique(array_filter(array_map('trim',explode(',',strtolower((string)($_POST['servers']??'')))))));
        if(!$servers||count($servers)>20||array_filter($servers,static fn($v)=>!preg_match('/^[a-z0-9_.-]{1,64}$/',$v)))throw new InvalidArgumentException('Enter valid connector server names separated by commas.');
        $options=['servers'=>$servers,'role_id'=>$role,'delivery_mode'=>$mode,'channel_id'=>$channel];
        foreach(['enabled','chat_flags','vulcan','health','errors','performance','bans','recoveries'] as $key)$options[$key]=isset($_POST[$key]);
        foreach(['down_minutes'=>[5,120],'queue_threshold'=>[1,50000],'reminder_minutes'=>[5,1440],'minimum_vl'=>[0,100000]] as $key=>[$min,$max]){
            $value=$_POST[$key]??$s[$key];
            if(!is_numeric($value)||(float)$value<$min||(float)$value>$max)throw new InvalidArgumentException('A notification threshold is outside its allowed range.');
            $options[$key]=$key==='minimum_vl'?(float)$value:(int)$value;
        }
        $options['minimum_severity']=(string)($_POST['minimum_severity']??'low');
        if(!in_array($options['minimum_severity'],['low','medium','high','critical'],true))throw new InvalidArgumentException('Choose a valid minimum alert severity.');
        $candidate=array_replace($s,$options,['bot_token'=>$botToken?:$s['bot_token'],'webhook'=>$webhook?:$s['webhook']]);
        if($options['enabled']&&$mode==='bot'){
            if(!notificationBotTokenValid((string)$candidate['bot_token'])||$channel==='')throw new InvalidArgumentException('Add Freddie\'s bot token and the Discord alert channel ID before enabling notifications.');
            if(!notificationDestinationValid($candidate)){
                notificationVerifyBot($candidate);$options['bot_verified_context']=notificationBotContext($candidate);
            }
        }elseif($options['enabled']&&!notificationWebhookValid((string)$candidate['webhook']))throw new InvalidArgumentException('Add a Discord webhook before enabling notifications.');
        $pdo->beginTransaction();
        try{
            foreach($options as $key=>$value)notificationSetting($pdo,'option.'.$key,jsonEncode($value));
            if($botToken!=='')notificationSetting($pdo,'option.bot_token_encrypted',encryptValue($botToken));
            if($webhook!=='')notificationSetting($pdo,'option.webhook_encrypted',encryptValue($webhook));
            if($webhook!==''||($mode==='bot'&&($botToken!==''||$channel!==$s['channel_id']||$mode!==$s['delivery_mode']))){
                $pdo->exec("UPDATE notification_outbox SET status='pending',available_at=NOW(),claimed_at=NULL WHERE status='blocked'");
                notificationSetting($pdo,'discord_pause_until','0');notificationSetting($pdo,'destination_blocked','0');notificationSetting($pdo,'last_error','');
            }
            audit('notification.settings','system','discord',['enabled'=>$options['enabled'],'servers'=>$servers,'delivery_mode'=>$mode,'channel_id'=>$channel,'bot_token_replaced'=>$botToken!=='','webhook_replaced'=>$webhook!=='']);
            $pdo->commit();
        }catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();throw $e;}
        notificationSettings(true);setFlash('success','Discord alert settings saved.');
    }elseif($action==='notification-test'){
        if(!$s['enabled']||!notificationDestinationValid($s))throw new InvalidArgumentException('Enable notifications and configure the Discord delivery method first.');
        $id=notificationEnqueue($pdo,'test:'.bin2hex(random_bytes(16)),'test','hq','low','Discord test · Towny Reborn Staff HQ',notificationDetails('This is a test notification. Discord delivery is configured; no server incident was created.',['Requested by'=>currentUser()['display_name']],'attention'));
        audit('notification.test','system','discord',['notification_id'=>$id]);notificationDispatch($pdo,1);
        $q=$pdo->prepare('SELECT status FROM notification_outbox WHERE id=?');$q->execute([$id]);
        setFlash('success',$q->fetchColumn()==='sent'?'Test message delivered to Discord.':'Test message queued. Check delivery status below; the background task will retry when possible.');
    }elseif($action==='notification-retry'){
        if(!$s['enabled']||!notificationDestinationValid($s))throw new InvalidArgumentException('Enable notifications and configure the Discord delivery method first.');
        $pdo->exec("UPDATE notification_outbox SET status='pending',available_at=NOW(),claimed_at=NULL WHERE status='blocked'");
        notificationSetting($pdo,'discord_pause_until','0');notificationSetting($pdo,'destination_blocked','0');notificationSetting($pdo,'last_error','');
        audit('notification.retry','system','discord');notificationDispatch($pdo,1);setFlash('success','Blocked deliveries released for retry.');
    }else throw new InvalidArgumentException('Unknown notification action.');
    redirect('attention');
}

"""Journey Store -> connector JSON -> real PHP/MariaDB; priority liveness and alert stability."""
fixture_path=os.environ.get('TOWNY_JOURNEY_FIXTURE')
if not fixture_path:raise RuntimeError('Build the real JourneyBridgeFixture and set TOWNY_JOURNEY_FIXTURE')
bridge=json.loads(Path(fixture_path).read_text());bridge_uuid=bridge['profile']['uuid']
post(event('player.join',uuid=bridge_uuid,username='BridgePlayer'))
check('Actual Journey catalogue accepted',post(event('integration.snapshot',plugin='TownyRebornProgression',snapshot_key='catalog',data=bridge['catalogue']))[0]==202)
profile_event=event('integration.snapshot',plugin='TownyRebornProgression',snapshot_key='player.'+bridge_uuid,data=bridge['profile'])
check('Actual Journey player snapshot accepted',post(profile_event)[0]==202)
for row in bridge['events']:
 row={**row,'server':'smp'}
 first=post(row);second=post(row)
 check('Actual Store audit accepted and deduplicated: '+row['metric']+' / '+row['dimension'],first[0]==202 and second[1].get('duplicate') is True)
check('Quest JP credited once across retries',scalar("SELECT SUM(value_sum) FROM game_player_daily WHERE plugin_name='TownyRebornProgression' AND metric='journey_jp'")=='10')
check('Confirmed eGlow spending uses receipt amount',float(scalar("SELECT SUM(value_sum) FROM game_player_daily WHERE plugin_name='TownyRebornProgression' AND metric='journey_money_spent'"))==19999)
check('Confirmed eGlow ownership counted once',scalar("SELECT SUM(value_sum) FROM game_player_daily WHERE plugin_name='TownyRebornProgression' AND metric='journey_perk_purchases'")=='1')
check('Reward money and Town claims retain exact receipts',float(scalar("SELECT SUM(value_sum) FROM game_player_daily WHERE plugin_name='TownyRebornProgression' AND metric='journey_reward_money'"))==1234 and scalar("SELECT SUM(value_sum) FROM game_player_daily WHERE plugin_name='TownyRebornProgression' AND metric='journey_reward_chunks'")=='2')
status,body,_=req('index.php?page=journey',headers=cookie)
check('Journey is a direct detailed HQ page',status==200 and 'Confirmed perk spending' in body and 'BridgePlayer' in body and 'Glow colours and effects' in body and '19,999' in body and 'cratekey give' in body)
check('Journey card is visible immediately on dashboard','data-game-card="journey"' in req('index.php?page=dashboard',headers=cookie)[1])
check('Journey appears on the existing player profile','profile-journey' in req('index.php?page=player&uuid='+bridge_uuid,headers=cookie)[1])
scalar("INSERT INTO role_permissions(role,permission,allowed,updated_at) VALUES('helper','analytics.view',0,NOW()) ON DUPLICATE KEY UPDATE allowed=0")
check('Journey data honors explicit staff permission denial',req('index.php?page=journey',headers=helper)[0]==403 and 'data-game-card="journey"' not in req('index.php?page=dashboard',headers=helper)[1])
scalar("DELETE FROM role_permissions WHERE role='helper' AND permission='analytics.view'")
# A deterministic broken ingest transaction must not prevent fresh liveness.
scalar("CREATE TRIGGER journey_ingest_failure BEFORE INSERT ON game_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture processing failure'")
failure=post(event('game.event',plugin='TownyRebornProgression',metric='journey_jp',dimension='quests',value=10,uuid=bridge_uuid))
check('Server-side ingest failure returns a safe reference',failure[0]==500 and failure[1].get('reference') and 'fixture processing failure' not in json.dumps(failure[1]))
live={'operation':'connector.heartbeat','server':'smp','occurred_at':now(),'online_players':7,'tps':20,'uptime_seconds':1000,'metadata':{'connector_version':'1.5.0','connection_state':'portal error (HTTP 500)','last_http_status':500,'queued_events':249}}
check('Priority heartbeat survives a broken ordinary ingest route',post(live)[0]==202)
health=json.loads(cli("$_SERVER['SCRIPT_NAME']='api.php';require '"+str(web/'app/bootstrap.php')+"';$r=hqServerStatus(db(),'smp');echo json_encode(['fresh'=>hqStatusFresh($r),'players'=>$r['online_players'],'source'=>$r['status_source'],'http'=>json_decode($r['metadata_json'],true)['last_http_status']]);"))
check('SMP liveness and HTTP 500 delivery status stay separate',health['fresh'] and int(health['players'])==7 and health['http']==500 and health['source']=='priority heartbeat')
scalar('DROP TRIGGER journey_ingest_failure')
stale={**live,'occurred_at':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime(time.time()-3600)),'online_players':99}
post(stale)
check('Old queued heartbeat cannot replace the fresh player count',scalar("SELECT online_players FROM connector_heartbeats WHERE server_name='smp'")=='7')
check('Future heartbeat rejected without faking liveness',post({**live,'occurred_at':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime(time.time()+3600))})[0]==422)
code="""$_SERVER['SCRIPT_NAME']='api.php';require '%s';$p=db();$t=time();
for($n=0;$n<=5;$n++)notificationCondition($p,'fixture:stable','smp','connector','Fixture delivery','high',true,300,[],$t+$n*60);
$id=$p->query("SELECT incident_notification_id FROM notification_monitors WHERE monitor_key='fixture:stable'")->fetchColumn();$p->exec("UPDATE notification_outbox SET status='sent' WHERE id=".(int)$id);
notificationCondition($p,'fixture:stable','smp','connector','Fixture delivery','high',false,300,[],$t+310);
notificationCondition($p,'fixture:stable','smp','connector','Fixture delivery','high',true,300,[],$t+350);
$mid=$p->query("SELECT state,cycle FROM notification_monitors WHERE monitor_key='fixture:stable'")->fetch();
notificationCondition($p,'fixture:stable','smp','connector','Fixture delivery','high',false,300,[],$t+400);
notificationCondition($p,'fixture:stable','smp','connector','Fixture delivery','high',false,300,[],$t+500);
$waiting=$p->query("SELECT state FROM notification_monitors WHERE monitor_key='fixture:stable'")->fetchColumn();
notificationCondition($p,'fixture:stable','smp','connector','Fixture delivery','high',false,300,[],$t+521);
$end=$p->query("SELECT state,cycle FROM notification_monitors WHERE monitor_key='fixture:stable'")->fetch();
echo json_encode(['mid'=>$mid,'waiting'=>$waiting,'end'=>$end,'alerts'=>$p->query("SELECT COUNT(*) FROM notification_outbox WHERE title='Fixture delivery'")->fetchColumn(),'recoveries'=>$p->query("SELECT COUNT(*) FROM notification_outbox WHERE title='Fixture delivery · recovered'")->fetchColumn()]);
"""%str(web/'app/bootstrap.php')
stable=json.loads(cli(code))
check('Flapping connection remains one incident',stable['mid']['state']=='incident' and int(stable['mid']['cycle'])==1 and int(stable['alerts'])==1)
check('Recovery waits for two healthy minutes',stable['waiting']=='incident' and stable['end']['state']=='ok' and int(stable['recoveries'])==1)
# Fresh receipt time by itself is not enough when capture time is old.
check('Old captured status remains unknown',cli("$_SERVER['SCRIPT_NAME']='api.php';require '"+str(web/'app/bootstrap.php')+"';echo hqStatusFresh(['captured_at'=>date('Y-m-d H:i:s',time()-3600),'updated_at'=>date('Y-m-d H:i:s')])?'bad':'ok';")=='ok')

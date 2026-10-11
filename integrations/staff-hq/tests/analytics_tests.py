"""Additional real MySQL/PHP analytics checks, executed inside the isolated integration harness."""
import datetime,csv,io

def analytics_cli(expression):
 return json.loads(cli("$_SERVER['SCRIPT_NAME']='api.php';require '"+str(web/'app/bootstrap.php')+"';echo jsonEncode("+expression+");"))
def utcday(offset):return time.strftime('%Y-%m-%d',time.gmtime(time.time()+offset*86400))
def localstamp(iso):return datetime.datetime.fromisoformat(iso.replace('Z','+00:00')).astimezone(__import__('zoneinfo').ZoneInfo('Europe/Berlin')).strftime('%Y-%m-%d %H:%M:%S')
# A version-6 site had raw session history but no imported retention cohorts.
historic=str(uuid.uuid4());first=utcday(-20)+'T00:30:00Z'
scalar("INSERT INTO players(uuid,username,first_seen_at,last_seen_at,last_server,metadata_json,updated_at) VALUES('"+historic+"','Historical','"+localstamp(first)+"',NOW(),'smp','{}',NOW());")
for offset in [-20,-19,-17,-13]:
 at=localstamp(utcday(offset)+'T00:30:00Z');left=localstamp(utcday(offset)+'T01:30:00Z')
 scalar("INSERT INTO player_sessions(player_uuid,player_name,server_name,joined_at,left_at,duration_seconds,created_at) VALUES('"+historic+"','Historical','smp','"+at+"','"+left+"',3600,NOW());")
# Prove a real midnight-spanning session, and avoid inventing returns from an unclosed one.
for label,offset,closed in [('Overnight',-15,True),('MissingQuit',-14,False)]:
 identity=str(uuid.uuid4());born=utcday(offset)+'T23:30:00Z';left=utcday(offset+1)+'T01:30:00Z'
 scalar("INSERT INTO players(uuid,username,first_seen_at,last_seen_at,last_server,online,metadata_json,updated_at) VALUES('"+identity+"','"+label+"','"+localstamp(born)+"',NOW(),'smp',1,'{}',NOW());")
 end="'"+localstamp(left)+"'" if closed else 'NULL'
 scalar("INSERT INTO player_sessions(player_uuid,player_name,server_name,joined_at,left_at,created_at) VALUES('"+identity+"','"+label+"','smp','"+localstamp(born)+"',"+end+",NOW());")
oldcohort=utcday(-365);scalar("INSERT INTO retention_cohorts(day_utc,new_players,d1,d3,d7,d30) VALUES('"+oldcohort+"',100,65,55,45,25);")
scalar("DELETE FROM settings WHERE setting_key='analytics.cohorts.repaired';UPDATE settings SET setting_value='6' WHERE setting_key='schema.version';")
check('Version-6 retention repair migrates automatically',req('api.php')[0]==200 and scalar("SELECT setting_value FROM settings WHERE setting_key='schema.version'")=='9')
check('Existing sessions restore D1/D3/D7',scalar("SELECT CONCAT(new_players,',',d1,',',d3,',',d7) FROM retention_cohorts WHERE day_utc='"+utcday(-20)+"'")=='1,1,1,1')
check('UTC first-visit date survives local timezone conversion',scalar("SELECT cohort_day FROM retention_members WHERE player_uuid='"+historic+"'")==utcday(-20))
check('Confirmed midnight session restores D1',scalar("SELECT d1 FROM retention_cohorts WHERE day_utc='"+utcday(-15)+"'")=='1')
check('Missing quit does not invent 14 days of returns',scalar("SELECT CONCAT(d1,',',d3,',',d7) FROM retention_cohorts WHERE day_utc='"+utcday(-14)+"'")=='0,0,0')
check('Old permanent retention is preserved',scalar("SELECT CONCAT(new_players,',',d7) FROM retention_cohorts WHERE day_utc='"+oldcohort+"'")=='100,45')
req('api.php');check('Retention repair is idempotent',scalar("SELECT new_players FROM retention_cohorts WHERE day_utc='"+utcday(-20)+"'")=='1')
ret=analytics_cli("analyticsRetention(analyticsRange(['days'=>'90']))['summary']")
check('Mature D7 has real numerator and denominator',ret['7']['eligible']==3 and ret['7']['returned']==1 and abs(ret['7']['percent']-33.3)<.01)
check('Target day in progress is excluded from final D3',ret['3']['eligible']==3 and ret['3']['pending']>=1)
check('D30 distinguishes waiting from zero percent',ret['30']['state']=='waiting' and ret['30']['percent'] is None and ret['30']['next'] is not None)
# Prefilled visits and out-of-order queue delivery must not bypass cohort registration.
queued=str(uuid.uuid4());birth=utcday(-10)+'T10:00:00Z';target=utcday(-3)+'T10:00:00Z'
post(event('player.join',uuid=queued,username='QueuedCohort',first_seen_at=birth,occurred_at=target))
post(event('player.join',uuid=queued,username='QueuedCohort',first_seen_at=birth,occurred_at=birth))
post(event('player.join',uuid=queued,username='QueuedCohort',first_seen_at=birth,occurred_at=target,server='hub'))
check('Out-of-order visits restore a return once',scalar("SELECT CONCAT(new_players,',',d7) FROM retention_cohorts WHERE day_utc='"+utcday(-10)+"'")=='1,1')
# A delayed Paper event can correct a provisional proxy first-seen date without creating two cohorts.
corrected=str(uuid.uuid4());born=utcday(-4)+'T10:00:00Z'
post(event('player.join',uuid=corrected,username='Corrected',server='proxy'))
post(event('player.join',uuid=corrected,username='Corrected',occurred_at=born,first_seen_at=born))
check('Late first-seen correction moves the cohort',scalar("SELECT cohort_day FROM retention_members WHERE player_uuid='"+corrected+"'")==utcday(-4) and scalar("SELECT new_players FROM retention_cohorts WHERE day_utc='"+utcday(-4)+"'")=='1')
# Staff activity groups only genuine active HQ identities, including linked alternate names.
actor=str(uuid.uuid4());scalar("UPDATE users SET minecraft_uuid='"+actor+"',minecraft_username='StaffAlt' WHERE id=1")
post(event('command.action',staff='OrdinaryPlayer',actor_uuid=u,action='command.home',details={'command':'home'}))
post(event('staff.action',staff='FakeStaffPlayer',actor_uuid=v,action='vanish.enabled'))
post(event('command.action',staff='StaffAlt',actor_uuid=actor,action='command.pva',details={'command':'pva'}))
post(event('command.action',staff='Fixture Owner',actor_uuid=v,action='command.home',details={'command':'home'}))
check('Display-name collision cannot turn a player into staff',scalar("SELECT actor_user_id IS NULL FROM audit_logs WHERE actor_label='Fixture Owner' AND actor_minecraft_uuid='"+v+"' ORDER BY id DESC LIMIT 1")=='1')
scalar("INSERT INTO audit_logs(actor_user_id,actor_label,actor_minecraft_uuid,source_server,actor_kind,action,created_at) VALUES(1,'MisattributedPlayer','"+v+"','smp','player','command.home',NOW());")
# Legacy linked row has no resolved user id; migration-friendly UUID matching still includes it.
scalar("INSERT INTO audit_logs(actor_label,actor_minecraft_uuid,actor_kind,action,created_at) VALUES('OlderStaffName','"+actor+"','player','command.tp',NOW());")
staff=analytics_cli("analyticsStaffRows(analyticsRange(['days'=>'30']))")
check('New staff totals exclude unlinked player actions',scalar("SELECT COUNT(*) FROM metric_hourly WHERE metric='staff_actions'")=='0')
check('Regular players excluded from staff activity',all(r['display_name'] in ['Fixture Owner','Fixture Helper'] for r in staff) and int(next(r for r in staff if r['display_name']=='Fixture Owner')['commands'])==2)
status,body,_=req('index.php?page=analytics&view=community',headers=cookie)
check('Staff UI contains only linked staff rows',status==200 and 'data-staff-user="1"' in body and 'OrdinaryPlayer' not in body and 'FakeStaffPlayer' not in body and 'MisattributedPlayer' not in body)
# Bounded long-term series, correct weighted averages, gaps and scope.
for day,total,count in [(utcday(-800),60,3),(utcday(-799),10,1)]:
 scalar("INSERT INTO metric_hourly(hour_utc,server_name,plugin_name,metric,dimension,value_sum,sample_count,value_min,value_max) VALUES('"+day+" 12:00:00','smp','Server','tps','',"+str(total)+","+str(count)+",10,20)")
range_test=analytics_cli("analyticsRange(['days'=>'0','group'=>'hour'])")
check('All-time range includes old permanent totals',range_test['start']<=utcday(-800))
check('Oversized hourly range is safely grouped',range_test['group']!='hour' and len(analytics_cli("analyticsLabels(analyticsRange(['days'=>'0','group'=>'hour']))"))<=800)
series=analytics_cli("analyticsMetricSeries(analyticsRange(['days'=>'custom','start'=>'"+utcday(-800)+"','end'=>'"+utcday(-790)+"','group'=>'month']),'smp',['tps'],'Server',true)")
check('Long-term gauge averages remain sample-weighted',abs(list(series[0]['values'].values())[0]-17.5)<1e-9)
series=analytics_cli("analyticsMetricSeries(analyticsRange(['days'=>'custom','start'=>'"+utcday(-801)+"','end'=>'"+utcday(-790)+"','group'=>'day']),'smp',['tps'],'Server',true)")
check('Missing gauge history remains a gap',series[0]['values'][utcday(-801)] is None and series[0]['values'][utcday(-790)] is None)
for view in ['overview','retention','moderation','economy','community','performance']:
 status,body,_=req('index.php?page=analytics&view='+view+'&days=0',headers=cookie)
 check('Professional analytics section '+view,status==200 and 'data-analysis-chart' in body if view not in ['community','economy','performance'] else status==200)
 check('No bar wall in '+view,'class="bar-track"' not in body and 'class="spark-bars"' not in body)
status,body,_=req('index.php?page=statistics&days=custom&start='+utcday(-801)+'&end='+utcday(-790)+'&section=Performance&plugin=Server&metric=tps',headers=cookie)
check('Custom historical metric page renders',status==200 and 'data-analysis-chart' in body and '800' not in body.split('<title>')[0])
status,body,_=req('index.php?page=stats-export&days=custom&start='+utcday(-800)+'&end='+utcday(-799)+'&section=Performance&server=smp&plugin=Server&metric=tps',headers=cookie)
rows=list(csv.reader(io.StringIO(body)))
check('CSV honors date, category, server, plugin and metric',status==200 and len(rows)==3 and all(row[1:4]==['smp','Server','tps'] for row in rows[1:]))
status,body,_=req('index.php?page=retention-export&days=90',headers=cookie)
check('Retention export is anonymous and marks pending days',status==200 and historic not in body and 'Historical' not in body and utcday(-20) in body and ',,' in body)
scalar("INSERT INTO role_permissions(role,permission,allowed,updated_at) VALUES('helper','analytics.view',0,NOW()) ON DUPLICATE KEY UPDATE allowed=0")
check('Explicit role denial blocks retention export',req('index.php?page=retention-export',headers=helper)[0]==403)
scalar("DELETE FROM role_permissions WHERE role='helper' AND permission='analytics.view'")
status,body,_=req('index.php?page=session-ping',headers=cookie)
check('Session ping reports the actual one-hour timeout',status==200 and json.loads(body)['expires_in']==3600)

# Historic DATETIME records must use winter/summer offsets, without requiring MySQL timezone tables.
check('Winter timestamps convert correctly to UTC',analytics_cli("statScalar(\"SELECT TIMESTAMPDIFF(SECOND,'2025-01-09 23:30:00',\".analyticsUtcSql(\"'2025-01-10 00:30:00'\",['from'=>'2025-01-01 00:00:00','until'=>'2025-12-31 00:00:00']).\")\")")==0)
check('Summer timestamps convert correctly to UTC',analytics_cli("statScalar(\"SELECT TIMESTAMPDIFF(SECOND,'2025-07-09 23:30:00',\".analyticsUtcSql(\"'2025-07-10 01:30:00'\",['from'=>'2025-01-01 00:00:00','until'=>'2025-12-31 00:00:00']).\")\")")==0)

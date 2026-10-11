"""Isolated real PHP/MariaDB integration checks; never uses deployment credentials."""
from pathlib import Path
import subprocess,os,time,json,urllib.request,urllib.error,urllib.parse,uuid,re,socket,shutil
w=Path(__file__).resolve().parents[1];r=w/'build/runtime';web=w/'build/fixture/web';results=[]
web.parent.mkdir(parents=True,exist_ok=True)
if web.exists():shutil.rmtree(web)
shutil.copytree(w/'source/web',web)
env=os.environ.copy();env['LD_LIBRARY_PATH']=str(r/'usr/lib/x86_64-linux-gnu')
php=json.loads((w/'build/php-args.json').read_text());(r/'sessions').mkdir(exist_ok=True);php+=['-d','session.save_path='+str(r/'sessions')]
mysql=[str(r/'usr/bin/mariadb'),'--no-defaults','--protocol=TCP','--host=127.0.0.1','--port=43293','--user=root','--batch','--skip-column-names']
data=r/'db-integration';data.mkdir(exist_ok=True)
base=[str(r/'usr/sbin/mariadbd'),'--no-defaults','--basedir='+str(r/'usr'),'--datadir='+str(data),'--user=root','--lc-messages-dir='+str(r/'usr/share/mysql')]
if not (data/'mysql/global_priv.MAD').exists():
 sql='CREATE DATABASE IF NOT EXISTS mysql;\nUSE mysql;\n'+(r/'usr/share/mysql/mysql_system_tables.sql').read_text()
 p=subprocess.run(base+['--bootstrap','--skip-grant-tables','--skip-log-bin'],input=sql,text=True,env=env,capture_output=True,timeout=50)
 if p.returncode:raise RuntimeError(p.stderr[-4000:])
dbenv=env.copy();dbenv['LD_PRELOAD']=str(r/'socketshim.so')
dblog=open(r/'db.log','w');dbproc=subprocess.Popen(base+['--skip-grant-tables','--skip-log-bin','--bind-address=127.0.0.1','--port=43293','--socket='+str(r/'test.sock'),'--pid-file='+str(r/'test.pid'),'--innodb-buffer-pool-size=64M'],env=dbenv,stdout=dblog,stderr=dblog)
http=None

def check(name,value):
 if not value:raise AssertionError(name)
 results.append(name);print('PASS',name,flush=True)
def sql(text):
 p=subprocess.run(mysql+['-e',text],env=env,text=True,capture_output=True,timeout=20)
 if p.returncode:raise RuntimeError(p.stderr)
 return p.stdout.strip()
def scalar(text):return sql('USE staff_fixture;'+text)
def cli(code):
 p=subprocess.run(php+['-r',code],env=env,text=True,capture_output=True,timeout=30)
 if p.returncode or 'Warning' in p.stderr:raise RuntimeError(p.stdout+p.stderr)
 return p.stdout
url='http://127.0.0.1:43295/'
token='tr_live_'+uuid.uuid4().hex

def req(path,body=None,headers=None):
 h={'X-Forwarded-Proto':'https',**(headers or {})}
 data=None
 if body is not None:
  if isinstance(body,dict):data=json.dumps(body).encode();h['Content-Type']='application/json'
  else:data=body.encode();h['Content-Type']='application/x-www-form-urlencoded'
 try:
  with urllib.request.urlopen(urllib.request.Request(url+path,data=data,headers=h),timeout=25) as p:return p.status,p.read().decode(),dict(p.headers)
 except urllib.error.HTTPError as p:return p.code,p.read().decode(),dict(p.headers)
def event(kind,**kw):return {'type':kind,'event_id':str(uuid.uuid4()),'server':'smp',**kw}
def post(ev,key=token):
 status,body,h=req('api.php',ev,{'Authorization':'Bearer '+key})
 try:parsed=json.loads(body)
 except Exception:raise RuntimeError('Invalid API response '+body[:1000])
 return status,parsed
u=str(uuid.uuid4());v=str(uuid.uuid4());now=lambda:time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())
def session(age=0,role='owner'):
 sid=uuid.uuid4().hex
 code="ini_set('session.use_strict_mode','0');session_name('tr_staff_session');session_id('"+sid+"');session_start();$_SESSION=['user_id'=>"+('1' if role=='owner' else '2')+",'session_version'=>1,'started_at'=>time()-4000,'last_activity'=>time()-"+str(age)+",'csrf_token'=>'fixture_csrf'];session_write_close();"
 cli(code);return sid
try:
 for i in range(100):
  if dbproc.poll() is not None:raise RuntimeError((r/'db.log').read_text()[-4000:])
  try:sql('SELECT 1');break
  except Exception:time.sleep(.15)
 sql('DROP DATABASE IF EXISTS staff_fixture;CREATE DATABASE staff_fixture CHARACTER SET utf8mb4;')
 (web/'config').mkdir(exist_ok=True)
 (web/'config/config.php').write_text("<?php return ['app_key'=>'local-test-only-key','timezone'=>'Europe/Berlin','app_url'=>'https://fixture.invalid','db'=>['host'=>'127.0.0.1','port'=>43293,'database'=>'staff_fixture','username'=>'root','password'=>'']];")
 # Bootstrap the version-5 schema so the deployed automatic migration runs itself.
 schema=str(web/'app/schema.php').replace("'","\\'")
 cli("$p=new PDO('mysql:host=127.0.0.1;port=43293;dbname=staff_fixture','root','');require '"+schema+"';upgradeSchema($p);")
 scalar("INSERT INTO users(id,email,display_name,password_hash,role,must_change_password,totp_enabled,created_at,updated_at) VALUES(1,'owner@example.invalid','Fixture Owner','x','owner',0,1,NOW(),NOW()),(2,'helper@example.invalid','Fixture Helper','x','helper',0,1,NOW(),NOW());INSERT INTO api_clients(client_name,token_prefix,token_hash,active,created_at) VALUES('fixture','tr_live_test','"+__import__('hashlib').sha256(token.encode()).hexdigest()+"',1,NOW());")
 httplog=open(r/'php.log','w');http=subprocess.Popen(php+['-S','127.0.0.1:43295','-t',str(web)],env=env,stdout=httplog,stderr=httplog)
 for i in range(70):
  try:
   status,body,h=req('api.php');break
  except Exception:time.sleep(.1)
 check('Real API health / schema migration',status==200 and json.loads(body)['version']==7 and scalar("SELECT setting_value FROM settings WHERE setting_key='schema.version'")=='10')
 check('Unknown bearer rejected',post(event('player.join',uuid=u,username='Tester'),'tr_live_invalid')[0]==401)
 check('Join accepted',post(event('player.join',uuid=u,username='Tester',platform='java'))[0]==202)
 check('Hub join accepted',post(event('player.join',uuid=u,username='Tester',server='hub'))[0]==202)
 check('Network daily visitors deduplicated',scalar("SELECT active_players FROM daily_server_stats WHERE server_name='network' AND day_utc=UTC_DATE()")=='1')
 sample={'minute':now(),'plugin':'Minecraft','metric':'mob_kills','dimension':'ZOMBIE|NATURAL','uuid':u,'username':'Tester','world':'earth','value':7,'count':7,'min':1,'max':1}
 telem=event('telemetry.batch',samples=[sample])
 check('Gameplay batch accepted',post(telem)[0]==202)
 check('Anonymous count correct',scalar("SELECT SUM(value_sum) FROM metric_hourly WHERE metric='mob_kills'")=='7')
 check('Actor activity recorded',scalar("SELECT value_sum FROM gameplay_activity WHERE player_uuid='"+u+"'")=='7')
 check('Retry recognised',post(telem)[1]['duplicate'])
 check('Retry does not double totals',scalar("SELECT SUM(value_sum) FROM metric_hourly WHERE metric='mob_kills'")=='7')
 second='tr_live_'+uuid.uuid4().hex
 scalar("INSERT INTO api_clients(client_name,token_prefix,token_hash,active,created_at) VALUES('rotated','tr_live_test','"+__import__('hashlib').sha256(second.encode()).hexdigest()+"',1,NOW());")
 check('Token rotation retains event identity',post(telem,second)[1]['duplicate'])
 check('Rotation does not double totals',scalar("SELECT SUM(value_sum) FROM metric_hourly WHERE metric='mob_kills'")=='7')
 bad=event('telemetry.batch',samples=[{**sample,'count':0}]);good=event('telemetry.batch',samples=[{**sample,'metric':'blocks_broken'}])
 check('Invalid batch rolls back',post({'events':[good,bad]})[0]==422 and scalar("SELECT COUNT(*) FROM metric_hourly WHERE metric='blocks_broken'")=='0')
 check('Retry after rollback succeeds',post(good)[0]==202)
 plugins=[{'name':'Plugin'+str(i),'version':'1.0','enabled':True,'coverage':'technical'} for i in range(114)]
 plugins += [{'name':'Jobs','version':'5.2.4','enabled':True,'coverage':'native','hook_names':['JobsPaymentEvent'],'category':'Economy'}]
 snap=event('server.snapshot',plugins=plugins,online_players=15,max_players=150,tps=20,mspt=12.5,metadata={'connector_version':'1.3.0','connection_state':'connected','queued_events':0})
 check('Large plugin inventory accepted',post(snap)[0]==202 and scalar("SELECT COUNT(*) FROM server_plugins WHERE server_name='smp'")=='115')
 check('Native hooks shown',scalar("SELECT hooks_json FROM plugin_observability WHERE plugin_name='Jobs'")=='["JobsPaymentEvent"]')
 release=event('plugin.updates',plugins=[{'name':'Jobs','installed_version':'5.2.4','latest_version':'5.2.6','state':'update_available','url':'https://www.spigotmc.org/resources/4216/','checked_at':now()}])
 check('Release results accepted',post(release)[0]==202)
 post(event('server.snapshot',plugins=plugins,online_players=15))
 check('Inventory preserves releases',scalar("SELECT latest_version FROM plugin_observability WHERE plugin_name='Jobs'")=='5.2.6')
 for text,source in [('I found a dupe bug','smp'),('debugging the debugger','smp'),('Hub glitch message','hub'),('normal greeting','hub')]:post(event('chat.message',uuid=u,username='Tester',message=text,channel='global',server=source))
 check('Chat phrase boundaries',scalar('SELECT COUNT(*) FROM chat_signals')=='2')
 check('Private chat accepted',post(event('chat.message',uuid=u,username='Tester',message='private exploit hint',channel='private',recipient_name='Other',recipient_uuid=v))[0]==202)
 diag=event('diagnostic.alert',plugin='Quests',level='WARN',message='Could not load 127.0.0.1 '+u)
 post(diag);post({**diag,'event_id':str(uuid.uuid4())});post(event('diagnostic.alert',plugin='Auth',level='WARN',message='API token tr_live_secret'))
 check('Repeated diagnostics grouped',scalar('SELECT occurrences FROM server_diagnostics')=='2')
 check('Diagnostic secrets excluded / identity redacted',scalar('SELECT COUNT(*) FROM server_diagnostics')=='1' and u not in scalar('SELECT message FROM server_diagnostics') and '127.0.0.1' not in scalar('SELECT message FROM server_diagnostics'))
 sid=session();cookie={'Cookie':'tr_staff_session='+sid}
 for page in ['dashboard','analytics','statistics','plugins','activity','chat-review','diagnostics','players','player&uuid='+u,'cases','punishments','documents','commands','messages','team','audit','settings','leaderboards','crates','economy','permissions','staff-guide']:
  status,body,h=req('index.php?page='+page,headers=cookie)
  check('Authenticated page '+page.split('&')[0],status==200 and 'Secure staff sign-in' not in body and 'Warning:' not in body and 'Fatal error' not in body and 'data-session-timeout="3600"' in body)
 status,body,h=req('index.php?page=messages&server=hub',headers=cookie)
 check('Hub chat filter isolated',status==200 and 'Hub glitch message' in body and 'I found a dupe bug' not in body)
 status,body,h=req('index.php?page=stats-export',headers=cookie)
 check('CSV contains only anonymous dimensions',status==200 and 'mob_kills' in body and u not in body and 'Tester' not in body)
 helper={'Cookie':'tr_staff_session='+session(role='helper')}
 check('Helper can view player activity',req('index.php?page=activity',headers=helper)[0]==200)
 check('Role denies chat review',req('index.php?page=chat-review',headers=helper)[0]==403)
 check('Review CSRF enforced',req('index.php?page=chat-review','action=review-chat-dismiss-all',cookie)[0]==419)
 signal=scalar("SELECT id FROM chat_signals WHERE server_name='smp' AND channel_name='global' LIMIT 1")
 form=urllib.parse.urlencode({'action':'review-chat-case','id':signal,'csrf_token':'fixture_csrf'})
 status,body,h=req('index.php?page=chat-review',form,cookie)
 check('Chat hint creates a case with evidence',status==200 and scalar('SELECT COUNT(*) FROM cases')=='1' and scalar('SELECT COUNT(*) FROM case_events')=='1')
 check('Case links the correct player',scalar('SELECT player_uuid FROM case_players')==u)
 req('index.php?page=chat-review',form,cookie)
 check('Repeated case click is idempotent',scalar('SELECT COUNT(*) FROM cases')=='1')
 req('index.php?page=chat-review','action=review-chat-dismiss-all&server=hub&csrf_token=fixture_csrf',cookie)
 check('Bulk dismiss scoped to Hub',scalar("SELECT COUNT(*) FROM chat_signals WHERE server_name='hub' AND status='open'")=='0' and scalar("SELECT COUNT(*) FROM chat_signals WHERE server_name='smp' AND status='open'")=='1')
 check('Helper cannot dismiss hints',req('index.php?page=chat-review','action=review-chat-dismiss-all&csrf_token=fixture_csrf',helper)[0]==403)
 diagid=scalar('SELECT id FROM server_diagnostics LIMIT 1')
 req('index.php?page=diagnostics','action=review-diagnostic-case&id='+diagid+'&csrf_token=fixture_csrf',cookie)
 check('Diagnostic can become a case',scalar('SELECT COUNT(*) FROM cases')=='2' and scalar("SELECT status FROM server_diagnostics LIMIT 1")=='escalated')
 exec(compile((w/'tests/workspace_checks.py').read_text(),str(w/'tests/workspace_checks.py'),'exec'),globals())
 expectedCases=scalar('SELECT COUNT(*) FROM cases');expectedEvidence=scalar('SELECT COUNT(*) FROM case_events')
 d3day=time.strftime('%Y-%m-%dT12:00:00Z',time.gmtime(time.time()-3*86400));cohort=str(uuid.uuid4())
 post(event('player.join',uuid=cohort,username='DayThree',occurred_at=d3day,first_seen_at=d3day))
 post(event('player.join',uuid=cohort,username='DayThree'))
 post(event('player.join',uuid=cohort,username='DayThree',server='hub'))
 exec(compile((w/'tests/analytics_tests.py').read_text(),str(w/'tests/analytics_tests.py'),'exec'),globals())
 exec(compile((w/'tests/experience_checks.py').read_text(),str(w/'tests/experience_checks.py'),'exec'),globals())
 exec(compile((w/'tests/guide_role_checks.py').read_text(),str(w/'tests/guide_role_checks.py'),'exec'),globals())
 check('D3 cohort return is counted once across servers',scalar("SELECT d3 FROM retention_cohorts WHERE day_utc=DATE_SUB(UTC_DATE(),INTERVAL 3 DAY)")=='1')
 sid55=session(3300);h55={'Cookie':'tr_staff_session='+sid55,'X-TR-Background':'1'}
 check('Still authenticated after 55 minutes',req('index.php?page=plugins',headers=h55)[0]==200)
 sessiondata=(r/'sessions'/('sess_'+sid55)).read_text()
 age=time.time()-int(re.search(r'last_activity\|i:(\d+)',sessiondata).group(1))
 check('Automatic refresh does not extend inactivity',age>=3290)
 sid61=session(3700);status,body,h=req('index.php?page=plugins',headers={'Cookie':'tr_staff_session='+sid61,'X-TR-Background':'1'})
 check('Expired after 60 minutes',('Secure staff sign-in' in body or 'Sign in' in body) and 'data-plugin-live' not in body)
 old=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime(time.time()-40*86400));post(event('telemetry.batch',samples=[{**sample,'minute':old,'metric':'old_totals'}]))
 check('Old queued totals accepted without player history',scalar("SELECT SUM(value_sum) FROM metric_hourly WHERE metric='old_totals'")=='7' and scalar("SELECT COUNT(*) FROM gameplay_activity WHERE metric='old_totals'")=='0')
 scalar("UPDATE gameplay_activity SET last_at=DATE_SUB(NOW(),INTERVAL 31 DAY);UPDATE player_messages SET sent_at=DATE_SUB(NOW(),INTERVAL 31 DAY);UPDATE chat_signals SET created_at=DATE_SUB(NOW(),INTERVAL 31 DAY);UPDATE player_sessions SET joined_at=DATE_SUB(NOW(),INTERVAL 31 DAY);UPDATE server_diagnostics SET last_at=DATE_SUB(NOW(),INTERVAL 31 DAY);UPDATE ingest_events SET received_at=DATE_SUB(NOW(),INTERVAL 31 DAY);UPDATE retention_members SET cohort_day=DATE_SUB(UTC_DATE(),INTERVAL 31 DAY);UPDATE players SET observability_cohort=DATE_SUB(UTC_DATE(),INTERVAL 31 DAY),last_seen_at=DATE_SUB(NOW(),INTERVAL 31 DAY),metadata_json='{\"world\":\"earth\"}',balance=500;INSERT IGNORE INTO daily_player_visits(day_utc,server_name,player_uuid) SELECT DATE_SUB(UTC_DATE(),INTERVAL 31 DAY),server_name,player_uuid FROM daily_player_visits;DELETE FROM daily_player_visits WHERE day_utc>=DATE_SUB(UTC_DATE(),INTERVAL 30 DAY);")
 clean=cli("$_SERVER['SCRIPT_NAME']='api.php';require '"+str(web/'app/bootstrap.php')+"';obsRetention(db());")
 check('Thirty-day activity removed',scalar('SELECT COUNT(*) FROM gameplay_activity')=='0' and scalar('SELECT COUNT(*) FROM player_messages')=='0' and scalar('SELECT COUNT(*) FROM chat_signals')=='0')
 check('Temporary cohort membership expires while anonymous cohorts remain',scalar('SELECT COUNT(*) FROM retention_members')=='0' and int(scalar('SELECT SUM(new_players) FROM retention_cohorts'))>=100)
 check('Saved cases and evidence survive raw-log expiry',scalar('SELECT COUNT(*) FROM cases')==expectedCases and scalar('SELECT COUNT(*) FROM case_events')==expectedEvidence)
 check('Anonymous statistics survive cleanup',scalar("SELECT SUM(value_sum) FROM metric_hourly WHERE metric='mob_kills'")=='7')
 check('Idle player metadata scrubbed',scalar("SELECT balance IS NULL AND metadata_json='{}' FROM players WHERE uuid='"+u+"'")=='1')
 check('Event receipts survive cleanup',post(telem)[1]['duplicate'] and scalar("SELECT SUM(value_sum) FROM metric_hourly WHERE metric='mob_kills'")=='7')
 # Create current points and a session for browser tests, using only fixture data.
 for offset,value in [(48,4),(24,7),(0,2)]:
  at=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime(time.time()-offset*3600));post(event('telemetry.batch',samples=[{**sample,'minute':at,'metric':'mob_kills','value':value,'count':value}]))
 post(event('telemetry.batch',server='hub',samples=[{**sample,'minute':now(),'metric':'mob_kills','value':3,'count':3}]))
 post(event('chat.message',uuid=u,username='Tester',message='Fixture: a very long chat message '+('context and evidence ' * 18),channel='global'))
 post(event('command.action',actor_uuid=u,staff='Tester',action='command.town',command='/town claim '+('longargument'*16)))
 (w/'tests/browser-session.json').write_text(json.dumps({'url':url,'session':sid,'uuid':u}))
 if (w/'tests/browser_tests.cjs').exists():
  node=os.environ.get('CODEX_PRIMARY_RUNTIME_NODE','node');p=subprocess.run([node,str(w/'tests/browser_tests.cjs')],env=env,capture_output=True,text=True,timeout=120)
  print(p.stdout,flush=True)
  if p.returncode:raise RuntimeError(p.stderr)
  check('Browser charts, mobile layout and JavaScript',True)
 print('TOTAL',len(results),'passed',flush=True)
finally:
 (w/'tests/live-results.json').write_text(json.dumps({'passed':len(results),'checks':results},indent=2))
 if http:http.terminate();http.wait(timeout=10)
 dbproc.terminate();dbproc.wait(timeout=15)

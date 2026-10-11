"""Behavioural checks executed by live_tests.py against isolated PHP and MySQL."""
import hashlib

def form(action, actor=cookie, **fields):
 return req('index.php?page=player&uuid='+u,urllib.parse.urlencode({'action':action,'csrf_token':'fixture_csrf','player_uuid':u,**fields}),actor)

check('Profile mutations require CSRF',req('index.php?page=player&uuid='+u,'action=profile-note&player_uuid='+u+'&body=bad',cookie)[0]==419)
status,body,_=form('profile-note',body='Watch the farm <script>alert(1)</script>',note_type='watch',priority='high')
dossier=scalar("SELECT id FROM player_dossiers WHERE player_uuid='"+u+"'")
check('Watch instruction needs no case',status==200 and scalar('SELECT COUNT(*) FROM player_notes')=='1' and scalar('SELECT COUNT(*) FROM cases')=='2')
check('Profile notes escape HTML', '&lt;script&gt;alert(1)&lt;/script&gt;' in body and '<script>alert(1)</script>' not in body)
note=scalar('SELECT id FROM player_notes LIMIT 1')
form('profile-note-archive',note_id=note)
check('Notes can be archived',scalar('SELECT archived_at IS NOT NULL FROM player_notes WHERE id='+note)=='1')
form('profile-note-archive',note_id=note,restore='1')
check('Archived notes can be restored',scalar('SELECT archived_at IS NULL FROM player_notes WHERE id='+note)=='1')
message=scalar("SELECT id FROM player_messages WHERE channel_name='global' AND source_server='smp' LIMIT 1")
form('profile-pin',message_id=message,staff_note='Preserve this discussion')
form('profile-pin',message_id=message)
check('Message pin is idempotent',scalar('SELECT COUNT(*) FROM player_pins')=='1')
nonce=uuid.uuid4().hex
form('profile-case',message_id=message,request_key=nonce,title='Farm investigation',description='Check farm history')
form('profile-case',message_id=message,request_key=uuid.uuid4().hex,title='Repeated click',description='Check farm history')
check('Message case deduplicates independent clicks',scalar('SELECT COUNT(*) FROM cases')=='3')
general=uuid.uuid4().hex
form('profile-case',request_key=general,title='Profile investigation',description='Check report')
form('profile-case',request_key=general,title='Profile investigation',description='Check report')
check('Profile case deduplicates form retries',scalar('SELECT COUNT(*) FROM cases')=='4')
scalar('DELETE FROM player_messages WHERE id='+message)
status,body,_=req('index.php?page=player&uuid='+u,headers=cookie)
check('Permanent pin survives original message deletion','Preserve this discussion' in body and scalar('SELECT COUNT(*) FROM player_pins')=='1')

privateMessage=scalar("SELECT id FROM player_messages WHERE channel_name='private' LIMIT 1")
form('profile-pin',message_id=privateMessage)
form('profile-case',message_id=privateMessage,request_key=uuid.uuid4().hex,title='Private evidence',description='Review the conversation')
privateCase=scalar('SELECT MAX(id) FROM cases')
for permission in ['players.view','profile.notes.view','profile.pins.view','profile.files.view','messages.view','messages.review','cases.view','cases.create','team.manage']:
 scalar("INSERT INTO role_permissions(role,permission,allowed,updated_at) VALUES('helper','"+permission+"',1,NOW()) ON DUPLICATE KEY UPDATE allowed=1")
scalar("INSERT INTO role_permissions(role,permission,allowed,updated_at) VALUES('helper','messages.private',0,NOW()) ON DUPLICATE KEY UPDATE allowed=0")
status,body,_=req('index.php?page=player&uuid='+u,headers=helper)
check('Restricted profile hides private raw chat and pins',status==200 and 'private exploit hint' not in body)
status,body,_=req('index.php?page=cases&case_id='+privateCase,headers=helper)
check('Private case evidence requires extra permission',status==200 and 'private exploit hint' not in body and 'additional permission required' in body)
status,body,_=req('index.php?page=messages',headers=helper)
check('Private message search requires extra permission','private exploit hint' not in body)
status,body,_=form('profile-pin',actor=helper,message_id=privateMessage)
check('Restricted staff cannot pin a hidden private message',scalar('SELECT COUNT(*) FROM player_pins')=='2')
req('index.php?page=chat-review','action=review-chat-dismiss-all&server=smp&csrf_token=fixture_csrf',helper)
check('Bulk dismiss does not touch hidden private flags',scalar("SELECT status FROM chat_signals WHERE channel_name='private' LIMIT 1")=='open')
before=scalar('SELECT COUNT(*) FROM users')
status,body,_=req('index.php?page=team',urllib.parse.urlencode({'action':'create-user','csrf_token':'fixture_csrf','email':'forbidden@example.invalid','display_name':'Forbidden','role':'helper'}),helper)
check('Staff creation stays Owner-only despite team.manage',scalar('SELECT COUNT(*) FROM users')==before)

def multipart(path,fields,files,actor=cookie):
 boundary='fixture-'+uuid.uuid4().hex;chunks=[]
 for key,value in fields.items():chunks.append(('--'+boundary+'\r\nContent-Disposition: form-data; name="'+key+'"\r\n\r\n'+str(value)+'\r\n').encode())
 for key,name,content in files:
  chunks.append(('--'+boundary+'\r\nContent-Disposition: form-data; name="'+key+'"; filename="'+name+'"\r\nContent-Type: text/plain\r\n\r\n').encode()+content+b'\r\n')
 chunks.append(('--'+boundary+'--\r\n').encode())
 request=urllib.request.Request(url+path,data=b''.join(chunks),headers={**actor,'X-Forwarded-Proto':'https','Content-Type':'multipart/form-data; boundary='+boundary})
 try:
  with urllib.request.urlopen(request,timeout=20) as response:return response.status,response.read().decode()
 except urllib.error.HTTPError as response:return response.code,response.read().decode()

data=b'Permanent profile evidence, local fixture only.'
status,body,_=req('upload.php',urllib.parse.urlencode({'action':'init','csrf_token':'fixture_csrf','owner_type':'player','owner_id':dossier,'name':'evidence.txt','size':len(data),'chunks':1}),cookie)
upload=json.loads(body);check('Profile chunk upload initializes without case',status==200 and upload['ok'])
uploadId=upload['upload_id']
status,body=multipart('upload.php',{'action':'chunk','csrf_token':'fixture_csrf','upload_id':uploadId,'index':0},[('chunk','part.txt',data)])
check('Profile evidence chunk accepted',status==200 and json.loads(body)['ok'])
status,body,_=req('upload.php',urllib.parse.urlencode({'action':'complete','csrf_token':'fixture_csrf','upload_id':uploadId}),cookie)
attachment=json.loads(body)['attachment_id'];check('Profile evidence assembled and attached',status==200 and scalar('SELECT owner_type FROM attachments WHERE id='+str(attachment))=='player')
check('Profile media authorizes the role',req('media.php?id='+str(attachment),headers=helper)[0]==200)
scalar("UPDATE role_permissions SET allowed=0 WHERE role='helper' AND permission='profile.files.view'")
check('Revoked profile attachment permission blocks media',req('media.php?id='+str(attachment),headers=helper)[0]==403)
status,body,_=req('upload.php',urllib.parse.urlencode({'action':'init','csrf_token':'fixture_csrf','owner_type':'player','owner_id':dossier,'name':'huge.txt','size':104857601,'chunks':200}),cookie)
check('Profile upload size has a server-side cap',status==422 and '100 MB' in body)

batch=event('game.event',plugin='ExcellentCrates',metric='crate_openings',dimension='vote',uuid=u,username='Tester',value=7,count=1,details={'selected_amount':7,'rewards':['coin']*7})
check('Seven-opening batch ingested',post(batch)[0]==202)
post(batch)
check('Batch retry never doubles openings',scalar("SELECT SUM(value_sum) FROM game_player_daily WHERE metric='crate_openings'")=='7')
scalar("DELETE FROM ingest_events WHERE event_id='"+batch['event_id']+"';DELETE FROM observability_receipts WHERE event_id='"+batch['event_id']+"'")
post(batch)
check('Permanent game ledger survives receipt expiry',scalar("SELECT SUM(value_sum) FROM game_player_daily WHERE metric='crate_openings'")=='7')
reward=event('game.event',plugin='ExcellentCrates',metric='crate_reward',dimension='vote|coin',uuid=u,username='Tester',value=7,count=7)
post(reward)
status,body,_=req('index.php?page=crates&crate=vote&days=0',headers=cookie)
check('Crate filter includes opening and reward dimensions',status==200 and 'vote · coin' in body and 'Batch 7' in body)
vote=event('game.event',plugin='Votifier',metric='votes',username='Tester',value=1)
post(vote);post(vote)
check('Vote leaderboard resolves known UUID and deduplicates',scalar("SELECT CONCAT(player_uuid,',',value_sum) FROM game_player_daily WHERE metric='votes'")==u+',1')
bad=event('game.event',plugin='ExcellentCrates',metric='crate_openings',value='NaN')
check('Invalid game value rejected',post(bad)[0]==422)
snap=lambda key,entries,**kw:event('integration.snapshot',plugin='QuickShop-Hikari',snapshot_key=key,data={'entries':entries},**kw)
post(snap('catalog.0',[{'id':'shop-one','name':'DIAMOND'}]));post(snap('catalog.1',[{'id':'stale-shop','name':'STONE'}]))
post(event('integration.snapshot',plugin='QuickShop-Hikari',snapshot_key='catalog-manifest',data={'prefix':'catalog.','keys':['catalog.0']}))
check('Catalog shrink removes stale chunks',scalar("SELECT COUNT(*) FROM integration_snapshots WHERE plugin_name='QuickShop-Hikari' AND snapshot_key LIKE 'catalog.%'")=='1')
post(snap('catalog.0',[{'id':'older-shop'}],occurred_at='2026-01-01T00:00:00Z'))
check('Delayed snapshot cannot overwrite current catalog','shop-one' in scalar("SELECT data_json FROM integration_snapshots WHERE snapshot_key='catalog.0'"))
insightsSnap=event('server.snapshot',plugins=[{'name':'ExcellentCrates','version':'7.1.0','enabled':True,'coverage':'native','insights_hooks':['RewardGrantedEvent'],'failed_hooks':[]}])
post(insightsSnap)
status,body,_=req('index.php?page=plugins',headers=cookie)
check('Native insights hooks render on plugin inventory',status==200 and 'RewardGrantedEvent' in body and 'Warning:' not in body and 'Fatal error' not in body)

# An explicitly denied role must stay denied during additive migrations.
scalar("DELETE FROM role_permissions WHERE role='admin';INSERT INTO role_permissions(role,permission,allowed,updated_at) VALUES('admin','players.view',0,NOW())")
cli("$_SERVER['SCRIPT_NAME']='api.php';require '"+str(web/'app/bootstrap.php')+"';workspaceSchema(db());")
check('Migration preserves a role with all permissions denied',scalar("SELECT SUM(allowed) FROM role_permissions WHERE role='admin'")=='0')

# Purely local notification transport: no Discord HTTP requests are made.
code="""$_SERVER['SCRIPT_NAME']='api.php';require '%s';
$s=notificationDefaults();$s['enabled']=true;$s['bot_token']=str_repeat('x',40);$s['channel_id']='123456789012345678';$s['bot_verified_context']=notificationBotContext($s);$GLOBALS['notification_settings']=$s;
$t=time();$p=['message'=>'we found a dupe machine','username'=>'Fixture','channel'=>'private'];
notificationOnEvent(db(),$p,'chat.message','smp',date('Y-m-d H:i:s',$t),$t);
notificationOnEvent(db(),$p,'chat.message','smp',date('Y-m-d H:i:s',$t),$t);
$before=db()->query(\"SELECT COUNT(*) FROM notification_outbox WHERE kind='chat_flag'\")->fetchColumn();
$calls=[];$r=notificationDispatch(db(),5,function($url,$body)use(&$calls){$calls[]=$body;return ['status'=>200,'headers'=>[],'body'=>'{}'];},$t);
notificationOnEvent(db(),$p,'chat.message','smp',date('Y-m-d H:i:s',$t-600),$t);
echo jsonEncode(['before'=>(int)$before,'result'=>$r,'calls'=>count($calls),'occurrences'=>(int)db()->query(\"SELECT occurrences FROM notification_outbox WHERE kind='chat_flag' LIMIT 1\")->fetchColumn()]);
"""%str(web/'app/bootstrap.php')
notification=json.loads(cli(code))
check('Dupe chat queues an immediate Discord flag',notification['before']==1 and notification['result']['sent']==1 and notification['calls']==1)
check('Repeated chat flag is coalesced and old replay ignored',notification['occurrences']==2)

retentionCode="$_SERVER['SCRIPT_NAME']='api.php';require '"+str(web/'app/bootstrap.php')+"';echo jsonEncode(chatReviewMatches('a dupe-machine, duping and duplizieren; debugging',[]));"
check('Built-in dupe variants survive empty custom rules','Duplication' in json.loads(cli(retentionCode))['categories'])

# The CLI import is statistical, repeat-safe and uses no server command queue.
historyFile=w/'build/fixture/history.jsonl'
historyFile.write_text(json.dumps({**batch,'event_id':'fixture-history','occurred_at':now(),'imported':True})+'\n')
commandBefore=scalar('SELECT COUNT(*) FROM action_queue')
def importHistory():
 p=subprocess.run(php+[str(web/'cli/import-game-history.php'),str(historyFile)],env=env,text=True,capture_output=True,timeout=20)
 if p.returncode:raise RuntimeError(p.stdout+p.stderr)
 return json.loads(p.stdout)
firstImport=importHistory();secondImport=importHistory()
check('Historical CLI import is repeat-safe',firstImport['new_statistics_records']==1 and secondImport['new_statistics_records']==0)
check('Historical import creates no grants or remote commands',scalar('SELECT COUNT(*) FROM action_queue')==commandBefore)

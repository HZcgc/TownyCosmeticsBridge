"""Persistent guide editing, access controls and the requested once-only role rollout."""
status,body,_=req('index.php?page=documents',headers=cookie)
guide=scalar("SELECT setting_value FROM settings WHERE setting_key='staff.guide.document_id'")
check('Staff guide is available in Documents',status==200 and guide.isdigit() and 'Staff field guide' in body)
before=scalar('SELECT COUNT(*) FROM documents')
changed='Updated staff guide <script>unsafe guide</script>'
payload=urllib.parse.urlencode({'action':'update-document','csrf_token':'fixture_csrf','document_id':guide,'title':'Our staff handbook','category':'Staff procedures','body':changed})
status,body,_=req('index.php?page=documents',payload,cookie)
check('Owner edits the guide through the existing document action',status==200 and scalar('SELECT title FROM documents WHERE id='+guide)=='Our staff handbook')
status,body,_=req('index.php?page=staff-guide',headers=cookie)
check('Direct Staff guide shows the renamed edited document safely',status==200 and '&lt;script&gt;unsafe guide&lt;/script&gt;' in body and '<script>unsafe guide</script>' not in body and 'Our staff handbook' in body)
req('index.php?page=documents',headers=cookie);req('index.php?page=staff-guide',headers=cookie)
check('Reload never duplicates or overwrites the edited guide',scalar('SELECT COUNT(*) FROM documents')==before and scalar('SELECT body FROM documents WHERE id='+guide)==changed)
helper={'Cookie':'tr_staff_session='+session(role='helper')}
check('Helper reads but cannot edit the Staff guide',req('index.php?page=staff-guide',headers=helper)[0]==200 and req('index.php?page=documents',payload,helper)[0]==403)
check('Guide editing retains CSRF protection',req('index.php?page=documents',payload.replace('fixture_csrf','wrong'),cookie)[0]==419)

# Simulate the actual schema-9 -> 10 upgrade with restrictive pre-existing rows.
scalar("UPDATE settings SET setting_value='9' WHERE setting_key='schema.version';DELETE FROM settings WHERE setting_key='staff.roles.preset_5_1_1';UPDATE role_permissions SET allowed=0 WHERE role IN ('helper','moderator','admin');INSERT INTO role_permissions(role,permission,allowed,updated_at) VALUES('helper','custom.future.permission',0,NOW()) ON DUPLICATE KEY UPDATE allowed=0")
req('api.php')
check('Upgrade applies the requested presets and keeps a rollback snapshot',scalar("SELECT setting_value FROM settings WHERE setting_key='schema.version'")=='11' and bool(scalar("SELECT setting_value FROM settings WHERE setting_key='staff.roles.before_5_1_1'")))
for role in ['helper','moderator','admin']:
 code="$_SERVER['SCRIPT_NAME']='api.php';require '"+str(web/'app/bootstrap.php')+"';$wanted=defaultRolePermissions('"+role+"');$q=db()->prepare('SELECT permission,allowed FROM role_permissions WHERE role=?');$q->execute(['"+role+"']);$rows=array_column($q->fetchAll(),'allowed','permission');foreach(permissionCatalog() as $p=>$_){if((bool)$rows[$p]!==in_array($p,$wanted,true))throw new RuntimeException($p);}echo 'ok';"
 check('All known permissions match the '+role+' preset',cli(code)=='ok')
check('Unknown future permissions are preserved',scalar("SELECT allowed FROM role_permissions WHERE role='helper' AND permission='custom.future.permission'")=='0')
code="$_SERVER['SCRIPT_NAME']='api.php';require '"+str(web/'app/bootstrap.php')+"';echo jsonEncode([can('documents.manage',['role'=>'admin']),can('api.manage',['role'=>'admin']),can('messages.private',['role'=>'moderator']),can('punishments.execute',['role'=>'moderator']),can('messages.private',['role'=>'helper']),can('team.manage',['role'=>'admin']),can('team.manage',['role'=>'owner'])]);"
check('Admin, moderator and Owner boundaries match the requested hierarchy',json.loads(cli(code))==[True,True,True,True,False,False,True])
scalar("UPDATE role_permissions SET allowed=0 WHERE role='helper' AND permission='statistics.economy';UPDATE settings SET setting_value='9' WHERE setting_key='schema.version'")
req('api.php')
check('Later Owner edits survive subsequent upgrades and requests',scalar("SELECT allowed FROM role_permissions WHERE role='helper' AND permission='statistics.economy'")=='0')
scalar("UPDATE role_permissions SET allowed=1 WHERE role='helper' AND permission='statistics.economy'")

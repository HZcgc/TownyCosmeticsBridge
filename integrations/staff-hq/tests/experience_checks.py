"""Navigation, native detail-page totals and access regressions in the isolated harness."""
import html
for plugin,metric,value in [('TownyDragonEvent','dragon_damage',120),('TownyDragonEvent','dragon_wins',1),('QuickShop-Hikari','trade_value',250),('QuickShop-Hikari','shop_transactions',1),('QuickShop-Hikari','items_traded',16),('TownyRebornPetShops','pet_purchases',1),('MCPets','pet_summons',2),('PinataParty','pinata_hits',4),('PyroFishingPro','fish_caught',3)]:
 row=event('game.event',plugin=plugin,metric=metric,uuid=u,username='Tester',value=value,details={'event':'fixture-fight','rank':1})
 post(row);post(row)
post(event('integration.snapshot',plugin='TownyDragonEvent',snapshot_key='lifetime',data={'players':[{'uuid':u,'name':'Tester','total-damage':900,'wins':3,'personal-best':700,'deaths':2,'crystals':5}]}))
shops=[{'id':str(i),'name':'SHOP-'+str(i),'owner':u,'item':'<script>unsafe catalogue</script>'} for i in range(50)]
post(event('integration.snapshot',plugin='QuickShop-Hikari',snapshot_key='catalog.0',data={'entries':shops}))
post(event('integration.snapshot',plugin='TownyRebornPetShops',snapshot_key='catalog.0',data={'entries':[{'id':'sakura-pet','name':'Fixture pet','ingame-price':75000,'tebex':{'command':'grant {username}'}}]}))
pages=['crates','dragon','votes','pinata','fishing','shops','pets']
status,body,_=req('index.php?page=dashboard',headers=cookie)
check('Dashboard exposes all seven game cards directly',status==200 and all('data-game-card="'+p+'"' in body for p in pages))
nav=body.split('aria-label="Staff navigation"',1)[1].split('</nav>',1)[0]
check('All seven systems are direct navigation links',all('index.php?page='+p+'"' in nav for p in pages))
check('Navigation headings never duplicate',all(nav.count('>'+group+'</span>')==1 for group in ['Workspace','Games &amp; economy','Players &amp; moderation','Server &amp; analysis','Team &amp; guidance']))
status,body,_=req('index.php?page=statistics',headers=cookie)
check('Statistics opens an overview instead of an arbitrary metric',status==200 and 'Choose a game or system' in body and 'Mob Kills' not in body)
for page in pages+['metrics','leaderboards','economy']:
 status,body,_=req('index.php?page='+page,headers=cookie)
 check('Direct page works: '+page,status==200 and 'Warning:' not in body and 'Fatal error' not in body)
 if page in pages:check('Correct navigation entry active: '+page,('index.php?page='+page+'" class="active" aria-current="page"') in body)
status,body,_=req('index.php?page=dragon',headers=cookie)
check('Dragon lifetime history is visible on its dedicated page',status==200 and 'Lifetime Dragon records' in body and '900' in body and '700' in body)
chart=json.loads(html.unescape(re.search(r'data-chart-data="([^"]+)"',body).group(1)))
check('Native Dragon chart excludes lifetime snapshot totals',sum(v or 0 for v in chart['series'][0]['values'])==120)
check('Native game charts use live theme colours',chart['series'][0]['color']=='var(--chart-1)')
status,body,_=req('index.php?page=shops',headers=cookie)
check('Shop catalogue is bounded to 24 entries',status==200 and body.count('class="catalog-entry"')==24 and 'Page 1 of 3' in body)
check('Catalogue HTML is escaped','<script>unsafe catalogue</script>' not in body and '&lt;script&gt;unsafe catalogue&lt;/script&gt;' in body)
status,body,_=req('index.php?page=shops&catalog_page=99999',headers=cookie)
check('Out-of-range catalogue pages are clamped',status==200 and body.count('class="catalog-entry"')==2 and 'Page 3 of 3' in body)
status,body,_=req('index.php?page=pets',headers=cookie)
check('Pet offer details include nested Tebex information',status==200 and '75000' in body and 'grant {username}' in body)
q=scalar("SELECT allowed FROM role_permissions WHERE role='helper' AND permission='statistics.economy'")
scalar("INSERT INTO role_permissions(role,permission,allowed,updated_at) VALUES('helper','statistics.economy',0,NOW()) ON DUPLICATE KEY UPDATE allowed=0")
helper={'Cookie':'tr_staff_session='+session(role='helper')}
check('Denied economy role cannot open shops or pets',req('index.php?page=shops',headers=helper)[0]==403 and req('index.php?page=pets',headers=helper)[0]==403)
status,body,_=req('index.php?page=dashboard',headers=helper)
check('Denied systems disappear from both cards and navigation',status==200 and 'data-game-card="shops"' not in body and 'data-game-card="pets"' not in body and 'index.php?page=shops"' not in body and 'index.php?page=pets"' not in body)
if q:scalar("UPDATE role_permissions SET allowed="+q+" WHERE role='helper' AND permission='statistics.economy'")
else:scalar("DELETE FROM role_permissions WHERE role='helper' AND permission='statistics.economy'")

const fs = require('fs');
const path = require('path');
const { chromium } = require(path.join(process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES, 'playwright'));
const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, 'browser-session.json')));
const checks = [];
function check(name, value) { if (!value) throw new Error(name); checks.push(name); console.log('PASS', name); }
(async () => {
 const browser = await chromium.launch({headless:true,executablePath:path.join(__dirname,'../build/chromium/chrome-headless-shell-linux64/chrome-headless-shell'),args:['--no-sandbox']});
 const context = await browser.newContext({viewport:{width:1440,height:1000}});
 await context.addCookies([{name:'tr_staff_session',value:fixture.session,url:fixture.url}]);
 await context.route('**/*',route => new URL(route.request().url()).hostname === '127.0.0.1' ? route.continue() : route.abort());
 const page=await context.newPage(); const errors=[]; page.on('pageerror',e=>errors.push(e.message));
 await page.goto(fixture.url+'index.php?page=dashboard');
 await page.locator('[data-theme-choice]').selectOption('light');
 check('Light theme changes and persists',await page.evaluate(()=>document.documentElement.dataset.theme==='light'&&localStorage.getItem('tr.hq.theme')==='light'));
 const out=path.join(__dirname,'../build/screenshots');fs.mkdirSync(out,{recursive:true});
 await page.screenshot({path:path.join(out,'desktop-light.png'),fullPage:true});
 await page.locator('[data-theme-choice]').selectOption('cherry');
 await page.reload();
 check('Cherry theme survives reload',await page.evaluate(()=>document.documentElement.dataset.theme==='cherry'));
 await page.screenshot({path:path.join(out,'desktop-cherry.png'),fullPage:true});
 await page.locator('[data-large-text]').check();
 check('Larger text preference works',await page.evaluate(()=>document.documentElement.dataset.largeText==='1'));
 await page.setViewportSize({width:375,height:850});
 for(const view of ['messages','commands','player&uuid='+fixture.uuid,'crates','permissions','diagnostics','staff-guide']){
  await page.goto(fixture.url+'index.php?page='+view);
  await page.waitForTimeout(100);
  const width=await page.evaluate(()=>({body:document.documentElement.scrollWidth,screen:innerWidth,overflow:Array.from(document.querySelectorAll('main *')).filter(e=>e.getBoundingClientRect().right>innerWidth+1).slice(0,5).map(e=>e.className)}));
  if(width.body>width.screen+1) console.log('OVERFLOW',view,JSON.stringify(width));
  check('375px layout without page overflow: '+view.split('&')[0],width.body<=width.screen+1);
  if(view==='messages')check('Mobile message cards keep column labels',await page.locator('.table-wrap td[data-label="Message"]').count()>0);
  if(view.startsWith('player'))await page.screenshot({path:path.join(out,'mobile-profile-cherry.png'),fullPage:true});
 }
 check('No browser JavaScript errors',errors.length===0);
 fs.writeFileSync(path.join(__dirname,'browser-results.json'),JSON.stringify({passed:checks.length,checks},null,2));
 await browser.close();
})().catch(error=>{console.error(error);process.exit(1);});

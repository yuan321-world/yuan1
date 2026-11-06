const {chromium,expect}=require('@playwright/test');
const fs=require('fs'),path=require('path');
(async()=>{
 const base=process.env.LISTENING_TEST_BASE,token=process.env.LISTENING_TEST_TOKEN,set=process.env.LISTENING_TEST_SET,version=process.env.LISTENING_TEST_VERSION;
 if(!/^http:\/\/127\.0\.0\.1:\d+$/.test(base||'')||!token)throw Error('Isolated local backend required');
 const out=path.resolve(__dirname,'../../../.tool-cache/practice-listening-implementation');fs.mkdirSync(out,{recursive:true});
 const browser=await chromium.launch();const reports=[];const context=await browser.newContext();await context.addCookies(JSON.parse(process.env.LISTENING_TEST_COOKIES));
 for(const width of [1440,768,390,320]){
  const page=await context.newPage();await page.setViewportSize({width,height:1000});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/api/v1/**',async route=>{const url=new URL(route.request().url());await route.fulfill({response:await route.fetch({url:base+url.pathname+url.search})})});
  const csrf=await (await context.request.get(base+'/api/v1/auth/csrf')).json();
  const response=await context.request.post(base+'/api/v1/practice/sets/'+set+'/attempts',{headers:{Authorization:'Bearer '+token,Origin:'http://127.0.0.1:4174',[csrf.headerName]:csrf.token},data:{setVersionId:version,requestId:crypto.randomUUID()}});
  if(!response.ok())throw Error('Start failed '+response.status());const attempt=await response.json();
  await page.goto('http://127.0.0.1:4174/practice/attempts/'+attempt.id);
  const audio=page.getByLabel('整套听力音频');await audio.waitFor();await expect.poll(()=>audio.evaluate(e=>e.readyState)).toBeGreaterThanOrEqual(1);
  expect(await audio.evaluate(e=>e.paused)).toBe(true);await audio.evaluate(e=>e.play());await expect.poll(()=>audio.evaluate(e=>e.currentTime)).toBeGreaterThan(0.1);await audio.evaluate(e=>e.pause());
  await page.getByRole('button',{name:'前进5秒',exact:true}).click();await page.getByLabel('听力倍速').selectOption('1.5');
  await page.locator('[data-question-id="'+attempt.questions[0].id+'"]').getByLabel('B. Second',{exact:true}).check();await expect(page.getByRole('status').filter({hasText:'选择已保存'})).toBeVisible();
  await page.reload();await audio.waitFor();await expect.poll(()=>audio.evaluate(e=>e.readyState)).toBeGreaterThanOrEqual(1);expect(await audio.evaluate(e=>e.paused)).toBe(true);expect(await audio.evaluate(e=>e.currentTime)).toBeGreaterThan(4);await expect(page.locator('[data-question-id="'+attempt.questions[0].id+'"]').getByLabel('B. Second',{exact:true})).toBeChecked();
  for(let i=0;i<attempt.questions.length;i++){await page.getByRole('button',{name:'第'+(i+1)+'题',exact:true}).click();await page.locator('[data-question-id="'+attempt.questions[i].id+'"]').getByLabel('A. First',{exact:true}).check()}
  await page.getByRole('button',{name:'整组交卷',exact:true}).click();await page.getByRole('button',{name:'确认交卷',exact:true}).click();await expect(page.getByText('本轮已交卷，成绩已保存',{exact:true})).toBeVisible();await expect(page.locator('summary').filter({hasText:'配套原文'})).toBeVisible();await expect(page.getByRole('dialog')).toBeHidden();await page.screenshot({path:path.join(out,'real-result-'+width+'.png'),fullPage:true});
  if(!await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))throw Error('Overflow '+width);if(errors.length)throw Error(errors.join(';'));reports.push({width,playback:true,seek:true,resumePaused:true,submit:true,errors:0});await page.close();
 }
 await context.close();await browser.close();fs.writeFileSync(path.join(out,'real-browser-results.json'),JSON.stringify(reports,null,2));console.log(JSON.stringify(reports));
})().catch(e=>{console.error(e.message);process.exit(1)});

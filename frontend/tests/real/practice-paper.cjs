const {chromium,expect}=require('@playwright/test');
const fs=require('fs'),path=require('path');
(async()=>{
 const base=process.env.PAPER_TEST_BASE,token=process.env.PAPER_TEST_TOKEN,id=process.env.PAPER_TEST_ID;
 if(!/^http:\/\/127\.0\.0\.1:\d+$/.test(base||'')||!token||!/^\d+$/.test(id||''))throw Error('Dedicated test backend required');
 const out=path.resolve(__dirname,'../../../.tool-cache/practice-paper-implementation');
 const browser=await chromium.launch(),context=await browser.newContext();
 await context.addCookies(JSON.parse(process.env.PAPER_TEST_COOKIES));
 const results=[];
 for(const width of [1440,320]){
  const page=await context.newPage();await page.setViewportSize({width,height:1000});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/api/v1/**',async r=>{const u=new URL(r.request().url());await r.fulfill({response:await r.fetch({url:base+u.pathname+u.search})})});
  const part=label=>page.getByRole('button',{name:label,exact:true}).first().click();
  await page.goto('http://127.0.0.1:4174/practice/cet?source=PAST&collection=PAST&section=paper&exam=CET4&year=2024&month=6');
  await page.getByRole('button',{name:'开始练习',exact:true}).click();
  await expect(page).toHaveURL(/paper-attempts\/\d+/);
  const attemptPath=new URL(page.url()).pathname;
  const writing='A quiet room helps students concentrate.',translation='The community organizes weekend walks.';
  await page.getByLabel('英文作文',{exact:true}).fill(writing);
  await part('听力');await expect(page.locator('audio')).toHaveCount(1);
  const audio=await page.locator('audio').elementHandle();
  await expect(page.locator('.listening-question')).toHaveCount(25);
  for(const q of await page.locator('.listening-question').all())await q.locator('input[type=radio]').first().check();
  await part('阅读');await part('选词填空');
  for(let i=0;i<10;i++){
   await page.getByRole('button',{name:new RegExp('^第'+(26+i)+'空，')}).click();
   await page.getByRole('button',{name:new RegExp('^'+String.fromCharCode(65+i)+'，')}).click();
  }
  await part('长篇匹配');
  const questionTab=page.locator('.practice-match-mobile-tabs').getByRole('button',{name:'题目与匹配',exact:true});if(await questionTab.isVisible())await questionTab.click();
  await expect(page.locator('.practice-match-question-nav button')).toHaveCount(10);
  for(let i=0;i<10;i++){
   await page.locator('.practice-match-question-nav button').nth(i).click();
   await page.getByRole('button',{name:'匹配A段',exact:true}).click();
  }
  for(const label of ['仔细阅读 · 第一篇','仔细阅读 · 第二篇']){
   await part(label);await expect(page.locator('.paper-question')).toHaveCount(5);
   for(const q of await page.locator('.paper-question').all())await q.locator('input[type=radio]').first().check();
  }
  await part('翻译');await page.getByLabel('英文译文',{exact:true}).fill(translation);
  await part('听力');expect(await page.locator('audio').evaluate((node,prior)=>node===prior,audio)).toBe(true);
  await part('翻译');await page.getByRole('button',{name:'保存草稿',exact:true}).click();
  await expect(page.getByRole('status').filter({hasText:'草稿已保存'})).toBeVisible();
  const reloadResponse=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1'+attemptPath&&r.request().method()==='GET');
  await page.reload();const restored=await (await reloadResponse).json();
  expect(restored.paperId).toBe(id);
  expect(restored.responses.reduce((n,r)=>n+Object.keys(r.choices).length,0)).toBe(55);
  expect(restored.responses.find(r=>r.slot==='WRITING').text).toBe(writing);
  expect(restored.responses.find(r=>r.slot==='TRANSLATION').text).toBe(translation);
  expect(JSON.stringify(restored)).not.toMatch(/SECRET|correctOption|"reference"/);
  await expect(page.getByLabel('英文译文',{exact:true})).toHaveValue(translation);
  await part('听力');await expect(page.locator('.listening-question input[type=radio]:checked')).toHaveCount(25);
  await page.getByRole('button',{name:'交卷并查看结果',exact:true}).click();
  const confirm=page.locator('dialog[open]');await expect(confirm).not.toContainText('未答');
  await confirm.getByRole('button',{name:'返回检查',exact:true}).click();
  await page.getByRole('button',{name:'交卷并查看结果',exact:true}).click();
  const submittedResponse=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1'+attemptPath+'/submit');
  await page.getByRole('button',{name:'确认交卷',exact:true}).click();
  const submitted=await (await submittedResponse).json();expect(submitted.status).toBe('COMPLETED');
  expect(submitted.components.reduce((n,c)=>n+(c.objective?.correctCount||0),0)).toBe(55);
  await expect(page.getByRole('heading',{name:'本次整卷已完成'})).toBeVisible();
  await page.getByRole('button',{name:'查看作答与参考',exact:true}).click();
  await part('写作');await expect(page.getByLabel('英文作文',{exact:true})).toHaveAttribute('readonly','');
  if(!await page.locator('.paper-reference').evaluate(e=>e.open))await page.getByText('参考范文',{exact:true}).click();await expect(page.getByText('SECRET reference text',{exact:true})).toBeVisible();
  await part('翻译');await expect(page.getByLabel('英文译文',{exact:true})).toHaveAttribute('readonly','');
  if(!await page.locator('.paper-reference').evaluate(e=>e.open))await page.getByText('参考译文',{exact:true}).click();await expect(page.getByText('SECRET reference text',{exact:true})).toBeVisible();
  await part('听力');await expect(page.locator('.listening-question input[type=radio]').first()).toBeDisabled();
  await expect(page.getByText('SECRET explanation',{exact:true}).first()).toBeVisible();
  await expect(page.getByText('配套原文',{exact:true})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await page.screenshot({path:path.join(out,'real-paper-review-'+width+'.png'),fullPage:false});
  await page.getByRole('link',{name:'← 返回整卷列表',exact:true}).click();
  await page.getByRole('tab',{name:'我的练习',exact:true}).click();
  await expect(page.getByRole('link',{name:'查看作答与参考',exact:true}).first()).toBeVisible();
  expect(errors).toEqual([]);
  results.push({width,parts:7,objectiveAnswered:55,objectiveCorrect:55,start:true,save:true,reload:true,audioPreserved:true,submit:true,readonly:true,references:true,history:true,pageErrors:0});
  fs.writeFileSync(path.join(out,'real-results.json'),JSON.stringify(results,null,2));
  console.log(JSON.stringify(results.at(-1)));
  await page.close();
 }
 await context.close();await browser.close();
 fs.writeFileSync(path.join(out,'real-results.json'),JSON.stringify(results,null,2));console.log(JSON.stringify(results));
})().catch(e=>{console.error(e.stack);process.exit(1)});

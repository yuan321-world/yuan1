const {chromium,expect}=require('@playwright/test');
const fs=require('fs'),path=require('path');
(async()=>{
 const base=process.env.PAPER_TEST_BASE,token=process.env.PAPER_TEST_TOKEN,id=process.env.PAPER_TEST_ID;
 if(!/^http:\/\/127\.0\.0\.1:\d+$/.test(base||'')||!token||!/^\d+$/.test(id||''))throw Error('Dedicated test backend required');
 const bundle=JSON.parse(fs.readFileSync(process.env.PAPER_SOURCE_BUNDLE,'utf8')),sets=JSON.parse(process.env.PAPER_SOURCE_SETS),out=process.env.PAPER_SOURCE_OUTPUT;
 const slots=['LISTENING','CLOZE','MATCH','DETAILED_1','DETAILED_2','WRITING','TRANSLATION'];
 const sources=Object.fromEntries(slots.map((slot,i)=>[slot,bundle[i]]));
 const browser=await chromium.launch(),context=await browser.newContext();await context.addCookies(JSON.parse(process.env.PAPER_TEST_COOKIES));
 const results=[];
 async function api(method,url,data){
  const headers={Authorization:'Bearer '+token,Origin:'http://127.0.0.1:4174'};
  if(method!=='GET'){const c=await(await context.request.get(base+'/api/v1/auth/csrf')).json();headers[c.headerName]=c.token}
  const r=await context.request.fetch(base+'/api/v1'+url,{method,headers,data});expect(r.ok(),method+' '+url+' '+r.status()).toBe(true);return r.json();
 }
 for(const width of [1440,320]){
  const page=await context.newPage(),errors=[];await page.setViewportSize({width,height:1000});expect(page.viewportSize().width).toBe(width);page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/api/v1/**',async r=>{const u=new URL(r.request().url());await r.fulfill({response:await r.fetch({url:base+u.pathname+u.search})})});
  const part=label=>page.getByRole('button',{name:label,exact:true}).first().click();
  const metadata=sources.WRITING.metadata;
  const library=`/practice/cet?source=PAST&collection=PAST&section=paper&exam=CET4&year=${metadata.year}&month=${metadata.month}`;
  await page.goto('http://127.0.0.1:4174'+library);await page.getByRole('button',{name:'开始练习',exact:true}).click();
  await expect(page).toHaveURL(/paper-attempts\/\d+/);const attemptPath=new URL(page.url()).pathname,attempt=await api('GET',attemptPath);
  expect(attempt.paperId).toBe(id);expect(attempt.components).toHaveLength(7);
  const wrongLetter=letter=>letter==='A'?'B':'A';
  const chooseFor=(slot,i)=>['LISTENING','DETAILED_1','DETAILED_2'].includes(slot)&&i===0?wrongLetter(sources[slot].questions[i].correctOption):sources[slot].questions[i].correctOption;
  const writing='A quiet study room helps students concentrate. '+sources.WRITING.subjective.reference;
  const translation=sources.TRANSLATION.subjective.reference;
  await page.getByLabel('英文作文',{exact:true}).fill(writing);
  await part('听力');const full=page.getByLabel('整套听力音频',{exact:true});await expect.poll(()=>full.evaluate(a=>a.readyState)).toBeGreaterThanOrEqual(1);
  const originalAudio=await full.elementHandle();
  await expect(page.locator('.listening-question')).toHaveCount(sources.LISTENING.listening.groups[0].questionKeys.length);
  const clip=page.locator('.listening-clip-audio');await expect(clip).toBeVisible();const slider=clip.getByRole('slider');
  await expect(slider).toBeEnabled();await expect(slider).toHaveValue('0');
  await clip.getByRole('button').click();await expect.poll(async()=>Number(await slider.inputValue())).toBeGreaterThan(0.1);
  await expect.poll(()=>full.evaluate(a=>a.paused)).toBe(true);await clip.getByRole('button').click();
  const clipDuration=Number(await slider.getAttribute('max'));expect(clipDuration).toBeGreaterThan(5);
  await slider.evaluate((el,value)=>{el.value=String(value);el.dispatchEvent(new Event('input',{bubbles:true}))},clipDuration-0.3);await clip.getByRole('button').click();
  await expect.poll(()=>clip.locator('audio').evaluate(a=>a.paused),{timeout:5000}).toBe(true);
  await full.evaluate(a=>a.play());await expect.poll(()=>full.evaluate(a=>a.currentTime)).toBeGreaterThan(0.1);await full.evaluate(a=>a.pause());
  for(let i=0;i<25;i++){
   await page.locator('.listening-number-panel').getByRole('button',{name:'第'+(i+1)+'题',exact:true}).click();
   const q=attempt.components.find(c=>c.slot==='LISTENING').objective.questions[i];
   await page.locator('[data-question-id="'+q.id+'"]').locator('input[type=radio]').nth(chooseFor('LISTENING',i).charCodeAt(0)-65).check();
  }
  await part('阅读');await part('选词填空');
  for(let i=1;i<10;i++){
   await page.getByRole('button',{name:new RegExp('^第'+(26+i)+'空，')}).click();
   await page.getByRole('button',{name:new RegExp('^'+chooseFor('CLOZE',i)+'，')}).click();
  }
  await part('长篇匹配');const mobile=page.locator('.practice-match-mobile-tabs').getByRole('button',{name:'题目与匹配',exact:true});if(await mobile.isVisible())await mobile.click();
  for(let i=1;i<10;i++){await page.locator('.practice-match-question-nav button').nth(i).click();await page.getByRole('button',{name:'匹配'+chooseFor('MATCH',i)+'段',exact:true}).click()}
  for(const [slot,label] of [['DETAILED_1','仔细阅读 · 第一篇'],['DETAILED_2','仔细阅读 · 第二篇']]){
   await part(label);const qs=await page.locator('.paper-question').all();expect(qs).toHaveLength(5);
   for(let i=0;i<5;i++)await qs[i].locator('input[type=radio]').nth(chooseFor(slot,i).charCodeAt(0)-65).check();
  }
  await part('翻译');await page.getByLabel('英文译文',{exact:true}).fill(translation);
  await part('听力');expect(await full.evaluate((node,old)=>node===old,originalAudio)).toBe(true);
  await part('翻译');await page.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(page.getByRole('status').filter({hasText:'草稿已保存'})).toBeVisible();
  await page.reload();await expect(page.getByLabel('英文译文',{exact:true})).toHaveValue(translation);
  const restored=await api('GET',attemptPath);expect(restored.responses.find(r=>r.slot==='WRITING').text).toBe(writing);expect(restored.responses.reduce((n,r)=>n+Object.keys(r.choices).length,0)).toBe(53);
  expect(JSON.stringify(restored)).not.toMatch(/"correctOption"|"explanation"|"reference"/);
  await page.getByRole('button',{name:'交卷并查看结果',exact:true}).click();await page.locator('dialog[open]').getByRole('button',{name:'返回检查',exact:true}).click();
  await page.getByRole('button',{name:'交卷并查看结果',exact:true}).click();await page.getByRole('button',{name:'确认交卷',exact:true}).click();
  await expect(page.getByRole('heading',{name:'本次整卷已完成',exact:true})).toBeVisible();
  const done=await api('GET',attemptPath);expect(done.status).toBe('COMPLETED');expect(done.components.reduce((n,c)=>n+(c.objective?.correctCount||0),0)).toBe(50);
  for(const c of done.components)if(c.objective){for(let i=0;i<c.objective.answers.length;i++)expect(c.objective.answers[i].explanation).toBe(sources[c.slot].questions[i].explanation)}
  expect(done.components.find(c=>c.slot==='LISTENING').objective.answers[0].correct).toBe(false);
  await page.getByRole('button',{name:'查看作答与参考',exact:true}).click();await part('听力');
  await expect(page.locator('.listening-question input[type=radio]').first()).toBeDisabled();
  await expect(page.locator('.listening-clip-panel')).toContainText('【关键英文');await expect(page.locator('.listening-clip-panel')).toContainText('【中文释义】');
  await part('阅读');await part('选词填空');await expect(page.locator('.practice-cloze-results')).toContainText('补全示例');
  await page.screenshot({path:path.join(out,'source-cloze-'+width+'.png')});
  await part('仔细阅读 · 第一篇');await expect(page.locator('.paper-explanation').first()).toHaveCSS('white-space','pre-line');
  for(const [label,field] of [['写作','英文作文'],['翻译','英文译文']]){await part(label);await expect(page.getByLabel(field,{exact:true})).toHaveAttribute('readonly','');await page.locator('.paper-reference').evaluate(d=>d.open=true);await expect(page.locator('.paper-reference')).toContainText(sources[label==='写作'?'WRITING':'TRANSLATION'].subjective.reference)}
  await page.goto('http://127.0.0.1:4174'+attemptPath);await page.getByRole('button',{name:'复习本卷错题',exact:true}).click();
  await expect(page).toHaveURL(/tab=mistakes/);
  const mistakeLibrary=new URL(page.url()).pathname+new URL(page.url()).search;
  await expect(page.locator('.paper-mistake-card')).toHaveCount(5);
  await page.locator('.paper-mistake-card').filter({has:page.getByRole('heading',{name:'第51题 · 仔细阅读',exact:true})}).getByRole('button',{name:'查看原卷',exact:true}).click();
  await expect(page.locator('[data-question-id="'+done.components.find(c=>c.slot==='DETAILED_2').objective.questions[0].id+'"]').getByRole('heading')).toContainText('51.');
  await expect(page.locator('.paper-question input[type=radio]').first()).toBeDisabled();
  await page.goto('http://127.0.0.1:4174'+mistakeLibrary);
  const numbers={LISTENING:1,CLOZE:26,MATCH:36,DETAILED_1:46,DETAILED_2:51};
  for(const slot of ['LISTENING','CLOZE','MATCH','DETAILED_1','DETAILED_2']){
   const card=page.locator('.paper-mistake-card').filter({has:page.getByRole('heading',{name:new RegExp('^第'+numbers[slot]+'题 ·')})});
   await card.getByRole('button',{name:/^重/}).click();await expect(page).toHaveURL(/practice\/attempts\/\d+/);
   const reviewPath=new URL(page.url()).pathname,review=await api('GET',reviewPath);
   expect(review.total).toBe(slot==='LISTENING'?25:['CLOZE','MATCH'].includes(slot)?10:1);
   expect(review.reviewSource.slot).toBe(slot);expect(JSON.stringify(review)).not.toMatch(/"correctOption"|"explanation"/);
   if(slot==='LISTENING'){
    for(let i=0;i<25;i++){
     await page.locator('.listening-number-panel').getByRole('button',{name:'第'+(i+1)+'题',exact:true}).click();
     await page.locator('[data-question-id="'+review.questions[i].id+'"]').getByRole('radio').nth(sources[slot].questions[i].correctOption.charCodeAt(0)-65).check();
    }
   }else if(slot==='CLOZE'){
    for(let i=0;i<10;i++){await page.getByRole('button',{name:new RegExp('^第'+(26+i)+'空，')}).click();await page.getByRole('button',{name:new RegExp('^'+sources[slot].questions[i].correctOption+'，')}).click()}
    const closeBank=page.getByRole('button',{name:'关闭词库',exact:true});if(await closeBank.isVisible())await closeBank.click();
   }else if(slot==='MATCH'){
    const tab=page.locator('.practice-match-mobile-tabs').getByRole('button',{name:'题目与匹配',exact:true});if(await tab.isVisible())await tab.click();
    for(let i=0;i<10;i++){await page.locator('.practice-match-question-nav button').nth(i).click();await page.getByRole('button',{name:'匹配'+sources[slot].questions[i].correctOption+'段',exact:true}).click()}
   }else{
    await expect(page.locator('.practice-answer-panel h3')).toHaveText(new RegExp('^'+numbers[slot]+'\\. '));
    await page.locator('.practice-answer-panel').getByRole('radio').nth(sources[slot].questions[0].correctOption.charCodeAt(0)-65).check();
   }
   await expect.poll(async()=>{const saved=await api('GET',reviewPath);return saved.answers.filter(a=>a.draftChoice).length}).toBe(review.total);
   await page.reload();const saved=await api('GET',reviewPath);expect(saved.answers.filter(a=>a.draftChoice)).toHaveLength(review.total);
   await page.getByRole('button',{name:'整组交卷',exact:true}).filter({visible:true}).first().click();await page.getByRole('button',{name:'确认交卷',exact:true}).click();
   await expect.poll(async()=>{const r=await api('GET',reviewPath);return r.status==='COMPLETED'?r.correctCount:-1}).toBe(review.total);
   await page.getByRole('button',{name:'返回题库',exact:true}).click();
  }
  await expect(page.locator('.paper-mistake-card')).toHaveCount(0);
  await page.getByLabel('整卷错题状态',{exact:true}).selectOption('RESOLVED');await expect(page.locator('.paper-mistake-card')).toHaveCount(5);
  const resolved=await api('GET','/practice/paper-mistakes?sourceAttemptId='+done.id+'&status=RESOLVED');expect(resolved.total).toBe(5);
  expect(resolved.items.every(r=>r.wrongCount===(width===1440?1:2))).toBe(true);
  expect(await api('GET',attemptPath)).toEqual(done);expect((await api('GET','/practice/mistakes?section=all')).total).toBe(0);
  await expect(page.locator('.practice-mistake-explanation').first()).toHaveCSS('white-space','pre-line');
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect(errors).toEqual([]);
  await page.screenshot({path:path.join(out,'paper-mistakes-'+width+'.png')});
  results.push({width,sourcePaper:true,parts:7,answered:53,correct:50,explanationsMatched:55,clipPlayback:true,clipStopsAtEnd:true,fullAudioRetained:true,savedAndRestored:true,sourceJump:true,reviewRanges:[25,10,10,1,1],mistakesResolved:5,originalUnchanged:true,singleLedgerEmpty:true,pageErrors:0});
  fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(results,null,2));await page.close();
 }
 await context.close();await browser.close();console.log(JSON.stringify(results));
})().catch(e=>{console.error(e.stack);process.exit(1)});

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
  const chooseFor=(slot,i)=>slot==='LISTENING'&&i===0?wrongLetter(sources[slot].questions[i].correctOption):sources[slot].questions[i].correctOption;
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
  for(let i=0;i<10;i++){
   await page.getByRole('button',{name:new RegExp('^第'+(26+i)+'空，')}).click();
   await page.getByRole('button',{name:new RegExp('^'+chooseFor('CLOZE',i)+'，')}).click();
  }
  await part('长篇匹配');const mobile=page.locator('.practice-match-mobile-tabs').getByRole('button',{name:'题目与匹配',exact:true});if(await mobile.isVisible())await mobile.click();
  for(let i=0;i<10;i++){await page.locator('.practice-match-question-nav button').nth(i).click();await page.getByRole('button',{name:'匹配'+chooseFor('MATCH',i)+'段',exact:true}).click()}
  for(const [slot,label] of [['DETAILED_1','仔细阅读 · 第一篇'],['DETAILED_2','仔细阅读 · 第二篇']]){
   await part(label);const qs=await page.locator('.paper-question').all();expect(qs).toHaveLength(5);
   for(let i=0;i<5;i++)await qs[i].locator('input[type=radio]').nth(chooseFor(slot,i).charCodeAt(0)-65).check();
  }
  await part('翻译');await page.getByLabel('英文译文',{exact:true}).fill(translation);
  await part('听力');expect(await full.evaluate((node,old)=>node===old,originalAudio)).toBe(true);
  await part('翻译');await page.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(page.getByRole('status').filter({hasText:'草稿已保存'})).toBeVisible();
  await page.reload();await expect(page.getByLabel('英文译文',{exact:true})).toHaveValue(translation);
  const restored=await api('GET',attemptPath);expect(restored.responses.find(r=>r.slot==='WRITING').text).toBe(writing);expect(restored.responses.reduce((n,r)=>n+Object.keys(r.choices).length,0)).toBe(55);
  expect(JSON.stringify(restored)).not.toMatch(/"correctOption"|"explanation"|"reference"/);
  await page.getByRole('button',{name:'交卷并查看结果',exact:true}).click();await page.locator('dialog[open]').getByRole('button',{name:'返回检查',exact:true}).click();
  await page.getByRole('button',{name:'交卷并查看结果',exact:true}).click();await page.getByRole('button',{name:'确认交卷',exact:true}).click();
  await expect(page.getByRole('heading',{name:'本次整卷已完成',exact:true})).toBeVisible();
  const done=await api('GET',attemptPath);expect(done.status).toBe('COMPLETED');expect(done.components.reduce((n,c)=>n+(c.objective?.correctCount||0),0)).toBe(54);
  for(const c of done.components)if(c.objective){for(let i=0;i<c.objective.answers.length;i++)expect(c.objective.answers[i].explanation).toBe(sources[c.slot].questions[i].explanation)}
  expect(done.components.find(c=>c.slot==='LISTENING').objective.answers[0].correct).toBe(false);
  await page.getByRole('button',{name:'查看作答与参考',exact:true}).click();await part('听力');
  await expect(page.locator('.listening-question input[type=radio]').first()).toBeDisabled();
  await expect(page.locator('.listening-clip-panel')).toContainText('【关键英文');await expect(page.locator('.listening-clip-panel')).toContainText('【中文释义】');
  await part('阅读');await part('选词填空');await expect(page.locator('.practice-cloze-results')).toContainText('补全示例');
  await page.screenshot({path:path.join(out,'source-cloze-'+width+'.png')});
  await part('仔细阅读 · 第一篇');await expect(page.locator('.paper-explanation').first()).toHaveCSS('white-space','pre-line');
  for(const [label,field] of [['写作','英文作文'],['翻译','英文译文']]){await part(label);await expect(page.getByLabel(field,{exact:true})).toHaveAttribute('readonly','');await page.locator('.paper-reference').evaluate(d=>d.open=true);await expect(page.locator('.paper-reference')).toContainText(sources[label==='写作'?'WRITING':'TRANSLATION'].subjective.reference)}
  await page.getByRole('link',{name:'← 返回整卷列表',exact:true}).click();await page.getByRole('tab',{name:'我的练习',exact:true}).click();await expect(page.getByRole('link',{name:'查看作答与参考',exact:true}).first()).toBeVisible();
  // Paper attempts remain independent of single-set attempts. Verify the existing mistake loop separately.
  const set=sets.DETAILED_1,detail=await api('GET','/practice/sets/'+set);
  const single=await api('POST','/practice/sets/'+set+'/attempts',{setVersionId:detail.setVersionId,requestId:crypto.randomUUID()});
  const readingLibrary=`/practice/cet?source=PAST&collection=PAST&section=reading&exam=CET4&year=${metadata.year}&month=${metadata.month}`;
  await page.goto('http://127.0.0.1:4174/practice/attempts/'+single.id+'?returnTo='+encodeURIComponent(readingLibrary));
  for(let i=0;i<5;i++){
   const key=sources.DETAILED_1.questions[i].correctOption,answer=i===0?wrongLetter(key):key;
   await page.locator('.practice-answer-panel').getByRole('radio').nth(answer.charCodeAt(0)-65).check();
   if(i<4)await page.locator('.practice-answer-panel').getByRole('button',{name:'下一题',exact:true}).click();
  }
  await page.getByRole('button',{name:'整组交卷',exact:true}).click();await page.getByRole('button',{name:'确认交卷',exact:true}).click();await expect(page.getByText(/正确 4 \/ 5/)).toBeVisible();
  await page.getByRole('button',{name:'返回题库',exact:true}).click();await page.getByRole('tab',{name:'错题本',exact:true}).click();
  await expect(page.locator('.practice-card').filter({hasText:'待订正'})).toHaveCount(1);
  await page.getByRole('button',{name:'重做该轮错题',exact:true}).click();await expect(page).toHaveURL(/practice\/attempts\/\d+/);
  await page.locator('.practice-answer-panel').getByRole('radio').nth(sources.DETAILED_1.questions[0].correctOption.charCodeAt(0)-65).check();
  await page.getByRole('button',{name:'整组交卷',exact:true}).click();await page.getByRole('button',{name:'确认交卷',exact:true}).click();await expect(page.getByText(/正确 1 \/ 1/)).toBeVisible();
  await page.getByRole('button',{name:'返回题库',exact:true}).click();await page.getByRole('tab',{name:'错题本',exact:true}).click();await page.getByLabel('错题状态').selectOption('RESOLVED');
  await expect(page.locator('.practice-card').filter({hasText:'已订正'})).toHaveCount(1);
  await expect(page.locator('.practice-mistake-explanation').first()).toHaveCSS('white-space','pre-line');
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect(errors).toEqual([]);
  await page.screenshot({path:path.join(out,'source-mistake-'+width+'.png')});
  results.push({width,sourcePaper:true,parts:7,answered:55,correct:54,explanationsMatched:55,clipPlayback:true,clipStopsAtEnd:true,fullAudioRetained:true,savedAndRestored:true,history:true,mistakeResolved:true,pageErrors:0});
  fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(results,null,2));await page.close();
 }
 await context.close();await browser.close();console.log(JSON.stringify(results));
})().catch(e=>{console.error(e.stack);process.exit(1)});

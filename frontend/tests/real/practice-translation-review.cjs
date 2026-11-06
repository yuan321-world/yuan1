const {chromium,expect}=require('@playwright/test');
const fs=require('fs'),path=require('path');
(async()=>{
 const base=process.env.AI_TEST_BASE,sources=JSON.parse(process.env.AI_TEST_SOURCES||'[]');
 const quality=process.env.AI_TEST_QUALITY==='true';
 if(!/^http:\/\/127\.0\.0\.1:\d+$/.test(base||'')||sources.length<1||sources.length>(quality?3:1))throw Error('Dedicated test backend and expected sources required');
 const replay=process.env.AI_TEST_REPLAY==='true';
 const out=path.resolve(__dirname,'../../../.tool-cache/20261026-ai-live/'+(replay?'translation-quality-replay':quality?(process.env.AI_TEST_COVERAGE==='true'?'translation-quality-v4':'translation-quality-v3'):'translation-calibration')),browser=await chromium.launch(),reports=[];
 let sessionCookies=JSON.parse(process.env.AI_TEST_COOKIES);
 try{
  for(const source of sources){const context=await browser.newContext();await context.addCookies(sessionCookies);
   for(const width of [1440,320]){
    const page=await context.newPage();await page.setViewportSize({width,height:1000});const errors=[];page.on('pageerror',e=>errors.push(e.message));let posts=0;
    await page.route('**/api/v1/**',async route=>{const request=route.request(),url=new URL(request.url());if(request.method()==='POST'&&url.pathname==='/api/v1/practice/subjective-reviews')posts++;await route.fulfill({response:await route.fetch({url:base+url.pathname+url.search})})});
    const enter=async()=>{await page.goto('http://127.0.0.1:4174/practice/'+(source.type==='PAPER'?'paper-attempts/':'subjective-attempts/')+source.id);if(source.type==='PAPER')await page.getByRole('button',{name:'查看作答与参考'}).click();};
    await enter();const panel=source.type==='PAPER'?page.locator('[data-slot=TRANSLATION]').getByTestId('ai-review-panel'):page.getByTestId('ai-review-panel');
    await expect(panel.getByRole('button',{name:'本次已批改'})).toBeDisabled();await expect(panel.locator('.review-summary')).toContainText(/\w/);await expect(panel.locator('.review-budget')).toContainText(replay?'预留已释放':'按返回用量结算');
    await panel.getByRole('button',{name:'当次原文'}).click();await expect(panel.locator('.review-fulltext')).toHaveText(source.text);await expect(page.getByLabel('英文译文',{exact:true})).toHaveValue(source.text);await expect(page.getByLabel('英文译文',{exact:true})).toHaveAttribute('readonly','');
    await enter();await expect(panel.getByRole('button',{name:'本次已批改'})).toBeDisabled();await panel.getByRole('button',{name:'参考修改稿'}).click();await expect(panel.locator('.review-fulltext')).not.toBeEmpty();
    expect(posts).toBe(0);expect(errors).toEqual([]);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await panel.scrollIntoViewIfNeeded();await page.screenshot({path:path.join(out,source.type+'-'+source.id+'-'+width+'.png'),fullPage:false});reports.push({source:source.type,id:source.id,width,reload:true,originalFrozen:true,revised:true,budget:true,duplicateExternalCalls:0,errors:0});await page.close();
   }sessionCookies=await context.cookies();await context.close();
  }
 }finally{await browser.close();fs.writeFileSync(path.join(out,'browser-results.json'),JSON.stringify(reports,null,2));}
 console.log(JSON.stringify(reports));
})().catch(e=>{console.error(e.message);process.exit(1)});

const {chromium,expect}=require('@playwright/test');
const fs=require('fs'),path=require('path');
(async()=>{
 const base=process.env.AI_TEST_BASE,sources=JSON.parse(process.env.AI_TEST_SOURCES||'[]');
 if(!/^http:\/\/127\.0\.0\.1:\d+$/.test(base||''))throw Error('Dedicated local test backend required');
 const browser=await chromium.launch(),reports=[];const out=path.resolve(__dirname,'../../../.tool-cache/20261026-ai-live');
 try{for(const source of sources){const context=await browser.newContext();await context.addCookies(JSON.parse(process.env['AI_TEST_COOKIES_'+source.type]));
  for(const width of [1440,320]){const page=await context.newPage();await page.setViewportSize({width,height:1000});const errors=[];page.on('pageerror',e=>errors.push(e.message));
   await page.route('**/api/v1/**',async route=>{const url=new URL(route.request().url());await route.fulfill({response:await route.fetch({url:base+url.pathname+url.search})})});
   await page.goto('http://127.0.0.1:4174/practice/subjective-attempts/'+source.id);
   const panel=page.getByTestId('ai-review-panel');await expect(panel.getByRole('button',{name:'本次已批改'})).toBeDisabled();await expect(panel.locator('.review-score strong')).toContainText(/\d+/);await expect(panel.locator('.review-summary')).not.toBeEmpty();
   await panel.getByRole('button',{name:'当次原文'}).click();await expect(panel.locator('.review-fulltext')).toHaveText(source.text);await page.reload();await expect(panel.getByRole('button',{name:'本次已批改'})).toBeDisabled();await panel.getByRole('button',{name:'参考修改稿'}).click();await expect(panel.locator('.review-fulltext')).not.toBeEmpty();
   expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect(errors).toEqual([]);await page.screenshot({path:path.join(out,source.type+'-'+width+'.png'),fullPage:true});reports.push({type:source.type,width,privateHistory:true,original:true,reload:true,revised:true,errors:0});await page.close();
  }await context.close();}
 }finally{await browser.close()}
 fs.writeFileSync(path.join(out,'real-browser-results.json'),JSON.stringify(reports,null,2));console.log(JSON.stringify(reports));
})().catch(e=>{console.error(e.message);process.exit(1)});

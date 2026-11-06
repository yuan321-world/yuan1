const {chromium,expect}=require('@playwright/test');
const fs=require('fs'),path=require('path');
(async()=>{
 const base=process.env.SUBJECTIVE_TEST_BASE,token=process.env.SUBJECTIVE_TEST_TOKEN,set=process.env.SUBJECTIVE_TEST_SET,version=process.env.SUBJECTIVE_TEST_VERSION,type=process.env.SUBJECTIVE_TEST_TYPE;
 if(!/^http:\/\/127\.0\.0\.1:\d+$/.test(base||'')||!token)throw Error('Isolated local backend required');
 const out=path.resolve(__dirname,'../../../.tool-cache/practice-subjective-implementation');
 const browser=await chromium.launch();const reports=[];const context=await browser.newContext();await context.addCookies(JSON.parse(process.env.SUBJECTIVE_TEST_COOKIES));
 for(const width of [1440,320]){
  const page=await context.newPage();await page.setViewportSize({width,height:1000});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/api/v1/**',async route=>{const url=new URL(route.request().url());await route.fulfill({response:await route.fetch({url:base+url.pathname+url.search})})});
  const csrf=await (await context.request.get(base+'/api/v1/auth/csrf')).json();
  const response=await context.request.post(base+'/api/v1/practice/sets/'+set+'/subjective-attempts',{headers:{Authorization:'Bearer '+token,Origin:'http://127.0.0.1:4174',[csrf.headerName]:csrf.token},data:{setVersionId:version,requestId:crypto.randomUUID()}});
  if(!response.ok())throw Error('Start failed '+response.status());const a=await response.json();
  await page.goto('http://127.0.0.1:4174/practice/subjective-attempts/'+a.id);
  const input=page.getByLabel(type==='WRITING'?'英文作文':'英文译文',{exact:true});await input.waitFor();
  await expect(page.getByText('参考范文',{exact:true})).toHaveCount(0);await expect(page.getByText('参考译文',{exact:true})).toHaveCount(0);
  await input.fill('A quiet space supports study.');await page.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(page.getByRole('status').filter({hasText:'草稿已保存'})).toBeVisible();
  await page.reload();await expect(input).toHaveValue('A quiet space supports study.');await page.screenshot({path:path.join(out,'real-'+type+'-'+width+'.png'),fullPage:true});
  await page.getByRole('button',{name:'提交作答',exact:true}).click();await page.getByRole('button',{name:'确认提交',exact:true}).click();await expect(input).toHaveAttribute('readonly','');
  await page.getByText(type==='WRITING'?'参考范文':'参考译文',{exact:true}).click();await expect(page.getByText(/SECRET .*reference|SECRET REFERENCE frozen original/)).toBeVisible();
  await page.getByRole('button',{name:'继续修改（保留本次提交）',exact:true}).click();await expect(page).not.toHaveURL(new RegExp('subjective-attempts/'+a.id+'$'));await expect(input).toHaveValue('A quiet space supports study.');
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect(errors).toEqual([]);reports.push({width,type,save:true,reload:true,submit:true,reference:true,revise:true,errors:0});await page.close();
 }
 await context.close();await browser.close();fs.writeFileSync(path.join(out,'real-'+type+'-results.json'),JSON.stringify(reports,null,2));console.log(JSON.stringify(reports));
})().catch(e=>{console.error(e.message);process.exit(1)});

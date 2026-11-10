import {test,expect,type Page} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
const version={id:'11',articleId:'10',versionNo:1,title:'Small steps',titleZh:'循序渐进',summary:'Learning',category:{id:'1',code:'STUDY',name:'学习'},difficulty:'B1',wordCount:5,estimatedMinutes:1,author:'Example author',sourceType:'THIRD_PARTY',sourceUrl:'https://example.test/source',usageNote:'原作者与许可说明',content:{schemaVersion:1,paragraphs:[{key:'p0001',sentences:[{key:'p0001-s0001',text:'Small steps help us.'}]}]}}
const translation={available:true,source:'机器参考译文',paragraphs:[{key:'p0001',text:'小小的进步帮助我们。'}],sentences:[]}
async function setup(page:Page){
 await mockIdentity(page)
 await page.route('**/api/v1/reading/articles/10',r=>r.fulfill({json:{version,progress:null}}))
 await page.route('**/api/v1/reading/articles/10/start',r=>r.fulfill({json:{version,progress:{id:'1',articleId:'10',articleVersionId:'11',sentenceKey:'p0001-s0001',offset:0,status:'READING',lockVersion:0},created:false}}))
}
test('full article translation prepares once and is reused in bilingual reading',async({page})=>{
 await setup(page);let writes=0,reads=0
 await page.route('**/api/v1/reading/articles/10/versions/11/full-translation',r=>{
  if(r.request().method()==='POST'){writes++;return r.fulfill({json:{status:'QUEUED',total:1,completed:0,configured:true,error:null,translation:{...translation,available:false,paragraphs:[]}}})}
  reads++;return r.fulfill({json:{status:writes?'SUCCEEDED':'MISSING',total:1,completed:writes?1:0,configured:true,error:null,translation:writes?translation:{...translation,available:false,paragraphs:[]}}})
 })
 await page.goto('/reading/10');await page.locator('.reader-full-translation summary').click();await page.getByRole('button',{name:'准备全文翻译',exact:true}).click();await expect(page.locator('.reader-full-translation')).toContainText(translation.paragraphs[0]!.text);expect(writes).toBe(1)
 await page.getByRole('button',{name:'英中对照',exact:true}).click();await expect(page.locator('.article-body')).toContainText(translation.paragraphs[0]!.text)
 await page.reload();await page.locator('.reader-full-translation summary').click();await expect(page.locator('.reader-full-translation')).toContainText(translation.paragraphs[0]!.text);expect(writes).toBe(1);expect(reads).toBeGreaterThan(1)
 await expect(page.locator('.article-heading .reader-source-details')).toHaveCount(0);await expect(page.locator('.reader-attribution')).toContainText('原文链接');await expect(page.getByText('写作积累',{exact:true})).toHaveCount(0);await expect(page.locator('.article-writing-panel')).toHaveCount(0);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})
test('failed full translation keeps a clear retry without claiming completion',async({page})=>{
 await setup(page)
 await page.route('**/api/v1/reading/articles/10/versions/11/full-translation',r=>r.fulfill({json:{status:'FAILED',total:3,completed:1,configured:true,error:'翻译服务暂时不可用，请稍后重试。',translation:{...translation,available:false}}}))
 await page.goto('/reading/10');await page.locator('.reader-full-translation summary').click();await expect(page.locator('.reader-full-translation')).toContainText('1 / 3');await expect(page.getByRole('button',{name:'重试全文翻译',exact:true})).toBeVisible();await expect(page.locator('.reader-full-translation')).not.toContainText('译文已齐备')
})

test('cached translation survives slow tools and keyword refresh',async({page})=>{
 await setup(page)
 await page.route('**/api/v1/reading/articles/10/versions/11/tools',async r=>{await new Promise(resolve=>setTimeout(resolve,400));await r.fulfill({json:{related:[],previous:null,next:null,keywords:[],translation:{available:false,source:'',paragraphs:[]}}})})
 await page.route('**/api/v1/reading/articles/10/versions/11/full-translation',r=>r.fulfill({json:{status:'SUCCEEDED',total:1,completed:1,configured:true,error:null,translation}}))
 await page.goto('/reading/10');await expect(page.locator('.reader-full-translation summary')).toContainText('已齐备');await page.getByRole('button',{name:'英中对照',exact:true}).click();await expect(page.locator('.article-body')).toContainText(translation.paragraphs[0]!.text);await page.getByRole('button',{name:'重点词',exact:true}).click();await page.waitForTimeout(500);await expect(page.locator('.article-body')).toContainText(translation.paragraphs[0]!.text)
})

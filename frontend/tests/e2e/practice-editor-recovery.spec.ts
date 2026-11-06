import {test,expect,type Page} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
async function fixture(page:Page){
 await mockIdentity(page,false,true)
 const article={id:'10',articleId:'9',versionNo:1,title:'Original source v1',titleZh:'原文一',content:{schemaVersion:1,paragraphs:[{key:'p1',sentences:[{key:'s1',text:'We learn every day.'}]}]},category:{id:'1',code:'education',name:'教育'},wordCount:4,estimatedMinutes:1,sourceType:'ORIGINAL',usageNote:'原创',summary:'',difficulty:'B1',author:'',sourceUrl:null}
 const next={...article,id:'11',versionNo:2,title:'Corrected source v2'}
 const questions=[{key:'q1',type:'DETAIL',stem:'What helps?',options:['Steps','Waiting','Nothing','Stopping'],correctOption:'A',explanation:'坚持练习。',sortOrder:0}]
 let set:any={id:'21',articleId:'9',articleVersionId:'10',examCode:'CET4',readingType:'DETAILED',metadata:{title:'Saved practice',titleZh:'已保存题组',sourceKind:'ORIGINAL',topic:'education',sourceName:'原创',usageNote:'测试内容'},questions,reviewedQuestionKeys:['q1'],sourceReviewed:true,status:'DRAFT',revision:0,lockVersion:1,article,checks:[]}
 await page.route('**/api/v1/admin/articles?*',r=>r.fulfill({json:{items:[{id:'9',status:'PUBLISHED',draftVersionId:'11',publishedVersionId:'11',draft:next,lockVersion:1}],page:1,pageSize:50,total:1}}))
 await page.route('**/api/v1/admin/articles/9',r=>r.fulfill({json:{id:'9',status:'PUBLISHED',draftVersionId:'11',publishedVersionId:'11',draft:next,lockVersion:1}}))
 await page.route('**/api/v1/admin/articles/9/preview?*',r=>r.fulfill({json:new URL(r.request().url()).searchParams.get('versionId')==='11'?next:article}))
 await page.route('**/api/v1/admin/practice/sets',r=>{set={...set,...r.request().postDataJSON(),article:next,id:'21',lockVersion:1,reviewedQuestionKeys:[],sourceReviewed:false};return r.fulfill({json:set})})
 await page.route('**/api/v1/admin/practice/sets/21',r=>{if(r.request().method()==='PUT')set={...set,...r.request().postDataJSON(),article:next,lockVersion:set.lockVersion+1,reviewedQuestionKeys:[],sourceReviewed:false};return r.fulfill({json:set})})
}
test('new draft replaces the creation URL and refresh restores the saved set',async({page})=>{
 await fixture(page);await page.goto('/admin/practice/new');await page.getByRole('combobox',{name:'关联文章',exact:true}).selectOption('9');await expect(page.locator('.practice-reader')).toContainText('Corrected source v2');await page.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(page).toHaveURL(/\/admin\/practice\/21$/);await page.reload();await expect(page.getByRole('heading',{name:'编辑专项题组',exact:true})).toBeVisible();await expect(page.locator('.practice-reader')).toContainText('Corrected source v2')
})
test('existing set can explicitly choose a newer same-article version',async({page})=>{
 await fixture(page);await page.goto('/admin/practice/21');await page.locator('.practice-metadata summary').click();await page.getByRole('combobox',{name:'关联文章版本',exact:true}).selectOption('11');await expect(page.locator('.practice-reader')).toContainText('Corrected source v2');await expect(page.locator('.practice-current-question .practice-checkbox input')).not.toBeChecked();await page.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(page.getByRole('status')).toContainText('草稿已保存')
})
test('expired administrator credentials retain input and release the save button',async({page})=>{
 await fixture(page);await page.goto('/admin/practice/21');await expect(page.getByLabel('英文题干',{exact:true})).toHaveValue('What helps?');await page.route('**/api/v1/admin/practice/sets/21',r=>r.request().method()==='PUT'?r.fulfill({status:401,json:{code:'ACCESS_TOKEN_EXPIRED',message:'登录已过期'}}):r.continue());await page.route('**/api/v1/auth/refresh',r=>r.fulfill({status:401,json:{code:'UNAUTHENTICATED',message:'请重新登录'}}));await page.getByLabel('英文题干',{exact:true}).fill('Keep my local edit');await page.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(page.getByRole('button',{name:'保存草稿',exact:true})).toBeEnabled();await expect(page.getByRole('alert')).toContainText('登录');await expect(page.getByLabel('英文题干',{exact:true})).toHaveValue('Keep my local edit')
})

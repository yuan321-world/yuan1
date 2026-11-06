import { test, expect } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
const category={id:'1',code:'WORK',name:'职场'}
const version={id:'11',articleId:'10',versionNo:1,title:'Meeting with time',titleZh:'留出思考时间',summary:'A meeting.',difficulty:'B2',category,wordCount:8,estimatedMinutes:1,author:'Lingua Loop',sourceType:'ORIGINAL',sourceUrl:null,usageNote:'原创',content:{schemaVersion:1,paragraphs:[{key:'p0001',sentences:[{key:'p0001-s0001',text:'Instead of waiting, we can start.'}]}]}}
const content={expression:'Instead of …, we can …',meaningZh:'与其……，我们可以……',keyPhrases:['instead of'],structure:'Instead of + doing',exampleEn:'Instead of waiting, we can start.',exampleZh:'与其等待，不如开始。',scenarios:['议论文'],sortOrder:1}
test('conflict reload confirms before discarding local material edits',async({page})=>{
 await mockIdentity(page,false,true);await page.route('**/api/v1/admin/articles/10',r=>r.fulfill({json:{id:'10',status:'PUBLISHED',draftVersionId:'11',publishedVersionId:'11',lockVersion:1,draft:version}}))
 await page.route('**/api/v1/admin/articles/10/versions/11/writing-materials',r=>r.fulfill({json:[{id:'1',articleId:'10',articleVersionId:'11',draft:content,published:content,status:'PUBLISHED',revision:1,lockVersion:2}]}))
 await page.route('**/api/v1/admin/writing-materials/1',r=>r.fulfill({status:409,json:{code:'WRITING_CONFLICT',message:'素材已被更新，请重新加载后编辑'}}))
 await page.goto('/admin/articles/10/writing?version=11');await page.locator('.writing-admin-list button').nth(1).click();await page.locator('#writing-expression').fill('My local edits');await page.getByRole('button',{name:'保存素材草稿',exact:true}).click()
 await page.getByRole('button',{name:'重新加载最新素材',exact:true}).click();await page.getByRole('button',{name:'继续编辑',exact:true}).click();await expect(page.locator('#writing-expression')).toHaveValue('My local edits')
 await page.getByRole('button',{name:'重新加载最新素材',exact:true}).click();await page.getByRole('button',{name:'放弃修改',exact:true}).click();await expect(page.locator('#writing-expression')).toHaveValue('');await page.locator('.writing-admin-list button').nth(1).click();await expect(page.locator('#writing-expression')).toHaveValue(content.expression)
})
test('remote identity change clears historical material and closes its details',async({page})=>{
 await mockIdentity(page,false)
 const progress={id:'1',articleId:'10',articleVersionId:'11',sentenceKey:'p0001-s0001',offset:0,status:'READING',lockVersion:0}
 await page.route('**/api/v1/reading/articles/10/start',r=>r.fulfill({json:{version,progress,created:false}}))
 await page.route('**/api/v1/reading/articles/10/versions/11/writing-materials',r=>r.fulfill({json:[{id:'1',articleId:'10',articleVersionId:'11',content,revision:1}]}))
 await page.goto('/reading/10');const panel=page.locator('.article-writing-panel');if(test.info().project.name==='mobile')await panel.locator('summary').click();await panel.getByRole('button',{name:'查看解析',exact:true}).click();await expect(page.getByRole('dialog',{name:'写作积累'})).toBeVisible()
 await page.route('**/api/v1/auth/refresh',r=>r.fulfill({status:401,json:{code:'UNAUTHENTICATED'}}));await page.route('**/api/v1/auth/me',r=>r.fulfill({status:401,json:{code:'UNAUTHENTICATED'}}));await page.route('**/api/v1/reading/articles/10/versions/11/writing-materials',r=>r.fulfill({status:404,json:{code:'ARTICLE_NOT_FOUND',message:'文章不可访问'}}))
 await page.evaluate(()=>{const channel=new BroadcastChannel('lingua-loop-identity-v1');channel.postMessage('logout');channel.close()})
 await expect(page.getByRole('dialog',{name:'写作积累'})).not.toBeVisible();await expect(panel.getByRole('button',{name:'查看解析',exact:true})).toHaveCount(0)
})
test('administrator creates publishes unpublishes and archives material',async({page})=>{
 await mockIdentity(page,false,true)
 await page.route('**/api/v1/admin/articles/10',r=>r.fulfill({json:{id:'10',status:'PUBLISHED',draftVersionId:'11',publishedVersionId:'11',lockVersion:1,draft:version}}))
 let material:any=null
 await page.route('**/api/v1/admin/articles/10/versions/11/writing-materials',async r=>{if(r.request().method()==='POST'){material={id:'1',articleId:'10',articleVersionId:'11',draft:r.request().postDataJSON(),published:null,status:'DRAFT',revision:0,lockVersion:0};return r.fulfill({status:201,json:material})}return r.fulfill({json:material?[material]:[]})})
 await page.route('**/api/v1/admin/writing-materials/1**',r=>{if(r.request().method()==='DELETE'){material=null;return r.fulfill({status:204})}material={...material,status:r.request().url().endsWith('/unpublish')?'UNLISTED':'PUBLISHED',lockVersion:material.lockVersion+1,revision:material.revision+1,published:material.draft};return r.fulfill({json:material})})
 await page.goto('/admin/articles/10/writing?version=11')
 for(const field of ['expression','meaningZh','structure','exampleEn','exampleZh'] as const)await page.locator('#writing-'+field).fill(content[field])
 await page.locator('#writing-keyPhrases').fill('instead of');await page.locator('#writing-scenarios').fill('议论文')
 await page.getByRole('button',{name:'保存素材草稿',exact:true}).click();await expect(page.getByRole('button',{name:'发布素材',exact:true})).toBeEnabled()
 await page.getByRole('button',{name:'发布素材',exact:true}).click();await page.getByRole('button',{name:'确认',exact:true}).click();await expect(page.getByRole('button',{name:'下架素材',exact:true})).toBeVisible()
 await page.getByRole('button',{name:'下架素材',exact:true}).click();await page.getByRole('button',{name:'确认',exact:true}).click();await expect(page.getByRole('button',{name:'发布素材',exact:true})).toBeVisible()
 await page.getByRole('button',{name:'归档素材',exact:true}).click();await page.getByRole('button',{name:'确认',exact:true}).click();await expect(page.locator('.writing-admin-list')).toContainText('尚无素材')
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})
test('published writing keeps reading tools and opens accessible plain text details',async({page})=>{
 await mockIdentity(page,true)
 await page.route('**/api/v1/reading/articles/10',r=>r.fulfill({json:{version,progress:null}}))
 await page.route('**/api/v1/reading/articles/10/versions/11/writing-materials',r=>r.fulfill({json:[{id:'1',articleId:'10',articleVersionId:'11',content,revision:1}]}))
 await page.goto('/reading/10')
 const panel=page.locator('.article-writing-panel');await expect(panel).toBeVisible()
 if(test.info().project.name==='mobile')await panel.locator('summary').click()
 await panel.getByRole('button',{name:'查看解析'}).click()
 const dialog=page.getByRole('dialog',{name:'写作积累'});await expect(dialog).toContainText(content.exampleZh);await expect(dialog).toContainText('议论文');await page.keyboard.press('Escape')
 await expect(page.getByRole('button',{name:'阅读设置',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'重点词',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'本篇生词',exact:true})).toBeVisible()
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})
test('writing failure is local and retry restores materials',async({page})=>{
 await mockIdentity(page,true);await page.route('**/api/v1/reading/articles/10',r=>r.fulfill({json:{version,progress:null}}));let attempts=0
 await page.route('**/api/v1/reading/articles/10/versions/11/writing-materials',r=>++attempts===1?r.fulfill({status:503,json:{code:'UNAVAILABLE',message:'素材暂不可用'}}):r.fulfill({json:[]}))
 await page.goto('/reading/10');const panel=page.locator('.article-writing-panel');if(test.info().project.name==='mobile')await panel.locator('summary').click();await panel.getByRole('button',{name:'重试写作素材'}).click();await expect(panel).toContainText('暂无已发布写作素材');await expect(page.locator('.article-body')).toContainText('Instead of waiting')
})

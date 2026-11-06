import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
const slots=['WRITING','LISTENING','CLOZE','MATCH','DETAILED_1','DETAILED_2','TRANSLATION']
test('paper keeps writing and translation together and submits once with readonly references',async({page})=>{
 await mockIdentity(page)
 const cardAction=async(name:string)=>{if((page.viewportSize()?.width||0)<=900)await page.getByRole('button',{name:'查看全部题号',exact:true}).click();await page.locator('.paper-answer-card:visible').getByRole('button',{name,exact:true}).click()}
 let attempt:any={id:'81',paperId:'8',paperVersionId:'80',title:'整卷测试夹具',sourceNote:'Synthetic only',status:'IN_PROGRESS',lockVersion:0,currentSlot:'WRITING',currentQuestionId:null,responses:slots.map(slot=>({slot,text:'',choices:{}})),components:slots.map(slot=>({slot,type:slot,title:slot,text:'',wordCount:0,...(['WRITING','TRANSLATION'].includes(slot)?{prompt:{prompt:slot==='WRITING'?'Write about a study room.':'社区组织步行。',requirements:'Complete the task.',minWords:3,maxWords:180,minutes:30}}:{objective:{id:'paper-component',questions:[],answers:[],total:0,readingType:slot,status:'IN_PROGRESS'}})})),startedAt:new Date().toISOString(),completedAt:null}
 await page.route('**/api/v1/practice/paper-attempts/81',r=>r.fulfill({json:attempt}))
 await page.route('**/api/v1/practice/paper-attempts/81/draft',r=>{const d=r.request().postDataJSON();attempt={...attempt,...d,lockVersion:attempt.lockVersion+1};return r.fulfill({json:attempt})})
 await page.route('**/api/v1/practice/paper-attempts/81/submit',r=>{attempt={...attempt,...r.request().postDataJSON(),status:'COMPLETED',lockVersion:attempt.lockVersion+1,components:attempt.components.map((c:any)=>({...c,reference:'Frozen paper reference.',notes:'Compare your response.'}))};return r.fulfill({json:attempt})})
 await page.goto('/practice/paper-attempts/81');await page.getByLabel('英文作文',{exact:true}).fill('A quiet room supports study.');await cardAction('翻译');await page.getByLabel('英文译文',{exact:true}).fill('The community organizes walks.');await page.getByRole('button',{name:'保存草稿',exact:true}).click();await expect.poll(()=>attempt.responses[6].text).toContain('organizes');await page.reload();await expect(page.getByLabel('英文译文',{exact:true})).toHaveValue('The community organizes walks.');await cardAction('交卷并查看结果');await page.getByRole('button',{name:'返回检查',exact:true}).click();await cardAction('交卷并查看结果');await page.getByRole('button',{name:'确认交卷',exact:true}).click();await expect(page.getByRole('heading',{name:'本次整卷已完成'})).toBeVisible();await page.getByRole('button',{name:'查看作答与参考',exact:true}).click();await cardAction('写作');await expect(page.getByLabel('英文作文',{exact:true})).toHaveAttribute('readonly','');await page.getByText('参考范文',{exact:true}).click();await expect(page.locator('.paper-part[data-slot=WRITING]').getByText('Frozen paper reference.',{exact:true})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})
test('paper directory uses actual collapsed date facets with mistake tab',async({page},info)=>{
 await mockIdentity(page);await page.route('**/api/v1/practice/papers?*',r=>r.fulfill({json:{items:[],total:0,page:1,pageSize:20,years:[{value:'2024',count:1}],months:[{value:'6',count:1},{value:'12',count:1}]}}));await page.goto('/practice/cet?source=PAST&collection=PAST&section=paper&exam=CET4');await expect(page.getByRole('heading',{name:'大学英语（四六级）',exact:true})).toBeVisible();await expect(page.getByRole('tab',{name:'错题本'})).toHaveCount(1);if(info.project.name==='mobile')await page.getByRole('button',{name:'筛选题组',exact:true}).click();await expect(page.locator('.practice-simple-filters').filter({visible:true})).toHaveCount(1);const month=page.locator('.practice-simple-filters:visible details').filter({has:page.locator('summary').filter({hasText:'月份'})});await expect(month).not.toHaveAttribute('open','');await month.locator('summary').click();await expect(page.getByRole('button',{name:'月份：6月',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'月份：1月',exact:true})).toHaveCount(0)
})
test('paper directory changes page without resetting it to the first page',async({page})=>{
 await mockIdentity(page);await page.route('**/api/v1/practice/papers?*',r=>r.fulfill({json:{items:[],total:21,page:Number(new URL(r.request().url()).searchParams.get('page')||1),pageSize:20,years:[],months:[]}}));await page.goto('/practice/cet?source=PAST&collection=PAST&section=paper&exam=CET4');await page.getByRole('button',{name:'下一页',exact:true}).click();await expect(page).toHaveURL(/page=2/);await expect(page.getByText('第 2 / 2 页',{exact:true})).toBeVisible()
})
test('admin navigation exposes the paper library directly',async({page})=>{
 await mockIdentity(page,false,true);await page.route('**/api/v1/admin/practice/papers',r=>r.fulfill({json:[]}));await page.goto('/admin/practice/papers');const navigation=page.getByRole('navigation',{name:'管理导航',exact:true});await expect(navigation.getByRole('link',{name:'整卷管理',exact:true})).toBeVisible();await navigation.getByRole('link',{name:'整卷管理',exact:true}).click();await expect(page.getByRole('heading',{name:'整卷管理',exact:true})).toBeVisible()
})


test('switch to paper requests only its final query and keeps catalog positions during loading',async({page},info)=>{
 await mockIdentity(page)
 await page.route('**/api/v1/practice/sets?*',r=>r.fulfill({json:{items:[],total:0,page:1,pageSize:20}}))
 await page.route('**/api/v1/practice/facets?*',r=>r.fulfill({json:{source:[],readingType:[],topic:[],state:[],year:[],month:[],paperNo:[]}}))
 let release!:()=>void
 const held=new Promise<void>(resolve=>release=resolve)
 const requests:string[]=[]
 await page.route('**/api/v1/practice/papers?*',async r=>{requests.push(r.request().url());await held;await r.fulfill({json:{items:[],total:0,page:1,pageSize:20,years:[],months:[]}})})
 await page.goto('/practice/cet?exam=CET4&source=PAST&collection=PAST&section=listening')
 const root=page.locator('.practice-catalog-page')
 const before=await root.locator('> [role=tablist]').boundingBox()
 const summaryBefore=await root.locator('.practice-library-results>.practice-hint').boundingBox()
 await page.getByRole('navigation',{name:'练习题型',exact:true}).getByRole('button',{name:'整卷',exact:true}).click()
 await expect(page.locator('.practice-catalog-placeholder')).toHaveCount(3)
 expect(requests).toHaveLength(1)
 const after=await root.locator('> [role=tablist]').boundingBox()
 const summaryAfter=await root.locator('.paper-summary').boundingBox()
 expect(after!.y).toBe(before!.y)
 expect(summaryAfter!.y).toBe(summaryBefore!.y)
 if(info.project.name==='mobile'){
  await page.getByRole('button',{name:'筛选题组',exact:true}).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page.locator('dialog .practice-simple-filters')).toBeVisible()
  await page.getByRole('button',{name:'关闭',exact:true}).click()
 }
 release()
 await expect(page.locator('.practice-catalog-placeholder')).toHaveCount(0)
 expect(requests).toHaveLength(1)
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})


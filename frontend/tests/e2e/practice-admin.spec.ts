import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'

for(const tablet of [false,true])test.describe(tablet?'tablet':'standard',()=>{
 if(tablet)test.use({viewport:{width:768,height:1000}})
 test('practice editor preserves edits across navigation and has actionable overview checks',async({page})=>{
  await mockIdentity(page,false,true)
  const article={id:'10',articleId:'9',versionNo:1,title:'Original practice',titleZh:'原创练习材料',content:{schemaVersion:1,paragraphs:[{key:'p1',sentences:[{key:'s1',text:'Small steps help us learn every day.'}]}]},category:{id:'1',code:'education',name:'教育'},wordCount:9,estimatedMinutes:1,sourceType:'ORIGINAL',usageNote:'原创测试',summary:'',difficulty:'B1',author:'',sourceUrl:null}
  const set={id:'1',articleId:'9',articleVersionId:'10',examCode:'CET4',readingType:'DETAILED',metadata:{title:'Original practice',titleZh:'原创专项',sourceKind:'ORIGINAL',topic:'education',sourceName:'原创测试',usageNote:'仅用于测试'},questions:[{key:'q1',type:'DETAIL',stem:'What helps?',options:['Steps','Waiting','Nothing','Stopping'],correctOption:'A',explanation:'持续练习。',sortOrder:0},{key:'q2',type:'DETAIL',stem:'What next?',options:['Try','Stop','Wait','Forget'],correctOption:'A',explanation:'继续尝试。',sortOrder:1}],reviewedQuestionKeys:[],sourceReviewed:false,status:'DRAFT',revision:0,lockVersion:1,article,checks:[]}
  await page.route('**/api/v1/admin/practice/sets?*',r=>r.fulfill({json:{items:[set],page:1,pageSize:20,total:1}}))
  await page.route('**/api/v1/admin/articles/9',r=>r.fulfill({json:{id:'9',draftVersionId:'10',publishedVersionId:'10',draft:article,status:'PUBLISHED',lockVersion:1}}))
  await page.route('**/api/v1/admin/practice/sets/1',r=>{if(r.request().method()==='PUT'){Object.assign(set,r.request().postDataJSON());set.lockVersion++}return r.fulfill({json:set})})
  await page.goto('/admin/practice')
  await expect(page.getByRole('heading',{name:'专项题组',exact:true})).toBeVisible()
  await page.getByRole('button',{name:'编辑题组',exact:true}).filter({visible:true}).click()
  await page.getByLabel('英文题干',{exact:true}).fill('Unsaved current stem')
  await page.getByRole('button',{name:'下一题',exact:true}).click()
  await page.getByRole('button',{name:'上一题',exact:true}).click()
  await expect(page.getByLabel('英文题干',{exact:true})).toHaveValue('Unsaved current stem')
  await page.getByLabel('答案解析',{exact:true}).fill('')
  await expect(page.locator('.practice-question-nav')).toContainText('缺解析')
  await expect(page.locator('.practice-overview')).toContainText('正确选项')
  await page.locator('.practice-checks').getByRole('button',{name:/第 1 题.*解析/}).click()
  await expect(page.getByLabel('答案解析',{exact:true})).toBeFocused()
  await page.getByRole('button',{name:'保存草稿',exact:true}).click()
  await expect(page.getByRole('status').filter({hasText:'草稿已保存'})).toBeVisible()
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 })
})

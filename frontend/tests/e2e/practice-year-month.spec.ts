import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'

function deferred(){let release!:()=>void,signalStarted!:()=>void;const promise=new Promise<void>(resolve=>release=resolve),started=new Promise<void>(resolve=>signalStarted=resolve);return{promise,started,release,signalStarted}}

for(const exam of ['CET4','CET6'])for(const section of ['listening','paper'])test(`${exam} ${section} months follow the selected year including March`,async({page},info)=>{
 await mockIdentity(page,true)
 const months=(year:string|null)=>(year==='2020'?['12','9','7']:year==='2022'?['12','9','6']:year==='2024'?['12','6']:['12','6','3']).map(value=>({value,count:1}))
 await page.route('**/api/v1/practice/sets?*',r=>r.fulfill({json:{items:[],total:0,page:1,pageSize:20}}))
 await page.route('**/api/v1/practice/facets?*',r=>r.fulfill({json:{source:[],readingType:[],topic:[],state:[],year:[{value:'2023',count:3},{value:'2024',count:2}],month:months(new URL(r.request().url()).searchParams.get('year')),paperNo:[]}}))
 await page.route('**/api/v1/practice/papers?*',r=>r.fulfill({json:{items:[],total:0,page:1,pageSize:20,years:[{value:'2020',count:3},{value:'2022',count:3},{value:'2023',count:3},{value:'2024',count:2}],months:months(new URL(r.request().url()).searchParams.get('year'))}}))
 await page.goto(`/practice/cet?exam=${exam}&source=PAST&collection=PAST&section=${section}&year=2023`)
 if(info.project.name==='mobile')await page.getByRole('button',{name:'筛选题组',exact:true}).click()
 const panel=page.locator('.practice-simple-filters').filter({visible:true})
 const month=panel.locator('details').filter({has:page.locator('summary').filter({hasText:'月份'})})
 const year=panel.locator('details').filter({has:page.locator('summary').filter({hasText:'年份'})})
 await month.locator('summary').click();await year.locator('summary').click()
 const march=()=>panel.getByRole('button',{name:'月份：3月',exact:true})
 await expect(march()).toBeVisible();await march().click();await expect(page).toHaveURL(/month=3/)
 await panel.getByRole('button',{name:'年份：2024年',exact:true}).click()
 await expect(page).toHaveURL(/year=2024/)
 await expect.poll(()=>new URL(page.url()).searchParams.get('month')).toBeNull()
 await expect(march()).toHaveCount(0)
 await expect(panel.getByRole('button',{name:'月份：6月',exact:true})).toBeVisible()
 await panel.getByRole('button',{name:'年份：2023年',exact:true}).click()
 await expect(march()).toBeVisible()
 for(const target of ['2020','2022']){
  await panel.getByRole('button',{name:'年份：'+target+'年',exact:true}).click()
  await expect(panel.getByRole('button',{name:'月份：9月',exact:true})).toBeVisible()
  await expect(march()).toHaveCount(0)
  const july=panel.getByRole('button',{name:'月份：7月',exact:true})
  if(target==='2020')await expect(july).toBeVisible();else await expect(july).toHaveCount(0)
 }
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

test('direct history entry also obtains year-specific months',async({page},info)=>{
 await mockIdentity(page)
 await page.route('**/api/v1/practice/history?*',r=>r.fulfill({json:{items:[],total:0,page:1,pageSize:20}}))
 await page.route('**/api/v1/practice/facets?*',r=>r.fulfill({json:{source:[],readingType:[],topic:[],state:[],year:[],month:[{value:'7',count:1},{value:'9',count:1}],paperNo:[]}}))
 await page.goto('/practice/cet?exam=CET4&source=PAST&collection=PAST&section=listening&year=2020&tab=history')
 if(info.project.name==='mobile')await page.getByRole('button',{name:'筛选题组',exact:true}).click()
 const months=page.getByLabel('考试月份筛选',{exact:true}).filter({visible:true})
 await expect(months.locator('option')).toHaveText(['全部月份','9月','7月'])
})

for(const section of ['listening','paper'])test(`${section} does not show a zero before the first catalog response`,async({page})=>{
 await mockIdentity(page,true)
 const pending=deferred();let first=true
 if(section==='paper')await page.route('**/api/v1/practice/papers?*',async route=>{const q=new URL(route.request().url()).searchParams;if(first&&q.get('year')==='2023'){first=false;pending.signalStarted();await pending.promise}return route.fulfill({json:{items:[],total:0,page:1,pageSize:20,years:[{value:'2023',count:3}],months:[{value:'3',count:1},{value:'6',count:2}]}})})
 else await page.route('**/api/v1/practice/sets?*',async route=>{const q=new URL(route.request().url()).searchParams;if(first&&q.get('year')==='2023'){first=false;pending.signalStarted();await pending.promise}return route.fulfill({json:{items:[],total:0,page:1,pageSize:20}})})
 await page.route('**/api/v1/practice/facets?*',route=>route.fulfill({json:{source:[],readingType:[],topic:[],state:[],year:[{value:'2023',count:3}],month:[{value:'3',count:1},{value:'6',count:2}],paperNo:[]}}))
 try{
  await page.goto(`/practice/cet?exam=CET4&source=PAST&collection=PAST&section=${section}&year=2023`)
  await pending.started
  const summary=section==='paper'?page.locator('.paper-summary'):page.locator('.practice-library-results > p.practice-hint')
  await expect(summary).toContainText('匹配 —')
  pending.release()
  await expect(summary).toContainText('匹配 0')
 }finally{pending.release()}
})

for(const section of ['listening','paper'])test(`${section} disables stale months while a new year facet is pending`,async({page},info)=>{
 await mockIdentity(page,true)
 const changing=deferred();let held=false
 const months=(year:string|null)=>(year==='2024'?['12','6']:['12','6','3']).map(value=>({value,count:1}))
 if(section==='paper')await page.route('**/api/v1/practice/papers?*',async route=>{const q=new URL(route.request().url()).searchParams;if(q.get('year')==='2024'&&!held){held=true;changing.signalStarted();await changing.promise}return route.fulfill({json:{items:[],total:0,page:1,pageSize:20,years:[{value:'2024',count:2},{value:'2023',count:3}],months:months(q.get('year'))}})})
 else{
  await page.route('**/api/v1/practice/sets?*',route=>route.fulfill({json:{items:[],total:0,page:1,pageSize:20}}))
  await page.route('**/api/v1/practice/facets?*',async route=>{const q=new URL(route.request().url()).searchParams;if(q.get('year')==='2024'&&!held){held=true;changing.signalStarted();await changing.promise}return route.fulfill({json:{source:[],readingType:[],topic:[],state:[],year:[{value:'2024',count:2},{value:'2023',count:3}],month:months(q.get('year')),paperNo:[]}})})
 }
 try{
  await page.goto(`/practice/cet?exam=CET4&source=PAST&collection=PAST&section=${section}&year=2023`)
  if(info.project.name==='mobile')await page.getByRole('button',{name:'筛选题组',exact:true}).click()
  const panel=page.locator('.practice-simple-filters').filter({visible:true})
  const month=panel.locator('details').filter({has:page.locator('summary').filter({hasText:'月份'})})
  const year=panel.locator('details').filter({has:page.locator('summary').filter({hasText:'年份'})})
  await month.locator('summary').click();await year.locator('summary').click()
  await panel.getByRole('button',{name:'月份：3月',exact:true}).click()
  await expect(page).toHaveURL(/month=3/)
  await panel.getByRole('button',{name:'年份：2024年',exact:true}).click()
  await changing.started
  await expect(panel.getByRole('button',{name:'月份：3月',exact:true})).toBeDisabled()
  changing.release()
  await expect(panel.getByRole('button',{name:'月份：3月',exact:true})).toHaveCount(0)
  await expect(panel.getByRole('button',{name:'月份：6月',exact:true})).toBeVisible()
 }finally{changing.release()}
})

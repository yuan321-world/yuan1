import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'

for(const section of ['listening','paper'])test(`${section} date counts follow year selection without replacing the filters`,async({page},info)=>{
 await mockIdentity(page,true)
 const dateData=(q:URLSearchParams)=>({
  years:q.has('month')?[{value:'2023',count:1}]:[{value:'2023',count:5},{value:'2024',count:4}],
  months:q.get('year')==='2024'?[{value:'12',count:2},{value:'6',count:2}]:[{value:'12',count:2},{value:'6',count:2},{value:'3',count:1}],
 })
 await page.route('**/api/v1/practice/sets?*',r=>r.fulfill({json:{items:[],total:0,page:1,pageSize:20}}))
 await page.route('**/api/v1/practice/facets?*',r=>{const d=dateData(new URL(r.request().url()).searchParams);return r.fulfill({json:{source:[],readingType:[],topic:[],state:[],paperNo:[],year:d.years,month:d.months}})})
 await page.route('**/api/v1/practice/papers?*',r=>r.fulfill({json:{items:[],total:0,page:1,pageSize:20,...dateData(new URL(r.request().url()).searchParams)}}))
 await page.goto(`/practice/cet?exam=CET4&source=PAST&collection=PAST&section=${section}&year=2023&month=3`)
 if(info.project.name==='mobile')await page.getByRole('button',{name:'筛选题组',exact:true}).click()
 const panel=page.locator('.practice-simple-filters').filter({visible:true})
 await panel.locator('summary').filter({hasText:'月份'}).click()
 await panel.locator('summary').filter({hasText:'年份'}).click()
 const year=panel.getByRole('button',{name:'年份：2024年',exact:true})
 await expect(year.locator('.practice-filter-count')).toHaveText('4')
 await expect(panel.getByRole('button',{name:'年份：全部',exact:true}).locator('.practice-filter-count')).toHaveText('9')
 await expect(panel.getByRole('button',{name:'月份：全部',exact:true}).locator('.practice-filter-count')).toHaveText('5')
 const original=await panel.elementHandle()
 await year.click()
 await expect.poll(()=>new URL(page.url()).searchParams.get('month')).toBeNull()
 await expect(panel.getByRole('button',{name:'月份：全部',exact:true}).locator('.practice-filter-count')).toHaveText('4')
 await expect(panel.getByRole('button',{name:'月份：3月',exact:true})).toHaveCount(0)
 await expect(year).toHaveAttribute('aria-pressed','true')
 expect(await original!.evaluate(el=>el.isConnected)).toBe(true)
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await expect(panel.getByRole('button',{name:'年份：2026年',exact:true}).locator('.practice-filter-count')).toHaveText('0')
})

for(const section of ['listening','paper'])test(`${section} delayed date facets cannot accept stale months or show an unloaded zero`,async({page},info)=>{
 await mockIdentity(page,true)
 let releaseInitial!:()=>void,releaseYear!:()=>void
 const initial=new Promise<void>(resolve=>{releaseInitial=resolve})
 const nextYear=new Promise<void>(resolve=>{releaseYear=resolve})
 let requests=0
 const data=(q:URLSearchParams)=>({years:[{value:'2023',count:5},{value:'2024',count:4}],months:q.get('year')==='2024'?[{value:'6',count:4}]:[{value:'3',count:1},{value:'6',count:4}]})
 await page.route('**/api/v1/practice/sets?*',r=>r.fulfill({json:{items:[],total:9,page:1,pageSize:20}}))
 await page.route(section==='paper'?'**/api/v1/practice/papers?*':'**/api/v1/practice/facets?*',async r=>{
  const q=new URL(r.request().url()).searchParams;requests++
  await (q.get('year')==='2024'?nextYear:initial)
  const d=data(q)
  await r.fulfill({json:section==='paper'?{items:[],total:9,page:1,pageSize:20,...d}:{source:[],readingType:[],topic:[],state:[],paperNo:[],year:d.years,month:d.months}})
 })
 await page.goto(`/practice/cet?exam=CET4&source=PAST&collection=PAST&section=${section}&year=2023`)
 await expect.poll(()=>requests).toBeGreaterThan(0)
 const summary=page.locator(section==='paper'?'.paper-summary':'.practice-library-results>.practice-hint')
 try{await expect(summary).toContainText('匹配 —')}finally{releaseInitial()}
 await expect(summary).toContainText('匹配 9')
 if(info.project.name==='mobile')await page.getByRole('button',{name:'筛选题组',exact:true}).click()
 const panel=page.locator('.practice-simple-filters').filter({visible:true})
 await panel.locator('summary').filter({hasText:'月份'}).click()
 await panel.locator('summary').filter({hasText:'年份'}).click()
 await panel.getByRole('button',{name:'年份：2024年',exact:true}).click()
 try{
  const oldMonth=panel.getByRole('button',{name:'月份：3月',exact:true})
  await expect(oldMonth).toBeDisabled()
  await oldMonth.evaluate((el:HTMLButtonElement)=>el.click())
  expect(new URL(page.url()).searchParams.get('month')).toBeNull()
 }finally{releaseYear()}
 await expect(panel.getByRole('button',{name:'月份：3月',exact:true})).toHaveCount(0)
 await expect(panel.getByRole('button',{name:'月份：6月',exact:true})).toBeEnabled()
})

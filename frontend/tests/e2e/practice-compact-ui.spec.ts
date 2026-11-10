import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
const facets={source:[{value:'PAST',count:2},{value:'ORIGINAL',count:1}],readingType:[],topic:[],state:[],year:[{value:'2026',count:2}],month:[{value:'6',count:2}],paperNo:[]}
async function setup(page:import('@playwright/test').Page){
 await mockIdentity(page);const queries:string[]=[]
 await page.route('**/api/v1/practice/catalog',r=>r.fulfill({json:{exams:[{code:'CET4',available:true}]}}))
 await page.route('**/api/v1/practice/facets?*',r=>r.fulfill({json:facets}))
 await page.route('**/api/v1/practice/sets?*',r=>r.fulfill({json:{items:[{id:'1',setVersionId:'2',examCode:'CET4',readingType:'DETAILED',questionCount:5,metadata:{title:'Making Room for Quiet on Campus · Original Practice',titleZh:'校园安静空间',sourceKind:new URL(r.request().url()).searchParams.get('source')||'PAST',year:2026,month:6,paperNo:3,sourceName:'Fixture',usageNote:'测试'}}],total:1,page:1,pageSize:20}}))
 await page.route('**/api/v1/practice/history?*',r=>{queries.push(r.request().url());return r.fulfill({json:{items:[{id:'20',setId:'1',setVersionId:'2',kind:'FULL',status:'IN_PROGRESS',title:'2026年6月四级 第3套 仔细阅读 第二篇',total:5},{id:'21',setId:'1',setVersionId:'2',kind:'FULL',status:'COMPLETED',title:'2024年6月四级 选词填空',correctCount:3,total:10}],page:1,pageSize:20,total:2}})})
 return queries
}
for(const width of [1440,718,320])test(`practice library is compact and scoped at ${width}px`,async({page})=>{
 await page.setViewportSize({width,height:900});const queries=await setup(page)
 await page.goto('/practice/cet?exam=CET4&source=PAST&collection=PAST&tab=history')
 await expect(page.locator('.practice-history-row')).toHaveCount(2);expect((await page.locator('.practice-history-row').first().boundingBox())!.height).toBeLessThan(135)
 const back=page.getByRole('link',{name:'← 返回考试类别',exact:true});expect(await back.evaluate(el=>getComputedStyle(el).borderStyle)).toBe('solid');await expect(page.getByLabel('来源筛选',{exact:true})).toHaveCount(0);await expect(page.getByText('更多筛选 · 套次、主题',{exact:true})).toHaveCount(0)
 if(width<=900)await page.getByRole('button',{name:'筛选题组',exact:true}).click()
 const state=page.getByLabel('练习状态筛选',{exact:true}).filter({visible:true});await state.selectOption('COMPLETED');await expect.poll(()=>new URL(queries.at(-1)!).searchParams.get('state')).toBe('COMPLETED');expect(new URL(queries.at(-1)!).searchParams.get('source')).toBe('PAST');if(width<=900)await page.getByRole('button',{name:'关闭筛选',exact:true}).click()
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:`../.tool-cache/practice-compact-history-${width}.png`,fullPage:true})
 await page.goto('/practice?source=ORIGINAL');await expect(page.locator('.practice-original-library .practice-catalog-row')).toHaveCount(1);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:`../.tool-cache/practice-compact-original-${width}.png`,fullPage:true})
 await page.goto('/practice');await expect(page.locator('.practice-family-card')).toHaveCount(3);expect((await page.locator('.practice-family-card').first().boundingBox())!.height).toBeLessThan(190);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

test('paper catalog uses the same narrow-screen filter dialog without a duplicate sidebar',async({page})=>{
 await page.setViewportSize({width:718,height:900});await setup(page)
 await page.route('**/api/v1/practice/papers?*',r=>r.fulfill({json:{items:[],total:0,page:1,pageSize:20,years:[{value:'2026',count:2}],months:[{value:'6',count:2}]}}))
 await page.goto('/practice/cet?exam=CET4&source=PAST&collection=PAST&section=paper');await expect(page.locator('.paper-page')).toBeVisible();await expect(page.locator('.paper-library>.practice-desktop-filters')).toBeHidden();await page.locator('.paper-page>.practice-filter-toggle').click();await expect(page.getByRole('dialog')).toBeVisible();await expect(page.getByRole('dialog').getByText('类型',{exact:false}).first()).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

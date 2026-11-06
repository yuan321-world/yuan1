import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
test('date filters fold independently and filter changes preserve the page',async({page},info)=>{
 await mockIdentity(page,true)
 await page.route('**/api/v1/practice/sets?*',r=>r.fulfill({json:{items:[],total:0,page:1,pageSize:20}}));await page.route('**/api/v1/practice/facets?*',r=>r.fulfill({json:{source:[],readingType:[],topic:[],state:[],year:[],month:[],paperNo:[]}}));await page.route('**/api/v1/practice/catalog',r=>r.fulfill({json:{exams:[]}}))
 await page.goto('/practice?source=ORIGINAL');await expect(page.getByRole('button',{name:'导入题目',exact:false})).toHaveCount(0);await expect(page.getByRole('button',{name:'生成题目',exact:false})).toBeDisabled()
 await page.goto('/practice/cet?exam=CET4&source=PAST&collection=PAST&section=listening')
 if(info.project.name==='mobile')await page.getByRole('button',{name:'筛选题组',exact:true}).click()
 const panel=page.locator('.practice-simple-filters').filter({visible:true});const year=panel.locator('details').filter({has:page.locator('summary').filter({hasText:'年份'})});const month=panel.locator('details').filter({has:page.locator('summary').filter({hasText:'月份'})})
 await expect(year).not.toHaveAttribute('open','');await expect(panel.getByRole('button',{name:'年份：2024年',exact:true})).not.toBeVisible();await year.locator('summary').click();await expect(panel.getByRole('button',{name:'年份：2024年',exact:true})).toBeVisible();await expect(month).not.toHaveAttribute('open','')
 if(info.project.name==='desktop'){await page.setViewportSize({width:1440,height:600});await panel.getByRole('button',{name:'年份：2024年',exact:true}).scrollIntoViewIfNeeded();expect(await page.evaluate(()=>scrollY)).toBeGreaterThan(100)}
 await page.evaluate(()=>{(window as any).__filterNode=document.querySelector('.practice-study-library')});await panel.getByRole('button',{name:'年份：2024年',exact:true}).click();await expect(page).toHaveURL(/year=2024/);await expect(year).toHaveAttribute('open','');expect(await page.evaluate(()=>(window as any).__filterNode===document.querySelector('.practice-study-library'))).toBe(true)
 if(info.project.name==='desktop')await expect.poll(()=>page.evaluate(()=>scrollY)).toBeGreaterThan(100)
})

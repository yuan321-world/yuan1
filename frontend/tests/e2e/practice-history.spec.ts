import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
test('my practice status selector is sent to the history API',async({page})=>{
 await mockIdentity(page);const seen:string[]=[]
 await page.route('**/api/v1/practice/history?*',r=>{seen.push(new URL(r.request().url()).searchParams.get('state')||'');return r.fulfill({json:{items:[],page:1,pageSize:20,total:0}})})
 await page.goto('/practice/cet4?tab=history');if((page.viewportSize()?.width||0)<=700)await page.getByRole('button',{name:'筛选题组',exact:true}).click();await page.getByLabel('练习状态筛选').filter({visible:true}).selectOption('COMPLETED');await expect.poll(()=>seen.at(-1)).toBe('COMPLETED');await expect(page.getByLabel('练习状态筛选').filter({visible:true}).locator('option[value=NOT_STARTED]')).toHaveCount(0)
})

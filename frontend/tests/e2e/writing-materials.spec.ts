import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
// Read-only UI retirement checks; stored materials and backend authorization tests remain.
test('article management no longer advertises retired writing materials',async({page})=>{
 await mockIdentity(page,false,true)
 await page.route('**/api/v1/admin/articles?*',r=>r.fulfill({json:{items:[],page:1,pageSize:20,total:0}}))
 await page.route('**/api/v1/admin/articles/categories',r=>r.fulfill({json:[]}))
 await page.goto('/admin/articles');await expect(page.getByRole('link',{name:/写作素材/})).toHaveCount(0)
})

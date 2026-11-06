import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
test('JSON must be revalidated after editing and never skips draft review',async({page})=>{
 await mockIdentity(page,false,true);let validations=0
 await page.route('**/api/v1/admin/practice/import/validate',r=>{validations++;return r.fulfill({json:{valid:true,fieldErrors:{},itemCount:1}})})
 await page.goto('/admin/practice/import');await expect(page.getByRole('heading',{name:'JSON导入题组草稿'})).toBeVisible();await expect(page.getByRole('button',{name:'导入为草稿',exact:true})).toBeDisabled();await page.getByLabel('题组JSON').fill('{bad');await page.getByRole('button',{name:'校验内容',exact:true}).click();await expect(page.getByRole('alert')).toBeVisible();expect(validations).toBe(0);await page.getByLabel('题组JSON').fill('{"articleId":"9"}');await page.getByRole('button',{name:'校验内容',exact:true}).click();await expect(page.getByRole('button',{name:'导入为草稿',exact:true})).toBeEnabled();await page.getByLabel('题组JSON').fill('{"articleId":"10"}');await expect(page.getByRole('button',{name:'导入为草稿',exact:true})).toBeDisabled();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

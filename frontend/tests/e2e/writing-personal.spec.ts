import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
// Reading writing materials were retired by explicit user request; keep old bookmarks safe.
test('retired writing library redirects without loading personal writing data',async({page})=>{
 await mockIdentity(page);let requests=0
 page.on('request',r=>{if(r.url().includes('writing-materials'))requests++})
 await page.goto('/writing');await expect(page).toHaveURL(/\/reading$/);await page.getByRole('button',{name:'用户菜单',exact:true}).click();await expect(page.getByRole('link',{name:'我的写作素材',exact:true})).toHaveCount(0);await expect(page.getByRole('link',{name:'账户设置',exact:true})).toBeVisible();expect(requests).toBe(0)
})

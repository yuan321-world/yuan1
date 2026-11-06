import {test,expect} from '@playwright/test'
test('admin users prototype searches, protects accounts and records explicit actions',async({page},info)=>{
 const errors:string[]=[],requests:string[]=[];page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(r.url().includes('/api/'))requests.push(r.url())})
 await page.goto('http://127.0.0.1:4187/admin-users-preview.html')
 await expect(page.getByRole('heading',{name:'用户管理',exact:true})).toBeVisible()
 await expect(page.locator('[data-id="1001"]').getByRole('button',{name:'受保护'})).toBeDisabled()
 await page.getByLabel('搜索账号、昵称或ID').fill('lin_reader');await page.getByRole('button',{name:'搜索',exact:true}).click();await expect(page.locator('#list .account')).toHaveCount(1)
 await page.getByRole('button',{name:'详情',exact:true}).click();await expect(page.locator('#facts')).toContainText('已验证')
 await expect(page.getByRole('button',{name:'发送重置指引',exact:true})).toHaveCount(0);await page.keyboard.press('Escape')
 await page.locator('[data-id="1002"]').getByRole('button',{name:'停用',exact:true}).click();await page.getByRole('button',{name:'确认停用'}).click();await expect(page.getByRole('alert')).toContainText('请填写');await expect(page.locator('#impact')).toContainText('访客');await page.getByLabel('操作原因（必填，仅管理员可见）').fill('测试暂停')
 await page.getByRole('button',{name:'确认停用'}).click();await expect(page.locator('[data-id="1002"] .state')).toContainText('已停用')
 await page.locator('[data-id="1002"]').getByRole('button',{name:'恢复',exact:true}).click();await page.getByLabel('操作原因（必填，仅管理员可见）').fill('测试恢复')
 await page.getByRole('button',{name:'确认恢复'}).click();await expect(page.locator('[data-id="1002"] .state')).toContainText('正常')
 await page.getByRole('button',{name:'清空筛选'}).click();await page.getByRole('button',{name:'下一页',exact:true}).click();await expect(page.locator('#list .account')).toHaveCount(2)
 await page.getByLabel('搜索账号、昵称或ID').fill('不存在');await page.getByRole('button',{name:'搜索',exact:true}).click();await expect(page.locator('#list')).toContainText('没有找到')
 await page.getByLabel('搜索账号、昵称或ID').fill('new_learner');await page.getByRole('button',{name:'搜索',exact:true}).click();await page.getByRole('button',{name:'详情',exact:true}).click();await expect(page.getByRole('button',{name:'发送重置指引',exact:true})).toHaveCount(0);await expect(page.locator('#accountHelp')).toContainText('自行验证邮箱重置')
 await page.keyboard.press('Escape');await page.getByRole('button',{name:'操作记录',exact:true}).click();await expect(page.locator('#allHistory')).toContainText('测试恢复')
 await page.keyboard.press('Escape');await page.getByRole('button',{name:'清空筛选'}).click()
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await page.screenshot({path:`../.tool-cache/wall-implementation/admin-users-${info.project.name}.png`,fullPage:true})
 expect(errors).toEqual([]);expect(requests).toEqual([])
})

test('compact filters remain readable at 320 and 390 pixels',async({page})=>{
 for(const width of [320,390]){await page.setViewportSize({width,height:844});await page.goto('http://127.0.0.1:4187/admin-users-preview.html');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect(await page.locator('#state').evaluate(e=>e.clientWidth)).toBeGreaterThan(95);await expect(page.locator('nav .active')).toBeInViewport()}
})

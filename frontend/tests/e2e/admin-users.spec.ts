import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
test('admin can search and inspect masked account details without credential fields',async({page})=>{
 await mockIdentity(page,false,true)
 const user={id:'11',username:'lin_reader',displayName:'林同学',roles:['USER'],enabled:true,version:0,createdAt:'2026-10-01T00:00:00Z',protectedAccount:false}
 await page.route('**/api/v1/admin/users**',r=>{const p=new URL(r.request().url()).pathname;if(p.endsWith('/11'))return r.fulfill({json:{...user,maskedEmail:'l***@e***.test',emailVerified:true}});return r.fulfill({json:{items:[user],page:1,pageSize:20,total:1}})})
 await page.goto('/admin/users');await expect(page.getByRole('heading',{name:'用户管理',exact:true})).toBeVisible();await page.getByLabel('搜索账号、昵称或ID').fill('lin');await page.getByRole('button',{name:'搜索',exact:true}).click();await page.getByRole('button',{name:'详情',exact:true}).click();await expect(page.getByRole('dialog',{name:'账号详情'})).toContainText('l***@e***.test');await expect(page.getByRole('button',{name:'发送重置指引'})).toHaveCount(0)
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

test('status changes keep unknown request IDs and show an escaped audit trail',async({page})=>{
 await mockIdentity(page,false,true)
 const user={id:'21',username:'reader',displayName:'同学',roles:['USER'],enabled:true,version:0,createdAt:'2026-10-01T00:00:00Z',protectedAccount:false}
 let requestId='',writes=0
 await page.route('**/api/v1/admin/users**',async r=>{const p=new URL(r.request().url()).pathname;if(p.endsWith('/status')){writes++;requestId=r.request().postDataJSON().requestId;user.enabled=false;user.version=1;return r.abort('failed')}if(p.includes('/requests/'))return r.fulfill({json:{requestId,targetId:'21',enabled:false,version:1,completed:true}});if(p.endsWith('/audit'))return r.fulfill({json:{items:[{id:'1',actor:{id:'2',username:'admin'},target:{id:'21',username:'reader'},beforeEnabled:true,afterEnabled:false,reason:'<img src=x> 操作原因',createdAt:'2026-10-27T00:00:00Z'}],nextCursor:null,hasMore:false}});if(p.endsWith('/21'))return r.fulfill({json:{...user,maskedEmail:'r***@e***.test',emailVerified:true}});return r.fulfill({json:{items:[user],total:1,page:1,pageSize:20}})})
 await page.goto('/admin/users');const row=page.locator('[data-user-id="21"]');await row.getByRole('button',{name:'停用',exact:true}).click();await page.getByRole('button',{name:'确认停用'}).click();await expect(page.getByRole('alert')).toContainText('操作原因')
 await page.getByLabel('操作原因（必填，仅管理员可见）').fill('操作原因');await page.getByRole('button',{name:'确认停用'}).click();await expect(page.getByRole('button',{name:'确认操作结果'})).toBeVisible()
 await page.getByRole('button',{name:'取消',exact:true}).click();await row.getByRole('button',{name:'停用',exact:true}).click();await expect(page.getByLabel('操作原因（必填，仅管理员可见）')).toHaveValue('操作原因');await page.getByRole('button',{name:'确认操作结果'}).click();await expect(row.locator('.users-state')).toContainText('已停用');expect(writes).toBe(1)
 await page.getByRole('button',{name:'操作记录',exact:true}).click();const audit=page.getByRole('dialog',{name:'操作记录',exact:true});await expect(audit).toContainText('<img src=x> 操作原因');await expect(audit.locator('img')).toHaveCount(0)
})

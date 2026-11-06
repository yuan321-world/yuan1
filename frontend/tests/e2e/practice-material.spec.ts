import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
test('dedicated material is entered inside practice and a failed save retains text and request identity',async({page})=>{
 await mockIdentity(page,false,true)
 await page.route('**/api/v1/admin/articles?*',r=>r.fulfill({json:{items:[],page:1,pageSize:50,total:0}}))
 const requests:any[]=[];await page.route('**/api/v1/admin/practice/material-sets',r=>{requests.push(r.request().postDataJSON());return r.fulfill({status:503,json:{code:'TEMPORARY',message:'保存暂不可用，请重试'}})})
 await page.goto('/admin/practice/new');await page.getByLabel('专项原文',{exact:true}).fill('Small steps help us learn every day.');await page.getByLabel('英文标题',{exact:true}).fill('Practice material');await page.getByLabel('中文标题',{exact:true}).fill('专项原文')
 await page.getByRole('button',{name:'预览当前草稿',exact:true}).click();await expect(page.getByRole('dialog')).toContainText('Small steps help us learn every day.');await page.getByRole('button',{name:'关闭预览',exact:true}).click();await page.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(page.getByRole('alert')).toContainText('保存暂不可用');await expect(page.getByLabel('专项原文',{exact:true})).toHaveValue('Small steps help us learn every day.');await page.getByRole('button',{name:'保存草稿',exact:true}).click();await expect.poll(()=>requests.length).toBe(2);expect(requests[0].requestId).toBe(requests[1].requestId);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

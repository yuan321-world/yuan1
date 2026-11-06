import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
test('moderation separates author-visible reason and internal note',async({page})=>{
 await mockIdentity(page,false,true)
 const message={id:'31',version:0,kind:'MESSAGE',category:'STUDY',color:'yellow',text:'需要管理员检查的内容',title:'',author:{displayName:'学习者',isAdmin:false},createdAt:'2026-10-27T00:00:00Z',editedByAdmin:false,position:{x:0,y:0,width:280,height:420},likes:0,liked:false,commentCount:0,owned:false}
 let changed=false
 await page.route('**/api/v1/admin/wall/**',route=>{
  const path=new URL(route.request().url()).pathname
  if(path.endsWith('/moderation')){const body=route.request().postDataJSON();expect(body.authorReason).toBe('请保护个人隐私。');expect(body.internalNote).toBe('内部巡查记录');expect(body.expectedVersion).toBe(0);changed=true;return route.fulfill({json:{id:'31',version:1,status:'HIDDEN'}})}
  if(path.endsWith('/messages'))return route.fulfill({json:{items:[{message,status:changed?'HIDDEN':'VISIBLE',pendingReports:0}],nextCursor:null,hasMore:false}})
  if(path.endsWith('/messages/31'))return route.fulfill({json:{message,status:'VISIBLE',reports:[],history:[]}})
  return route.fulfill({json:{items:[],nextCursor:null,hasMore:false}})
 })
 await page.goto('/admin/wall');await page.getByRole('button',{name:'隐藏',exact:true}).first().click();await page.getByLabel('告知作者的原因（必填）').fill('请保护个人隐私。');await page.getByLabel('内部备注（选填，仅管理员可见）').fill('内部巡查记录');await page.getByRole('button',{name:'确认处理并通知作者'}).click();await expect(page.locator('.wall-status').first()).toHaveText('已隐藏')
})

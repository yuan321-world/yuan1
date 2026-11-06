import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
test('wall-only bell shows private reason and marks the captured boundary read',async({page})=>{
 await mockIdentity(page)
 let unread=true
 await page.route('**/api/v1/wall/**',route=>{
  const path=new URL(route.request().url()).pathname
  if(path.endsWith('/notifications/read-all')){expect(route.request().postDataJSON()).toEqual({throughId:'51'});unread=false;return route.fulfill({json:{read:true}})}
  if(path.endsWith('/notifications'))return route.fulfill({json:{items:[{id:'51',messageId:'1',targetId:'1',targetType:'MESSAGE',action:'HIDE',authorReason:'请围绕学习交流发言。',excerpt:'我的学习留言',createdAt:'2026-10-27T00:00:00Z',readAt:unread?null:'2026-10-27T00:01:00Z',canOpen:false}],nextCursor:null,hasMore:false,readBoundary:'51',unreadCount:unread?1:0}})
  return route.fulfill({json:{items:[],nextCursor:null,hasMore:false}})
 })
 await page.goto('/wall');await expect(page.locator('.wall-dock').getByRole('button',{name:/我的消息/})).toBeVisible();await expect(page.locator('.topbar').getByRole('button',{name:/我的消息/})).toHaveCount(0)
 await page.getByRole('button',{name:/我的消息/}).click();await expect(page.getByText('请围绕学习交流发言。',{exact:false})).toBeVisible();await expect(page.getByRole('button',{name:'查看当前内容'})).toHaveCount(0)
 await page.getByRole('button',{name:'全部标为已读'}).click();await expect(page.getByText('1 条消息 · 0 条未读')).toBeVisible()
})

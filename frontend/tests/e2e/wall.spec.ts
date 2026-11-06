import { test, expect } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
test('wall publishes with account identity and renders text without HTML', async ({ page }) => {
 await mockIdentity(page)
 let created = false
 const item = { id:'1',version:0,kind:'MESSAGE',category:'STUDY',color:'yellow',text:'<img src=x> 阅读进步',title:'',author:{displayName:'学习者',isAdmin:false},createdAt:'2026-10-27T00:00:00Z',editedByAdmin:false,position:{x:0,y:0,width:280,height:420},likes:0,liked:false,commentCount:0,owned:true }
 await page.route('**/api/v1/wall/**', route => {
  const url = new URL(route.request().url())
  if(route.request().method()==='POST'&&url.pathname.endsWith('/messages')){created=true;return route.fulfill({json:item})}
  if(url.pathname.endsWith('/messages'))return route.fulfill({json:{items:created?[item]:[],hasMore:false,nextCursor:null}})
  return route.fulfill({status:404,json:{code:'WALL_NOT_FOUND'}})
 })
 await page.goto('/wall')
 await expect(page.getByRole('heading',{name:'留言墙',exact:true})).toBeVisible()
 await expect(page.getByRole('button',{name:/向 AI/})).toHaveCount(0)
 await page.getByRole('button',{name:'写留言',exact:true}).first().click()
 await page.getByLabel('留言内容',{exact:true}).fill(item.text)
 await page.getByRole('button',{name:'发布留言',exact:true}).click()
 await expect(page.locator('.wall-note').first()).toContainText(item.text)
 await expect(page.locator('.wall-note img')).toHaveCount(0)
})
test('wall detail supports explicit like and a persisted comment response', async ({page})=>{
 await mockIdentity(page)
 const item={id:'31',version:0,kind:'MESSAGE',category:'STUDY',color:'blue',text:'今天练听力。',title:'',author:{displayName:'同学',isAdmin:false},createdAt:'2026-10-27T00:00:00Z',editedByAdmin:false,position:{x:0,y:0,width:280,height:420},likes:0,liked:false,commentCount:0,owned:false}
 const comments:unknown[]=[]
 await page.route('**/api/v1/wall/**',route=>{
  const url=new URL(route.request().url()),method=route.request().method()
  if(url.pathname.endsWith('/like')){item.liked=route.request().postDataJSON().liked;item.likes=item.liked?1:0;return route.fulfill({json:item})}
  if(url.pathname.endsWith('/comments')){if(method==='POST'){const comment={id:'41',messageId:'31',version:0,text:route.request().postDataJSON().text,author:item.author,createdAt:item.createdAt,owned:true};comments.unshift(comment);item.commentCount++;return route.fulfill({json:comment})}return route.fulfill({json:{items:comments,nextCursor:null,hasMore:false}})}
  if(url.pathname.endsWith('/messages/31'))return route.fulfill({json:item})
  return route.fulfill({json:{items:[item],nextCursor:null,hasMore:false}})
 })
 await page.goto('/wall');await page.getByRole('button',{name:'展开 ↗'}).click()
 const detail=page.locator('dialog[aria-labelledby="wall-detail-heading"]');await detail.getByRole('button',{name:'点赞',exact:true}).click();await expect(detail.getByRole('button',{name:'取消点赞',exact:true})).toBeVisible()
 await page.getByLabel('留下你的鼓励或想法').fill('明天继续！');await page.getByRole('button',{name:'发表评论',exact:true}).click();await expect(page.locator('.wall-reply')).toContainText('明天继续！')
})
test('far persisted notes fit on first load and newly published notes are located',async({page},info)=>{
 test.skip(info.project.name==='mobile','Canvas-only viewport behavior; mobile uses the list')
 await mockIdentity(page);let created=false
 const note={id:'61',version:0,kind:'MESSAGE',category:'STUDY',color:'yellow',text:'远处的便签',title:'',author:{displayName:'学习者',isAdmin:false},createdAt:'2026-10-27T00:00:00Z',editedByAdmin:false,position:{x:5000,y:4000,width:280,height:420},likes:0,liked:false,commentCount:0,owned:true}
 await page.route('**/api/v1/wall/**',route=>{const path=new URL(route.request().url()).pathname;if(route.request().method()==='POST'&&path.endsWith('/messages')){created=true;return route.fulfill({json:{...note,id:'62',text:'新便签',position:{x:-5000,y:-4000,width:280,height:420}}})}return route.fulfill({json:{items:created?[{...note,id:'62',text:'新便签',position:{x:-5000,y:-4000,width:280,height:420}},note]:[note],nextCursor:null,hasMore:false}})})
 await page.goto('/wall');await expect(page.locator('[data-id="61"]')).toBeInViewport();await page.getByRole('button',{name:'写留言',exact:true}).click();await page.getByLabel('留言内容',{exact:true}).fill('新便签');await page.getByRole('button',{name:'发布留言',exact:true}).click();await expect(page.locator('[data-id="62"]')).toBeInViewport()
})


test('wall view switches retain the frame, controls and loaded content',async({page},info)=>{
 if(info.project.name==='mobile')await page.setViewportSize({width:1011,height:840})
 await mockIdentity(page);let reads=0
 const notes=Array.from({length:24},(_,i)=>({id:String(i+1),version:0,kind:'MESSAGE',category:'STUDY',color:'yellow',text:`学习便签 ${i+1}`,title:'',author:{displayName:'同学',isAdmin:false},createdAt:'2026-10-27T00:00:00Z',editedByAdmin:false,position:{x:(i%6)*310,y:Math.floor(i/6)*450,width:280,height:420},likes:0,liked:false,commentCount:0,owned:false}))
 await page.route('**/api/v1/wall/**',route=>{if(new URL(route.request().url()).pathname.endsWith('/messages'))reads++;return route.fulfill({json:{items:notes,nextCursor:null,hasMore:false}})})
 await page.goto('/wall');await expect(page.locator('.wall-note')).toHaveCount(24)
 const geometry=()=>page.evaluate(()=>({rects:['.wall-stage','.wall-canvas-controls','.wall-dock','.wall-zoom'].map(s=>{const e=document.querySelector(s)!;const r=e.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height,display:getComputedStyle(e).display}}),pageHeight:document.documentElement.scrollHeight,scrollY:window.scrollY}))
 const before=await geometry()
 await page.getByRole('button',{name:'列表',exact:true}).click();await expect(page.locator('.wall-stage')).toHaveClass(/is-list/)
 expect(await geometry()).toEqual(before)
 expect(reads).toBe(1)
 await page.screenshot({path:`../.tool-cache/wall-implementation/unified-list-${info.project.name}.png`})
 await expect(page.getByLabel('在留言墙里搜索')).toBeVisible()
 await page.locator('.wall-world').evaluate(e=>{e.scrollTop=e.scrollHeight})
 await expect(page.locator('.wall-note').last()).toBeInViewport()
 const afterScroll=await geometry();expect(afterScroll).toEqual(before)
 await page.getByRole('button',{name:'便签画布',exact:true}).click()
 await expect(page.locator('.wall-stage')).not.toHaveClass(/is-list/)
 expect(await geometry()).toEqual(before);expect(reads).toBe(1)
})


async function wallLikeFixture(page: import('@playwright/test').Page,guest:boolean){
 await mockIdentity(page,guest)
 const item={id:'71',version:0,kind:'MESSAGE',category:'STUDY',color:'pink',text:'点赞测试留言',title:'',author:{displayName:'同学',isAdmin:false},createdAt:'2026-10-27T00:00:00Z',editedByAdmin:false,position:{x:0,y:0,width:280,height:420},likes:0,liked:false,commentCount:0,owned:false}
 let writes=0
 await page.route('**/api/v1/wall/**',async route=>{const path=new URL(route.request().url()).pathname;if(path.endsWith('/like')){writes++;item.liked=route.request().postDataJSON().liked;item.likes=item.liked?1:0;await new Promise(resolve=>setTimeout(resolve,150));return route.fulfill({json:item})}if(path.endsWith('/messages/71'))return route.fulfill({json:item});return route.fulfill({json:{items:path.endsWith('/comments')?[]:[item],nextCursor:null,hasMore:false}})})
 await page.goto('/wall');await expect(page.locator('.wall-note')).toHaveCount(1)
 return ()=>writes
}
test('guest detail yields to login and restores its comment draft on cancel',async({page})=>{
 const writes=await wallLikeFixture(page,true)
 await page.getByRole('button',{name:'展开 ↗'}).click()
 const detail=page.locator('dialog[aria-labelledby="wall-detail-heading"]')
 await detail.getByLabel('留下你的鼓励或想法').fill('尚未发送的鼓励')
 await detail.getByRole('button',{name:'点赞',exact:true}).click()
 const auth=page.locator('.auth-dialog')
 await expect(auth).toBeVisible();await expect(detail).not.toBeVisible()
 await auth.getByLabel('用户名',{exact:true}).fill('learner')
 await expect(auth.getByLabel('用户名',{exact:true})).toBeFocused()
 await page.screenshot({animations:'disabled',path:`../.tool-cache/wall-implementation/like-login-${page.viewportSize()?.width}.png`})
 await auth.getByRole('button',{name:'关闭认证窗口'}).click()
 await expect(detail).toBeVisible();await expect(detail.getByLabel('留下你的鼓励或想法')).toHaveValue('尚未发送的鼓励')
 expect(writes()).toBe(0)
})
test('guest card heart opens an accessible login without opening detail',async({page})=>{
 const writes=await wallLikeFixture(page,true)
 await page.locator('.wall-note').getByRole('button',{name:'点赞',exact:true}).click()
 const auth=page.locator('.auth-dialog');await expect(auth).toBeVisible()
 await auth.getByLabel('用户名',{exact:true}).fill('learner')
 await expect(page.locator('dialog[aria-labelledby="wall-detail-heading"]')).not.toBeVisible();expect(writes()).toBe(0)
})
test('card heart toggles persisted likes without opening detail or duplicating pending writes',async({page})=>{
 const writes=await wallLikeFixture(page,false)
 const card=page.locator('.wall-note'),heart=card.getByRole('button',{name:'点赞',exact:true})
 await heart.click();await expect(heart).toBeDisabled()
 await expect(card.getByRole('button',{name:'取消点赞',exact:true})).toHaveAttribute('aria-pressed','true')
 await expect(card.getByRole('button',{name:'取消点赞',exact:true})).toContainText('1')
 expect(writes()).toBe(1)
 await card.getByRole('button',{name:'取消点赞',exact:true}).click();await expect(heart).toContainText('0');expect(writes()).toBe(2)
 await expect(page.locator('dialog[aria-labelledby="wall-detail-heading"]')).not.toBeVisible()
})

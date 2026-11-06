import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'

// Match desktop Windows scrollbars rather than Chromium's hidden headless defaults.
test.use({launchOptions:{ignoreDefaultArgs:['--hide-scrollbars']}})

test('learner navigation keeps its geometry through route and scrollbar changes',async({page},info)=>{
 await mockIdentity(page)
 await page.route('**/api/v1/reading/categories',r=>r.fulfill({json:[]}))
 await page.route('**/api/v1/reading/articles?*',r=>r.fulfill({json:{items:[],total:0,page:1,pageSize:6}}))
 await page.route('**/api/v1/reading/recommendation',r=>r.fulfill({json:null}))
 await page.goto('/reading')
 await page.addStyleTag({content:'html::-webkit-scrollbar{width:16px}'})
 await page.evaluate(()=>document.documentElement.style.overflowY='scroll')
 const shell=page.locator('.nav-shell')
 const before=await shell.boundingBox()
 const geometry=()=>page.locator('#learner-navigation .nav-link').evaluateAll(es=>es.map(e=>{const r=e.getBoundingClientRect();return {x:r.x,width:r.width}}))
 const baseline=await geometry()
 await page.evaluate(()=>document.documentElement.style.overflowY='hidden')
 await page.evaluate(()=>new Promise<void>(r=>requestAnimationFrame(()=>r())))
 expect((await shell.boundingBox())!.x).toBe(before!.x)
 await page.evaluate(()=>document.documentElement.style.overflowY='auto')
 const snapshots=[]
 for(const label of ['首页','背单词','外刊精读','专项练习','留言墙','外刊精读']){
  const menu=page.getByRole('button',{name:'打开导航',exact:true});if(await menu.isVisible())await menu.click()
  await page.getByRole('navigation',{name:'学习导航',exact:true}).getByRole('link',{name:label,exact:true}).click()
  await page.waitForTimeout(80)
  snapshots.push({label,shell:await shell.boundingBox(),links:await geometry(),scroll:await page.evaluate(()=>({x:scrollX,y:scrollY,gutter:getComputedStyle(document.documentElement).scrollbarGutter}))})
 }
 await expect(page.locator('html')).toHaveCSS('scrollbar-gutter','stable')
 for(const s of snapshots){expect(s.shell!.x).toBe(before!.x);expect(s.shell!.width).toBe(before!.width);if(info.project.name==='desktop')expect(s.links).toEqual(baseline)}
})


test('login dialog scroll locking keeps the navigation in place',async({page})=>{
 await mockIdentity(page,true)
 await page.goto('/reading')
 await page.addStyleTag({content:'html::-webkit-scrollbar{width:16px}'})
 const before=await page.locator('.nav-shell').boundingBox()
 await page.getByRole('button',{name:'登录',exact:true}).click()
 await expect(page.getByRole('dialog')).toBeVisible()
 expect((await page.locator('.nav-shell').boundingBox())!.x).toBe(before!.x)
 await page.getByRole('button',{name:'关闭认证窗口',exact:true}).click()
 await expect(page.getByRole('dialog')).not.toBeVisible()
 await expect.poll(async()=>(await page.locator('.nav-shell').boundingBox())!.x).toBe(before!.x)
})

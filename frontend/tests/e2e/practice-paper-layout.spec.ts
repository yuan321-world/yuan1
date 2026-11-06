import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'

test('paper listening uses available width and keeps desktop progress accessible while scrolling',async({page},info)=>{
 if(info.project.name==='desktop')await page.setViewportSize({width:1011,height:668})
 await mockIdentity(page)
 const slots=['WRITING','LISTENING','CLOZE','MATCH','DETAILED_1','DETAILED_2','TRANSLATION']
 const questions=Array.from({length:4},(_,i)=>({id:String(i+1),key:'q'+(i+1),type:'LISTENING',stem:`${i+1}. A synthetic listening question with enough text to check the question layout.`,options:['First option','Second option','Third option','Fourth option']}))
 const objective={id:'component',status:'IN_PROGRESS',readingType:'LISTENING',questions,answers:[],total:4,listening:{audioAssetId:'8',groups:[{key:'news1',title:'短篇新闻1',questionKeys:questions.map(q=>q.key)}]}}
 let attempt:any={id:'82',title:'Layout fixture',status:'IN_PROGRESS',lockVersion:0,currentSlot:'LISTENING',currentQuestionId:'1',responses:slots.map(slot=>({slot,text:'',choices:{}})),components:slots.map(slot=>({slot,title:slot,...(slot==='LISTENING'?{objective}:{})}))}
 await page.route('**/api/v1/practice/paper-attempts/82',r=>r.fulfill({json:attempt}))
 await page.route('**/api/v1/practice/paper-attempts/82/draft',r=>{attempt={...attempt,...r.request().postDataJSON(),lockVersion:attempt.lockVersion+1};return r.fulfill({json:attempt})})
 await page.route('**/api/v1/practice/paper-attempts/82/audio-ticket',r=>r.fulfill({json:{url:'/api/v1/practice/audio/8?ticket=fixture',expiresAt:new Date(Date.now()+600000).toISOString()}}))
 await page.route('**/api/v1/practice/audio/8?*',r=>r.fulfill({status:404}))
 await page.goto('/practice/paper-attempts/82')
 await expect(page.locator('.listening-question')).toHaveCount(4)
 await expect(page.locator('.listening-audio')).toHaveCSS('position','static')
 const width=await page.locator('.listening-workspace').evaluate(e=>({outer:e.getBoundingClientRect().width,inner:e.querySelector('.listening-clip-panel')!.getBoundingClientRect().width}))
 expect(width.inner/width.outer).toBeGreaterThan(info.project.name==='desktop'?.65:.95)
 const numberPanel=page.locator('.paper-progress')
 const radio=page.locator('.listening-question input[type=radio]').first()
 await expect(radio).toHaveCSS('min-height','0px')
 expect((await radio.boundingBox())!.height).toBeLessThanOrEqual(18)
 expect((await page.locator('.listening-question .practice-option').first().boundingBox())!.height).toBeLessThan(70)
 if(info.project.name==='desktop'){
  await expect(numberPanel).toBeVisible()
  const q=await page.locator('.listening-clip-panel').boundingBox(),n=await numberPanel.boundingBox()
  expect(n!.x).toBeGreaterThanOrEqual(q!.x+q!.width)
  await numberPanel.getByRole('button',{name:'第3题',exact:true}).click()
  await expect(numberPanel.getByRole('button',{name:'第3题',exact:true})).toHaveAttribute('aria-pressed','true')
 }
 const progress=page.locator('.paper-progress')
 if(info.project.name==='desktop'){
  await expect(progress).toHaveCSS('position','sticky')
  await page.evaluate(()=>window.scrollTo(0,650))
  await expect.poll(()=>progress.evaluate(e=>e.getBoundingClientRect().top)).toBeGreaterThanOrEqual(110)
  const box=await progress.boundingBox();expect(box!.y+box!.height).toBeLessThanOrEqual(668-16)
  await expect(progress.getByRole('navigation',{name:'全部题号',exact:true})).toBeVisible()
  await progress.evaluate(e=>e.scrollTop=e.scrollHeight)
  await expect(progress.getByRole('button',{name:'交卷并查看结果',exact:true})).toBeInViewport()
 }else {await expect(progress).toHaveCount(0);await page.getByRole('button',{name:'查看全部题号',exact:true}).click();await expect(page.locator('dialog[open]').getByRole('button',{name:'第3题',exact:true})).toBeVisible();await page.getByRole('button',{name:'关闭题号面板',exact:true}).click()}
 await page.screenshot({path:'../.tool-cache/20261025-paper-sheet/listening-layout-'+info.project.name+'.png',fullPage:false})
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

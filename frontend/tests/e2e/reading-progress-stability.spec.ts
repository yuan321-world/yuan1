import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
test('background position saves keep the status and read button stable',async({page})=>{
 await page.addInitScript(()=>{window.IntersectionObserver=class{constructor(callback:any){(window as any).__progressObserver=callback}observe(){}disconnect(){}unobserve(){}takeRecords(){return []}root=null;rootMargin='';thresholds=[]} as any})
 await mockIdentity(page)
 const version={id:'11',articleId:'10',versionNo:1,title:'Small steps',titleZh:'小小进步',category:{id:'1',name:'学习'},difficulty:'B1',wordCount:8,estimatedMinutes:1,author:'Lingua Loop',sourceType:'ORIGINAL',usageNote:'原创',content:{schemaVersion:1,paragraphs:[{key:'p0001',sentences:[{key:'p0001-s0001',text:'Every small step matters.'},{key:'p0001-s0002',text:'Keep reading every day.'}]}]}}
 const progress={id:'1',articleId:'10',articleVersionId:'11',sentenceKey:'p0001-s0001',offset:0,status:'READING',lockVersion:0,updatedAt:'2026-11-08T00:00:00Z',completedAt:null}
 await page.route('**/api/v1/reading/articles/10/start',r=>r.fulfill({json:{version,progress,created:false}}));let release:undefined|(()=>void),calls=0
 await page.route('**/api/v1/reading/progress/11',async r=>{calls++;await new Promise<void>(resolve=>release=resolve);const input=r.request().postDataJSON();await r.fulfill({json:{...progress,...input,lockVersion:calls,status:input.completed?'COMPLETED':'READING'}})})
 await page.goto('/reading/10');await expect(page.locator('.reading-progress-bar')).toBeVisible();const bar=page.locator('.reading-progress-bar'),label=bar.getByRole('status'),button=bar.getByRole('button',{name:'标记为已读',exact:true});const original=await label.textContent()
 await page.evaluate(()=>{const el=document.getElementById('p0001-s0002')!;(window as any).__progressObserver([{isIntersecting:true,target:el,boundingClientRect:{top:180}}])})
 await expect(bar.getByRole('button',{name:'重试保存',exact:true})).toHaveCount(0);await expect(label).toHaveText(original!);await expect.poll(()=>calls,{timeout:5000}).toBe(1);await expect(button).toBeEnabled();await expect(label).toHaveText(original!);await button.click();await expect(bar.getByRole('button',{name:'正在标记…',exact:true})).toBeDisabled();release?.();await expect.poll(()=>calls).toBe(2);release?.();await expect(bar.getByRole('button',{name:'已读完',exact:true})).toBeDisabled();await expect(label).toHaveText(original!);await expect(bar.getByRole('button',{name:'重试保存',exact:true})).toHaveCount(0)
})

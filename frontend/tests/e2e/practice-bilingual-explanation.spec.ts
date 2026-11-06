import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'

test('completed detailed reading keeps English evidence and Chinese explanation on separate lines',async({page})=>{
 await mockIdentity(page)
 const explanation='【关键英文】\nIt helps.\n【中文释义】\n有帮助。\n【解析】练习有助于学习。'
 const article={id:'10',articleId:'9',versionNo:1,title:'Evidence fixture',titleZh:'英文定位测试',content:{schemaVersion:1,paragraphs:[{key:'p1',sentences:[{key:'s1',text:'It helps.'}]}]},category:{id:'1',code:'education',name:'教育'},wordCount:2,estimatedMinutes:1,sourceType:'ORIGINAL',usageNote:'Test fixture',summary:'',difficulty:'B1',author:'',sourceUrl:null}
 const attempt={id:'4242',setId:'1',setVersionId:'3',quizVersionId:'4',kind:'FULL',status:'COMPLETED',version:1,currentQuestionId:'101',article,readingType:'DETAILED',title:'双语解析测试',questions:[{id:'101',key:'q1',type:'DETAIL',stem:'What helps?',options:['Practice','Waiting','Nothing','Stopping']}],answers:[{questionId:'101',draftChoice:'A',submittedChoice:'A',correct:true,correctOption:'A',explanation,flagged:false}],total:1,correctCount:1,startedAt:'2026-10-24T00:00:00Z',completedAt:'2026-10-24T00:01:00Z'}
 await page.route('**/api/v1/practice/attempts/4242',r=>r.fulfill({json:attempt}))
 await page.goto('/practice/attempts/4242')
 const tab=page.getByRole('button',{name:'成绩解析',exact:true});if(await tab.isVisible())await tab.click()
 const result=page.locator('p.practice-explanation').first();await expect(result).toBeVisible()
 const lines=await result.evaluate(e=>{
  const walker=document.createTreeWalker(e,NodeFilter.SHOW_TEXT);let node:Node|null
  while((node=walker.nextNode())){if(node.textContent?.includes('【关键英文】')){
   const top=(s:string)=>{const i=node!.textContent!.indexOf(s),r=document.createRange();r.setStart(node!,i);r.setEnd(node!,i+s.length);return r.getBoundingClientRect().top}
   return [top('【关键英文】'),top('It helps.'),top('【中文释义】')]
  }}throw new Error('Explanation missing')
 })
 expect(lines[1]!).toBeGreaterThan(lines[0]!);expect(lines[2]!).toBeGreaterThan(lines[1]!)
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

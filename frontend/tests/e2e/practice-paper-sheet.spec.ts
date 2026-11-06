import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
const slots=['WRITING','LISTENING','CLOZE','MATCH','DETAILED_1','DETAILED_2','TRANSLATION']
const article=(slot:string)=>({id:'10',articleId:'9',versionNo:1,title:slot==='CLOZE'?'A changing city':'Reading passage',content:{schemaVersion:1,paragraphs:[{key:'p',sentences:[{key:'s',text:slot==='CLOZE'?Array.from({length:10},(_,i)=>`People {{${26+i}}} learn together.`).join(' '):slot==='MATCH'?'[A] People learn together.\n\n[B] Practice helps everyone.':'Evidence supports careful reading.'}]}]}})
function fixture(){return {id:'84',paperId:'8',paperVersionId:'80',title:'2024年6月四级 第1套 完整试卷',sourceNote:'Synthetic layout fixture',status:'IN_PROGRESS',lockVersion:0,currentSlot:'WRITING',currentQuestionId:null,responses:slots.map(slot=>({slot,text:'',choices:{}})),components:slots.map((slot,index)=>{
 if(['WRITING','TRANSLATION'].includes(slot))return {slot,type:slot,title:slot,text:'',wordCount:0,prompt:{prompt:slot==='WRITING'?'Write a proposal about a study room.':'社区组织步行。',requirements:'Complete the task.',minWords:120,maxWords:180,minutes:30}}
 const start=({LISTENING:1,CLOZE:26,MATCH:36,DETAILED_1:46,DETAILED_2:51} as any)[slot],count=slot==='LISTENING'?25:slot.startsWith('DETAILED')?5:10
 const questions=Array.from({length:count},(_,i)=>({id:String(start+i),key:'q'+(start+i),type:slot.startsWith('DETAILED')?'DETAIL':slot,stem:slot==='CLOZE'?String(start+i):`${start+i}. What does careful practice support?`,options:['CLOZE','MATCH'].includes(slot)?[]:['Learning','Waiting','Nothing','Stopping']}))
 return {slot,type:slot,title:slot,objective:{id:'paper-component',status:'IN_PROGRESS',article:article(slot),readingType:slot.startsWith('DETAILED')?'DETAILED':slot,questions,answers:questions.map(q=>({questionId:q.id,draftChoice:null})),total:count,wordBank:slot==='CLOZE'?Array.from({length:15},(_,i)=>'word'+i):[],listening:slot==='LISTENING'?{audioAssetId:'8',groups:[{key:'news1',title:'短篇新闻1',questionKeys:questions.slice(0,2).map(q=>q.key)},{key:'rest',title:'后续片段',questionKeys:questions.slice(2).map(q=>q.key)}]}:null}}
 }),startedAt:'2026-10-25T00:00:00Z',completedAt:null}}
test('continuous paper keeps all questions left and full card right with inline cloze and persistence',async({page},info)=>{
 await mockIdentity(page);let attempt:any=fixture();const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message))
 await page.route('**/api/v1/practice/paper-attempts/84',r=>r.fulfill({json:attempt}))
 await page.route('**/api/v1/practice/paper-attempts/84/draft',r=>{attempt={...attempt,...r.request().postDataJSON(),lockVersion:attempt.lockVersion+1};return r.fulfill({json:attempt})})
 await page.route('**/api/v1/practice/paper-attempts/84/audio-ticket',r=>r.fulfill({json:{url:'/api/v1/practice/audio/8?ticket=fixture',expiresAt:new Date(Date.now()+600000).toISOString()}}))
 await page.route('**/api/v1/practice/audio/8?*',r=>r.fulfill({status:404}))
 await page.goto('/practice/paper-attempts/84')
 await expect(page.locator('.paper-part')).toHaveCount(7)
 expect(await page.locator('.paper-part').evaluateAll(es=>es.map(e=>e.getAttribute('data-slot')))).toEqual(slots)
 await expect(page.locator('.listening-question')).toHaveCount(25)
 await expect(page.locator('.practice-match-paper-question')).toHaveCount(10)
 await expect(page.locator('.paper-part[data-slot=WRITING]')).toBeInViewport()
 await expect(page.locator('.practice-match-mobile-bar')).toHaveCount(0)
 await page.getByLabel('英文作文',{exact:true}).fill('A quiet room supports learning.')
 const openCard=async()=>{if(info.project.name==='mobile')await page.getByRole('button',{name:'查看全部题号',exact:true}).click()}
 const card=page.locator('.paper-answer-card:visible')
 await openCard();await expect(card.getByRole('button',{name:/^第\d+题$/})).toHaveCount(55)
 await expect(card).toContainText('1 / 57')
 await card.getByRole('button',{name:'第25题',exact:true}).click()
 await expect(page.locator('.listening-question[data-question-id="25"]')).toBeInViewport()
 await page.locator('.listening-question[data-question-id="25"]').getByRole('radio').first().check()
 await openCard();await expect(card.getByRole('button',{name:'第25题',exact:true})).toHaveClass(/answered/)
 await card.getByRole('button',{name:'第26题',exact:true}).click()
 const cloze=page.locator('.practice-cloze-player'),bank=cloze.locator('.practice-cloze-bank'),passage=cloze.locator('.practice-cloze-article')
 await expect(bank).toBeVisible();expect(await bank.evaluate(e=>e.matches(':modal'))).toBe(false)
 const bankBox=await bank.boundingBox(),passageBox=await passage.boundingBox();expect(bankBox!.y+bankBox!.height).toBeLessThanOrEqual(passageBox!.y+1)
 await cloze.getByRole('button',{name:/^第26空，/}).click();await cloze.getByRole('button',{name:/^A，/}).click()
 await cloze.getByRole('button',{name:/^第27空，/}).click();await cloze.getByRole('button',{name:/^A，/}).click();await page.getByRole('button',{name:'取消',exact:true}).click()
 await expect(cloze.getByRole('button',{name:/^第26空，/})).toContainText('word0')
 await expect.poll(()=>attempt.responses.find((r:any)=>r.slot==='CLOZE').choices['26']).toBe('A')
 await page.getByRole('button',{name:'保存草稿',exact:true}).click();await page.reload()
 await expect(cloze.getByRole('button',{name:/^第26空，/})).toContainText('word0')
 await expect(page.getByLabel('英文作文',{exact:true})).toHaveValue('A quiet room supports learning.')
 await openCard();await card.getByRole('button',{name:'第51题',exact:true}).click()
 await expect(page.locator('.paper-question[data-question-id="51"]')).toBeInViewport()
 await page.locator('.paper-question[data-question-id="51"]').getByRole('radio').nth(1).check()
 await expect.poll(()=>attempt.responses.find((r:any)=>r.slot==='DETAILED_2').choices['51']).toBe('B')
 expect(attempt.responses.find((r:any)=>r.slot==='LISTENING').choices['25']).toBe('A')
 if(info.project.name==='desktop'){
  await expect(page.locator('.paper-progress')).toHaveCSS('position','sticky')
  const b=await page.locator('.paper-progress').boundingBox();expect(b!.y).toBeGreaterThanOrEqual(110)
  expect(b!.y+b!.height).toBeLessThanOrEqual(1000-12)
 }
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect(errors).toEqual([])
 await page.screenshot({path:'../.tool-cache/20261025-paper-sheet/sheet-'+info.project.name+'.png'})
})

test('paper matching keeps undo after answering a different row',async({page})=>{
 await mockIdentity(page);let a:any=fixture()
 await page.route('**/api/v1/practice/paper-attempts/84',r=>r.fulfill({json:a}))
 await page.route('**/api/v1/practice/paper-attempts/84/draft',r=>{a={...a,...r.request().postDataJSON(),lockVersion:a.lockVersion+1};return r.fulfill({json:a})})
 await page.route('**/api/v1/practice/paper-attempts/84/audio-ticket',r=>r.fulfill({status:404,json:{code:'NOT_FOUND'}}))
 await page.goto('/practice/paper-attempts/84')
 const match=page.locator('.practice-match-player'),choice=page.getByLabel('第37题匹配段落',{exact:true})
 await choice.selectOption('A')
 await expect(match.getByRole('button',{name:'撤销上一步',exact:true})).toBeEnabled()
 await match.getByRole('button',{name:'撤销上一步',exact:true}).click();await expect(choice).toHaveValue('')
})

test('complete card reveals a matching question hidden by its filter',async({page},info)=>{
 await mockIdentity(page);let a:any=fixture()
 await page.route('**/api/v1/practice/paper-attempts/84',r=>r.fulfill({json:a}))
 await page.route('**/api/v1/practice/paper-attempts/84/draft',r=>{a={...a,...r.request().postDataJSON(),lockVersion:a.lockVersion+1};return r.fulfill({json:a})})
 await page.route('**/api/v1/practice/paper-attempts/84/audio-ticket',r=>r.fulfill({status:404,json:{code:'NOT_FOUND'}}))
 await page.goto('/practice/paper-attempts/84')
 await page.getByLabel('第37题匹配段落',{exact:true}).selectOption('A')
 await page.getByLabel('筛选题目',{exact:true}).selectOption('unanswered')
 await expect(page.locator('.practice-match-paper-question[data-question-id="37"]')).toHaveCount(0)
 if(info.project.name==='mobile')await page.getByRole('button',{name:'查看全部题号',exact:true}).click()
 await page.locator('.paper-answer-card:visible').getByRole('button',{name:'第37题',exact:true}).click()
 await expect(page.locator('.practice-match-paper-question[data-question-id="37"]')).toBeInViewport()
 await expect(page.getByLabel('筛选题目',{exact:true})).toHaveValue('all')
})

import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
const article={id:'10',articleId:'9',versionNo:1,title:'Frozen passage',titleZh:'原文',content:{schemaVersion:1,paragraphs:[{key:'p',sentences:[{key:'s',text:'Evidence supports careful reading.'}]}]},category:{id:'1',code:'education',name:'教育'},wordCount:5,estimatedMinutes:1,sourceType:'THIRD_PARTY',usageNote:'Test fixture',summary:'',difficulty:'B1',author:'',sourceUrl:null}
const question={id:'501',key:'q51',type:'DETAIL',stem:'51. What does the evidence support?',options:['Reading','Waiting','Stopping','Nothing']}
const row={questionId:'501',setId:'5',setVersionId:'8',sourceAttemptId:'77',slot:'DETAILED_2',originalNumber:51,paperTitle:'2024年6月四级 第1套 完整试卷',readingType:'DETAILED',status:'PENDING',wrongCount:2,stem:question.stem,submittedChoice:'B',correctOption:'A',explanation:'【关键英文】Evidence supports careful reading.\n【中文释义】证据支持仔细阅读。'}
const attempt={id:'900',setId:'5',setVersionId:'8',quizVersionId:'9',kind:'MISTAKES',sourceAttemptId:null,status:'IN_PROGRESS',version:0,currentQuestionId:'501',total:1,article,questions:[question],answers:[{questionId:'501',draftChoice:null}],title:'Frozen passage',readingType:'DETAILED',wordBank:[],startedAt:'2026-10-25T01:00:00Z',completedAt:null,reviewSource:{attemptId:'77',slot:'DETAILED_2',originalNumbers:{'501':51}}}

test('paper mistakes retain source filters and start an original-number review',async({page},info)=>{
 await mockIdentity(page);const seen:URLSearchParams[]=[]
 await page.route('**/api/v1/practice/papers?*',r=>r.fulfill({json:{items:[],total:0,page:1,pageSize:20,years:[{value:'2024',count:1}],months:[{value:'6',count:1}]}}))
 await page.route('**/api/v1/practice/paper-mistakes?*',r=>{const q=new URL(r.request().url()).searchParams;seen.push(q);return r.fulfill({json:{items:q.get('status')==='RESOLVED'?[]:[row],total:q.get('status')==='RESOLVED'?0:1,page:1,pageSize:20}})})
 await page.route('**/api/v1/practice/paper-attempts/77/retry-mistakes',r=>{expect(r.request().postDataJSON().slot).toBe('DETAILED_2');return r.fulfill({json:attempt})})
 await page.route('**/api/v1/practice/attempts/900',r=>r.fulfill({json:attempt}))
 await page.goto('/practice/cet?exam=CET4&source=PAST&collection=PAST&section=paper&year=2024&month=6&sourceAttemptId=77')
 await page.getByRole('tab',{name:'错题本',exact:true}).click()
 await expect(page.getByText('第51题',{exact:false}).first()).toBeVisible()
 await expect.poll(()=>seen.at(-1)?.get('sourceAttemptId')).toBe('77')
 await page.getByLabel('整卷错题状态',{exact:true}).selectOption('RESOLVED');await expect.poll(()=>seen.at(-1)?.get('status')).toBe('RESOLVED')
 await page.getByLabel('整卷错题状态',{exact:true}).selectOption('PENDING')
 await page.getByRole('button',{name:'重做错题',exact:true}).click()
 await expect(page).toHaveURL(/practice\/attempts\/900/)
 await expect(page.locator('.practice-answer-panel h3')).toHaveText('51. What does the evidence support?')
 await expect(page.getByText('来自整卷',{exact:false}).first()).toBeVisible()
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

test('source jump opens the original completed question without saving a draft',async({page})=>{
 await mockIdentity(page);let writes=0
 const objective={...attempt,id:'paper-component',status:'COMPLETED',answers:[{questionId:'501',submittedChoice:'B',correct:false,correctOption:'A',explanation:row.explanation}],correctCount:0}
 const paper={id:'77',paperId:'1',paperVersionId:'2',title:row.paperTitle,sourceNote:'Frozen source',status:'COMPLETED',lockVersion:1,currentSlot:'WRITING',currentQuestionId:null,responses:[{slot:'DETAILED_2',text:'',choices:{'501':'B'}}],components:[{slot:'DETAILED_2',type:'DETAILED',title:'Frozen passage',objective}],startedAt:attempt.startedAt,completedAt:attempt.startedAt}
 await page.route('**/api/v1/practice/paper-attempts/77',r=>r.fulfill({json:paper}))
 await page.route('**/api/v1/practice/paper-attempts/77/draft',r=>{writes++;return r.fulfill({status:409,json:{code:'PRACTICE_CONFLICT'}})})
 await page.goto('/practice/paper-attempts/77?slot=DETAILED_2&questionId=501')
 await expect(page.locator('.paper-question h3')).toHaveText('51. What does the evidence support?')
 await expect(page.locator('.paper-question input[type=radio]').first()).toBeDisabled()
 expect(writes).toBe(0)
})


test('paper review keeps a dedicated wrongbook link when opened from single history',async({page})=>{
 await mockIdentity(page)
 await page.route('**/api/v1/practice/attempts/900',r=>r.fulfill({json:attempt}))
 await page.goto('/practice/attempts/900?returnTo='+encodeURIComponent('/practice/cet?exam=CET4&source=PAST&collection=PAST&section=reading&tab=history'))
 const link=page.getByRole('link',{name:'返回整卷错题本',exact:true})
 await expect(link).toHaveAttribute('href',/section=paper/)
 await expect(link).toHaveAttribute('href',/tab=mistakes/)
 await expect(page.getByRole('button',{name:'退出并保存',exact:true})).toBeVisible()
})

test('completed paper review conflict offers the existing owned draft',async({page})=>{
 await mockIdentity(page)
 await page.route('**/api/v1/practice/attempts/900',r=>r.fulfill({json:{...attempt,status:'COMPLETED',correctCount:0,answers:[{questionId:'501',submittedChoice:'B',correct:false,correctOption:'A',explanation:row.explanation}]}}))
 await page.route('**/api/v1/practice/paper-attempts/77/retry-mistakes',r=>r.fulfill({status:409,json:{code:'PRACTICE_REVIEW_ACTIVE',message:'该题组已有其他来源的练习',fieldErrors:{activeAttemptId:'901'}}}))
 await page.goto('/practice/attempts/900')
 await page.getByRole('button',{name:'重做本轮错题',exact:true}).click()
 await expect(page.getByRole('link',{name:'继续已有错题练习',exact:true})).toHaveAttribute('href',/practice\/attempts\/901/)
})

import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
test('listening player loads the frozen audio and saves choices and playback before submitting',async({page})=>{
 await mockIdentity(page)
 let attempt:any={id:'61',setId:'6',setVersionId:'60',quizVersionId:'60',kind:'FULL',status:'IN_PROGRESS',version:0,currentQuestionId:'601',total:2,title:'整套听力测试夹具',readingType:'LISTENING',article:null,listening:{audioAssetId:'8',groups:[{key:'all',title:'整套测试',questionKeys:['q1','q2']}]},playback:{positionSeconds:0,rate:1.25},questions:[{id:'601',key:'q1',type:'LISTENING',stem:'1. Choose the test option.',options:['One','Two','Three','Four']},{id:'602',key:'q2',type:'LISTENING',stem:'2. Choose the test option.',options:['One','Two','Three','Four']}],answers:[{questionId:'601',draftChoice:null},{questionId:'602',draftChoice:null}]}
 await page.route('**/api/v1/practice/attempts/61',r=>r.fulfill({json:attempt}))
 await page.route('**/api/v1/practice/attempts/61/audio-ticket',r=>r.fulfill({json:{url:'/api/v1/practice/audio/8?ticket=mock-only',expiresAt:new Date(Date.now()+600000).toISOString()}}))
 await page.route('**/api/v1/practice/audio/8?*',r=>r.fulfill({status:404,body:'fixture has no real audio'}))
 let saved:any
 await page.route('**/api/v1/practice/attempts/61/draft',r=>{saved=r.request().postDataJSON();for(const a of saved.answers){attempt.answers.find((x:any)=>x.questionId===a.questionId).draftChoice=a.choice}attempt={...attempt,version:attempt.version+1,playback:saved.playback||attempt.playback};return r.fulfill({json:attempt})})
 await page.goto('/practice/attempts/61');await expect(page.getByLabel('整套听力音频')).toBeVisible();await expect(page.getByText('音频暂时无法播放',{exact:false})).toBeVisible();await expect(page.getByText('配套原文',{exact:true})).toHaveCount(0)
 await page.locator('[data-question-id="601"]').getByLabel('B. Two',{exact:true}).check();await expect.poll(()=>saved?.answers?.[0]?.choice).toBe('B');await page.getByLabel('听力倍速').selectOption('1.5');await expect.poll(()=>saved?.playback?.rate).toBe(1.5);await expect(page.getByRole('button',{name:'退出并保存',exact:true})).toBeEnabled()
})

test('admin whole-listening editor uploads an asset and saves it only as a draft',async({page})=>{
 await mockIdentity(page,false,true);await page.route('**/api/v1/admin/articles?*',r=>r.fulfill({json:{items:[],total:0,page:1,pageSize:50}}))
 await page.route('**/api/v1/admin/practice/audio-assets',r=>r.fulfill({json:{id:'8',originalName:'test.mp3',byteSize:8}}))
 let saved:any
 const article={id:'10',articleId:'9',versionNo:1,title:'Synthetic listening',titleZh:'听力测试',content:{schemaVersion:1,paragraphs:[{key:'p1',sentences:[{key:'s1',text:'Synthetic test transcript.'}]}]},category:{id:'1',code:'STUDY',name:'学习'},wordCount:4,estimatedMinutes:1,sourceType:'ORIGINAL',usageNote:'test',summary:'',difficulty:'B1',author:'',sourceUrl:null}
 let record:any
 await page.route('**/api/v1/admin/practice/material-sets',r=>{saved=r.request().postDataJSON();record={...saved,id:'6',articleId:'9',articleVersionId:'10',examCode:'CET4',readingType:'LISTENING',listening:{...saved.listening,audioReviewed:false},reviewedQuestionKeys:[],sourceReviewed:false,status:'DRAFT',revision:0,lockVersion:0,materialOnly:true,article,checks:[]};return r.fulfill({json:record})})
 await page.route('**/api/v1/admin/practice/sets/6',r=>r.fulfill({json:record}));await page.route('**/api/v1/admin/articles/9',r=>r.fulfill({json:{id:'9',draftVersionId:'10',draft:article}}))
 await page.goto('/admin/practice/new');await page.locator('select').filter({has:page.locator('option[value="LISTENING"]')}).selectOption('LISTENING')
 await page.getByLabel('上传完整音频').setInputFiles({name:'test.mp3',mimeType:'audio/mpeg',buffer:Buffer.from([255,251,144,0,0,0,0,0])});await expect(page.getByText('test.mp3 · 资产 8',{exact:false})).toBeVisible();await page.locator('#practice-material-body').fill('Synthetic listening transcript used only in browser tests.');await page.getByRole('button',{name:'添加套内分组',exact:true}).click();await page.getByRole('button',{name:'保存草稿',exact:true}).first().click();await expect.poll(()=>saved?.listening?.audioAssetId).toBe('8');expect(saved.questions[0].type).toBe('LISTENING');expect(record.status).toBe('DRAFT');await expect(page).toHaveURL(/admin\/practice\/6/)
})


test('whole listening shows three separated number sections and keeps navigation',async({page},testInfo)=>{
 await mockIdentity(page)
 const questions=Array.from({length:25},(_,i)=>({id:String(701+i),key:'q'+(i+1),type:'LISTENING',stem:'Navigation fixture question '+(i+1),options:['One','Two','Three','Four']}))
 const groups=[['news1','短篇新闻1',0,2],['news2','短篇新闻2',2,4],['news3','短篇新闻3',4,7],['conversation1','长对话1',7,11],['conversation2','长对话2',11,15],['passage1','听力篇章1',15,18],['passage2','听力篇章2',18,21],['passage3','听力篇章3',21,25]].map(([key,title,start,end])=>({key,title,questionKeys:questions.slice(Number(start),Number(end)).map(q=>q.key)}))
 const attempt:any={id:'71',status:'IN_PROGRESS',version:0,currentQuestionId:'701',total:25,title:'题号分组测试夹具',readingType:'LISTENING',article:null,listening:{audioAssetId:'8',groups},playback:{positionSeconds:0,rate:1},questions,answers:questions.map(q=>({questionId:q.id,draftChoice:null}))}
 await page.route('**/api/v1/practice/attempts/71',r=>r.fulfill({json:attempt}))
 await page.route('**/api/v1/practice/attempts/71/audio-ticket',r=>r.fulfill({json:{url:'/api/v1/practice/audio/8?ticket=fixture',expiresAt:new Date(Date.now()+600000).toISOString()}}))
 await page.route('**/api/v1/practice/audio/8?*',r=>r.fulfill({status:404}))
 await page.route('**/api/v1/practice/attempts/71/draft',r=>{const data=r.request().postDataJSON();attempt.currentQuestionId=data.currentQuestionId;for(const a of data.answers){const answer=attempt.answers.find((x:any)=>x.questionId===a.questionId);answer.draftChoice=a.choice;if(a.flagged!==undefined)answer.flagged=a.flagged};attempt.version++;return r.fulfill({json:attempt})})
 await page.goto('/practice/attempts/71')
 const sections=page.locator('aside .listening-number-section')
 if(testInfo.project.name==='mobile')await page.getByRole('button',{name:'展开题号',exact:true}).click()
 await expect(sections.locator('h3')).toHaveText(['短篇新闻','长对话','听力短文'])
 for(const [i,count] of [7,8,10].entries())await expect(sections.nth(i).getByRole('button')).toHaveCount(count)
 const active=page.locator('.listening-question')
 await expect(page.locator('.listening-clip-panel')).toHaveCount(8)
 const audioNode=await page.getByLabel('整套听力音频').elementHandle()
 async function openNumbers(){const toggle=page.getByRole('button',{name:'展开题号',exact:true});if(await toggle.isVisible())await toggle.click()}
 if(testInfo.project.name==='mobile')await openNumbers()
 await expect(active).toHaveCount(25)
 await expect(page.getByRole('heading',{name:'1. Navigation fixture question 1',exact:true})).toBeVisible()
 await expect(page.getByRole('heading',{name:'2. Navigation fixture question 2',exact:true})).toBeVisible()
 await expect(page.locator('.listening-overview')).toHaveCount(0)
 await page.locator('[data-question-id="702"]').getByLabel('B. Two',{exact:true}).check()
 await page.getByRole('button',{name:'下一片段',exact:true}).click()
 await expect(page.getByRole('heading',{name:'3. Navigation fixture question 3',exact:true})).toBeVisible()
 await openNumbers();await page.getByRole('button',{name:'上一片段',exact:true}).click()
 await expect(page.locator('[data-question-id="702"]').getByLabel('B. Two',{exact:true})).toBeChecked()
 await openNumbers();await page.getByRole('button',{name:'第5题',exact:true}).click();await expect(active).toHaveCount(25)
 await openNumbers();await page.getByRole('button',{name:'第8题',exact:true}).click();await expect(active).toHaveCount(25)
 await page.getByRole('button',{name:'标记第9题待检查',exact:true}).click()
 await openNumbers();await expect(page.getByRole('button',{name:'第9题',exact:true})).toContainText('待检查')
 await openNumbers();await page.getByRole('button',{name:'第1题',exact:true}).click()
 expect(await audioNode!.evaluate(el=>el===document.querySelector('[aria-label="整套听力音频"]'))).toBe(true)
 await expect(active).toHaveCount(25)
 await page.evaluate(()=>{(document.activeElement as HTMLElement)?.blur();window.scrollTo(0,0)})
 await page.screenshot({path:'../.tool-cache/practice-listening-source/clip-questions-'+testInfo.project.name+'.png',fullPage:true})
 await expect(page.getByRole('status').filter({hasText:'选择已保存'})).toBeVisible()
 attempt.status='COMPLETED';attempt.correctCount=0
 await page.reload()
 await expect(page.getByRole('region',{name:'本次练习评分'}).locator('strong')).toHaveText('0 / 100')
 const overview=page.locator('.practice-listening-player > details.listening-overview')
 await expect(overview).toHaveCount(1)
 expect(await overview.getAttribute('open')).toBeNull()
 await overview.locator('summary').click()
 const list=overview.getByRole('region',{name:'全部听力题目'})
 await expect(list.getByRole('button')).toHaveCount(25)
 expect(await list.evaluate(e=>e.clientHeight<=360&&e.scrollHeight>e.clientHeight)).toBe(true)
 await list.getByRole('button',{name:'查看第25题',exact:true}).click()
 await expect(page.getByRole('heading',{name:'25. Navigation fixture question 25',exact:true})).toBeInViewport()
 expect(await overview.getAttribute('open')).toBeNull()
 await page.evaluate(()=>{(document.activeElement as HTMLElement)?.blur();window.scrollTo(0,0)})
 await page.screenshot({path:'../.tool-cache/practice-listening-source/number-groups-'+testInfo.project.name+'.png',fullPage:true})
})

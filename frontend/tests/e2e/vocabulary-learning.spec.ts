import {test,expect,type Page} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
import type {Session} from '../../src/api/vocabulary-learning'
async function begin(page:Page,kind='NEW',mode='看词模式'){await page.getByRole('button',{name:kind==='NEW'?'开始学习':'开始复习',exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:new RegExp(mode)}).click()}
async function fixture(page:Page,{review=false,empty=false}={}){
 await mockIdentity(page);let session:Session|null=null,joined=!empty,failNext=false;const requests:any[]=[],snapshots=new Map<string,Session>()
 const overview=()=>({candidates:empty?0:1,unlearned:joined?1:0,due:review?1:0,nextDueAt:null,newToday:0,reviewToday:0,settings:{newGoal:20,reviewGoal:50},savedSessions:session&&session.status!=='COMPLETED'?[{...session,sourceTitle:'个人生词'}]:[],sessionId:session?.status==='COMPLETED'?null:session?.id||null})
 await page.route('**/api/v1/vocabulary/learning/**',async route=>{const req=route.request(),path=new URL(req.url()).pathname,body=req.method()==='GET'?null:req.postDataJSON();
  if(path.endsWith('/stats'))return route.fulfill({json:{days:[],totalLearned:0,totalReviewPasses:0}})
  if(path.endsWith('/overview'))return route.fulfill({json:overview()})
  if(path.includes('/candidates?')||path.endsWith('/candidates'))return route.fulfill({json:{items:empty?[]:[{wordId:'w1',lemma:'apple',meaning:'苹果',joined,paused:false,unavailableReason:''}],page:1,pageSize:20,total:empty?0:1}})
  if(path.endsWith('/words')&&req.method()==='POST'){joined=true;return route.fulfill({status:200,body:''})}
  if(path.endsWith('/words'))return route.fulfill({json:{items:joined?[{id:'l1',entryId:'e1',lemma:'apple',meaning:'苹果',active:true,stage:review?2:0,dueAt:review?'2026-10-18T01:00:00Z':null}]:[],page:1,pageSize:20,total:joined?1:0}})
  if(path.endsWith('/session'))return route.fulfill({status:200,body:JSON.stringify(session?.status==='COMPLETED'?null:session),contentType:'application/json'})
  if(path.endsWith('/settings'))return route.fulfill({json:body})
  if(req.method()==='GET'&&path===`/api/v1/vocabulary/learning/sessions/${session?.id}`)return route.fulfill({json:session})
  requests.push(body)
  if(failNext){failNext=false;return route.fulfill({status:503,json:{code:'TEMPORARY',message:'保存暂不可用'}})}
  if(snapshots.has(body.requestId))return route.fulfill({json:snapshots.get(body.requestId)})
  if(path.endsWith('/sessions')&&session?.status==='PAUSED'){session.status='ACTIVE';session.version++}
  else if(path.endsWith('/sessions')){session={id:'s1',version:0,kind:body.kind,mode:body.mode,status:'ACTIVE',phase:'QUESTION',current:{id:'i1',lemma:body.mode==='SPELLING'?null:'apple',phonetic:'/ˈæpl/',partOfSpeech:'n.',meaning:'苹果',extra:'',options:[{id:'a',text:'苹果'},{id:'b',text:'橙子'},{id:'c',text:'桌子'},{id:'d',text:'书本'}],spellingTarget:body.mode==='SPELLING'?'apple':null,definition:null,source:null,outcome:null},queue:[{id:'i1',meaning:'苹果',current:true,failures:0}],passed:0,total:1,results:[]}}
  else if(session){session=structuredClone(session);session.version++
   if(path.endsWith('/state'))session.status=body.status
   if(path.endsWith('/feedback')){session.phase='DEFINITION';const correct=body.action!=='UNKNOWN'&&(!body.optionId||body.optionId==='a');session.passed=correct?1:0;session.current={...session.current!,lemma:'apple',spellingTarget:null,definition:{lemma:'apple',senses:[{partOfSpeech:'n.',definitionZh:'苹果',definitionEn:'A fruit.'}],sourceNote:'测试词典'},outcome:{correct,action:body.action,selectedText:'',stage:1,dueAt:correct?'2026-10-20T01:00:00Z':null}};if(!correct)session.queue[0]!.failures++}
   if(path.endsWith('/next')){if(session.passed){session.status='COMPLETED';session.results=[{lemma:'apple',failures:session.queue[0]!.failures,outcome:session.current!.outcome!}];session.current=null;session.queue=[]}else{session.phase='QUESTION';session.current={...session.current!,definition:null,outcome:null}}}
  }
  snapshots.set(body.requestId,structuredClone(session!));return route.fulfill({json:session})
 })
 return {requests,fail(){failNext=true}}
}
test('recognition direct known, unknown tail, reload definition and result preserve manual next',async({page})=>{
 await fixture(page);await page.goto('/vocabulary');await begin(page);await expect(page.locator('.vl-choices button')).toHaveCount(4);await page.getByRole('button',{name:'不认识',exact:true}).click();await expect(page.locator('.vl-definition')).toContainText('先记住这一次');await page.getByRole('button',{name:'继续，稍后再练',exact:true}).click();await expect(page.locator('.vl-choices')).toBeVisible();await page.getByRole('button',{name:'认识',exact:true}).click();await expect(page.locator('.vl-definition')).toContainText('本轮通过');await page.reload();await expect(page.locator('.vl-definition')).toBeVisible();await page.getByRole('button',{name:'下一个',exact:true}).click();await expect(page.getByRole('heading',{name:'这一轮，全部通过了。'})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})
test('spelling clears wrong letters, auto submits once, retries same command, pauses and restores prefixes',async({page})=>{
 const server=await fixture(page);await page.goto('/vocabulary');await begin(page,'NEW','跟打模式');const input=page.getByLabel('输入英文拼写');await input.pressSequentially('x');await expect(input).toHaveValue('');await expect(page.locator('#vl-spelling-error')).toContainText('保留正确部分');await input.pressSequentially('ap');await page.getByRole('button',{name:'退出并保存',exact:true}).click();await page.getByRole('button',{name:'继续学习',exact:true}).click();await expect(input).toHaveValue('ap');await page.reload();await expect(input).toHaveValue('ap');await input.fill('');server.fail();await input.pressSequentially('apple');await expect(page.locator('.vl-notice')).toContainText('保存暂不可用');await expect(page.locator('.vl-definition')).toHaveCount(0);await page.getByRole('button',{name:'重试刚才的操作',exact:true}).click();await expect(page.locator('.vl-definition')).toContainText('本轮通过');const feedback=server.requests.filter(r=>r.action==='SPELLING');expect(feedback).toHaveLength(2);expect(feedback[0].requestId).toBe(feedback[1].requestId);await expect(page.getByRole('button',{name:'下一个',exact:true})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})
test('due review offers same modes and new queue never claims to be a whole wordbook',async({page})=>{
 await fixture(page,{review:true});await page.goto('/vocabulary');await begin(page,'REVIEW');await expect(page.getByRole('heading',{name:'到期复习',exact:true})).toBeVisible();await page.getByRole('button',{name:'A 苹果',exact:true}).click();await expect(page.locator('.vl-definition')).toBeVisible()
})
test('no vocabulary means real empty state and no fake task',async({page})=>{
 await fixture(page,{empty:true});await page.goto('/vocabulary');await expect(page.getByRole('button',{name:'开始学习',exact:true})).toBeDisabled();await expect(page.locator('.vl-page')).toContainText('还没有收藏的生词');await expect(page.locator('.vl-page')).toContainText('暂无到期复习词');await expect(page.getByRole('button',{name:'切换来源',exact:true})).toBeEnabled()
})
test('composition, invalid paste and touch hint keep the current word until complete',async({page},info)=>{
 await fixture(page);await page.goto('/vocabulary');await begin(page,'NEW','跟打模式');const input=page.getByLabel('输入英文拼写');await input.dispatchEvent('compositionstart');await input.fill('wrong');await expect(input).toHaveValue('wrong');await input.dispatchEvent('compositionend');await expect(input).toHaveValue('');await input.fill('applex');await expect(input).toHaveValue('apple');await input.fill('');await page.screenshot({path:`../.tool-cache/vocabulary-live-spelling-${info.project.name}.png`,fullPage:true});await input.fill('APPLE');await expect(page.locator('.vl-definition')).toContainText('本轮通过');await expect(page.getByRole('button',{name:'下一个',exact:true})).toBeVisible()
})
test('tablet learning home and card fit without dropping tools',async({page})=>{
 await page.setViewportSize({width:768,height:1000});await fixture(page);await page.goto('/vocabulary');await page.screenshot({path:'../.tool-cache/vocabulary-live-home-768.png',fullPage:true});await begin(page);await expect(page.locator('.vl-binary button')).toHaveCount(2);await expect(page.locator('.vl-choices button')).toHaveCount(4);await expect(page.getByRole('button',{name:'朗读当前单词',exact:true})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})


test('idle learning dashboard refreshes at Shanghai midnight and when returning without fetching a session',async({page})=>{
 await page.clock.install({time:new Date('2026-11-02T15:59:30Z')});await fixture(page);let reads=0,sessionReads=0,fresh=false;await page.route('**/api/v1/vocabulary/learning/overview',r=>{reads++;return r.fulfill({json:{candidates:1,unlearned:1,due:fresh?1:0,nextDueAt:null,newToday:fresh?0:20,reviewToday:0,settings:{newGoal:20,reviewGoal:50},savedSessions:[],sessionId:null}})});await page.route('**/api/v1/vocabulary/learning/session',r=>{sessionReads++;return r.fulfill({json:null})});await page.goto('/vocabulary');await expect(page.getByRole('button',{name:'开始学习',exact:true})).toBeDisabled();const initialSessionReads=sessionReads;fresh=true;await page.clock.fastForward(31000);await page.clock.fastForward(200);await expect(page.getByRole('button',{name:'开始学习',exact:true})).toBeEnabled();await expect(page.getByRole('button',{name:'开始复习',exact:true})).toBeEnabled();expect(sessionReads).toBe(initialSessionReads);const before=reads;await page.evaluate(()=>{window.dispatchEvent(new Event('focus'));document.dispatchEvent(new Event('visibilitychange'))});await page.clock.fastForward(200);await expect.poll(()=>reads).toBe(before+1);expect(sessionReads).toBe(initialSessionReads)
})

import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
test('compact article cards retain metadata and reachable favorite actions',async({page},info)=>{
 await mockIdentity(page)
 if(info.project.name==='desktop')await page.setViewportSize({width:718,height:668})
 const article={id:'10',versionId:'11',title:'Decoding Dyslexia',titleZh:'认识阅读障碍',summary:'了解阅读能力形成与阅读障碍支持，练习理解解释性文章和条件表达。',category:{id:'1',code:'STUDY',name:'学习'},difficulty:'B2',wordCount:648,estimatedMinutes:4,author:'Example author',sourceType:'THIRD_PARTY',sourceUrl:'https://example.test/source',usageNote:'许可说明',progress:null}
 await page.route('**/api/v1/reading/articles?*',r=>r.fulfill({json:{items:[article,{...article,id:'12',title:'Build Social Bonds to Protect Health in Our Community',titleZh:'如何养成健康习惯'}],total:2,page:1,pageSize:6}}))
 await page.goto('/reading');const card=page.locator('.reading-card').first();await expect(card).toContainText('648');await expect(card.getByRole('button',{name:'收藏文章：Decoding Dyslexia',exact:true})).toBeVisible();expect((await card.boundingBox())!.height).toBeLessThan(320);expect(Math.abs((await card.boundingBox())!.height-(await page.locator('.reading-card').nth(1).boundingBox())!.height)).toBeLessThan(1);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:`../.tool-cache/compact-library-${info.project.name}.png`,fullPage:true})
})
test('compact notebook and management rows retain every action',async({page},info)=>{
 await mockIdentity(page)
 if(info.project.name==='desktop')await page.setViewportSize({width:718,height:668})
 const word={id:'w1',entryId:'e1',lemma:'banana',phonetic:'bəˈnɑːnə',senses:[{partOfSpeech:'n.',definitionZh:'香蕉',definitionEn:''}],sourceNote:'本地词典',active:true,learning:null}
 await page.route('**/api/v1/vocabulary/words?**',r=>r.fulfill({json:{items:[word],total:1,page:1,pageSize:20}}))
 await page.route('**/api/v1/vocabulary/learning/words?**',r=>r.fulfill({json:{items:[{id:'l1',lemma:'banana',meaning:'香蕉',active:true,stage:1,proficient:false}],total:1,page:1,pageSize:20}}))
 await page.goto('/vocabulary?tab=words');const row=page.locator('.vn-library>.vn-row');await expect(row.getByRole('button',{name:'加入学习',exact:true})).toBeVisible();await expect(row.getByRole('button',{name:'取消收藏',exact:true})).toBeVisible();expect((await row.boundingBox())!.height).toBeLessThan(160)
 await page.locator('.vn-learning-management>summary').click();await expect(page.locator('.vn-learning').getByRole('button',{name:'暂停学习',exact:true})).toBeVisible();expect((await page.locator('.vn-learning .vn-row').boundingBox())!.height).toBeLessThan(130);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:`../.tool-cache/compact-notebook-${info.project.name}.png`,fullPage:true})
})
test('thirty day history is bounded and keyboard scrollable without dropping records',async({page},info)=>{
 await mockIdentity(page)
 const recentDays=Array.from({length:30},(_,i)=>({date:`2026-10-${String(i+1).padStart(2,'0')}`,newLearned:i,reviewed:i+1}))
 await page.route('**/api/v1/vocabulary/learning/stats',r=>r.fulfill({json:{today:'2026-10-30',weekStart:'2026-10-24',days:recentDays.slice(-7),recentDays,totalLearned:77,totalReviewPasses:31,due:27,paused:0,completedRounds:6,nextDueAt:null,independentReviewPasses:27,streakDays:3}}))
 await page.goto('/vocabulary');await page.locator('.vl-inline-records summary').click();const list=page.getByRole('list',{name:'近30天每日学习记录',exact:true});await expect(list.locator('li')).toHaveCount(30);expect((await list.boundingBox())!.height).toBeLessThanOrEqual(242);await list.focus();await page.keyboard.press('End');await expect.poll(()=>list.evaluate(el=>el.scrollTop)).toBeGreaterThan(0);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:`../.tool-cache/compact-history-${info.project.name}.png`,fullPage:true})
})

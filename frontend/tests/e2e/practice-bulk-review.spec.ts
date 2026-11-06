import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
test('whole group confirmation can cancel and retry; source and publication remain separate',async({page})=>{
 await mockIdentity(page,false,true)
  const article={id:'10',articleId:'9',versionNo:1,title:'Original practice',titleZh:'原创练习材料',content:{schemaVersion:1,paragraphs:[{key:'p1',sentences:[{key:'s1',text:'Small steps help us learn every day.'}]}]},category:{id:'1',code:'education',name:'教育'},wordCount:9,estimatedMinutes:1,sourceType:'ORIGINAL',usageNote:'原创测试',summary:'',difficulty:'B1',author:'',sourceUrl:null}
  const set={id:'1',articleId:'9',articleVersionId:'10',examCode:'CET4',readingType:'DETAILED',metadata:{title:'Original practice',titleZh:'原创专项',sourceKind:'ORIGINAL',topic:'education',sourceName:'原创测试',usageNote:'仅用于测试'},questions:[{key:'q1',type:'DETAIL',stem:'What helps?',options:['Steps','Waiting','Nothing','Stopping'],correctOption:'A',explanation:'持续练习。',sortOrder:0},{key:'q2',type:'DETAIL',stem:'What next?',options:['Try','Stop','Wait','Forget'],correctOption:'A',explanation:'继续尝试。',sortOrder:1}],reviewedQuestionKeys:[],sourceReviewed:false,status:'DRAFT',revision:0,lockVersion:1,article,checks:[]}

 let reviews=0,publishes=0,fail=true;
 await page.route('**/api/v1/admin/articles/9',r=>r.fulfill({json:{id:'9',draftVersionId:'10',publishedVersionId:'10',draft:article,status:'PUBLISHED',lockVersion:1}}));
 await page.route('**/api/v1/admin/practice/sets/1',r=>r.fulfill({json:set}));
 await page.route('**/api/v1/admin/practice/sets/1/review',r=>{const input=r.request().postDataJSON();expect(input).toEqual({expectedLockVersion:1,kind:'QUESTIONS',reviewed:true});reviews++;if(fail){fail=false;return r.fulfill({status:503,json:{code:'SERVICE_UNAVAILABLE',message:'校对暂不能保存'}})}set.lockVersion++;set.reviewedQuestionKeys=set.questions.map(q=>q.key) as never[];return r.fulfill({json:set})});
 await page.route('**/api/v1/admin/practice/sets/1/publish',r=>{publishes++;return r.fulfill({status:500})});
 await page.goto('/admin/practice/1');const button=page.getByRole('button',{name:'整组确认校对',exact:true});await expect(button).toBeVisible();
 await button.click();await page.getByRole('button',{name:'继续检查',exact:true}).click();expect(reviews).toBe(0);
 await button.click();await page.getByRole('button',{name:'确认已校对整组',exact:true}).click();await expect(page.getByRole('alert')).toContainText('校对暂不能保存');await expect(page.locator('#practice-overview input:checked')).toHaveCount(0);
 await button.click();await page.getByRole('button',{name:'确认已校对整组',exact:true}).click();await expect(page.getByRole('status')).toContainText('整组2题校对确认已保存');await expect(page.locator('#practice-overview input:checked')).toHaveCount(2);await expect(button).toBeDisabled();await expect(page.locator('#practice-source-review')).not.toBeChecked();expect(publishes).toBe(0);
 await page.getByLabel('答案解析',{exact:true}).fill('');await expect(button).toBeDisabled();await expect(page.locator('#practice-overview')).toContainText('请先补齐题目问题');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

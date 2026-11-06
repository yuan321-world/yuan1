import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
test('section filters follow listening exam types, clear stale reading queries and hide for writing/translation/papers',async({page},info)=>{
 await mockIdentity(page,true);const calls:string[]=[];await page.route('**/api/v1/practice/catalog',r=>r.fulfill({json:{exams:[]}}));
 await page.route('**/api/v1/practice/sets?*',r=>{calls.push(r.request().url());return r.fulfill({json:{items:[],total:0,page:1,pageSize:20}})});await page.route('**/api/v1/practice/facets?*',r=>r.fulfill({json:{source:[],readingType:[],topic:[],state:[],year:[],month:[],paperNo:[]}}));
 await page.goto('/practice/cet?collection=PAST&source=PAST&exam=CET4&readingType=CLOZE');
 if(info.project.name==='mobile')await page.getByRole('button',{name:'筛选题组',exact:true}).click();
 const type=()=>page.getByLabel('题型筛选',{exact:true}).filter({visible:true});await expect(type()).toHaveValue('CLOZE');
 if(info.project.name==='mobile')await page.getByRole('button',{name:'关闭筛选',exact:true}).click();
 await page.getByRole('button',{name:'听力',exact:true}).click();
 await expect(page.getByLabel('题型筛选',{exact:true})).toHaveCount(0);expect(new URL(page.url()).searchParams.has('readingType')).toBe(false);
 if(info.project.name==='mobile')await page.getByRole('button',{name:'筛选题组',exact:true}).click();
 await page.getByRole('button',{name:'考试：六级',exact:true}).filter({visible:true}).click();await expect(page.getByLabel('题型筛选',{exact:true})).toHaveCount(0);
 if(info.project.name==='mobile')await page.getByRole('button',{name:'关闭筛选',exact:true}).click();
 for(const section of ['写作','翻译','整卷']){await page.getByRole('button',{name:section,exact:true}).click();await expect(page.getByLabel('题型筛选',{exact:true})).toHaveCount(0);expect(new URL(page.url()).searchParams.has('listeningType')).toBe(false);if(info.project.name==='mobile'){await page.getByRole('button',{name:'筛选题组',exact:true}).click();await expect(page.getByLabel('题型筛选',{exact:true})).toHaveCount(0);await page.getByRole('button',{name:'关闭筛选',exact:true}).click()}}
 await page.getByRole('button',{name:'阅读',exact:true}).click();if(info.project.name==='mobile')await page.getByRole('button',{name:'筛选题组',exact:true}).click();await expect(type().locator('option')).toContainText(['全部题型','仔细阅读 · 规划','选词填空 · 规划','长篇匹配 · 规划']);expect(calls.every(url=>!new URL(url).searchParams.has('listeningType'))).toBe(true);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

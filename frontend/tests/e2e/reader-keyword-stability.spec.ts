import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'
const category={id:'1',code:'STUDY',name:'学习'}
const version={id:'11',articleId:'10',versionNo:1,title:'Small steps',titleZh:'循序渐进',summary:'Learning.',category,difficulty:'B1',wordCount:20,estimatedMinutes:1,author:'Example author',sourceType:'ORIGINAL',usageNote:'原创',content:{schemaVersion:1,paragraphs:[{key:'p0001',sentences:[{key:'p0001-s0001',text:'Small steps help us. '.repeat(60)}]}]}}
const related=Array.from({length:3},(_,i)=>({id:String(20+i),versionId:String(30+i),title:'Another article',titleZh:'另一篇可以继续阅读的文章',category,difficulty:'B1',wordCount:400,estimatedMinutes:3,author:'Lingua Loop',sourceType:'ORIGINAL',progress:null}))
const keywords=Array.from({length:101},(_,i)=>({entryId:String(i+1),lemma:'step'+i,forms:['steps'],phonetic:null,senses:[{partOfSpeech:'n.',definitionZh:'步骤',definitionEn:''}],sourceNote:''}))
test('toggling keywords preserves related cards and vocabulary position during refresh and cancellation',async({page})=>{
 await mockIdentity(page);let calls=0,fail=false,release:undefined|(()=>void)
 await page.route('**/api/v1/reading/articles/10',r=>r.fulfill({json:{version,progress:null}}))
 await page.route('**/api/v1/reading/articles/10/start',r=>r.fulfill({json:{version,progress:{id:'1',articleId:'10',articleVersionId:'11',sentenceKey:'p0001-s0001',offset:0,status:'READING',lockVersion:0},created:false}}))
 await page.route('**/api/v1/reading/articles/10/versions/11/tools',async r=>{calls++;if(calls>1)await new Promise<void>(resolve=>release=resolve);if(fail){await r.fulfill({status:503,json:{code:'SERVICE_UNAVAILABLE',message:'阅读工具刷新失败'}});return};await r.fulfill({json:{related,previous:null,next:null,keywordSource:'考研乱序版',keywordSourceKind:'BOOK',keywords,translation:{available:false,source:'',paragraphs:[]}}})})
 await page.goto('/reading/10');const relatedLinks=page.locator('.reader-related>a'),panel=page.locator('#reader-vocabulary'),list=page.locator('.reader-keyword-list');await expect(relatedLinks).toHaveCount(3)
 await list.evaluate(el=>el.scrollTop=140);const before=await panel.evaluate(el=>el.getBoundingClientRect().top+scrollY);const originalNode=await page.locator('[data-sentence]').evaluate(el=>{(window as any).__readerText=el.firstChild;return el.textContent})
 const button=page.getByRole('button',{name:'重点词',exact:true});await button.click();await expect.poll(()=>calls).toBe(2)
 const during=await panel.evaluate(el=>el.getBoundingClientRect().top+scrollY);expect(Math.abs(during-before)).toBeLessThan(1);await expect(relatedLinks).toHaveCount(3)
 await button.click();await expect(button).toHaveAttribute('aria-pressed','false');expect(Math.abs(await panel.evaluate(el=>el.getBoundingClientRect().top+scrollY)-before)).toBeLessThan(1);expect(await list.evaluate(el=>el.scrollTop)).toBe(140)
 release?.();await expect(page.locator('.reader-related')).not.toHaveAttribute('aria-busy','true');
 fail=true;await page.evaluate(()=>window.dispatchEvent(new Event('focus')));await expect.poll(()=>calls).toBe(3);release?.();await expect(page.locator('.reader-related').getByRole('button',{name:'重新加载阅读工具',exact:true})).toBeVisible();await expect(relatedLinks).toHaveCount(3);expect(Math.abs(await panel.evaluate(el=>el.getBoundingClientRect().top+scrollY)-before)).toBeLessThan(1);expect(await list.evaluate(el=>el.scrollTop)).toBe(140);
 fail=false;await page.locator('.reader-related').getByRole('button',{name:'重新加载阅读工具',exact:true}).click();await expect.poll(()=>calls).toBe(4);release?.();await expect(page.locator('.reader-related')).not.toHaveAttribute('aria-busy','true');await expect.poll(()=>page.evaluate(()=>CSS.highlights.get('reader-keywords')?.size??0)).toBe(0);expect(await page.locator('[data-sentence]').evaluate(el=>el.firstChild===(window as any).__readerText&&el.textContent)).toBe(originalNode)
})

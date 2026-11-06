import {test,expect,type Page} from '@playwright/test'
test.describe.configure({mode:'serial'})
test.setTimeout(60_000)
test.skip(process.env.E2E_PRACTICE_REAL!=='1','Explicit isolated MySQL/Redis backend and real credentials required; no Mock fallback.')
let setId=''
async function login(page:Page,admin:boolean){
 const name=process.env[admin?'PRACTICE_ADMIN_NAME':'PRACTICE_USER_NAME'],password=process.env[admin?'PRACTICE_ADMIN_PASSWORD':'PRACTICE_USER_PASSWORD'];if(!name||!password)throw new Error('Test credentials unavailable')
 try{await page.goto('/login');const d=page.locator('.auth-dialog');await d.getByLabel('用户名',{exact:true}).fill(name);await d.getByLabel('密码',{exact:true}).fill(password);await d.getByRole('button',{name:'登录',exact:true}).click();await expect(d).not.toBeVisible()}catch{throw new Error('Real authenticated login failed')}
}
test('real admin saves, reviews and publishes test-owned cloze; bank edits invalidate reviews',async({page})=>{
 await login(page,true);await page.goto('/admin/practice/new');await page.getByRole('combobox',{name:/^阅读题型/}).selectOption('CLOZE');
 await page.getByLabel('英文标题',{exact:true}).fill('Integration Cloze Fixture');await page.getByLabel('中文标题',{exact:true}).fill('选词填空全栈测试专用');await page.getByLabel('来源与使用说明',{exact:true}).fill('自动化测试专用原创结构，非真题，结束后清理。');
 await page.getByLabel('专项原文',{exact:true}).fill(Array.from({length:10},(_,i)=>`Test-only sentence number ${i+1} has a blank {{${i+26}}}.`).join('\n\n'));
 for(let i=0;i<15;i++)await page.getByLabel('词库 '+String.fromCharCode(65+i),{exact:true}).fill('word'+i);
 for(let i=0;i<10;i++){const row=page.locator('#cloze-edit-gap'+(26+i));await row.getByRole('combobox',{name:/^正确词语/}).selectOption(String.fromCharCode(65+i));await row.getByLabel('解析与原文依据',{exact:true}).fill('测试解释 '+(i+26))}
 await page.getByRole('button',{name:'保存当前编辑',exact:true}).click();await expect(page).toHaveURL(/\/admin\/practice\/\d+$/);setId=page.url().split('/').at(-1)!;
 await page.getByRole('button',{name:'整组确认校对',exact:true}).click();await page.getByRole('button',{name:'确认已校对整组',exact:true}).click();await expect(page.getByRole('status')).toContainText('整组10题校对确认已保存');await expect(page.locator('.cloze-admin-gap input:checked')).toHaveCount(10);
 await page.locator('#practice-source-review').check();await expect(page.getByRole('status')).toContainText('校对确认已保存');
 await page.getByLabel('词库 O',{exact:true}).fill('unusedword');await expect(page.locator('#cloze-edit-gap26').getByRole('checkbox')).not.toBeChecked();await expect(page.locator('#practice-source-review')).not.toBeChecked();
 await page.getByRole('button',{name:'保存当前编辑',exact:true}).click();await expect(page.getByRole('status')).toContainText('草稿已保存');
 await page.getByRole('button',{name:'整组确认校对',exact:true}).click();await page.getByRole('button',{name:'确认已校对整组',exact:true}).click();await expect(page.getByRole('status')).toContainText('整组10题校对确认已保存');await expect(page.locator('.cloze-admin-gap input:checked')).toHaveCount(10);
 await page.locator('#practice-source-review').check();await expect(page.getByRole('status')).toContainText('校对确认已保存');await page.getByRole('button',{name:'发布题组',exact:true}).click();await page.getByRole('button',{name:'确认发布',exact:true}).click();await expect(page.getByRole('status')).toContainText('题组已发布');
 await page.reload();await expect(page.getByLabel('词库 O',{exact:true})).toHaveValue('unusedword');await page.locator('.practice-metadata>summary').click();await expect(page.getByRole('combobox',{name:/^阅读题型/})).toBeDisabled();
 for(const width of [1440,320]){await page.setViewportSize({width,height:1000});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:process.env.PRACTICE_QA_DIR+`/admin-${width}.png`,fullPage:width>600})}
})
for(const width of [1440,768,320])test(`real cloze learner saves, resumes, moves and submits at ${width}`,async({page})=>{
 await page.setViewportSize({width,height:1000});const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));await login(page,false);await page.goto('/practice?source=ORIGINAL');await expect(page.getByRole('heading',{name:'Integration Cloze Fixture'})).toBeVisible();
 await page.getByRole('button',{name:'开始练习',exact:true}).click();await expect(page).toHaveURL(/\/practice\/attempts\//);await expect(page.locator('.practice-cloze-player')).toBeVisible();
 // The player exposes explicit labels for inline gaps and bank choices. Mobile opens its native dialog.
 const gap=(n:number)=>page.locator('.practice-cloze-gap').filter({has:page.locator('small',{hasText:new RegExp('^'+n+'$')})});
 await gap(26).click();if(width<901)await page.screenshot({path:process.env.PRACTICE_QA_DIR+`/bank-${width}.png`});await page.getByRole('button',{name:/^A，word0/}).click();
 await gap(27).click();await page.getByRole('button',{name:/^A，word0/}).click();await page.getByRole('button',{name:'取消',exact:true}).click();
 if(width<921)await page.keyboard.press('Escape');
 await gap(27).click();await page.getByRole('button',{name:/^A，word0/}).click();await page.getByRole('button',{name:/^确认移动$/,exact:false}).last().click();
 if(width<921)await page.keyboard.press('Escape');
 await page.getByRole('button',{name:'退出并保存',exact:true}).click();await expect(page).toHaveURL(/\/practice\?source=ORIGINAL/);await page.getByRole('button',{name:'继续练习',exact:true}).click();await expect(page).toHaveURL(/\/practice\/attempts\//);await expect(page.locator('.practice-cloze-player')).toBeVisible();await page.reload();await expect(gap(27)).toContainText('word0');
 await gap(26).click();await page.getByRole('button',{name:/^B，word1/}).click();if(width<921)await page.keyboard.press('Escape');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:process.env.PRACTICE_QA_DIR+`/reader-${width}.png`,fullPage:true});
 await page.getByRole('button',{name:'整组交卷',exact:true}).first().click();await page.getByRole('button',{name:'确认交卷',exact:true}).click();await expect(page.getByRole('heading',{name:/^逐空结果$/})).toBeVisible();
 await expect(page.getByText('测试解释 26',{exact:false}).first()).toBeVisible();await expect(page.locator('.el-overlay:visible')).toHaveCount(0);await page.screenshot({path:process.env.PRACTICE_QA_DIR+`/result-${width}.png`,fullPage:true});
 await page.getByRole('button',{name:'重练本组（含错空）',exact:true}).click();await expect(page.locator('.practice-cloze-player')).toBeVisible();await expect(page.getByRole('button',{name:'重练本组（含错空）',exact:true})).toHaveCount(0);expect(errors).toEqual([])
 // Wait for the retry submission result before closing this authenticated page.
 await page.getByRole('button',{name:'整组交卷',exact:true}).first().click();await page.getByRole('button',{name:'确认交卷',exact:true}).click();await expect(page.getByRole('heading',{name:'逐空结果',exact:true})).toBeVisible();
})

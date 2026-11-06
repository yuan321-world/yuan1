// Run exclusively through VocabularyBrowserVerification: isolated real MySQL + real JWT/CSRF.
const { chromium, expect } = require('../../node_modules/@playwright/test');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const fixture = JSON.parse(fs.readFileSync('.tool-cache/vocabulary-full/verification-fixture.json', 'utf8'));
const origin = 'http://127.0.0.1:4174';
const out = '.tool-cache/vocabulary-full/verification';

(async () => {
  const browser = await chromium.launch({headless:true});
  const errors = []; let realRequests = 0;
  async function context() {
    const c = await browser.newContext({viewport:{width:1440,height:1000}});
    await c.route('**/api/v1/**', async route => {
      const url = new URL(route.request().url()); url.port = String(fixture.port); realRequests++;
      try { const response = await route.fetch({url:url.toString(),timeout:20000}); await route.fulfill({response}); }
      catch { await route.abort('failed'); }
    });
    const p = await c.newPage(); p.on('pageerror', e => errors.push(e.message));
    return {c,p};
  }
  async function login(p, account) {
    await p.goto(origin+'/vocabulary');
    await p.getByRole('button',{name:'登录',exact:true}).click();
    const d = p.getByRole('dialog').filter({has:p.getByRole('heading',{name:'欢迎回来'})});
    await expect(d).toBeVisible();
    await p.waitForFunction(()=>!document.querySelector('.dialog-fade-enter-active,.dialog-fade-enter-from,.dialog-fade-enter-to'));
    await d.getByLabel('用户名',{exact:true}).fill(account.username);
    await d.getByLabel('密码',{exact:true}).fill(account.password);
    await d.getByRole('button',{name:'登录',exact:true}).click(); await expect(d).not.toBeVisible();
  }
  async function tab(p,name) { const button=p.getByRole('navigation',{name:'词汇中心'}).getByRole('button',{name,exact:true});await button.click();await expect(button).toHaveAttribute('aria-current','page');await expect(p.getByRole('navigation',{name:'词汇中心'})).toHaveAttribute('aria-busy','false');await expect(button).toBeEnabled();if(name==='词组本')await expect(p.locator('.phrase-notice[role=alert]')).toHaveCount(0); }
  async function noOverflow(p) { assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'horizontal overflow'); }
  async function collectPhrase(p,text,meaning) {
    await p.getByRole('button',{name:'手动收藏词组',exact:true}).click();
    const d=p.getByRole('dialog',{name:'收藏词组'});
    await d.getByLabel('英文词组',{exact:true}).fill(text);
    await d.getByLabel('个人中文释义',{exact:true}).fill(meaning);
    await d.getByRole('button',{name:'保存',exact:true}).click(); await expect(d).not.toBeVisible();
  }
  try {
    const {p:user}=await context(); await login(user,fixture.user);
    await user.goto(origin+'/notebook'); await expect(user).toHaveURL(/\/vocabulary\?tab=words/);
    const agenda=user.locator('.vn-row').filter({hasText:'agenda'});
    await expect(agenda).toBeVisible(); await agenda.getByRole('button',{name:'加入学习',exact:true}).click();
    await expect(agenda.getByRole('button',{name:'已加入学习',exact:true})).toBeDisabled();
    await agenda.getByRole('button',{name:'取消收藏',exact:true}).click(); await expect(agenda).toHaveCount(0);
    await tab(user,'今日学习'); await user.getByRole('button',{name:'开始学习',exact:true}).click();
    await user.getByRole('button',{name:/跟打模式/}).click();
    const input=user.getByRole('textbox',{name:'输入英文拼写'});
    await expect(input).toBeVisible(); await expect(user.locator('.vl-ghost')).toHaveText('agenda');
    await expect(user.getByRole('button',{name:'提示一个字母'})).toHaveCount(0);
    await input.fill('agx'); await expect(input).toHaveValue('ag');
    await user.getByRole('button',{name:'加入生词本',exact:true}).click();
    await expect(user.getByText('已加入生词本',{exact:true})).toBeVisible(); await expect(input).toHaveValue('ag');
    await tab(user,'词组本'); await tab(user,'今日学习');
    await user.getByRole('button',{name:'继续学习',exact:true}).click(); await expect(input).toHaveValue('ag');
    await user.reload(); await expect(input).toHaveValue('ag');
    await input.fill('agenda');
    // The earlier error must survive saving; its assisted attempt returns to the queue.
    await expect(input).toHaveValue(''); await input.fill('agenda');
    await expect(user.getByRole('button',{name:'返回背单词',exact:true})).toBeVisible();
    await expect(user.locator('.vl-batch-details')).toContainText('agenda');
    await user.getByRole('button',{name:'返回背单词',exact:true}).click();
    await tab(user,'生词本');
    await agenda.getByRole('button',{name:'详情与来源 ↗',exact:true}).click();
    const drawer=user.getByRole('dialog',{name:'agenda',exact:true});
    await expect(drawer.getByText('学习中收藏',{exact:true})).toBeVisible();
    await drawer.getByRole('button',{name:'标记熟练 · 免复习',exact:true}).click();
    await expect(drawer.getByRole('button',{name:'恢复复习',exact:true})).toBeVisible();
    await drawer.getByRole('button',{name:'恢复复习',exact:true}).click();
    await user.getByRole('button',{name:'关闭生词详情'}).click();
    const summary=user.locator('.vn-row').filter({hasText:'summary'});
    await summary.getByRole('button',{name:'加入学习',exact:true}).click();
    await tab(user,'今日学习'); await user.getByRole('button',{name:'开始学习',exact:true}).click();
    await user.getByRole('button',{name:/看词模式/}).click();
    await expect(user.locator('.vl-card h2').first()).toHaveText('summary');
    await user.getByRole('button',{name:'学会了',exact:true}).click();
    await expect(user.getByRole('region',{name:'单词释义'})).toBeVisible();
    await user.waitForTimeout(600); await expect(user.locator('.vl-card h2').first()).toHaveText('summary');
    await user.getByRole('button',{name:'下一个',exact:true}).click();
    await expect(user.getByRole('button',{name:'返回背单词',exact:true})).toBeVisible();
    await user.getByRole('button',{name:'返回背单词',exact:true}).click();
    await noOverflow(user); await user.screenshot({path:out+'-desktop.png',fullPage:true});

    await tab(user,'词组本'); await collectPhrase(user,'look forward to','期待');
    await collectPhrase(user,'LOOK  FORWARD TO','期待');
    const phrase=user.locator('.phrase-row'); await expect(phrase,'canonical phrase collection').toHaveCount(1);
    await phrase.getByRole('button',{name:'加入练习',exact:true}).click();
    await user.getByRole('button',{name:'看词组回想',exact:true}).click();
    await user.getByRole('button',{name:'保存并返回词组本',exact:true}).click();
    await user.reload(); await user.getByRole('button',{name:'继续保存的词组轮次',exact:true}).click();
    await expect(user.locator('.phrase-practice h2')).toHaveText(/look forward to/i);
    await user.getByRole('button',{name:'显示释义',exact:true}).click();
    await user.getByRole('button',{name:'认识，继续检验',exact:true}).click();
    await expect(user.getByText('已记录回想完成。',{exact:true})).toBeVisible();
    await user.getByRole('button',{name:'下一词组',exact:true}).click();
    await expect(user.getByRole('heading',{name:'本轮词组练习已完成'})).toBeVisible();
    await user.getByRole('button',{name:'保存并返回词组本',exact:true}).click();
    await expect(user.getByRole('button',{name:'看词组回想',exact:true})).toBeDisabled();
    await phrase.getByRole('button',{name:'取消收藏',exact:true}).click(); await expect(phrase).toHaveCount(0);
    await user.getByRole('checkbox',{name:'显示已取消收藏'}).check(); await expect(phrase,'cancelled collection filter').toHaveCount(1);
    await phrase.getByRole('button',{name:'恢复收藏',exact:true}).click();
    // A click starts a write; refresh only after its response and list refresh are confirmed.
    await expect(phrase.getByRole('button',{name:'取消收藏',exact:true})).toBeEnabled();
    await user.reload(); await expect(phrase,'restored collection survives reload').toHaveCount(1); await expect(phrase).toContainText('已加入练习');
    const {p:other}=await context(); await login(other,fixture.other); await tab(other,'生词本');
    await expect(other.locator('.vn-row')).toHaveCount(0); await tab(other,'词组本'); await expect(other.locator('.phrase-row')).toHaveCount(0);
    await user.setViewportSize({width:320,height:844});
    for(const name of ['今日学习','生词本','词组本']) { await tab(user,name); await noOverflow(user); await user.screenshot({path:out+'-mobile-'+({'今日学习':'today','生词本':'words','词组本':'phrases'}[name])+'.png',fullPage:true}); }
    assert.deepEqual(errors,[]); assert(realRequests>45);
    console.log('PASS real dedicated test-DB browser: JWT/CSRF login, legacy notebook redirect, join/cancel/learning recollect, retained error prefix across tabs/reload, copy queue retry and batch definitions, proficient recovery, manual look-next, phrase normalized dedup/join/recall/save/resume/due/cancel/restore, other-user isolation, 1440/320 layouts; API forwarding only, no mock business responses or AI calls.');
  } finally { await browser.close(); }
})().catch(e=>{console.error(e.stack??e.message);process.exit(1);});

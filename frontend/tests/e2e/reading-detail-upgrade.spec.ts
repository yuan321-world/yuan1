import { test, expect } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
const category = { id: '1', code: 'STUDY', name: '学习' }

test('unmatched phrase stays explicit while complete prepared text still works', async ({ page }) => {
  await setup(page)
  let requests=0
  await page.route('**/api/v1/dictionary/phrase?*', r=>{requests++;return r.fulfill({json:{found:false,phrase:null,sourceNote:'ECDICT'}})})
  await page.goto('/reading/10')
  await expect(page.getByRole('link',{name:/另一篇文章/})).toBeVisible()
  await page.locator('[data-sentence]').evaluate(el=>{el.scrollIntoView({block:'center'});const range=document.createRange();range.setStart(el.firstChild!,6);range.setEnd(el.firstChild!,16);const selection=getSelection()!;selection.removeAllRanges();selection.addRange(range);document.dispatchEvent(new Event('selectionchange'))})
  await page.getByRole('button',{name:'翻译',exact:true}).click()
  await expect(page.getByRole('dialog')).toContainText('未匹配到离线短语')
  await expect(page.getByRole('dialog')).not.toContainText('每一小步都很重要。')
  await page.keyboard.press('Escape')
  await page.locator('[data-sentence]').evaluate(el=>{el.scrollIntoView({block:'center'});const range=document.createRange();range.selectNodeContents(el);const selection=getSelection()!;selection.removeAllRanges();selection.addRange(range);document.dispatchEvent(new Event('selectionchange'))})
  await page.getByRole('button',{name:'翻译',exact:true}).click()
  await expect(page.getByRole('dialog')).toContainText('每一小步都很重要。')
  expect(requests).toBe(1)
})

test('multiword selection queries offline phrases, supports retry and never substitutes paragraph text', async ({ page }) => {
  await setup(page)
  let attempts=0
  await page.route('**/api/v1/dictionary/phrase?*', r => { attempts++; return attempts===1 ? r.fulfill({status:503,json:{message:'词典暂时不可用'}}) : r.fulfill({json:{found:true,phrase:{text:'small step',translationZh:'小小的一步'},sourceNote:'ECDICT 离线短语'}}) })
  await page.goto('/reading/10')
  await page.locator('[data-sentence]').evaluate(el=>{el.scrollIntoView({block:'center'});const range=document.createRange();range.setStart(el.firstChild!,6);range.setEnd(el.firstChild!,16);const selection=getSelection()!;selection.removeAllRanges();selection.addRange(range);document.dispatchEvent(new Event('selectionchange'))})
  await page.getByRole('button',{name:'翻译',exact:true}).click()
  const dialog=page.getByRole('dialog')
  await expect(dialog).toContainText('词典暂时不可用')
  await dialog.getByRole('button',{name:'重试查询',exact:true}).click()
  await expect(dialog).toContainText('小小的一步')
  await expect(dialog).not.toContainText('每一小步都很重要。')
  await expect(dialog).toContainText('ECDICT')
  await page.keyboard.press('Escape')
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})
const summary = { id: '10', versionId: '11', title: 'Small steps', titleZh: '小小进步', summary: 'A useful habit.', category, difficulty: 'B1', wordCount: 5, estimatedMinutes: 1, author: 'Lingua Loop', sourceType: 'ORIGINAL', sourceUrl: null, usageNote: '原创练习', progress: null }
const version = { ...summary, id: '11', articleId: '10', versionNo: 1, content: { schemaVersion: 1, paragraphs: [{ key: 'p0001', sentences: [{ key: 'p0001-s0001', text: 'Every small step matters.' }] }] } }
const senses = [{ partOfSpeech: 'n.', definitionZh: '一步；步骤', definitionEn: 'A small movement.' }]
const keyword = { entryId: '1', lemma: 'step', phonetic: null, senses, sourceNote: '原创编辑释义', forms: ['step', 'steps'] }
async function setup(page: import('@playwright/test').Page, guest = true) {
  await mockIdentity(page, guest)
  await page.route('**/api/v1/reading/articles/10', r => r.fulfill({ json: { version, progress: null } }))
  await page.route('**/api/v1/reading/articles/10/start', r => r.fulfill({ json: { version, progress: { id: '1', articleId: '10', articleVersionId: '11', sentenceKey: 'p0001-s0001', offset: 0, status: 'READING', lockVersion: 0, updatedAt: '2026-10-17T00:00:00Z' }, created: false } }))
  await page.route('**/api/v1/reading/articles/10/versions/11/tools', r => r.fulfill({ json: { related: [{ ...summary, id: '20', titleZh: '另一篇文章' }], previous: null, next: { ...summary, id: '20' }, keywords: [keyword], translation: { available: true, source: '项目编辑参考译文（待人工校对）', paragraphs: [{ key: 'p0001', text: '每一小步都很重要。' }] } } }))
}

for (const position of [
  { name: 'first sentence', sentenceKey: 'p0001-s0001', offset: 0, top: true },
  { name: 'later paragraph', sentenceKey: 'p0004-s0001', offset: 0, top: false },
  { name: 'within first sentence', sentenceKey: 'p0001-s0001', offset: 12, top: false },
  { name: 'saved article beginning without a locator', sentenceKey: 'p0001-s0001', offset: 0, top: true },
  { name: 'saved later paragraph without a locator', sentenceKey: 'p0004-s0001', offset: 0, top: false },
]) test(`resume at ${position.name} preserves the correct entry viewport`, async ({ page }) => {
  await setup(page, false)
  const longVersion = { ...version, content: { schemaVersion: 1, paragraphs: Array.from({ length: 12 }, (_, index) => ({ key: `p${String(index + 1).padStart(4, '0')}`, sentences: [{ key: `p${String(index + 1).padStart(4, '0')}-s0001`, text: 'Every small step matters. '.repeat(12) }] })) } }
  const progress = { id: '1', articleId: '10', articleVersionId: '11', sentenceKey: position.sentenceKey, offset: position.offset, status: 'READING', lockVersion: 3, updatedAt: '2026-10-17T00:00:00Z' }
  await page.route('**/api/v1/reading/articles/10/versions/11', r => r.fulfill({ json: { version: longVersion, progress } }))
  await page.route('**/api/v1/reading/articles/10/start', r => r.fulfill({ json: { version: longVersion, progress, created: false } }))
  await page.goto(position.name.includes('without a locator') ? '/reading/10' : `/reading/10?version=11&sentence=${position.sentenceKey}&start=${position.offset}`)
  await expect(page.locator('.article-body')).toBeVisible()
  if (position.top) {
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0)
    await expect(page.locator('.article-heading h1')).toBeInViewport()
  } else {
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(100)
    await expect(page.locator(`[data-sentence="${position.sentenceKey}"]`)).toBeInViewport()
  }
})
test('wide reader keeps native text, accessible Aa settings and truthful translations', async ({ page }) => {
  await setup(page)
  await page.goto('/reading/10')
  await expect(page.locator('.article-body')).toHaveText('Every small step matters.')
  await expect(page.locator('.paragraph-translation')).toHaveCount(0)
  await page.getByRole('button', { name: '阅读设置', exact: true }).click()
  await page.getByRole('button', { name: '放大字号' }).click()
  await expect(page.locator('.reader-size-value')).toHaveText('106%')
  await page.getByRole('button', { name: '恢复默认', exact: true }).click()
  await expect(page.locator('.reader-size-value')).toHaveText('100%')
  await page.keyboard.press('Escape')
  await expect(page.getByRole('button', { name: '阅读设置', exact: true })).toBeFocused()
  await page.getByRole('button', { name: '英中对照', exact: true }).click()
  await expect(page.locator('.paragraph-translation')).toHaveText('每一小步都很重要。')
  await page.getByRole('button', { name: '纯英文', exact: true }).click()
  await expect(page.locator('.paragraph-translation')).toHaveCount(0)
  const original = await page.locator('[data-sentence]').evaluate(el => ({ text: el.textContent, nodeType: el.firstChild?.nodeType, top: el.getBoundingClientRect().top }))
  await page.getByRole('button', { name: '重点词', exact: true }).click()
  expect(await page.evaluate(() => CSS.highlights.has('reader-keywords'))).toBe(true)
  const after = await page.locator('[data-sentence]').evaluate(el => ({ text: el.textContent, nodeType: el.firstChild?.nodeType, top: el.getBoundingClientRect().top }))
  expect(after).toEqual(original)
  await expect(page.getByRole('link', { name: /另一篇文章/ })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
test('native sentence selection opens prepared paragraph translation without a paid provider', async ({ page }) => {
  await setup(page)
  await page.goto('/reading/10')
  await expect(page.getByRole('link', { name: /另一篇文章/ })).toBeVisible()
  await page.locator('[data-sentence]').evaluate(el => { el.scrollIntoView({block:'center'}); const range = document.createRange(); range.selectNodeContents(el); const selection = getSelection()!; selection.removeAllRanges(); selection.addRange(range); document.dispatchEvent(new Event('selectionchange')) })
  await page.getByRole('button', { name: '翻译', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('每一小步都很重要。')
  await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toHaveCount(0)
  if (test.info().project.name === 'desktop') await expect(page.locator('.reader-vocabulary')).toBeVisible()
})
test('owned article word sidebar updates only after successful real API save', async ({ page }) => {
  await setup(page, false)
  let saved = false
  const word = { id: '5', entryId: '1', lemma: 'step', phonetic: null, senses, sourceNote: '原创', active: true }
  await page.route('**/api/v1/reading/articles/10/versions/11/words?*', r => r.fulfill({ json: { items: saved ? [{ word, source: { articleId: '10', articleVersionId: '11', sentenceKey: 'p0001-s0001', startOffset: 12, endOffset: 16, surfaceForm: 'step' } }] : [], total: saved ? 1 : 0, page: 1, pageSize: 10 } }))
  await page.route('**/api/v1/dictionary/lookup?word=step', r => r.fulfill({ json: { found: true, candidates: [{ ...keyword, collection: null }] } }))
  await page.route('**/api/v1/vocabulary/words', r => { saved = true; return r.fulfill({ status: 201, json: { word, created: true } }) })
  await page.goto('/reading/10')
  if (test.info().project.name === 'mobile') await page.getByRole('button', { name: '本篇生词', exact: true }).click()
  await page.getByRole('button', { name: '查看重点词：step' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '收藏生词', exact: true }).click()
  await expect.poll(() => saved).toBe(true)
  await page.getByRole('button', { name: '关闭查词' }).click()
  await expect(page.locator('.article-word-total')).toHaveText('1 个')
  await expect(page.locator('.reader-saved-word')).toContainText('step')
})
test('synthesized voice has a reachable action and uses the selected lemma', async ({ page }) => {
  await setup(page)
  await page.addInitScript(() => { Object.defineProperty(window, 'speechSynthesis', { configurable: true, value: { cancel: () => undefined, speak: (utterance: SpeechSynthesisUtterance) => { (window as Window & { __spoken?: string; __accent?: string }).__spoken = utterance.text; (window as Window & { __accent?: string }).__accent = utterance.lang } } }) })
  await page.route('**/api/v1/dictionary/lookup?word=step', r => r.fulfill({ json: { found: true, candidates: [{ ...keyword, collection: null }] } }))
  await page.goto('/reading/10')
  await page.locator('[data-sentence]').evaluate(el => { el.scrollIntoView({block:'center'}); const range = document.createRange(); range.setStart(el.firstChild!, 12); range.setEnd(el.firstChild!, 16); const selection = getSelection()!; selection.removeAllRanges(); selection.addRange(range); document.dispatchEvent(new Event('selectionchange')) })
  await page.getByRole('button', { name: '查询选中单词：step', exact: true }).click()
  await page.getByRole('button', { name: '英式合成朗读', exact: true }).click()
  await expect.poll(() => page.evaluate(() => (window as Window & { __spoken?: string }).__spoken)).toBe('step')
  await expect.poll(() => page.evaluate(() => (window as Window & { __accent?: string }).__accent)).toBe('en-GB')
  await page.getByRole('button', { name: '美式合成朗读', exact: true }).click()
  await expect.poll(() => page.evaluate(() => (window as Window & { __accent?: string }).__accent)).toBe('en-US')
  const beforeExpansion = await page.getByRole('dialog').evaluate(el => el.getBoundingClientRect().height)
  await page.locator('.lookup-example summary').click()
  await expect(page.locator('.lookup-example')).toContainText('Every small step matters.')
  await expect.poll(() => page.getByRole('dialog').evaluate(el => el.getBoundingClientRect().height)).toBeGreaterThanOrEqual(beforeExpansion)
  await page.locator('.lookup-example p').scrollIntoViewIfNeeded();const quote=await page.locator('.lookup-example p').boundingBox(),panel=await page.getByRole('dialog').boundingBox();expect(quote!.y+quote!.height).toBeLessThanOrEqual(panel!.y+panel!.height+1)
})

test('word lookup stays close to the selected word and inside the viewport', async ({ page }) => {
  await setup(page)
  await page.route('**/api/v1/dictionary/lookup?word=step', r => r.fulfill({ json: { found: true, candidates: [{ ...keyword, collection: null }] } }))
  await page.goto('/reading/10')
  await page.locator('[data-sentence]').evaluate(el => {
    window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 200)
    const range = document.createRange(); range.setStart(el.firstChild!, 12); range.setEnd(el.firstChild!, 16)
    const selection = getSelection()!; selection.removeAllRanges(); selection.addRange(range); document.dispatchEvent(new Event('selectionchange'))
  })
  const word = await page.locator('[data-sentence]').evaluate(el => { el.scrollIntoView({block:'center'}); const range = document.createRange(); range.setStart(el.firstChild!, 12); range.setEnd(el.firstChild!, 16); const rect = range.getBoundingClientRect(); return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom } })
  await page.getByRole('button', { name: '查询选中单词：step', exact: true }).click()
  await expect(page.getByRole('button', { name: '英式合成朗读', exact: true })).toBeVisible()
  const panel = await page.getByRole('dialog').boundingBox()
  expect(panel!.x).toBeGreaterThanOrEqual(12)
  expect(panel!.x + panel!.width).toBeLessThanOrEqual(page.viewportSize()!.width - 12)
  expect(panel!.y).toBeGreaterThanOrEqual(12)
  expect(panel!.y + panel!.height).toBeLessThanOrEqual(page.viewportSize()!.height - 12)
  if (test.info().project.name === 'desktop') {
    expect(Math.min(Math.abs(panel!.y - word.bottom), Math.abs(panel!.y + panel!.height - word.top))).toBeLessThanOrEqual(12)
    expect(Math.abs(panel!.x - word.left)).toBeLessThanOrEqual(16)
  }
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
})

test('lookup can cancel, retry failure, and restore a collected word without leaving reading', async ({ page }) => {
  await setup(page, false)
  let active = false, deletes = 0
  const word = { id: '5', entryId: '1', lemma: 'step', phonetic: null, senses, sourceNote: '原创', active: true }
  await page.route('**/api/v1/dictionary/lookup?word=step', r => r.fulfill({ json: { found: true, candidates: [{ ...keyword, collection: active ? { wordId: '5', active: true } : null }] } }))
  await page.route('**/api/v1/reading/articles/10/versions/11/words?*', r => r.fulfill({ json: { items: active ? [{ word, source: { articleId: '10', articleVersionId: '11', sentenceKey: 'p0001-s0001', startOffset: 12, endOffset: 16, surfaceForm: 'step' } }] : [], total: active ? 1 : 0, page: 1, pageSize: 10 } }))
  await page.route('**/api/v1/vocabulary/words', r => { active = true; return r.fulfill({ status: 200, json: { word, created: false } }) })
  await page.route('**/api/v1/vocabulary/words/5', r => {
    expect(r.request().method()).toBe('DELETE'); deletes++
    if (deletes === 1) return r.fulfill({ status: 503, json: { code: 'SERVICE_UNAVAILABLE', message: '暂时无法取消收藏，请重试' } })
    active = false; return r.fulfill({ status: 204 })
  })
  await page.goto('/reading/10')
  if (test.info().project.name === 'mobile') await page.getByRole('button', { name: '本篇生词', exact: true }).click()
  await page.getByRole('button', { name: '查看重点词：step' }).click()
  const lookup = page.locator('.word-lookup-panel')
  await lookup.getByRole('button', { name: '收藏生词', exact: true }).click()
  await expect(lookup.getByRole('button', { name: '取消收藏', exact: true })).toBeEnabled()
  await expect(page.locator('.article-word-total')).toHaveText('1 个')
  await lookup.getByRole('button', { name: '取消收藏', exact: true }).click()
  await expect(page.locator('.el-message-box')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.locator('.el-message-box')).not.toBeVisible()
  await expect(lookup).toBeVisible(); expect(deletes).toBe(0)
  await lookup.getByRole('button', { name: '取消收藏', exact: true }).click()
  await page.getByRole('button', { name: '确认取消收藏', exact: true }).click()
  await expect(lookup.getByRole('alert')).toContainText('暂时无法取消收藏')
  await expect(lookup.getByRole('button', { name: '取消收藏', exact: true })).toBeEnabled()
  await expect(page.locator('.article-word-total')).toHaveText('1 个')
  await lookup.getByRole('button', { name: '取消收藏', exact: true }).click()
  await page.getByRole('button', { name: '确认取消收藏', exact: true }).click()
  await expect(lookup.getByRole('button', { name: '恢复收藏', exact: true })).toBeEnabled()
  await expect(page.locator('.article-word-total')).toHaveText('0 个')
  await lookup.getByRole('button', { name: '恢复收藏', exact: true }).click()
  await expect(lookup.getByRole('button', { name: '取消收藏', exact: true })).toBeEnabled()
  await expect(page.locator('.article-word-total')).toHaveText('1 个')
  expect(deletes).toBe(2)
  await page.getByRole('button', { name: '关闭查词', exact: true }).click()
  await page.getByRole('button', { name: '查看重点词：step' }).click()
  await expect(lookup.getByRole('button', { name: '取消收藏', exact: true })).toBeEnabled()
  await expect(lookup.getByRole('button', { name: '记录此处来源', exact: true })).toHaveCount(0)
})

test('ambiguous word shows one compact card and switches between real entries', async ({ page }) => {
  await setup(page)
  const sharedVersion = { ...version, content: { schemaVersion: 1, paragraphs: [{ key: 'p0001', sentences: [{ key: 'p0001-s0001', text: 'They shared a plan.' }] }] } }
  await page.route('**/api/v1/reading/articles/10', r => r.fulfill({ json: { version: sharedVersion, progress: null } }))
  await page.route('**/api/v1/dictionary/lookup?word=shared', r => r.fulfill({ json: { found: true, candidates: [
    { ...keyword, entryId: '31', lemma: 'shared', senses: [{ partOfSpeech: 'adj.', definitionZh: '共享的；共同的', definitionEn: 'Used together.' }], collection: null },
    { ...keyword, entryId: '32', lemma: 'share', senses: [{ partOfSpeech: 'v.', definitionZh: '分享；共同使用', definitionEn: 'To use together.' }], collection: null },
  ] } }))
  await page.goto('/reading/10')
  await page.locator('[data-sentence]').evaluate(el => { el.scrollIntoView({block:'center'}); const range = document.createRange(); range.setStart(el.firstChild!, 5); range.setEnd(el.firstChild!, 11); const selection = getSelection()!; selection.removeAllRanges(); selection.addRange(range); document.dispatchEvent(new Event('selectionchange')) })
  await page.getByRole('button', { name: '查询选中单词：shared', exact: true }).click()
  const panel = page.locator('.word-lookup-panel')
  await expect(panel.locator('.dictionary-candidate')).toHaveCount(1)
  await expect(panel.getByRole('button', { name: '英式合成朗读', exact: true })).toHaveCount(1)
  await expect(panel.getByRole('button', { name: '登录后保存生词', exact: true })).toHaveCount(1)
  await expect(panel.locator('.lookup-lemma')).toHaveCount(0)
  await expect(panel.locator('.lookup-senses')).toContainText('共享的；共同的')
  await panel.getByRole('button', { name: '查看词条：share', exact: true }).click()
  await expect(panel.locator('.dictionary-candidate')).toHaveCount(1)
  await expect(panel.locator('.lookup-senses')).toContainText('分享；共同使用')
  await expect(panel.locator('.lookup-lemma strong')).toHaveText('share')
  await expect(panel.locator('.lookup-example')).toHaveCount(1)
  await expect(panel.locator('.lookup-source')).toHaveCount(1)
  await expect(panel.getByRole('button', { name: '登录后保存生词', exact: true })).toHaveCount(1)
  await panel.getByRole('button', { name: '查看词条：shared', exact: true }).click()
  await expect(panel.locator('.lookup-lemma')).toHaveCount(0)
  await expect(panel.locator('.lookup-senses')).toContainText('共享的；共同的')
})

test('collection follows the chosen dictionary entry without mixing candidate state', async ({ page }) => {
  await setup(page, false)
  await page.route('**/api/v1/dictionary/lookup?word=step', r => r.fulfill({ json: { found: true, candidates: [{ ...keyword, collection: null }, { ...keyword, entryId: '2', lemma: 'other', collection: null }] } }))
  let collectedEntry: string | undefined
  await page.route('**/api/v1/vocabulary/words', r => {
    collectedEntry = r.request().postDataJSON().entryId
    return r.fulfill({ status: 201, json: { word: { id: '50', entryId: '2', lemma: 'other', phonetic: null, senses, sourceNote: 'fixture', active: true }, created: true } })
  })
  await page.goto('/reading/10')
  if (test.info().project.name === 'mobile') await page.getByRole('button', { name: '本篇生词', exact: true }).click()
  await page.getByRole('button', { name: '查看重点词：step' }).click()
  const panel = page.locator('.word-lookup-panel')
  await panel.getByRole('button', { name: '查看词条：other', exact: true }).click()
  await panel.getByRole('button', { name: '收藏生词', exact: true }).click()
  await expect(panel.getByRole('button', { name: '取消收藏', exact: true })).toBeEnabled()
  expect(collectedEntry).toBe('2')
  await panel.getByRole('button', { name: '查看词条：step', exact: true }).click()
  await expect(panel.getByRole('button', { name: '收藏生词', exact: true })).toBeEnabled()
  await panel.getByRole('button', { name: '查看词条：other', exact: true }).click()
  await expect(panel.getByRole('button', { name: '取消收藏', exact: true })).toBeEnabled()
})

test('lexical sections show sourced synonyms and phrases for the selected entry only', async ({ page }) => {
  await setup(page)
  await page.route('**/api/v1/dictionary/lookup?word=step', r => r.fulfill({ json: { found: true, candidates: [
    { ...keyword, collection: null, synonyms: [{ partOfSpeech: 'n.', meaningEn: 'A stage in a process.', words: ['stage', 'phase'] }], phrases: [{ text: 'step by step', translationZh: '一步一步地' }], lexicalSourceNote: 'WordNet 3.0 / ECDICT' },
    { ...keyword, entryId: '2', lemma: 'other', collection: null, synonyms: [], phrases: [], lexicalSourceNote: 'WordNet 3.0 / ECDICT' },
  ] } }))
  await page.goto('/reading/10')
  await page.locator('[data-sentence]').evaluate(el => { el.scrollIntoView({block:'center'}); const range = document.createRange(); range.setStart(el.firstChild!, 12); range.setEnd(el.firstChild!, 16); const selection = getSelection()!; selection.removeAllRanges(); selection.addRange(range); document.dispatchEvent(new Event('selectionchange')) })
  await page.getByRole('button', { name: '查询选中单词：step', exact: true }).click()
  const panel = page.locator('.word-lookup-panel')
  await panel.locator('.lookup-synonyms summary').click()
  await expect(panel.locator('.lookup-synonyms')).toContainText('stage')
  await expect(panel.locator('.lookup-synonyms')).toContainText('A stage in a process.')
  await panel.locator('.lookup-phrases summary').click()
  await expect(panel.locator('.lookup-phrases')).toContainText('step by step')
  await expect(panel.locator('.lookup-phrases')).toContainText('一步一步地')
  await expect(panel.getByText('词典来源', { exact: true })).toHaveCount(0)
  await panel.locator('.lookup-source summary').click()
  await expect(panel.locator('.lookup-source')).toContainText('WordNet 3.0')
  await panel.getByRole('button', { name: '查看词条：other', exact: true }).click()
  await panel.locator('.lookup-synonyms summary').click()
  await expect(panel.locator('.lookup-synonyms')).toContainText('暂无可用近义词')
  await expect(panel.locator('.lookup-synonyms')).not.toContainText('stage')
  await panel.locator('.lookup-phrases summary').click()
  await expect(panel.locator('.lookup-phrases')).toContainText('暂无收录短语')
  await expect(panel.locator('.lookup-phrases')).not.toContainText('step by step')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('selection action sits below the selected words and follows scrolling',async({page})=>{
 await setup(page);await page.goto('/reading/10');const sentence=page.locator('[data-sentence]').first();await sentence.evaluate(el=>{el.scrollIntoView({block:'center'});const range=document.createRange();range.setStart(el.firstChild!,6);range.setEnd(el.firstChild!,16);const selection=getSelection()!;selection.removeAllRanges();selection.addRange(range);document.dispatchEvent(new Event('selectionchange'))})
 const button=page.getByRole('button',{name:'翻译',exact:true});await expect(button).toBeVisible();const rect=await page.evaluate(()=>{const r=getSelection()!.getRangeAt(0).getClientRects();const last=r[r.length-1]!;return {top:last.top,bottom:last.bottom}});const box=await button.boundingBox();expect(box!.y-rect.bottom).toBeGreaterThanOrEqual(6);expect(box!.y-rect.bottom).toBeLessThanOrEqual(12);expect(box!.x).toBeGreaterThanOrEqual(0);expect(box!.x+box!.width).toBeLessThanOrEqual(page.viewportSize()!.width)
 await page.screenshot({path:`../.tool-cache/selection-translation/anchor-${page.viewportSize()!.width}.png`})
})
test('clicked multiword selection falls back to a private translation task, never on selection alone',async({page})=>{
 await setup(page,false);let posts=0,gets=0;await page.route('**/api/v1/dictionary/phrase?*',r=>r.fulfill({json:{found:false,phrase:null,sourceNote:'ECDICT'}}));const task={id:'51',text:'small step',sourceCharacters:10,status:'RUNNING',translation:null,model:'baidu-general',errorCode:null,budgetStatus:'RESERVED',estimatedCostMicrocny:33334}
 await page.route('**/api/v1/reading/selection-translations**',r=>{if(r.request().method()==='POST'){posts++;return r.fulfill({json:task})}gets++;return r.fulfill({json:{...task,status:'SUCCEEDED',translation:'小小的一步',budgetStatus:'SETTLED',estimatedCostMicrocny:100}})})
 await page.goto('/reading/10');await page.locator('[data-sentence]').first().evaluate(el=>{el.scrollIntoView({block:'center'});const range=document.createRange();range.setStart(el.firstChild!,6);range.setEnd(el.firstChild!,16);const selection=getSelection()!;selection.removeAllRanges();selection.addRange(range);document.dispatchEvent(new Event('selectionchange'))});await expect(page.getByRole('button',{name:'翻译',exact:true})).toBeVisible();expect(posts).toBe(0);await page.getByRole('button',{name:'翻译',exact:true}).click();await expect(page.locator('.reader-sentence-dialog')).toContainText('小小的一步');await expect(page.locator('.reader-sentence-dialog .reader-translation-source')).toHaveText('本次 10 字符');await expect(page.locator('.reader-sentence-dialog')).not.toContainText('百度翻译');expect(posts).toBe(1);expect(gets).toBeGreaterThan(0)
})

test('grammar action shows structured English evidence without triggering translation or local dictionary',async({page})=>{
 await setup(page,false);const modes:string[]=[];let phrases=0;await page.route('**/api/v1/dictionary/phrase?*',r=>{phrases++;return r.fulfill({json:{found:false}})});
 await page.route('**/api/v1/reading/selection-translations',r=>{modes.push(r.request().postDataJSON().mode);return r.fulfill({json:{id:'61',text:'small step',mode:'GRAMMAR',status:'SUCCEEDED',translation:null,model:'deepseek-flash',errorCode:null,budgetStatus:'SETTLED',estimatedCostMicrocny:100,analysis:{scopeNote:'这是名词短语，未包含完整主谓。',mainClause:[{quote:'small step',role:'名词短语',explanation:'small修饰step。'}],clauses:[],clauseNote:'所选内容不是完整句子，没有从句。',grammarPoints:[{quote:'small',explanation:'形容词作定语。'}]}}})});
 await page.goto('/reading/10');await page.locator('[data-sentence]').first().evaluate(el=>{el.scrollIntoView({block:'center'});const range=document.createRange();range.setStart(el.firstChild!,6);range.setEnd(el.firstChild!,16);const selection=getSelection()!;selection.removeAllRanges();selection.addRange(range);document.dispatchEvent(new Event('selectionchange'))});
 const toolbar=page.getByRole('group',{name:'选文操作'});await expect(toolbar.getByRole('button',{name:'翻译',exact:true})).toHaveText('翻译');await expect(toolbar.getByRole('button',{name:'解析选中内容',exact:true})).toHaveText('解析');expect(modes).toEqual([]);await toolbar.getByRole('button',{name:'解析选中内容',exact:true}).click();const dialog=page.locator('.reader-sentence-dialog');await expect(dialog.getByRole('heading',{name:'语法解析',exact:true})).toBeVisible();for(const title of ['句子主干','从句划分','语法要点'])await expect(dialog.getByRole('heading',{name:title,exact:true})).toBeVisible();await expect(dialog.locator('strong[lang=en]').first()).toHaveText('small step');await expect(dialog).toContainText('未包含完整主谓');expect(modes).toEqual(['GRAMMAR']);expect(phrases).toBe(0);expect(await dialog.evaluate(el=>el.scrollWidth<=el.clientWidth)).toBe(true);await page.screenshot({path:`../.tool-cache/selection-analysis/grammar-${page.viewportSize()!.width}.png`});await dialog.getByRole('button',{name:'关闭语法解析',exact:true}).click();await expect(dialog).not.toBeVisible();
})

test('even one selected letter keeps explicit local-first translation',async({page})=>{await setup(page,false);let posts=0;await page.route('**/api/v1/dictionary/lookup?*',r=>r.fulfill({json:{found:false,candidates:[]}}));await page.route('**/api/v1/reading/selection-translations',r=>{posts++;expect(r.request().postDataJSON().text).toBe('v');return r.fulfill({json:{id:'52',text:'v',sourceCharacters:1,status:'SUCCEEDED',translation:'字母V',model:'baidu-general',mode:'TRANSLATION',budgetStatus:'SETTLED',estimatedCostMicrocny:0}})});await page.goto('/reading/10');await page.locator('[data-sentence]').first().evaluate(el=>{el.scrollIntoView({block:'center'});const r=document.createRange();r.setStart(el.firstChild!,1);r.setEnd(el.firstChild!,2);const s=getSelection()!;s.removeAllRanges();s.addRange(r);document.dispatchEvent(new Event('selectionchange'))});const button=page.getByRole('button',{name:'翻译',exact:true});await expect(button).toBeVisible();expect(posts).toBe(0);await button.click();await expect(page.locator('.reader-sentence-dialog')).toContainText('字母V');expect(posts).toBe(1)})

test('local word fragments retain the complete dictionary card',async({page})=>{await setup(page,false);let online=0;await page.route('**/api/v1/dictionary/lookup?*',r=>r.fulfill({json:{found:true,candidates:[{...keyword,lemma:'mall',synonyms:[{partOfSpeech:'n.',meaningEn:'shopping centre',words:['shopping center']}],phrases:[{text:'shopping mall',translationZh:'购物中心'}]}]}}));await page.route('**/api/v1/reading/selection-translations',r=>{online++;return r.fulfill({status:500})});await page.goto('/reading/10');await page.locator('[data-sentence]').first().evaluate(el=>{el.scrollIntoView({block:'center'});const r=document.createRange();r.setStart(el.firstChild!,7);r.setEnd(el.firstChild!,11);const s=getSelection()!;s.removeAllRanges();s.addRange(r);document.dispatchEvent(new Event('selectionchange'))});await page.getByRole('button',{name:'翻译',exact:true}).click();const card=page.locator('.word-lookup-panel');await expect(card.getByRole('heading',{name:'mall',exact:true})).toBeVisible();await card.locator('.lookup-synonyms summary').click();await expect(card).toContainText('shopping center');await card.locator('.lookup-example summary').click();await expect(card).toContainText('Every small step matters.');await expect(page.locator('.reader-sentence-dialog')).not.toBeVisible();expect(online).toBe(0)})

test('fixed bilingual examples stay separate from article context and switch with the candidate',async({page})=>{await setup(page);await page.route('**/api/v1/dictionary/lookup?word=step',r=>r.fulfill({json:{found:true,candidates:[{...keyword,lemma:'step',examples:[{text:'Take one small step at a time.',translationZh:'一次迈出一小步。'}]},{...keyword,entryId:'99',lemma:'steep',examples:[{text:'The hill is steep.',translationZh:'这座山很陡。'}]}]}}));await page.goto('/reading/10');await page.locator('[data-sentence]').first().evaluate(el=>{el.scrollIntoView({block:'center'});const r=document.createRange();r.setStart(el.firstChild!,12);r.setEnd(el.firstChild!,16);const s=getSelection()!;s.removeAllRanges();s.addRange(r);document.dispatchEvent(new Event('selectionchange'))});await page.getByRole('button',{name:'查询选中单词：step',exact:true}).click();const card=page.locator('.word-lookup-panel');await expect(card.locator('.lookup-fixed-examples')).toContainText('Take one small step at a time.');await expect(card.locator('.lookup-fixed-examples')).toContainText('一次迈出一小步。');await card.locator('.lookup-example summary').click();await expect(card.locator('.lookup-example')).toContainText('Every small step matters.');await page.getByRole('button',{name:'查看词条：steep',exact:true}).click();await expect(card.locator('.lookup-fixed-examples')).toContainText('这座山很陡。');await expect(card.locator('.lookup-fixed-examples')).not.toContainText('一次迈出');expect(await card.evaluate(el=>el.scrollWidth<=el.clientWidth)).toBe(true);await page.screenshot({path:`../.tool-cache/fixed-examples-${page.viewportSize()!.width}.png`})})

test('changing pronunciation ignores cancellation and stale failures but keeps real failures visible',async({page})=>{
 await setup(page);await page.addInitScript(()=>{const state={last:null as SpeechSynthesisUtterance|null,lateErrors:0,speaks:0};Object.assign(window,{__speechTest:state});Object.defineProperty(window,'speechSynthesis',{configurable:true,value:{speak:(u:SpeechSynthesisUtterance)=>{state.last=u;state.speaks++;u.onstart?.call(u,{} as SpeechSynthesisEvent)},cancel:()=>{const old=state.last;if(!old)return;old.onerror?.call(old,{error:'interrupted'} as SpeechSynthesisErrorEvent);setTimeout(()=>{old.onerror?.call(old,{error:'synthesis-failed'} as SpeechSynthesisErrorEvent);state.lateErrors++},0)}}})});await page.route('**/api/v1/dictionary/lookup?word=step',r=>r.fulfill({json:{found:true,candidates:[{...keyword,collection:null}]}}));await page.goto('/reading/10');await page.locator('[data-sentence]').first().evaluate(el=>{el.scrollIntoView({block:'center'});const r=document.createRange();r.setStart(el.firstChild!,12);r.setEnd(el.firstChild!,16);const s=getSelection()!;s.removeAllRanges();s.addRange(r);document.dispatchEvent(new Event('selectionchange'))});await page.getByRole('button',{name:'查询选中单词：step',exact:true}).click();for(const accent of ['英','美','英','美'])await page.getByRole('button',{name:accent+'式合成朗读',exact:true}).click();await page.waitForFunction(()=>(window as any).__speechTest.lateErrors>=3);await expect(page.locator('.word-lookup-panel')).not.toContainText('朗读暂不可用');await page.evaluate(()=>{const u=(window as any).__speechTest.last as SpeechSynthesisUtterance;u.onerror?.call(u,{error:'audio-hardware'} as SpeechSynthesisErrorEvent)});await expect(page.locator('.word-lookup-panel')).toContainText('朗读暂不可用');await page.getByRole('button',{name:'英式合成朗读',exact:true}).click();await expect(page.locator('.word-lookup-panel')).not.toContainText('朗读暂不可用')
})

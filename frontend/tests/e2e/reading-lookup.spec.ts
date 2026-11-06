import { test, expect } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
test('native selection offers explicit lookup with local errors and keyboard close', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 800 })
  await mockIdentity(page)
  const version = { id: '11', articleId: '10', versionNo: 1, title: 'Study with care', titleZh: '用心学习', category: { id: '1', name: '学习' }, difficulty: 'B1', wordCount: 4, estimatedMinutes: 1, author: 'Lingua Loop', sourceType: 'ORIGINAL', usageNote: '原创练习', content: { schemaVersion: 1, paragraphs: [{ key: 'p0001', sentences: [{ key: 'p0001-s0001', text: '😀 studies help us.' }] }] } }
  const progress = { id: '1', articleId: '10', articleVersionId: '11', sentenceKey: 'p0001-s0001', offset: 0, status: 'READING', lockVersion: 0 }
  await page.route('**/api/v1/reading/articles/10/start', route => route.fulfill({ json: { version, progress, created: true } }))
  let attempt = 0
  await page.route('**/api/v1/dictionary/lookup?word=studies', route => {
    attempt++
    if (attempt === 1) return route.fulfill({ status: 503, json: { code: 'DICTIONARY_UNAVAILABLE', message: '词典暂时不可用，请稍后重试' } })
    return route.fulfill({ json: { found: true, candidates: [{ entryId: '1', lemma: 'study', phonetic: null, senses: [{ partOfSpeech: 'v.', definitionZh: '学习；研究', definitionEn: 'To spend time learning about a subject.' }], sourceNote: 'Lingua Loop原创编辑释义', collection: null }] } })
  })
  await page.goto('/reading/10')
  await expect(page.locator('.article-body')).toBeVisible()
  await page.locator('.article-body [data-sentence]').evaluate(element => {
    element.scrollIntoView({block:'center'});const range = document.createRange(); range.setStart(element.firstChild!, 3); range.setEnd(element.firstChild!, 10)
    const selection = window.getSelection()!; selection.removeAllRanges(); selection.addRange(range)
    document.dispatchEvent(new Event('selectionchange'))
  })
  const open = page.getByRole('button', { name: '查询选中单词：studies', exact: true })
  await open.click()
  const panel = page.getByRole('dialog')
  await expect(panel.getByText('词典暂时不可用，请稍后重试')).toBeVisible()
  await panel.getByRole('button', { name: '重试查询', exact: true }).click()
  await expect(panel.getByRole('heading', { name: 'studies', exact: true })).toBeVisible()
  await expect(panel.locator('.lookup-lemma strong')).toHaveText('study')
  await expect(panel.getByText('v. 学习；研究', { exact: true })).toBeVisible()
  await expect(panel.getByRole('button', { name: '收藏', exact: true })).toHaveCount(0)
  expect(await panel.evaluate(e => e.getBoundingClientRect().right <= innerWidth)).toBe(true)
  await page.keyboard.press('Escape'); await expect(panel).toHaveCount(0)
  await page.locator('.article-body [data-sentence]').evaluate(element => {
    element.scrollIntoView({block:'center'});const range = document.createRange(); range.setStart(element.firstChild!, 11); range.setEnd(element.firstChild!, 15)
    const selection = window.getSelection()!; selection.removeAllRanges(); selection.addRange(range); document.dispatchEvent(new Event('selectionchange'))
  })
  await page.route('**/api/v1/dictionary/lookup?word=help', route => route.fulfill({ json: { found: false, candidates: [] } }))
  await page.getByRole('button', { name: '查询选中单词：help', exact: true }).click()
  await expect(panel.getByText('本地词典暂未收录这个词。可以继续阅读，稍后再查。')).toBeVisible()
  await panel.getByRole('button', { name: '关闭查词', exact: true }).click()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

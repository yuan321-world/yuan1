import { test, expect } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
test('explicit collection shows its source and cancellation asks for confirmation', async ({ page }) => {
  await mockIdentity(page)
  const candidate = { entryId: '1', lemma: 'study', phonetic: null, senses: [{ partOfSpeech: 'v.', definitionZh: '学习；研究', definitionEn: 'To spend time learning about a subject.' }], sourceNote: 'Lingua Loop原创编辑释义', collection: null }
  const word = { ...candidate, id: '20', active: true, createdAt: '2026-10-16T00:00:00Z', updatedAt: '2026-10-16T00:00:00Z' }
  const version = { id: '11', articleId: '10', title: 'A small study', titleZh: '学习点滴', category: { id: '1', name: '学习' }, difficulty: 'B1', wordCount: 3, estimatedMinutes: 1, author: 'Lingua Loop', sourceType: 'ORIGINAL', usageNote: '原创练习', content: { schemaVersion: 1, paragraphs: [{ key: 'p0001', sentences: [{ key: 'p0001-s0001', text: 'Our studies matter.' }] }] } }
  await page.route('**/api/v1/reading/articles/10/start', route => route.fulfill({ json: { version, progress: { id: '1', articleId: '10', articleVersionId: '11', sentenceKey: 'p0001-s0001', offset: 0, status: 'READING', lockVersion: 0 } } }))
  await page.route('**/api/v1/dictionary/lookup?word=studies', route => route.fulfill({ json: { found: true, candidates: [candidate] } }))
  let active = true, writes = 0, deletes = 0
  await page.route('**/api/v1/vocabulary/words', route => {
    if (route.request().method() === 'POST') {
      expect(route.request().postDataJSON()).toEqual({ articleVersionId: '11', sentenceKey: 'p0001-s0001', startOffset: 4, endOffset: 11, entryId: '1' })
      writes++; return route.fulfill({ status: 200, json: { word, created: false } })
    }
    return route.fallback()
  })
  await page.route('**/api/v1/vocabulary/words?*', route => route.fulfill({ json: { items: active ? [word] : [], page: 1, pageSize: 20, total: active ? 1 : 0 } }))
  await page.route('**/api/v1/vocabulary/words/20?*', route => route.fulfill({ json: { word, sources: { items: [{ id: '30', articleId: '10', articleVersionId: '11', title: 'A small study', sentenceKey: 'p0001-s0001', startOffset: 4, endOffset: 11, surfaceForm: 'studies', sentence: 'Our studies matter.' }], page: 1, pageSize: 10, total: 1 } } }))
  await page.route('**/api/v1/vocabulary/words/20', route => { deletes++; active = false; return route.fulfill({ status: 204 }) })
  await page.goto('/reading/10')
  await expect(page.locator('.article-body')).toBeVisible()
  await page.locator('.article-body [data-sentence]').evaluate(element => {
    element.scrollIntoView({block:'center'});const range = document.createRange(); range.setStart(element.firstChild!, 4); range.setEnd(element.firstChild!, 11)
    const selection = window.getSelection()!; selection.removeAllRanges(); selection.addRange(range); document.dispatchEvent(new Event('selectionchange'))
  })
  await page.getByRole('button', { name: '查询选中单词：studies', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('button', { name: '记录此处来源', exact: true })).toHaveCount(0)
  expect(writes).toBe(0)
  await page.getByRole('dialog').getByRole('button', { name: '收藏生词', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('button', { name: '取消收藏', exact: true })).toBeEnabled()
  await expect(page.getByRole('dialog').getByRole('button', { name: '记录此处来源', exact: true })).toHaveCount(0)
  expect(writes).toBe(1)
  await page.keyboard.press('Escape'); await page.goto('/notebook')
  await page.getByRole('button', { name: '查看来源', exact: true }).click()
  await expect(page.getByText('Our studies matter.', { exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: 'A small study · studies ↗', exact: true })).toHaveAttribute('href', /version=11.*sentence=p0001-s0001.*start=4.*end=11/)
  await page.getByRole('button', { name: '取消收藏', exact: true }).click()
  await page.getByRole('button', { name: '保留', exact: true }).click(); expect(deletes).toBe(0)
  await page.getByRole('button', { name: '取消收藏', exact: true }).click()
  await page.getByRole('button', { name: '确认取消收藏', exact: true }).click()
  await expect(page.getByRole('heading', { name: '还没有收藏的生词', exact: true })).toBeVisible(); expect(deletes).toBe(1)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

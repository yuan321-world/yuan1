import { expect, test } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
test('learner opens a published article with honest source and word count', async ({ page }) => {
  await mockIdentity(page)
  const category = { id: '1', code: 'STUDY', name: '学习' }
  const version = { id: '11', articleId: '10', versionNo: 1, title: 'A fresh page', titleZh: '新的一页', summary: 'Small steps.', category, difficulty: 'B1', content: { schemaVersion: 1, paragraphs: [{ key: 'p0001', sentences: [{ key: 'p0001-s0001', text: 'Every small step matters.' }] }] }, wordCount: 4, estimatedMinutes: 1, author: 'Lingua Loop编辑组', sourceType: 'ORIGINAL', sourceUrl: null, usageNote: '原创学习练习' }
  const progress = { id: '1', articleId: '10', articleVersionId: '11', sentenceKey: 'p0001-s0001', offset: 0, status: 'READING', lockVersion: 0, updatedAt: '2026-10-16T00:00:00Z', completedAt: null }
  await page.route('**/api/v1/reading/categories', route => route.fulfill({ json: [category] }))
  await page.route('**/api/v1/reading/history?*', route => route.fulfill({ json: { items: [], page: 1, pageSize: 10, total: 0 } }))
  await page.route('**/api/v1/reading/articles?*', route => route.fulfill({ json: { items: [{ ...version, id: '10', versionId: '11', progress: null }], page: 1, pageSize: 20, total: 1 } }))
  await page.route('**/api/v1/reading/articles/10/start', route => route.fulfill({ status: 201, json: { version, progress, created: true } }))
  await page.goto('/reading')
  await page.getByRole('link', { name: 'A fresh page', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'A fresh page', exact: true })).toBeVisible()
  await expect(page.locator('.article-body')).toHaveText('Every small step matters.')
  await expect(page.getByText(/Lingua Loop原创练习/)).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

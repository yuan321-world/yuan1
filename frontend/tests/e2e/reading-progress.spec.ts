import { test, expect } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
test('progress failures stay unsynced and conflicts require explicit reload', async ({ page }) => {
  await mockIdentity(page)
  const version = { id: '11', articleId: '10', versionNo: 1, title: 'Small steps', titleZh: '小小进步', category: { id: '1', name: '学习' }, difficulty: 'B1', wordCount: 4, estimatedMinutes: 1, author: 'Lingua Loop', sourceType: 'ORIGINAL', usageNote: '原创练习', content: { schemaVersion: 1, paragraphs: [{ key: 'p0001', sentences: [{ key: 'p0001-s0001', text: 'Every small step matters.' }] }] } }
  let progress = { id: '1', articleId: '10', articleVersionId: '11', sentenceKey: 'p0001-s0001', offset: 0, status: 'READING', lockVersion: 0, updatedAt: '2026-10-16T00:00:00Z', completedAt: null as string | null }
  await page.route('**/api/v1/reading/articles/10/start', route => route.fulfill({ json: { version, progress, created: false } }))
  await page.route('**/api/v1/reading/articles/10/versions/11', route => route.fulfill({ json: { version, progress } }))
  let attempts = 0
  await page.route('**/api/v1/reading/progress/11', route => {
    attempts++
    if (attempts === 1) return route.fulfill({ status: 503, json: { code: 'SERVICE_UNAVAILABLE', message: '服务暂不可用' } })
    if (attempts === 2) return route.fulfill({ status: 409, json: { code: 'READING_PROGRESS_CONFLICT', message: '进度冲突' } })
    progress = { ...progress, status: 'COMPLETED', lockVersion: 2, completedAt: '2026-10-16T00:00:00Z' }
    return route.fulfill({ json: progress })
  })
  await page.goto('/reading/10')
  await page.getByRole('button', { name: '标记为已读', exact: true }).click()
  await expect(page.getByText('进度尚未同步', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '重试保存', exact: true }).click()
  await expect(page.getByText('其他页面已更新进度，请加载最新位置', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '标记为已读', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '加载最新进度', exact: true }).click()
  await page.getByRole('button', { name: '重新加载', exact: true }).click()
  await expect(page.getByText('阅读位置自动保存', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '标记为已读', exact: true }).click()
  await expect(page.getByRole('button', { name: '已读完', exact: true })).toBeDisabled()
  expect(attempts).toBe(3)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

import { test, expect, type Page } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
async function editor(page: Page, published = false) {
  await mockIdentity(page, false, true)
  const category = { id: '1', code: 'STUDY', name: '学习' }
  const draft = { id: '11', articleId: '10', versionNo: 1, title: 'Keep this draft', titleZh: '保留草稿', summary: 'Small steps.', category, difficulty: 'B1', content: { schemaVersion: 1, paragraphs: [{ key: 'p0001', sentences: [{ key: 'p0001-s0001', text: 'The saved server text.' }] }] }, wordCount: 4, estimatedMinutes: 1, author: 'Lingua Loop', sourceType: 'ORIGINAL', sourceUrl: null, usageNote: '原创练习' }
  const article = { id: '10', status: published ? 'PUBLISHED' : 'DRAFT', draftVersionId: '11', publishedVersionId: published ? '11' : null, lockVersion: 0, draft }
  let reads = 0
  await page.route('**/api/v1/admin/articles/categories', route => route.fulfill({ json: [category] }))
  await page.route('**/api/v1/admin/articles/10', route => { reads++; return route.fulfill({ json: article }) })
  await page.goto('/admin/articles/10/edit'); await expect(page.getByLabel('正文', { exact: true })).toHaveValue('The saved server text.')
  return { article, reads: () => reads }
}
test('network reconnection preserves unsaved article edits and permits retry', async ({ page }) => {
  const fixture = await editor(page)
  let writes = 0
  await page.route('**/api/v1/admin/articles/10/draft', route => {
    writes++
    if (writes === 1) return route.abort('failed')
    expect(route.request().postDataJSON().body).toBe('This local correction must survive the outage.')
    return route.fulfill({ json: { ...fixture.article, lockVersion: 1, draftVersionId: '12' } })
  })
  await page.getByLabel('正文', { exact: true }).fill('This local correction must survive the outage.')
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await page.getByRole('button', { name: '重新连接', exact: true }).click()
  await expect(page.getByLabel('正文', { exact: true })).toHaveValue('This local correction must survive the outage.')
  expect(fixture.reads()).toBe(1)
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '草稿已保存' })).toBeVisible(); expect(writes).toBe(2)
})
for (const action of ['publish', 'unpublish'] as const) test(`a ${action} dialog closes when its administrator identity changes`, async ({ page }) => {
  await editor(page, action === 'unpublish')
  let writes = 0
  await page.route(`**/api/v1/admin/articles/10/${action}`, route => { writes++; return route.fulfill({ json: {} }) })
  await page.getByRole('button', { name: action === 'publish' ? '发布草稿' : '下架文章', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  const replacement = { id: 'another-admin', username: 'another_admin', displayName: '另一个管理员', roles: ['USER', 'ADMIN'] }
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ json: { user: replacement, accessToken: 'mock-b', tokenType: 'Bearer', expiresIn: 600 } }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: replacement }))
  await page.route('**/api/v1/admin/identity', route => route.fulfill({ json: replacement }))
  await page.evaluate(() => { const channel = new BroadcastChannel('lingua-loop-identity-v1'); channel.postMessage('account-change'); channel.close() })
  await expect(page).toHaveURL(/\/admin$/)
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(writes).toBe(0)
})

import { expect, test } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
test('administrator can publish a saved newer draft while the previous version stays published', async ({ page }) => {
  await mockIdentity(page, false, true)
  const category = { id: '1', code: 'STUDY', name: '学习' }
  const draft = { id: '12', articleId: '10', versionNo: 2, title: 'A new draft', titleZh: '新草稿', summary: 'Small steps.', category, difficulty: 'B1', content: { schemaVersion: 1, paragraphs: [{ key: 'p0001', sentences: [{ key: 'p0001-s0001', text: 'Small steps matter.' }] }] }, wordCount: 3, estimatedMinutes: 1, author: 'Lingua Loop', sourceType: 'ORIGINAL', sourceUrl: null, usageNote: '原创练习' }
  const article = { id: '10', status: 'PUBLISHED', draftVersionId: '12', publishedVersionId: '11', lockVersion: 2, draft }
  await page.route('**/api/v1/admin/articles/categories', route => route.fulfill({ json: [category] }))
  await page.route('**/api/v1/admin/articles/10', route => route.fulfill({ json: article }))
  let publishes = 0
  await page.route('**/api/v1/admin/articles/10/publish', route => { publishes++; expect(route.request().postDataJSON()).toEqual({ expectedLockVersion: 2 }); return route.fulfill({ json: { ...article, publishedVersionId: '12', lockVersion: 3 } }) })
  await page.goto('/admin/articles/10/edit')
  await expect(page.getByRole('button', { name: '发布新草稿', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '发布新草稿', exact: true }).click()
  await page.getByRole('button', { name: '确认发布', exact: true }).click()
  await expect(page.getByRole('button', { name: '发布新草稿', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '下架文章', exact: true })).toBeEnabled(); expect(publishes).toBe(1)
})
test('admin creates a draft and previews plain text without executing its markup', async ({ page }) => {
  await mockIdentity(page, false, true)
  const category = { id: '1', code: 'STUDY', name: '学习' }
  const version = { id: '11', articleId: '10', versionNo: 1, title: 'Small steps', titleZh: '小小的进步', summary: 'Start small.', category, difficulty: 'B1', content: { schemaVersion: 1, paragraphs: [{ key: 'p0001', sentences: [{ key: 'p0001-s0001', text: '<script>window.untrusted=true</script>' }] }] }, wordCount: 8, estimatedMinutes: 1, author: 'Lingua Loop编辑组', sourceType: 'ORIGINAL', sourceUrl: null, usageNote: '原创学习练习' }
  const article = { id: '10', status: 'DRAFT', draftVersionId: '11', publishedVersionId: null, lockVersion: 0, draft: version }
  await page.route('**/api/v1/admin/articles/categories', route => route.fulfill({ json: [category] }))
  await page.route('**/api/v1/admin/articles?*', route => route.fulfill({ json: { items: [], page: 1, pageSize: 20, total: 0 } }))
  await page.route('**/api/v1/admin/articles', route => route.fulfill({ status: 201, json: article }))
  await page.route('**/api/v1/admin/articles/10', route => route.fulfill({ json: article }))
  await page.route('**/api/v1/admin/articles/10/preview?*', route => route.fulfill({ json: version }))
  await page.goto('/admin/articles')
  await expect(page.getByRole('heading', { name: '文章管理', exact: true })).toBeVisible()
  await page.getByRole('link', { name: '创建文章', exact: true }).click()
  await page.getByLabel('英文标题', { exact: true }).fill('Small steps')
  await page.getByLabel('中文标题', { exact: true }).fill('小小的进步')
  await page.getByLabel('简介', { exact: true }).fill('Start small.')
  await page.getByLabel('正文', { exact: true }).fill('Every small step matters.')
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(page).toHaveURL(/\/admin\/articles\/10\/edit$/)
  await page.getByRole('link', { name: '预览草稿', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Small steps', exact: true })).toBeVisible()
  await expect(page.locator('.article-body')).toContainText('<script>window.untrusted=true</script>')
  expect(await page.evaluate(() => Reflect.get(window, 'untrusted'))).toBeUndefined()
})

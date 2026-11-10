import { test, expect } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
const category = { id: '1', code: 'STUDY', name: '学习' }
const article = { id: '10', versionId: '11', title: 'A public page', titleZh: '公开的一页', summary: 'Small steps.', category, difficulty: 'B1', wordCount: 4, estimatedMinutes: 1, author: 'Lingua Loop', sourceType: 'ORIGINAL', sourceUrl: null, usageNote: '原创练习', progress: null }
async function articleList(page: import('@playwright/test').Page) {
  await page.route('**/api/v1/reading/articles?*', route => route.fulfill({ json: { items: [article], total: 1, page: 1, pageSize: 6 } }))
}

test('switching library tabs while scrolled keeps the featured article still', async ({ page }) => {
  if (page.viewportSize()!.width > 600) await page.setViewportSize({ width: 874, height: 668 })
  await mockIdentity(page, true)
  await page.route('**/api/v1/reading/recommendation', route => route.fulfill({ json: { date: '2026-10-17', article } }))
  await page.route('**/api/v1/reading/articles?*', async route => {
    await new Promise(resolve => setTimeout(resolve, 160))
    await route.fulfill({ json: { items: Array.from({ length: 6 }, (_, i) => ({ ...article, id: String(10 + i), versionId: String(30 + i) })), total: 6, page: 1, pageSize: 6 } })
  })
  await page.goto('/reading')
  await expect(page.locator('.reading-card')).toHaveCount(6)
  await page.locator('.reading-list-heading').evaluate(el => window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 140))
  const initial = await page.locator('.reading-featured').boundingBox()
  for (const name of ['我的收藏', '阅读历史', '全部文章']) {
    await page.getByRole('tab', { name, exact: true }).click()
    const positions = await page.locator('.reading-featured').evaluate(async el => {
      const values: number[] = []
      for (let i = 0; i < 25; i++) { values.push(el.getBoundingClientRect().top); await new Promise(requestAnimationFrame) }
      return values
    })
    expect(Math.max(...positions.map(y => Math.abs(y - initial!.y)))).toBeLessThan(2)
  }
})

test('history supports the same combined filters without leaving the tab', async ({ page }) => {
  await mockIdentity(page)
  const requests: URL[] = []
  await page.route('**/api/v1/reading/categories', route => route.fulfill({ json: [category] }))
  await page.route('**/api/v1/reading/history?*', route => {
    requests.push(new URL(route.request().url()))
    return route.fulfill({ json: { items: [], total: 0, page: 1, pageSize: 10 } })
  })
  await page.goto('/reading')
  await page.getByRole('tab', { name: '阅读历史', exact: true }).click()
  await expect(page.getByLabel('CEFR难度')).toBeEnabled()
  await page.getByPlaceholder('搜索英文标题、中文译名或关键词').fill('small')
  await page.getByLabel('CEFR难度').selectOption('B1')
  await page.getByLabel('文章排序').selectOption('short')
  await page.getByRole('button', { name: '学习', exact: true }).click()
  await expect.poll(() => requests.at(-1)?.searchParams.get('categoryId')).toBe('1')
  expect(requests.at(-1)?.searchParams.get('keyword')).toBe('small')
  expect(requests.at(-1)?.searchParams.get('difficulty')).toBe('B1')
  expect(requests.at(-1)?.searchParams.get('sort')).toBe('short')
  await expect(page.getByRole('tab', { name: '阅读历史', exact: true })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByText('没有找到匹配的阅读记录')).toBeVisible()
})
test('guest list is public and filters never hide the daily pick', async ({ page }) => {
  await mockIdentity(page, true)
  await page.route('**/api/v1/reading/recommendation', route => route.fulfill({ json: { date: '2026-10-16', article } }))
  await page.route('**/api/v1/reading/articles?*', route => route.fulfill({ json: { items: [article], total: 1, page: 1, pageSize: 6 } }))
  await page.route('**/api/v1/reading/categories', route => route.fulfill({ json: [category] }))
  await page.goto('/reading')
  await expect(page.getByRole('heading', { name: '外刊精读', exact: true })).toBeVisible()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByLabel('CEFR难度').selectOption('B1')
  await expect(page.locator('.reading-featured')).toBeVisible()
  await expect(page.locator('.reading-featured')).toContainText(article.title)
  await page.getByRole('tab', { name: '我的收藏', exact: true }).click()
  await expect(page.getByRole('button', { name: '登录后查看' })).toBeVisible()
  await expect(page.locator('.reading-featured')).toBeVisible()
})
test('guest favorite resumes after login, survives reload, and can be canceled', async ({ page }, info) => {
  await mockIdentity(page, true); await articleList(page)
  let signedIn = false, favorite = false, mutations = 0
  const user = { id: 'favorite-user', username: 'reader', displayName: 'Reader', roles: ['USER'] }
  await page.route('**/api/v1/auth/refresh', route => route.fulfill(signedIn ? { json: { user, accessToken: 'mock-access', tokenType: 'Bearer', expiresIn: 600 } } : { status: 401, json: { code: 'UNAUTHENTICATED' } }))
  await page.route('**/api/v1/auth/me', route => route.fulfill(signedIn ? { json: user } : { status: 401, json: { code: 'UNAUTHENTICATED' } }))
  await page.route('**/api/v1/auth/login', route => { signedIn = true; return route.fulfill({ json: { user, accessToken: 'mock-access', tokenType: 'Bearer', expiresIn: 600 } }) })
  await page.route('**/api/v1/reading/favorites/states?*', route => route.fulfill({ json: favorite ? ['10'] : [] }))
  await page.route('**/api/v1/reading/articles/10/favorite', route => { mutations++; favorite = route.request().method() === 'PUT'; return route.fulfill({ json: { articleId: '10', favorite } }) })
  await page.goto('/reading')
  await page.getByRole('button', { name: '收藏文章：A public page' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('用户名', { exact: true }).fill('reader'); await dialog.getByLabel('密码', { exact: true }).fill('test-only-password')
  await dialog.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('button', { name: '取消收藏：A public page' })).toHaveAttribute('aria-pressed', 'true')
  await page.locator('.reading-card').first().screenshot({path:`../.tool-cache/article-favorite-dark-${info.project.name}.png`})
  expect(mutations).toBe(1)
  await page.reload(); await page.getByRole('button', { name: '取消收藏：A public page' }).click()
  await expect(page.getByRole('button', { name: '收藏文章：A public page' })).toHaveAttribute('aria-pressed', 'false')
  expect(mutations).toBe(2)
})
test('guest detail reads without starting progress and exposes reading controls', async ({ page }) => {
  await mockIdentity(page, true)
  let writes = 0
  await page.route('**/api/v1/reading/articles/10/start', route => { writes++; return route.fulfill({ status: 401, json: { code: 'UNAUTHENTICATED' } }) })
  await page.route('**/api/v1/reading/articles/10', route => route.fulfill({ json: { version: { ...article, id: '11', articleId: '10', versionNo: 1, content: { schemaVersion: 1, paragraphs: [{ key: 'p0001', sentences: [{ key: 'p0001-s0001', text: 'Every small step matters.' }] }] } }, progress: null } }))
  await page.goto('/reading/10')
  await expect(page.locator('.article-body')).toHaveText('Every small step matters.')
  expect(await page.locator('.article-body').evaluate(el=>getComputedStyle(el).fontSize)).toBe('17px')
  expect(await page.locator('.article-heading h1').evaluate(el=>parseFloat(getComputedStyle(el).fontSize))).toBeLessThanOrEqual(28)
  await page.getByRole('button', { name: '阅读设置', exact: true }).click()
  await page.getByRole('button', { name: '放大字号' }).click()
  expect(await page.locator('.article-body').evaluate(el => getComputedStyle(el).fontSize)).toBe('18px')
  await expect(page.getByText('游客可阅读与查词，阅读位置不会保存到账号。')).toBeVisible()
  expect(writes).toBe(0)
})
test('logging in from guest detail remounts the reader and starts owned progress', async ({ page }) => {
  await mockIdentity(page, true)
  const version = { ...article, id: '11', articleId: '10', versionNo: 1, content: { schemaVersion: 1, paragraphs: [{ key: 'p0001', sentences: [{ key: 'p0001-s0001', text: 'Every small step matters.' }] }] } }
  const progress = { id: '1', articleId: '10', articleVersionId: '11', sentenceKey: 'p0001-s0001', offset: 0, status: 'READING', lockVersion: 0, updatedAt: '2026-10-16T00:00:00Z', completedAt: null }
  let starts = 0
  await page.route('**/api/v1/reading/articles/10', route => route.fulfill({ json: { version, progress: null } }))
  await page.route('**/api/v1/reading/articles/10/start', route => { starts++; return route.fulfill({ json: { version, progress, created: true } }) })
  await page.route('**/api/v1/auth/login', route => route.fulfill({ json: { user: { id: 'reader', username: 'reader', displayName: 'Reader', roles: ['USER'] }, accessToken: 'mock-access', tokenType: 'Bearer', expiresIn: 600 } }))
  await page.goto('/reading/10'); await page.getByRole('button', { name: '登录后同步' }).click()
  await page.getByLabel('用户名', { exact: true }).fill('reader'); await page.getByLabel('密码', { exact: true }).fill('test-only-password'); await page.getByRole('dialog').getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.locator('.reading-progress-bar')).toBeVisible(); expect(starts).toBe(1)
  await expect(page.getByRole('button', { name: '收藏文章：A public page' })).toBeEnabled()
})
test('favorite failure is local and retains the last known state', async ({ page }) => {
  await mockIdentity(page); await articleList(page)
  await page.route('**/api/v1/reading/articles/10/favorite', route => route.fulfill({ status: 503, json: { code: 'INTERNAL_ERROR', message: '暂不能收藏，请重试' } }))
  await page.goto('/reading')
  await page.getByRole('button', { name: '收藏文章：A public page' }).click()
  await expect(page.locator('.article-favorite-error')).toContainText('暂不能收藏，请重试')
  await expect(page.getByRole('button', { name: '收藏文章：A public page' })).toHaveAttribute('aria-pressed', 'false')
  await page.route('**/api/v1/reading/articles/10/favorite', route => route.fulfill({ json: { articleId: '10', favorite: true } }))
  await page.getByRole('button', { name: '重试操作', exact: true }).click()
  await expect(page.getByRole('button', { name: '取消收藏：A public page' })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.locator('.article-favorite-error')).toHaveCount(0)
})

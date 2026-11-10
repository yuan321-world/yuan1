import { test, expect } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
test('admin lands in data management and keeps account settings within admin layout', async ({ page }) => {
  await mockIdentity(page, false, true)
  await page.goto('/')
  await expect(page).toHaveURL(/\/admin$/)
  await expect(page.getByRole('heading', { name: '数据管理', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '返回学习端' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: '背单词', exact: true })).toHaveCount(0)
  await expect(page.locator('.home-modules')).toHaveCount(0)
  await page.getByRole('link', { name: '账户设置', exact: true }).click()
  await expect(page).toHaveURL(/\/admin\/account$/)
  await expect(page.getByRole('heading', { name: '账户设置', exact: true })).toBeVisible()
  await expect(page.getByRole('navigation', { name: '管理导航' })).toBeVisible()
  await page.goto('/reading'); await expect(page).toHaveURL(/\/admin$/)
  await page.goto('/account'); await expect(page).toHaveURL(/\/admin\/account$/)
  await page.reload(); await expect(page.getByRole('heading', { name: '账户设置', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { token: 'mock-csrf', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/v1/auth/logout', route => route.fulfill({ status: 204 }))
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
})
test('ordinary user cannot open the admin screen', async ({ page }) => {
  await mockIdentity(page)
  await page.goto('/admin')
  await expect(page).toHaveURL(/\/account$/)
  await expect(page.getByRole('heading', { name: '账户设置', exact: true })).toBeVisible()
})

test('data management shows real module counts and keeps links usable when a count fails', async ({ page }) => {
  await mockIdentity(page, false, true)
  let usersAvailable = false
  await page.route('**/api/v1/admin/articles?*', route => route.fulfill({ json: { items: [], total: 6 } }))
  await page.route('**/api/v1/admin/practice/sets?*', route => route.fulfill({ json: { items: [], total: 256 } }))
  await page.route('**/api/v1/admin/practice/papers', route => route.fulfill({ json: [{ id: '1' }] }))
  await page.route('**/api/v1/admin/users?*', route => route.fulfill(usersAvailable
    ? { json: { items: [], total: 0 } }
    : { status: 503, json: { code: 'UNAVAILABLE', message: '服务暂不可用' } }))

  await page.goto('/admin')
  const modules = page.getByRole('list', { name: '管理模块' })
  await expect(modules.getByRole('listitem').filter({ hasText: '文章管理' })).toContainText('6 篇')
  await expect(modules.getByRole('listitem').filter({ hasText: '专项题组' })).toContainText('256 组')
  await expect(modules.getByRole('listitem').filter({ hasText: '整卷管理' })).toContainText('1 套')
  await expect(modules.getByRole('listitem').filter({ hasText: '用户管理' })).toContainText('数量暂不可用')
  await expect(modules.getByRole('link', { name: '进入留言管理' })).toHaveAttribute('href', '/admin/wall')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)

  await page.setViewportSize({ width: 718, height: 668 })
  const navigation = await page.getByRole('navigation', { name: '管理导航' }).boundingBox()
  const content = await page.getByRole('main').boundingBox()
  expect(content!.y).toBeGreaterThan(navigation!.y + navigation!.height)

  usersAvailable = true
  await page.getByRole('button', { name: '刷新数量' }).click()
  await expect(modules.getByRole('listitem').filter({ hasText: '用户管理' })).toContainText('0 个')
  await modules.getByRole('link', { name: '进入整卷管理' }).click()
  await expect(page).toHaveURL(/\/admin\/practice\/papers$/)
})

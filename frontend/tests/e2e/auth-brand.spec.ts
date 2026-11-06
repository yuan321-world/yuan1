import { test, expect } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
test.beforeEach(async ({ page }) => { await mockIdentity(page, true) })

test('legacy entries share modal branding without the old promotional layout', async ({ page }) => {
  for (const [path, title] of [['/login', '欢迎回来'], ['/register', '创建账号'], ['/forgot-password', '重置密码']]) {
    await page.goto(path!)
    await expect(page.getByRole('dialog').getByRole('heading', { name: title!, exact: true })).toBeVisible()
    await expect(page.locator('.auth-dialog-header .brand')).toContainText('Lingua Loop')
    await expect(page.locator('.auth-brand-panel, .auth-layout')).toHaveCount(0)
    await expect(page).toHaveURL('/')
  }
})
test('tablet entry uses the same modal without horizontal overflow', async ({ page }) => {
  await page.setViewportSize({ width: 768, height: 700 })
  await page.goto('/register')
  await page.getByRole('button', { name: '注册', exact: true }).scrollIntoViewIfNeeded()
  await expect(page.getByRole('button', { name: '注册', exact: true })).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
test('mode switches keep the home branding in place', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('dialog')).toBeVisible()
  const original = (await page.locator('.home-brand').boundingBox())!
  await page.getByRole('button', { name: '还没有账号？创建账号' }).click()
  await expect(page.getByRole('heading', { name: '创建账号', exact: true })).toBeVisible()
  const registered = (await page.locator('.home-brand').boundingBox())!
  expect(registered.x).toBe(original.x); expect(registered.y).toBe(original.y)
  await page.getByRole('button', { name: '已有账号？去登录' }).click()
  await expect(page.getByRole('heading', { name: '欢迎回来', exact: true })).toBeVisible()
})

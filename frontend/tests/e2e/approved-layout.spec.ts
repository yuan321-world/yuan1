import { test, expect } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'

test('approved home structure has six static modules and honest empty records', async ({ page }) => {
  await mockIdentity(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Lingua Loop', exact: true })).toBeVisible()
  await expect(page.locator('.home-modules .module-tile')).toHaveCount(6)
  await expect(page.locator('.home-modules a, .home-modules button')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '今日计划', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '本周打卡', exact: true })).toBeVisible()
  await expect(page.locator('.daily-quote')).toBeVisible()
  await expect(page.getByText('47 个到期')).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('login layout offers real submission with an anonymous Mock session', async ({ page }) => {
  await mockIdentity(page, true)
  await page.goto('/login')
  await expect(page.getByLabel('用户名', { exact: true })).toBeVisible()
  await expect(page.getByLabel('密码', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeEnabled()
  await expect(page.getByRole('link', { name: '还没有账号？创建账号' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

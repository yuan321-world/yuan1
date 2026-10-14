import { test, expect } from '@playwright/test'

test('approved home structure has six static modules and honest empty records', async ({ page }) => {
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

test('login layout does not impersonate a working authentication flow', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByLabel('用户名', { exact: true })).toBeVisible()
  await expect(page.getByLabel('密码', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeDisabled()
  await expect(page.getByText('真实账号登录将在阶段 1 开放。')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

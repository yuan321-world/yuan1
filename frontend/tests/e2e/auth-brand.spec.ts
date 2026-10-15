import { test, expect } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
test.beforeEach(async ({ page }) => { await mockIdentity(page, true) })

test('login and register share balanced desktop branding while mobile prioritizes the form', async ({ page }, info) => {
  for (const [path, title] of [['/login', '欢迎回来'], ['/register', '创建账号']]) {
    await page.goto(path!)
    await expect(page.getByRole('heading', { name: title!, exact: true })).toBeVisible()
    const panel = page.locator('.auth-brand-panel')
    const form = page.locator('.login-card')
    if (info.project.name === 'desktop') {
      await expect(panel).toBeVisible()
      await expect(panel.getByRole('heading')).toHaveText('Build your English, step by step.')
      await expect(panel.locator('.auth-feature')).toHaveCount(3)
      await expect(panel.getByText('Every small step matters.', { exact: true })).toBeVisible()
      const left = (await panel.boundingBox())!, right = (await form.boundingBox())!
      expect(left.x + left.width + 32).toBeLessThanOrEqual(right.x)
      expect(Math.abs(left.y + left.height / 2 - right.y - right.height / 2)).toBeLessThan(40)
    } else {
      await expect(panel).toBeHidden()
      const card = (await form.boundingBox())!
      expect(card.y + card.height).toBeLessThanOrEqual(page.viewportSize()!.height)
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  }
})

test('tablet layout keeps brand text compact and never squeezes the form', async ({ page }) => {
  await page.setViewportSize({ width: 820, height: 1180 })
  await page.goto('/register')
  await expect(page.locator('.auth-brand-panel')).toBeVisible()
  await expect(page.locator('.auth-word-card')).toBeHidden()
  const card = (await page.locator('.login-card').boundingBox())!
  expect(card.width).toBeGreaterThanOrEqual(400)
  expect(card.y + card.height).toBeLessThanOrEqual(1180)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('switching login and register preserves brand position on short and tall screens', async ({ page }) => {
  for (const [width, height] of [[1400, 740], [1120, 664], [1280, 800], [1440, 1000], [820, 1180]]) {
    await page.setViewportSize({ width: width!, height: height! })
    await page.goto('/login')
    await expect(page.getByRole('heading', { name: '欢迎回来', exact: true })).toBeVisible()
    const panel = page.locator('.auth-brand-panel')
    const original = (await panel.boundingBox())!
    await page.getByRole('link', { name: '还没有账号？创建账号', exact: true }).click()
    await expect(page.getByRole('heading', { name: '创建账号', exact: true })).toBeVisible()
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0)
    const registered = (await panel.boundingBox())!
    expect(Math.abs(registered.y - original.y), `${width}x${height}: brand vertical movement`).toBeLessThan(1)
    expect(Math.abs(registered.x - original.x), `${width}x${height}: brand horizontal movement`).toBeLessThan(1)
    await page.getByRole('link', { name: '已有账号？去登录', exact: true }).click()
    await expect(page.getByRole('heading', { name: '欢迎回来', exact: true })).toBeVisible()
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0)
    const returned = (await panel.boundingBox())!
    expect(Math.abs(returned.y - original.y)).toBeLessThan(1)
    expect(Math.abs(returned.x - original.x)).toBeLessThan(1)
  }
})

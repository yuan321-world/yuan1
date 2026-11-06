import { expect, test } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'

test.beforeEach(async ({ page }) => { await mockIdentity(page) })

test('home groups static modules and retains quote sources without personal records', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-12-31T16:05:00Z'))
  await page.goto('/')
  await expect(page.locator('.core-modules .module-tile')).toHaveCount(3)
  await expect(page.locator('.auxiliary-modules .module-tile')).toHaveCount(3)
  await expect(page.locator('.home-modules a')).toHaveCount(5)
  await expect(page.locator('.home-modules a[href="/wall"]')).toHaveCount(1)
  await expect(page.locator('.home-modules a[href="/reading"]')).toHaveCount(1)
  await expect(page.locator('.home-modules a[href="/notebook"]')).toHaveCount(1)
  await expect(page.locator('.home-modules button')).toHaveCount(0)
  await expect(page.locator('.module-glyph svg')).toHaveCount(6)
  await expect(page.locator('.daily-quote-english')).not.toBeEmpty()
  await expect(page.locator('.daily-quote footer a')).toHaveAttribute('href', /^https:/)
  await expect(page.locator('.home-records, .week-checkin, .today-plan')).toHaveCount(0)
  await expect(page.locator('.home-connection, .system-status-badge')).toHaveCount(0)
  await expect(page.getByText('工程基础预览', { exact: true })).toHaveCount(0)
})

test('account disclosure supports escape, outside click, navigation and a real logout request', async ({ page }) => {
  let logouts = 0
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { token: 'mock', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/v1/auth/logout', route => { logouts++; return route.fulfill({ status: 204 }) })
  await page.goto('/')
  const toggle = page.getByRole('button', { name: '用户菜单', exact: true })
  await toggle.focus(); await page.keyboard.press('Enter')
  await expect(toggle).toHaveAttribute('aria-expanded', 'true')
  await page.keyboard.press('Tab')
  await expect(page.getByRole('region', { name: '账户菜单' }).getByRole('link', { name: '账户设置' })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(toggle).toBeFocused(); await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  await toggle.click(); await page.locator('.home-brand').click({ position: { x: 2, y: 2 } })
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  await toggle.click(); await page.getByRole('region', { name: '账户菜单' }).getByRole('link', { name: '账户设置' }).click()
  await expect(page).toHaveURL('/account')
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  await toggle.click(); await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL('/'); expect(logouts).toBe(1)
})

test('short and narrow layouts remain scrollable with visible focused targets below sticky navigation', async ({ page }) => {
  for (const width of [320, 768, 1440]) {
    await page.setViewportSize({ width, height: 700 })
    await page.goto('/')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    const target = page.locator('.daily-quote footer a')
    await target.focus(); await target.scrollIntoViewIfNeeded()
    const nav = await page.locator('.topbar').boundingBox(), link = await target.boundingBox()
    expect(nav && link).toBeTruthy()
    expect(link!.y).toBeGreaterThanOrEqual(nav!.y + nav!.height)
    await page.getByRole('button', { name: '用户菜单', exact: true }).click()
    const panel = await page.getByRole('region', { name: '账户菜单' }).boundingBox()
    expect(panel!.x).toBeGreaterThanOrEqual(0)
    expect(panel!.x + panel!.width).toBeLessThanOrEqual(width)
  }
})

test('failed logout remains recoverable in the account menu and blocks duplicate submits', async ({ page }) => {
  let requests = 0, release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { token: 'mock', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/v1/auth/logout', async route => { requests++; await gate; await route.fulfill({ status: 503, json: { code: 'INTERNAL_ERROR' } }) })
  await page.goto('/')
  await page.getByRole('button', { name: '用户菜单', exact: true }).click()
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  try {
    await expect(page.getByRole('button', { name: '正在退出…', exact: true })).toBeDisabled()
    await expect.poll(() => requests).toBe(1)
  } finally { release() }
  await expect(page.getByRole('region', { name: '账户菜单' }).getByRole('alert')).toBeVisible()
  await expect(page.getByRole('button', { name: '退出登录', exact: true })).toBeEnabled()
  await expect(page).toHaveURL('/')
})

test('home exposes completed vocabulary and practice routes with a working review entry',async({page})=>{await page.goto('/');await expect(page.locator('.core-modules a[href="/vocabulary"]')).toBeVisible();await expect(page.locator('.core-modules a[href="/practice"]')).toBeVisible();await expect(page.locator('.core-modules')).not.toContainText('尚未开放');const review=page.getByRole('link',{name:'开始今天的复习',exact:true});await expect(review).toHaveAttribute('href','/vocabulary');await review.click();await expect(page).toHaveURL('/vocabulary')})

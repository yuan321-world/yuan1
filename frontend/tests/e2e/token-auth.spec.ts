import { test, expect } from '@playwright/test'

test('two tabs serialize credential recovery and broadcast logout without storing tokens', async ({ context, page }) => {
  let loggedIn = true, active = 0, maximum = 0
  const user = { id: 'mock-token-user', username: 'reader', displayName: 'Reader', roles: ['USER'] }
  await context.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { token: 'mock-csrf', headerName: 'X-CSRF-TOKEN' } }))
  await context.route('**/api/v1/auth/refresh', async route => {
    active++; maximum = Math.max(maximum, active)
    await new Promise(resolve => setTimeout(resolve, 80))
    active--
    await route.fulfill(loggedIn ? { json: { user, accessToken: 'mock-access', tokenType: 'Bearer', expiresIn: 600 } } : { status: 401, json: { code: 'UNAUTHENTICATED' } })
  })
  await context.route('**/api/v1/auth/me', route => route.fulfill(loggedIn ? { json: user } : { status: 401, json: { code: 'UNAUTHENTICATED' } }))
  await context.route('**/api/v1/auth/logout', async route => { loggedIn = false; await route.fulfill({ status: 204 }) })
  const other = await context.newPage()
  await Promise.all([page.goto('/'), other.goto('/')])
  await expect(page.getByRole('heading', { name: 'Lingua Loop', exact: true })).toBeVisible()
  await expect(other.getByRole('heading', { name: 'Lingua Loop', exact: true })).toBeVisible()
  expect(maximum).toBe(1)
  await page.getByRole('button', { name: '用户菜单', exact: true }).click()
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
  await expect(other).toHaveURL(/\/$/)
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
  await expect(other.getByRole('button', { name: '登录', exact: true })).toBeVisible()
  for (const tab of [page, other]) expect(await tab.evaluate(() => [Object.keys(localStorage), Object.keys(sessionStorage)])).toEqual([[], []])
  await other.close()
})

test('refresh outage shows a recoverable login state without redirect loops', async ({ page }) => {
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { token: 'mock-csrf', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ status: 503, json: { code: 'AUTH_SERVICE_UNAVAILABLE' } }))
  await page.goto('/account')
  await expect(page.getByRole('button', { name: '重新连接', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeDisabled()
})

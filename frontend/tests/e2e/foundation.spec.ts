import { test, expect } from '@playwright/test'
import { mockIdentity, realAccount } from './identity-fixtures'
test.beforeEach(async ({ page }, info) => { if (!info.title.startsWith('real backend')) await mockIdentity(page) })

const statusPath = '**/api/v1/system/status'
const ready = { status: 'UP', service: 'english-hero', database: 'UP' }

test('mocked healthy response appears without browser errors', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route(statusPath, route => route.fulfill({ status: 200, json: ready }))
  await page.goto('/')
  await expect(page.locator('.system-status-badge')).toHaveCount(0)
  await page.getByRole('link', { name: '读今天的文章' }).click()
  await expect(page.getByRole('heading', { name: '外刊精读', exact: true })).toBeVisible()
  expect(errors).toEqual([])
})

test('home does not request or display developer health diagnostics', async ({ page }) => {
  let requests = 0
  await page.route(statusPath, route => { requests++; return route.fulfill({ status: 503, json: { status: 'DOWN', service: 'english-hero', database: 'DOWN' } }) })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Lingua Loop', exact: true })).toBeVisible()
  await expect(page.getByText('开发连接状态', { exact: true })).toHaveCount(0)
  await expect(page.locator('.system-status-badge')).toHaveCount(0)
  expect(requests).toBe(0)
})

test('real backend readiness travels through Vite to MySQL', async ({ page }) => {
  test.info().annotations.push({ type: 'scope', description: 'Real identity + database' })
  test.skip(process.env.E2E_REAL_BACKEND !== '1' || !process.env.E2E_MAILBOX_URL, 'Requires the dedicated dev database, running Java backend, and local SMTP test mailbox; this is not a mocked test.')
  await realAccount(page)
  await page.goto('/')
  const response = await page.request.get('/api/v1/system/status')
  expect(response.status()).toBe(200)
  expect(await response.json()).toEqual(ready)
  await expect(page.getByRole('heading', { name: 'Lingua Loop', exact: true })).toBeVisible()
})

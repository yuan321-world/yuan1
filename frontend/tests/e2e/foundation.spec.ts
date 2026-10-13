import { test, expect } from '@playwright/test'

const statusPath = '**/api/v1/system/status'
const ready = { status: 'UP', service: 'english-hero', database: 'UP' }

test('mocked healthy response appears without browser errors', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route(statusPath, route => route.fulfill({ status: 200, json: ready }))
  await page.goto('/')
  await expect(page.getByText(/连接正常/)).toBeVisible()
  await page.locator('.entry-item').filter({ hasText: '外刊精读' }).click()
  await expect(page.getByRole('heading', { name: '外刊精读', exact: true })).toBeVisible()
  expect(errors).toEqual([])
})

test('mocked backend failure can be retried', async ({ page }) => {
  let unavailable = true
  await page.route(statusPath, route => route.fulfill(unavailable
    ? { status: 503, json: { status: 'DOWN', service: 'english-hero', database: 'DOWN' } }
    : { status: 200, json: ready }))
  await page.goto('/')
  await expect(page.getByText('服务暂不可用，点击重试', { exact: true })).toBeVisible()
  unavailable = false
  await page.getByRole('button', { name: /重试/ }).click()
  await expect(page.getByText(/连接正常/)).toBeVisible()
})

test('a 200 HTML fallback is displayed as unavailable', async ({ page }) => {
  await page.route(statusPath, route => route.fulfill({ status: 200, contentType: 'text/html', body: '<html>fallback</html>' }))
  await page.goto('/')
  await expect(page.getByText('服务暂不可用，点击重试', { exact: true })).toBeVisible()
})

test('real backend readiness travels through Vite to MySQL', async ({ page }) => {
  test.skip(process.env.E2E_REAL_BACKEND !== '1', 'Requires configured dedicated dev database and running Java backend; this is not a mocked test.')
  const responsePromise = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/system/status')
  await page.goto('/')
  const response = await responsePromise
  expect(response.status()).toBe(200)
  expect(await response.json()).toEqual(ready)
  await expect(page.getByText(/连接正常/)).toBeVisible()
})

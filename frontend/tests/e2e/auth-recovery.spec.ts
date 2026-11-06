import { test, expect } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
test('authenticated navigation failure offers connection retry without redirect loop', async ({ page }, info) => {
  await mockIdentity(page)
  await page.route('**/api/v1/system/status', route => route.fulfill({ json: { status: 'UP', service: 'english-hero', database: 'UP' } }))
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Lingua Loop', exact: true })).toBeVisible()
  await page.route('**/api/v1/auth/me', route => route.fulfill({ status: 503, json: { code: 'INTERNAL_ERROR' } }))
  await page.getByRole('button', { name: '用户菜单', exact: true }).click()
  await page.getByRole('link', { name: '账户设置', exact: true }).click()
  await expect(page.getByRole('button', { name: '重新连接' })).toBeVisible()
  await expect(page).toHaveURL('/')
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeDisabled()
})

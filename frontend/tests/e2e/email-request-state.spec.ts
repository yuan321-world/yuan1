import { expect, test } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'

test.beforeEach(async ({ page }) => {
  await mockIdentity(page, true)
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { token: 'mock', headerName: 'X-CSRF-TOKEN' } }))
})

test('reset cooldown describes request acceptance and never claims a matching address', async ({ page }) => {
  let release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/v1/auth/email-codes', async route => {
    await gate
    await route.fulfill({ status: 202, json: { requestState: 'ACCEPTED', challengeId: 'opaque', expiresIn: 300, resendAfter: 60 } })
  })
  await page.goto('/forgot-password')
  await page.getByLabel('用户名', { exact: true }).fill('unknown_reader')
  await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  try {
    await expect(page.getByRole('button', { name: '请求中…', exact: true })).toBeDisabled()
    await expect(page.getByRole('button', { name: /再次请求（/ })).toHaveCount(0)
  } finally { release() }
  await expect(page.getByRole('button', { name: /再次请求（\d+秒）/ })).toBeDisabled()
  await expect(page.getByRole('status')).toContainText('仅当用户名与已验证邮箱匹配时才会发送验证码')
  await expect(page.getByLabel('邮箱地址', { exact: true })).toBeEnabled()
})

test('binding reports asynchronous transport failure while preserving server cooldown', async ({ page }) => {
  await mockIdentity(page)
  await page.route('**/api/v1/account/email-codes', route => route.fulfill({ status: 202, json: { requestState: 'ACCEPTED', challengeId: 'binding-proof', expiresIn: 300, resendAfter: 60 } }))
  await page.route('**/api/v1/account/email-codes/binding-proof', route => route.fulfill({ json: { state: 'FAILED' } }))
  await page.goto('/account')
  await page.getByLabel('当前密码（验证邮箱）', { exact: true }).fill('valid-password')
  await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  await expect(page.locator('.account-email-form .form-feedback')).toContainText('邮件发送失败')
  await expect(page.getByRole('button', { name: /再次请求（\d+秒）/ })).toBeDisabled()
  await expect(page.getByLabel('邮箱地址', { exact: true })).toBeEnabled()
})

test('occupied binding address is an inline error without a normal request countdown', async ({ page }) => {
  await mockIdentity(page)
  await page.route('**/api/v1/account/email-codes', route => route.fulfill({ status: 409, json: { code: 'EMAIL_TAKEN', fieldErrors: { email: '该邮箱已被使用，请更换邮箱' } } }))
  await page.goto('/account')
  await page.getByLabel('当前密码（验证邮箱）', { exact: true }).fill('valid-password')
  await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  await expect(page.locator('#account-email-email-error')).toContainText('该邮箱已被使用')
  await expect(page.getByRole('button', { name: '获取验证码', exact: true })).toBeEnabled()
})

test('binding status network recovery checks status without resending mail', async ({ page }) => {
  await mockIdentity(page)
  let sends = 0, checks = 0
  await page.route('**/api/v1/account/email-codes', route => {
    sends++
    return route.fulfill({ status: 202, json: { requestState: 'ACCEPTED', challengeId: 'binding-proof', expiresIn: 300, resendAfter: 60 } })
  })
  await page.route('**/api/v1/account/email-codes/binding-proof', route => {
    checks++
    return checks === 1 ? route.abort() : route.fulfill({ json: { state: 'SMTP_ACCEPTED' } })
  })
  await page.goto('/account')
  await page.getByLabel('当前密码（验证邮箱）', { exact: true }).fill('valid-password')
  await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  await expect(page.locator('.page-feedback')).toContainText('无法连接')
  await page.getByRole('button', { name: '重新检查发送状态', exact: true }).click()
  await expect(page.locator('.page-feedback')).toHaveCount(0)
  await expect(page.getByRole('status')).toContainText('邮件服务已接受发送')
  expect(sends).toBe(1); expect(checks).toBe(2)
})

test('editing the binding address cancels stale delivery status feedback', async ({ page }) => {
  await mockIdentity(page)
  let release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  let requested = false
  await page.route('**/api/v1/account/email-codes', route => route.fulfill({ status: 202, json: { requestState: 'ACCEPTED', challengeId: 'binding-proof', expiresIn: 300, resendAfter: 60 } }))
  await page.route('**/api/v1/account/email-codes/binding-proof', async route => {
    requested = true; await gate
    await route.fulfill({ json: { state: 'FAILED' } }).catch(() => {})
  })
  await page.goto('/account')
  await page.getByLabel('当前密码（验证邮箱）', { exact: true }).fill('valid-password')
  await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  try {
    await expect.poll(() => requested).toBe(true)
    await page.getByLabel('邮箱地址', { exact: true }).fill('new-reader@example.com')
  } finally { release() }
  await expect(page.locator('#account-email-code-error')).toContainText('邮箱已更改')
  await expect(page.locator('.account-email-form .form-feedback')).toHaveCount(0)
  await expect(page.getByRole('status')).toHaveCount(0)
})

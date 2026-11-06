import { expect, test } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'

const csrf = (page: import('@playwright/test').Page) =>
  page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { token: 'mock-csrf', headerName: 'X-CSRF-TOKEN' } }))

test.beforeEach(async ({ page }) => {
  await mockIdentity(page, true)
  await csrf(page)
})

test('login credentials error is shown once inside the form and clears when credentials change', async ({ page }) => {
  await page.route('**/api/v1/auth/login', route => route.fulfill({
    status: 401,
    json: { code: 'BAD_CREDENTIALS', message: '用户名或密码不正确' },
  }))
  await page.goto('/login')
  const form = page.locator('.auth-form')
  await page.getByLabel('用户名', { exact: true }).fill('reader_test')
  await page.getByLabel('密码', { exact: true }).fill('wrong-passphrase')
  await page.getByRole('button', { name: '登录', exact: true }).click()

  await expect(form.getByText('用户名或密码不正确', { exact: true })).toHaveCount(1)
  await expect(page.locator('.auth-form .auth-error')).toHaveCount(1)
  await expect(page.locator('body').getByText('用户名或密码不正确', { exact: true })).toHaveCount(1)
  await page.getByLabel('密码', { exact: true }).fill('another-passphrase')
  await expect(form.getByText('用户名或密码不正确', { exact: true })).toHaveCount(0)
})

test('login network failure is page-level and keeps the username for retry', async ({ page }) => {
  await page.route('**/api/v1/auth/login', route => route.abort())
  await page.goto('/login')
  const form = page.locator('.auth-form')
  await page.getByLabel('用户名', { exact: true }).fill('reader_test')
  await page.getByLabel('密码', { exact: true }).fill('valid-passphrase')
  await page.getByRole('button', { name: '登录', exact: true }).click()

  await expect(page.getByRole('alert').filter({ hasText: /连接|网络/ })).toBeVisible()
  await expect(form.getByRole('alert')).toHaveCount(0)
  await expect(page.getByLabel('用户名', { exact: true })).toHaveValue('reader_test')
  await expect(page.getByLabel('密码', { exact: true })).toHaveValue('')
})

test('reset collects password errors and focuses the first invalid field in DOM order', async ({ page }) => {
  await page.route('**/api/v1/auth/email-codes', route => route.fulfill({
    status: 202,
    json: { challengeId: 'reset-challenge', requestState: 'ACCEPTED', expiresIn: 300, resendAfter: 60 },
  }))
  await page.goto('/forgot-password')
  await page.getByLabel('用户名', { exact: true }).fill('reader_test')
  await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  await page.getByLabel('邮箱验证码', { exact: true }).fill('123456')
  await page.getByLabel('新密码', { exact: true }).fill('short')
  await page.getByLabel('确认新密码', { exact: true }).fill('different')
  await page.getByRole('button', { name: '重置密码', exact: true }).click()

  await expect(page.locator('#newPassword-error')).toHaveText(/密码/)
  await expect(page.locator('#confirmation-error')).toHaveText('两次密码不一致')
  await expect(page.getByLabel('新密码', { exact: true })).toBeFocused()
  await expect(page.locator('.auth-form .auth-error')).toHaveCount(0)

  await page.getByLabel('新密码', { exact: true }).fill('different')
  await expect(page.locator('#newPassword-error')).toHaveCount(0)
  await expect(page.locator('#confirmation-error')).toHaveCount(0)
})

test('requesting a reset code validates username and email together in form order', async ({ page }) => {
  let requests = 0
  await page.route('**/api/v1/auth/email-codes', route => { requests++; return route.fulfill({ status: 202, json: { challengeId: 'reset-challenge', requestState: 'ACCEPTED', expiresIn: 300, resendAfter: 60 } }) })
  await page.goto('/forgot-password')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()

  await expect(page.locator('#username-error')).toHaveText('请填写用户名')
  await expect(page.locator('#reset-email-error')).toHaveText('请填写邮箱地址')
  await expect(page.getByLabel('用户名', { exact: true })).toBeFocused()
  expect(requests).toBe(0)
})

test('page reconnect uses a safe GET and leaves code retry to the email-code control', async ({ page }) => {
  let codeRequests = 0, statusChecks = 0
  await page.route('**/api/v1/auth/email-codes', async route => {
    codeRequests++
    if (codeRequests === 1) return route.abort()
    return route.fulfill({ status: 202, json: { challengeId: 'reset-challenge', requestState: 'ACCEPTED', expiresIn: 300, resendAfter: 60 } })
  })
  await page.route('**/api/v1/system/status', async route => {
    statusChecks++
    expect(route.request().method()).toBe('GET')
    await route.fulfill({ status: 200, json: { status: 'UP' } })
  })
  await page.goto('/forgot-password')
  await page.getByLabel('用户名', { exact: true }).fill('reader_test')
  await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  await expect(page.locator('.auth-page-feedback')).toBeVisible()
  await expect(page.locator('.auth-form .auth-error')).toHaveCount(0)

  await page.getByRole('button', { name: '重新连接', exact: true }).click()
  await expect(page.locator('.auth-page-feedback')).toHaveCount(0)
  expect(statusChecks).toBe(1)
  expect(codeRequests).toBe(1)

  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText('请求已受理。仅当用户名与已验证邮箱匹配时才会发送验证码。未收到时，请核对注册或绑定时使用的邮箱，并检查垃圾邮件。')
  await expect(page.getByRole('button', { name: /再次请求（\d+秒）/ })).toBeDisabled()
  expect(codeRequests).toBe(2)
})

test('reset keeps the server password error visible after clearing password values', async ({ page }) => {
  await page.route('**/api/v1/auth/email-codes', route => route.fulfill({ status: 202, json: { challengeId: 'reset-challenge', requestState: 'ACCEPTED', expiresIn: 300, resendAfter: 60 } }))
  await page.route('**/api/v1/auth/password-reset', route => route.fulfill({
    status: 400,
    json: { code: 'VALIDATION_ERROR', message: '请检查填写内容', fieldErrors: { newPassword: '新密码不符合要求' } },
  }))
  await page.goto('/forgot-password')
  await page.getByLabel('用户名', { exact: true }).fill('reader_test')
  await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  await page.getByLabel('邮箱验证码', { exact: true }).fill('123456')
  await page.getByLabel('新密码', { exact: true }).fill('replacement-passphrase')
  await page.getByLabel('确认新密码', { exact: true }).fill('replacement-passphrase')
  await page.getByRole('button', { name: '重置密码', exact: true }).click()

  await expect(page.getByLabel('新密码', { exact: true })).toHaveValue('')
  await expect(page.locator('#newPassword-error')).toHaveText('新密码不符合要求')
  await expect(page.getByLabel('新密码', { exact: true })).toHaveAttribute('aria-invalid', 'true')
  await expect(page.getByLabel('新密码', { exact: true })).toBeFocused()
})

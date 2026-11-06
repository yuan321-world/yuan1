import { expect, test } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'

const csrfToken = 'mock-csrf'
const csrf = async (page: import('@playwright/test').Page) => page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { token: csrfToken, headerName: 'X-CSRF-TOKEN' } }))

test.beforeEach(async ({ page }) => {
  await mockIdentity(page, true); await csrf(page)
  await page.route('**/api/v1/account/email-codes/*', route => route.fulfill({ json: { state: 'SMTP_ACCEPTED' } }))
})

test('registration requests a code, binds it to username/email, and exposes password visibility', async ({ page }) => {
  let release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  let codeRequest: Record<string, unknown> | undefined
  let registration: Record<string, unknown> | undefined
  await page.route('**/api/v1/auth/email-codes', async route => {
    codeRequest = route.request().postDataJSON()
    await gate
    await route.fulfill({ status: 202, json: { challengeId: 'register-challenge', requestState: 'ACCEPTED', expiresIn: 300, resendAfter: 60 } })
  })
  await page.route('**/api/v1/auth/register', async route => {
    registration = route.request().postDataJSON()
    await route.fulfill({ status: 201, json: { id: 'registered', username: 'reader_test', displayName: 'reader_test', roles: ['USER'], email: 'reader@example.com', emailVerified: true } })
  })
  await page.goto('/register')
  await page.getByLabel('用户名', { exact: true }).fill('reader_test')
  await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  try {
    await expect(page.getByRole('button', { name: '请求中…', exact: true })).toBeDisabled()
    await expect(page.getByLabel('密码', { exact: true })).toBeDisabled()
    await expect(page.getByRole('button', { name: '已有账号？去登录' })).toHaveCount(0)
  } finally { release() }
  await expect.poll(() => codeRequest).toEqual({ purpose: 'REGISTER', email: 'reader@example.com', username: 'reader_test' })
  await expect(page.getByRole('status')).toHaveText('验证码请求已提交，请留意邮箱')
  await expect(page.getByRole('button', { name: /再次请求（\d+秒）/ })).toBeDisabled()
  await page.getByLabel('邮箱验证码', { exact: true }).fill('123456')
  await page.getByLabel('密码', { exact: true }).fill('valid-passphrase')
  await page.getByRole('button', { name: '显示密码', exact: true }).first().click()
  await expect(page.getByLabel('密码', { exact: true })).toHaveAttribute('type', 'text')
  await page.getByLabel('确认密码', { exact: true }).fill('valid-passphrase')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText('注册成功，请登录')
  await expect(page.getByLabel('密码', { exact: true })).toHaveValue('')
  await expect(page.getByLabel('密码', { exact: true })).toHaveAttribute('type', 'password')
  expect(registration).toEqual({ username: 'reader_test', password: 'valid-passphrase', email: 'reader@example.com', code: '123456', challengeId: 'register-challenge' })
  expect(await page.evaluate(() => Object.keys(localStorage))).toEqual([])
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('forgot-password uses a generic response and remains scrollable on a short phone', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 568 })
  let codeRequest: Record<string, unknown> | undefined
  let resetRequest: Record<string, unknown> | undefined
  await page.route('**/api/v1/auth/email-codes', async route => {
    codeRequest = route.request().postDataJSON()
    await route.fulfill({ status: 202, json: { challengeId: 'reset-challenge', requestState: 'ACCEPTED', expiresIn: 300, resendAfter: 60 } })
  })
  await page.route('**/api/v1/auth/password-reset', async route => {
    resetRequest = route.request().postDataJSON()
    await route.fulfill({ status: 204 })
  })
  await page.goto('/login')
  await page.getByRole('button', { name: '忘记密码？', exact: true }).click()
  await expect(page).toHaveURL('/')
  await expect(page.locator('.auth-brand-panel')).toBeHidden()
  await page.getByLabel('用户名', { exact: true }).fill('reader_test')
  await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText('请求已受理。仅当用户名与已验证邮箱匹配时才会发送验证码。未收到时，请核对注册或绑定时使用的邮箱，并检查垃圾邮件。')
  expect(codeRequest).toEqual({ purpose: 'RESET', email: 'reader@example.com', username: 'reader_test' })
  await page.getByLabel('邮箱验证码', { exact: true }).fill('654321')
  await page.getByLabel('新密码', { exact: true }).fill('replacement-passphrase')
  await page.getByLabel('确认新密码', { exact: true }).fill('replacement-passphrase')
  expect(await page.locator('.auth-dialog').evaluate(el => el.scrollHeight > el.clientHeight)).toBe(true)
  await page.getByRole('button', { name: '重置密码', exact: true }).scrollIntoViewIfNeeded()
  await expect(page.getByRole('button', { name: '重置密码', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '重置密码', exact: true }).click()
  await expect(page).toHaveURL('/')
  await expect(page.getByRole('status')).toHaveText('密码已更新，请重新登录')
  expect(resetRequest).toEqual({ username: 'reader_test', email: 'reader@example.com', challengeId: 'reset-challenge', code: '654321', newPassword: 'replacement-passphrase' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('changing registration username or email clears its email challenge and code', async ({ page }) => {
  let registrations = 0
  await page.route('**/api/v1/auth/email-codes', route => route.fulfill({ status: 202, json: { challengeId: 'short-lived-challenge', requestState: 'ACCEPTED', expiresIn: 300, resendAfter: 60 } }))
  await page.route('**/api/v1/auth/register', route => { registrations++; return route.fulfill({ status: 201, json: {} }) })
  for (const changedField of ['邮箱地址', '用户名']) {
    await page.goto('/register')
    await page.getByLabel('用户名', { exact: true }).fill('reader_test')
    await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
    await page.getByRole('button', { name: '获取验证码', exact: true }).click()
    await page.getByLabel('邮箱验证码', { exact: true }).fill('123456')
    await page.getByLabel('密码', { exact: true }).fill('valid-passphrase')
    await page.getByLabel('确认密码', { exact: true }).fill('valid-passphrase')
    await page.getByLabel(changedField, { exact: true }).fill(changedField === '用户名' ? 'reader_changed' : 'changed@example.com')
    await expect(page.getByLabel('邮箱验证码', { exact: true })).toHaveValue('')
    await expect(page.locator('#register-code-error')).toHaveText('用户名或邮箱已更改，请重新获取验证码')
    await expect(page.locator('.auth-message[role="status"]')).toHaveCount(0)
    await page.getByRole('button', { name: '注册', exact: true }).click()
    expect(registrations).toBe(0)
  }
})

test('429 Retry-After controls the local resend cooldown', async ({ page }) => {
  let requests = 0
  await page.route('**/api/v1/auth/email-codes', async route => {
    requests++
    await route.fulfill({ status: 429, headers: { 'Retry-After': '3' }, json: { code: 'RATE_LIMITED' } })
  })
  await page.goto('/forgot-password')
  await page.getByLabel('用户名', { exact: true }).fill('reader_test')
  await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  const resend = page.getByRole('button', { name: /再次请求（\d+秒）/ })
  await expect(resend).toBeDisabled()
  await expect(page.getByRole('alert')).toHaveText('请求过于频繁，请稍后重试')
  expect(requests).toBe(1)
})

test('account email binding checks current password twice and shows the server verified address', async ({ page }, info) => {
  await mockIdentity(page)
  const user = { id: 'mock-identity', username: 'mock_learner', displayName: 'Mock 学习者', roles: ['USER'], email: null, emailVerified: false }
  await page.route('**/api/v1/auth/me', route => route.fulfill({ status: 200, json: user }))
  await page.route('**/api/v1/account/email-codes', async route => {
    expect(route.request().postDataJSON()).toEqual({ email: 'reader@example.com', currentPassword: 'current-passphrase' })
    await route.fulfill({ status: 202, json: { challengeId: 'email-challenge', requestState: 'ACCEPTED', expiresIn: 300, resendAfter: 60 } })
  })
  await page.route('**/api/v1/account/email', async route => {
    expect(route.request().postDataJSON()).toEqual({ email: 'reader@example.com', currentPassword: 'current-passphrase', challengeId: 'email-challenge', code: '777888' })
    await route.fulfill({ status: 200, json: { ...user, email: 'reader@example.com', emailVerified: true } })
  })
  await page.goto('/account')
  await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
  const bindingPassword = page.getByLabel('当前密码（验证邮箱）', { exact: true })
  const bindingToggle = page.locator('.account-email-form .password-toggle')
  const inputBox = await bindingPassword.boundingBox()
  const toggleBox = await bindingToggle.boundingBox()
  expect(inputBox && toggleBox).toBeTruthy()
  expect(Math.abs(inputBox!.y + inputBox!.height / 2 - toggleBox!.y - toggleBox!.height / 2)).toBeLessThanOrEqual(2)
  await bindingPassword.fill('current-passphrase')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  await page.getByLabel('邮箱验证码', { exact: true }).fill('777888')
  await page.getByRole('button', { name: '验证并绑定邮箱', exact: true }).click()
  await expect(page.getByText('reader@example.com', { exact: true })).toBeVisible()
  await expect(page.getByText('已验证', { exact: true })).toBeVisible()
  await expect(page.getByLabel('邮箱地址', { exact: true })).toHaveCount(0)
  if (info.project.name === 'mobile') expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

import { test, expect } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'
test.beforeEach(async ({ page }) => { await mockIdentity(page, true) })

test('auth pages share a compact brand header and only one mode switch', async ({ page }) => {
  for (const [path, heading, switchText] of [
    ['/login', '欢迎回来', '还没有账号？创建账号'],
    ['/register', '创建账号', '已有账号？去登录'],
  ]) {
    await page.goto(path!)
    await expect(page.getByRole('dialog')).toBeVisible(); await expect(page.locator('.auth-layout')).toHaveCount(0)
    await expect(page.getByRole('heading', { name: heading!, exact: true })).toBeVisible()
    await expect(page.locator('.auth-dialog-header')).toContainText('Lingua Loop')
    await expect(page.getByRole('banner').getByRole('link', { name: /登录|注册/ })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '打开导航' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: switchText!, exact: true })).toHaveCount(1)
    const switchLink = page.getByRole('button', { name: switchText!, exact: true })
    await switchLink.scrollIntoViewIfNeeded()
    await expect(switchLink).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollHeight >= document.documentElement.clientHeight)).toBe(true)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  }
  await page.getByLabel('用户名', { exact: true }).focus()
  await expect(page.getByLabel('用户名', { exact: true })).toBeFocused()
  expect(await page.getByLabel('用户名', { exact: true }).evaluate(el => getComputedStyle(el).boxShadow)).not.toBe('none')
})

test('server field errors identify and focus the relevant input', async ({ page }) => {
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { token: 'mock-csrf', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/v1/auth/email-codes', route => route.fulfill({ status: 202, json: { challengeId: 'register-challenge', requestState: 'ACCEPTED', expiresIn: 300, resendAfter: 60 } }))
  await page.route('**/api/v1/auth/register', route => route.fulfill({ status: 400, json: { code: 'VALIDATION_ERROR', message: '请检查填写内容', fieldErrors: { username: '用户名已被使用' } } }))
  await page.goto('/register')
  await page.getByLabel('用户名', { exact: true }).fill('duplicate_user')
  await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  await page.getByLabel('邮箱验证码', { exact: true }).fill('123456')
  await page.getByLabel('密码', { exact: true }).fill('fixture-password')
  await page.getByLabel('确认密码', { exact: true }).fill('fixture-password')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByLabel('用户名', { exact: true })).toHaveAttribute('aria-invalid', 'true')
  await expect(page.getByLabel('用户名', { exact: true })).toBeFocused()
  await expect(page.locator('#username-error')).toHaveText('用户名已被使用')
  await expect(page.locator('.auth-message.auth-error')).toHaveCount(0)
  await page.getByLabel('用户名', { exact: true }).fill('learner_example')
  await expect(page.getByLabel('用户名', { exact: true })).not.toHaveAttribute('aria-invalid', 'true')
  await expect(page.getByLabel('邮箱验证码', { exact: true })).toHaveValue('')
})

test('submission shows loading and prevents duplicate requests or mode changes', async ({ page }) => {
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { token: 'mock-csrf', headerName: 'X-CSRF-TOKEN' } }))
  let release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  let requests = 0
  await page.route('**/api/v1/auth/login', async route => { requests++; await gate; await route.fulfill({ status: 401, json: { code: 'BAD_CREDENTIALS', message: '用户名或密码不正确' } }) })
  await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill('learner_example')
  await page.getByLabel('密码', { exact: true }).fill('fixture-password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  try {
    await expect(page.getByRole('button', { name: '正在登录…', exact: true })).toBeDisabled()
    await expect(page.getByLabel('用户名', { exact: true })).toBeDisabled()
    await expect(page.getByRole('button', { name: '还没有账号？创建账号' })).toHaveCount(0)
  } finally { release() }
  await expect(page.getByRole('alert')).toHaveText('用户名或密码不正确')
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeEnabled()
  expect(requests).toBe(1)
})

test('small screens can reach the registration action without horizontal scrolling', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 668 })
  await page.goto('/register')
  await page.getByRole('button', { name: '注册', exact: true }).scrollIntoViewIfNeeded()
  await expect(page.getByRole('button', { name: '注册', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('password mismatch has one inline message and focuses confirmation', async ({ page }) => {
  let registrationRequests = 0
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { token: 'mock-csrf', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/v1/auth/email-codes', route => route.fulfill({ status: 202, json: { challengeId: 'register-challenge', requestState: 'ACCEPTED', expiresIn: 300, resendAfter: 60 } }))
  await page.route('**/api/v1/auth/register', route => { registrationRequests++; return route.fulfill({ status: 500 }) })
  await page.goto('/register')
  await page.getByLabel('用户名', { exact: true }).fill('learner_example')
  await page.getByLabel('邮箱地址', { exact: true }).fill('reader@example.com')
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  await page.getByLabel('邮箱验证码', { exact: true }).fill('123456')
  await page.getByLabel('密码', { exact: true }).fill('fixture-password')
  await page.getByLabel('确认密码', { exact: true }).fill('different-password')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.locator('#confirmation-error')).toHaveText('两次密码不一致')
  await expect(page.getByLabel('确认密码', { exact: true })).toBeFocused()
  await expect(page.getByLabel('确认密码', { exact: true })).toHaveAttribute('aria-invalid', 'true')
  await expect(page.locator('.auth-message.auth-error')).toHaveCount(0)
  expect(registrationRequests).toBe(0)
  await page.getByLabel('确认密码', { exact: true }).fill('fixture-password')
  await expect(page.locator('#confirmation-error')).toHaveCount(0)
})

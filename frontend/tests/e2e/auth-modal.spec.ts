import { test, expect, type Page } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'

async function guest(page: Page) { await mockIdentity(page, true) }
async function openLogin(page: Page) {
  await page.goto('/')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  // Element Plus re-focuses the first input in @opened. Wait for its enter
  // transition so that delayed focus cannot race with filling the password.
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.waitForFunction(() => !document.querySelector('.dialog-fade-enter-active, .dialog-fade-enter-from, .dialog-fade-enter-to'))
  return page.getByRole('dialog')
}
async function allowLogin(page: Page, admin = false) {
  const user = { id: 'modal-user', username: 'learner', displayName: '学习者', roles: admin ? ['USER', 'ADMIN'] : ['USER'] }
  await page.route('**/api/v1/auth/login', route => route.fulfill({ json: { user, accessToken: 'mock-modal', tokenType: 'Bearer', expiresIn: 600 } }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: user }))
  await page.route('**/api/v1/admin/identity', route => route.fulfill({ json: user }))
}
test.beforeEach(async ({ page }) => { await guest(page) })

test('legacy authentication links open a modal over home and preserve password-change notice', async ({ page }) => {
  for (const [path, heading] of [['/login?changed=1', '欢迎回来'], ['/register', '创建账号'], ['/forgot-password', '重置密码']]) {
    await page.goto(path!)
    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByRole('dialog').getByRole('heading', { name: heading!, exact: true })).toBeVisible()
    await expect(page.locator('.auth-brand-panel, .auth-layout')).toHaveCount(0)
    if (path!.includes('changed')) await expect(page.getByRole('dialog').getByRole('status')).toHaveText('密码已更新，请重新登录')
  }
})

test('guest home keeps full navigation without personal data requests or records', async ({ page }) => {
  let recommendations = 0
  page.on('request', request => { if (request.url().includes('/reading/recommendation')) recommendations++ })
  await page.goto('/')
  await expect(page).toHaveURL(/\/$/)
  await expect(page.locator('.daily-quote')).toBeVisible()
  await expect(page.locator('.home-records')).toHaveCount(0)
  await expect(page.locator('.learner-nav a')).toHaveCount(7)
  await expect(page.locator('.learner-nav a[href="/wall"]')).toHaveCount(1)
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
  expect(recommendations).toBe(0)
  await expect(page.getByRole('dialog')).toHaveCount(0)
})
test('dialog preserves registration draft and cooldown across modes and closing', async ({ page }, info) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/v1/auth/email-codes', route => route.fulfill({ status: 202, json: { challengeId: 'challenge', requestState: 'ACCEPTED', expiresIn: 300, resendAfter: 60 } }))
  const dialog = await openLogin(page)
  await dialog.getByRole('button', { name: '还没有账号？创建账号' }).click()
  await dialog.getByLabel('用户名', { exact: true }).fill('learner')
  await dialog.getByLabel('邮箱地址', { exact: true }).fill('sample@example.test')
  await dialog.getByRole('button', { name: '获取验证码', exact: true }).click()
  await expect(dialog.getByRole('button', { name: /再次请求/ })).toBeDisabled()
  await dialog.getByRole('button', { name: '已有账号？去登录' }).click()
  await dialog.getByRole('button', { name: '还没有账号？创建账号' }).click()
  await expect(dialog.getByLabel('邮箱地址', { exact: true })).toHaveValue('sample@example.test')
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeFocused()
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(dialog.getByLabel('邮箱地址', { exact: true })).toHaveValue('sample@example.test')
  await expect(dialog.getByRole('button', { name: /再次请求/ })).toBeDisabled()
  await dialog.locator('.auth-submit').scrollIntoViewIfNeeded()
  await expect(dialog.locator('.auth-submit')).toBeInViewport()
  const box = await page.locator('.auth-dialog').boundingBox()
  expect(box!.x).toBeGreaterThanOrEqual(0)
  expect(box!.y).toBeGreaterThanOrEqual(0)
  expect(box!.y + box!.height).toBeLessThanOrEqual(page.viewportSize()!.height)
  await page.locator('.auth-dialog').evaluate(element => { element.scrollTop = 0 })
  await page.screenshot({ path: `../.tool-cache/auth-modal-${info.project.name}.png` })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(errors).toEqual([])
})
test('protected deep link resumes after login and restores role boundaries', async ({ page }) => {
  await page.goto('/notebook')
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await expect(page).toHaveURL(/\/$/)
  await dialog.getByLabel('用户名', { exact: true }).fill('learner')
  await dialog.getByLabel('密码', { exact: true }).fill('test-only-password')
  await allowLogin(page)
  await dialog.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/notebook$/)
  await expect(dialog).not.toBeVisible()
})
test('login errors stay inside dialog; backdrop keeps input; reset stays in place', async ({ page }) => {
  await page.route('**/api/v1/auth/login', route => route.fulfill({ status: 401, json: { code: 'BAD_CREDENTIALS' } }))
  const dialog = await openLogin(page)
  await dialog.getByLabel('用户名', { exact: true }).fill('learner')
  await dialog.getByLabel('密码', { exact: true }).fill('test-only-password')
  await page.locator('.el-overlay').click({ position: { x: 1, y: 1 }, force: true })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '登录', exact: true }).click()
  await expect(dialog.getByRole('alert')).toHaveText('用户名或密码不正确')
  await dialog.getByRole('button', { name: '忘记密码？' }).click()
  await expect(dialog.getByRole('heading', { name: '重置密码' })).toBeVisible()
  await expect(page).toHaveURL(/\/$/)
})
test('administrator login still lands in data management', async ({ page }) => {
  const dialog = await openLogin(page)
  await dialog.getByLabel('用户名', { exact: true }).fill('administrator')
  await dialog.getByLabel('密码', { exact: true }).fill('test-only-password')
  await allowLogin(page, true)
  await dialog.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/admin$/)
  await expect(dialog).not.toBeVisible()
})

test('focus stays inside the dialog and a pending request cannot be dismissed or duplicated', async ({ page }) => {
  let release!: () => void, requests = 0
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/v1/auth/login', async route => { requests++; await gate; await route.fulfill({ status: 401, json: { code: 'BAD_CREDENTIALS' } }) })
  const dialog = await openLogin(page)
  await expect(dialog.getByLabel('用户名', { exact: true })).toBeFocused()
  await expect(page.locator('#app > div').first()).toHaveAttribute('inert', '')
  const last = dialog.getByRole('button', { name: '还没有账号？创建账号' })
  await last.focus(); await page.keyboard.press('Tab')
  await expect.poll(() => page.evaluate(() => Boolean(document.activeElement?.closest('[role=dialog]')))).toBe(true)
  const close = dialog.getByRole('button', { name: '关闭认证窗口' })
  await close.focus(); await page.keyboard.press('Shift+Tab')
  await expect(last).toBeFocused()
  await dialog.getByLabel('用户名', { exact: true }).fill('learner')
  await dialog.getByLabel('密码', { exact: true }).fill('test-only-password')
  await dialog.getByRole('button', { name: '登录', exact: true }).click()
  try {
    await expect(dialog.getByRole('button', { name: '关闭认证窗口' })).toBeDisabled()
    await page.keyboard.press('Escape'); await expect(dialog).toBeVisible()
    await expect.poll(() => requests).toBe(1)
  } finally { release() }
  await expect(dialog.getByRole('alert')).toHaveText('用户名或密码不正确')
})

test('expired identity leaves the protected view before showing the login modal', async ({ page }) => {
  await mockIdentity(page)
  await page.goto('/notebook')
  await expect(page.getByRole('heading', { name: '背单词', exact: true })).toBeVisible()
  await page.route('**/api/v1/auth/me', route => route.fulfill({ status: 401, json: { code: 'UNAUTHENTICATED' } }))
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ status: 401, json: { code: 'UNAUTHENTICATED' } }))
  await page.goto('/stats')
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page.locator('main .notebook-page')).toHaveCount(0)
})

test('modal registration and password reset return to login without changing the route', async ({ page }) => {
  await page.route('**/api/v1/auth/email-codes', route => route.fulfill({ status: 202, json: { challengeId: 'challenge', requestState: 'ACCEPTED', expiresIn: 300, resendAfter: 1 } }))
  await page.route('**/api/v1/auth/register', route => route.fulfill({ status: 201, json: { id: 'registered', username: 'learner', displayName: 'learner', roles: ['USER'] } }))
  await page.route('**/api/v1/auth/password-reset', route => route.fulfill({ status: 204 }))
  const dialog = await openLogin(page)
  await dialog.getByRole('button', { name: '还没有账号？创建账号' }).click()
  async function fillVerification() {
    await dialog.getByLabel('用户名', { exact: true }).fill('learner')
    await dialog.getByLabel('邮箱地址', { exact: true }).fill('sample@example.test')
    await dialog.getByRole('button', { name: '获取验证码', exact: true }).click()
    await dialog.getByLabel('邮箱验证码', { exact: true }).fill('123456')
  }
  await fillVerification()
  await dialog.getByLabel('密码', { exact: true }).fill('test-only-password')
  await dialog.getByLabel('确认密码', { exact: true }).fill('test-only-password')
  await dialog.getByRole('button', { name: '注册', exact: true }).click()
  await expect(dialog.getByRole('status')).toHaveText('注册成功，请登录')
  await dialog.getByRole('button', { name: '忘记密码？' }).click()
  await fillVerification()
  await dialog.getByLabel('新密码', { exact: true }).fill('test-only-password')
  await dialog.getByLabel('确认新密码', { exact: true }).fill('test-only-password')
  await dialog.getByRole('button', { name: '重置密码', exact: true }).click()
  await expect(dialog.getByRole('status')).toHaveText('密码已更新，请重新登录')
  await expect(page).toHaveURL(/\/$/)
})

test('login always requests seven days without displaying a remember option',async({page})=>{
 const dialog=await openLogin(page)
 await expect(dialog.getByRole('checkbox',{name:'保持登录7天',exact:true})).toHaveCount(0)
 const flags:boolean[]=[]
 await page.route('**/api/v1/auth/login',r=>{flags.push(r.request().postDataJSON().rememberMe);return r.fulfill({status:401,json:{code:'BAD_CREDENTIALS'}})})
 await dialog.getByLabel('用户名',{exact:true}).fill('learner');await dialog.getByLabel('密码',{exact:true}).fill('test-only-password')
 await dialog.getByRole('button',{name:'登录',exact:true}).click();await expect.poll(()=>flags.length).toBe(1);expect(flags[0]).toBe(true)
 await expect(dialog.getByText('公共设备请取消勾选')).toHaveCount(0)
})

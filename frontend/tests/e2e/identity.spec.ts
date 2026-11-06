import { test, expect } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { appendFileSync } from 'node:fs'
test.use({ trace: 'off', screenshot: 'off', video: 'off' })
test('real registration login refresh account password and logout', async ({ page }, info) => {
  test.skip(process.env.E2E_REAL_BACKEND !== '1', 'Requires dedicated test database and running backend')
  test.skip(!process.env.E2E_MAILBOX_URL, 'Requires the local SMTP test mailbox for real email verification')
  const username = 'e2e_' + randomUUID().replaceAll('-', '').slice(0, 16)
  const password = randomUUID() + 'aA!'
  const replacement = randomUUID() + 'zZ!'
  const email = `${username}@example.test`
  appendFileSync('../.tool-cache/phase1-browser-fixtures.txt', username + '\n')
  await page.goto('/')
  await expect(page).toHaveURL(/\/$/)
  await page.goto('/login')
  await page.getByRole('button', { name: '还没有账号？创建账号' }).click()
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('邮箱地址', { exact: true }).fill(email)
  await page.getByRole('button', { name: '获取验证码', exact: true }).click()
  let code: string | null = null
  await expect.poll(async () => {
    const response = await page.request.get(`${process.env.E2E_MAILBOX_URL!.replace(/\/$/, '')}/code?email=${encodeURIComponent(email)}`)
    if (!response.ok()) return null
    const result = await response.json() as { code?: unknown }
    code = typeof result.code === 'string' ? result.code : null
    return code
  }, { timeout: 15_000, message: 'The local SMTP test mailbox should receive the registration code' }).toMatch(/.+/)
  await page.getByLabel('邮箱验证码', { exact: true }).fill(code!)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('确认密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText('注册成功，请登录')
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Lingua Loop', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Lingua Loop', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '用户菜单', exact: true }).click()
    await page.getByRole('link', { name: '账户设置', exact: true }).click()
  await expect(page.getByText(email, { exact: true })).toBeVisible()
  await expect(page.getByText('已验证', { exact: true })).toBeVisible()
  await expect(page.getByLabel('邮箱地址', { exact: true })).toHaveCount(0)
  await page.getByLabel('昵称', { exact: true }).fill('认真学习')
  await page.getByRole('button', { name: '保存昵称' }).click()
  await expect(page.getByRole('status')).toHaveText('昵称已保存')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByLabel('当前密码', { exact: true }).fill(password)
  await page.getByLabel('新密码', { exact: true }).fill(replacement)
  await page.getByLabel('确认新密码', { exact: true }).fill(replacement)
  await page.getByRole('button', { name: '更新密码' }).click()
  await expect(page.getByRole('status').filter({ hasText: '密码已更新，请重新登录' })).toHaveText('密码已更新，请重新登录')
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill(replacement)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Lingua Loop', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '用户菜单', exact: true }).click()
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
  await page.goto('/account'); await expect(page.getByRole('dialog')).toBeVisible()
})

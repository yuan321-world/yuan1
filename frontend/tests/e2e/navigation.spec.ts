import { test, expect } from '@playwright/test'

const entries = [
  ['首页', '/', '把英语学习连成一个循环。'],
  ['背单词', '/vocabulary', '背单词'],
  ['外刊精读', '/reading', '外刊精读'],
  ['专项练习', '/practice', '专项练习'],
  ['学习统计', '/stats', '学习统计'],
  ['登录', '/login', '登录与账号'],
] as const

test('all learner entries and the admin preview are reachable', async ({ page }, info) => {
  await page.goto('/')
  for (const [label, path, heading] of entries) {
    if (info.project.name === 'mobile') await page.getByRole('button', { name: '打开导航' }).click()
    await page.getByRole('navigation', { name: '学习导航' }).getByRole('link', { name: label, exact: true }).click()
    await expect(page).toHaveURL(new RegExp(path === '/' ? '/$' : path + '$'))
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  }
  if (info.project.name === 'mobile') await page.getByRole('button', { name: '打开导航' }).click()
  await page.getByRole('navigation', { name: '学习导航' }).getByRole('link', { name: '管理端预览' }).click()
  await expect(page.getByRole('heading', { name: '管理端布局预览' })).toBeVisible()
  await page.getByRole('link', { name: '返回学习端' }).click()
  await expect(page.getByRole('heading', { name: '把英语学习连成一个循环。' })).toBeVisible()
})

test('direct route and refresh work; unknown addresses show 404', async ({ page }) => {
  await page.goto('/reading')
  await expect(page.getByRole('heading', { name: '外刊精读', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '外刊精读', exact: true })).toBeVisible()
  await page.goto('/this-page-does-not-exist')
  await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible()
  await page.getByRole('link', { name: '返回首页' }).click()
  await expect(page).toHaveURL(/\/$/)
})

test('keyboard can reach navigation; Escape restores mobile menu focus', async ({ page }, info) => {
  await page.goto('/')
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: '跳到主要内容' })).toBeFocused()
  if (info.project.name === 'mobile') {
    const toggle = page.getByRole('button', { name: '打开导航' })
    await toggle.focus()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('navigation', { name: '学习导航' })).toBeVisible()
    await page.keyboard.press('Tab')
    await expect(page.getByRole('navigation', { name: '学习导航' }).getByRole('link', { name: '首页', exact: true })).toBeFocused()
    await page.keyboard.press('Escape')
    await expect(toggle).toBeFocused()
    await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  } else {
    await page.keyboard.press('Tab')
    await page.keyboard.press('Tab')
    await expect(page.getByRole('navigation', { name: '学习导航' }).getByRole('link', { name: '首页', exact: true })).toBeFocused()
  }
})

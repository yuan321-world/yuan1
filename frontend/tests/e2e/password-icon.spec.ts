import { expect, test } from '@playwright/test'
import { mockIdentity } from './identity-fixtures'

test('password icons stay aligned and toggle independently without changing field geometry', async ({ page }, info) => {
  if (info.project.name === 'mobile') await page.setViewportSize({ width: 320, height: 568 })
  await mockIdentity(page, true)
  for (const path of ['/login', '/register']) {
    await page.goto(path)
    const controls = page.locator('.password-control')
    await expect(controls).toHaveCount(path === '/login' ? 1 : 2)
    for (const control of await controls.all()) {
      const input = control.locator('input')
      const toggle = control.getByRole('button', { name: '显示密码', exact: true })
      await input.fill('sample-passphrase')
      await expect(input).toHaveAttribute('type', 'password')
      await expect(toggle).toHaveAttribute('aria-pressed', 'false')
      await expect(toggle.locator('svg')).toBeVisible()
      await expect(toggle).toHaveText('')
      await toggle.scrollIntoViewIfNeeded()
      const before = await input.boundingBox(), button = await toggle.boundingBox()
      expect(before && button).toBeTruthy()
      expect(button!.width).toBeGreaterThanOrEqual(44)
      expect(button!.height).toBeGreaterThanOrEqual(44)
      expect(Math.abs(button!.y + button!.height / 2 - before!.y - before!.height / 2)).toBeLessThan(1)
      expect(button!.x + button!.width).toBeLessThanOrEqual(before!.x + before!.width)
      await toggle.focus()
      await expect(toggle).toBeFocused()
      await toggle.press('Enter')
      await expect(input).toHaveAttribute('type', 'text')
      await expect(control.getByRole('button', { name: '隐藏密码', exact: true })).toHaveAttribute('aria-pressed', 'true')
      const after = await input.boundingBox()
      expect(after!.width).toBeCloseTo(before!.width, 1)
      expect(after!.height).toBeCloseTo(before!.height, 1)
      await control.getByRole('button', { name: '隐藏密码', exact: true }).click()
      await expect(input).toHaveAttribute('type', 'password')
      await expect(input).toHaveValue('sample-passphrase')
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  }
})

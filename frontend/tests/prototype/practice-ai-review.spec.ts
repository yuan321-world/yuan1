import { test, expect } from '@playwright/test'
import { pathToFileURL } from 'node:url'
import { resolve } from 'node:path'

const url = pathToFileURL(resolve(process.cwd(), '../docs/prototypes/practice-ai-review-preview.html')).href
test.beforeEach(async ({ page }) => { await page.goto(url) })

test('submitted writing freezes and feedback retains the corresponding original across revisions', async ({ page }) => {
  const original = await page.locator('#answer').inputValue()
  await expect(page.locator('#review-button')).toBeDisabled()
  await page.locator('#submit').click()
  await expect(page.locator('#answer')).toHaveJSProperty('readOnly', true)
  await page.locator('#review-button').click()
  await expect(page.locator('#review-button')).toHaveText('本轮已批改')
  await expect(page.locator('.correction')).toHaveCount(3)
  await expect(page.locator('.correction .muted').last()).toContainText('optional')
  await page.getByRole('tab', { name: '当次原文' }).click()
  await expect(page.locator('#review-panel')).toHaveText(original)
  await page.locator('#revise').click()
  await page.locator('#answer').fill('A new draft written by the learner.')
  await expect(page.locator('#review-panel')).toHaveText(original)
  await expect(page.locator('.task-note').first()).toContainText('历史提交')
  await page.locator('#submit').click()
  await page.locator('#review-button').click()
  await expect(page.locator('#feedback')).toContainText('预置反馈无法评价这份新文本')
  await expect(page.locator('#answer')).toHaveValue('A new draft written by the learner.')
})

test('translation failure retries, keeps its own feedback, and switches to a stable paper layout', async ({ page }) => {
  await page.locator('#translation').click()
  await page.locator('#context').selectOption('paper')
  await expect(page.locator('#progress')).toBeVisible()
  await page.locator('#submit').click()
  await page.locator('#fail').check()
  await page.locator('#review-button').click()
  await expect(page.getByRole('alert')).toContainText('作答已保留')
  await page.locator('#review-button').click()
  await expect(page.locator('.correction')).toHaveCount(3)
  await expect(page.locator('.score strong')).toHaveText('10')
  await page.getByRole('tab', { name: '参考修改稿' }).click()
  await expect(page.locator('#review-panel')).toContainText('have begun')
  await page.locator('#jump-writing').click()
  await expect(page.locator('#review-button')).toBeDisabled()
  await page.locator('#jump-translation').click()
  await expect(page.locator('.score strong')).toHaveText('10')
  await expect(page.locator('#t-status')).toHaveText('已提交')
  await expect(page.locator('#w-status')).toHaveText('未提交')
})

test('empty input is blocked, restore is explicit, keyboard tabs work, and demo does not call services', async ({ page }) => {
  const requests: string[] = []
  page.on('request', req => { if (/^https?:/.test(req.url())) requests.push(req.url()) })
  await page.locator('#answer').fill('')
  await expect(page.locator('#submit')).toBeDisabled()
  page.once('dialog', dialog => dialog.dismiss())
  await page.locator('#restore').click()
  await expect(page.locator('#answer')).toHaveValue('')
  page.once('dialog', dialog => dialog.accept())
  await page.locator('#restore').click()
  await expect(page.locator('#submit')).toBeEnabled()
  await page.locator('#submit').click()
  await page.locator('#review-button').click()
  await expect(page.locator('.correction')).toHaveCount(3)
  await page.locator('#tab-notes').focus()
  await page.keyboard.press('ArrowRight')
  await expect(page.locator('#tab-reference')).toBeFocused()
  await expect(page.locator('#review-panel')).toContainText('不会覆盖你的原文')
  expect(requests).toEqual([])
})

test('both contexts and subjects fit the viewport and completed feedback is visually inspectable', async ({ page }, testInfo) => {
  for (const context of ['single', 'paper']) {
    await page.locator('#context').selectOption(context)
    for (const mode of ['writing', 'translation']) {
      await page.locator('#' + mode).click()
      if (await page.locator('#submit').isVisible()) await page.locator('#submit').click()
      if (await page.locator('#review-button').isEnabled()) await page.locator('#review-button').click()
      await expect(page.locator('.correction')).toHaveCount(3)
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
      await page.screenshot({ path: resolve(process.cwd(), `../.tool-cache/20261025-ai-review/${testInfo.project.name}-${context}-${mode}.png`), fullPage: true })
    }
  }
})

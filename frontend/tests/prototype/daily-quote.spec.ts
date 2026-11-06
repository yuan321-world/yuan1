import { test, expect, type Page } from '@playwright/test'
import { pathToFileURL } from 'node:url'
import { resolve } from 'node:path'

const url = pathToFileURL(resolve(process.cwd(), '../docs/prototypes/lingua-loop-preview.html')).href
const initialTime = new Date('2026-10-13T08:00:00Z')
async function login(page: Page) {
  await page.getByRole('button', { name: '使用学习账号' }).click()
  await page.locator('#form-login button[type=submit]').click()
}
test.beforeEach(async ({ page }) => {
  await page.clock.install({ time: initialTime })
})

test('daily metadata is stable on refresh and rotates at Shanghai midnight', async ({ page }) => {
  await page.goto(url); await login(page)
  await expect(page.locator('#dailyQuoteDate')).toHaveAttribute('datetime', '2026-10-13')
  const english = await page.locator('#dailyQuoteText').innerText()
  await expect(page.locator('#dailyQuoteAuthor')).not.toBeEmpty()
  await expect(page.locator('#dailyQuoteSource')).toHaveAttribute('href', /^https:\/\/www\.gutenberg\.org\//)
  await page.reload(); await login(page)
  await expect(page.locator('#dailyQuoteText')).toHaveText(english)
  await page.clock.setSystemTime(new Date('2026-10-13T15:59:00Z'))
  await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')))
  await expect(page.locator('#dailyQuoteDate')).toHaveAttribute('datetime', '2026-10-13')
  await page.clock.setSystemTime(new Date('2026-10-13T16:01:00Z'))
  await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')))
  await expect(page.locator('#dailyQuoteDate')).toHaveAttribute('datetime', '2026-10-14')
  await expect(page.locator('#dailyQuoteText')).not.toHaveText(english)
})

test('translation toggle preserves preference without moving following modules', async ({ page }) => {
  await page.goto(url); await login(page)
  const toggle = page.locator('[data-quote-action=translation]')
  await expect(toggle).toHaveAttribute('aria-pressed', 'true')
  await page.locator('#view-today').evaluate(async element => { await Promise.all(element.getAnimations().map(animation => animation.finished)) })
  const before = await page.locator('#view-today .hero-ctas').evaluate(element => element.getBoundingClientRect().top + window.scrollY)
  await toggle.click()
  await expect(page.locator('#dailyQuoteChinese')).toBeHidden()
  await expect(toggle).toHaveAttribute('aria-pressed', 'false')
  const after = await page.locator('#view-today .hero-ctas').evaluate(element => element.getBoundingClientRect().top + window.scrollY)
  expect(after).toBe(before)
  await page.reload(); await login(page)
  await expect(page.locator('#dailyQuoteChinese')).toBeHidden()
  await toggle.click()
  await expect(page.locator('#dailyQuoteChinese')).toBeVisible()
})

test('favorite persists full metadata and can be removed', async ({ page }) => {
  await page.goto(url); await login(page)
  const button = page.locator('[data-quote-action=favorite]')
  await button.click()
  await expect(button).toHaveAttribute('aria-pressed', 'true')
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('lingua-loop.quotes.favorites.v1:kevin') ?? '[]'))
  expect(saved).toHaveLength(1)
  expect(saved[0]).toMatchObject({ english: expect.any(String), chinese: expect.any(String), author: expect.any(String), source: { title: expect.any(String), url: expect.any(String) } })
  await page.reload(); await login(page)
  await expect(button).toHaveAttribute('aria-pressed', 'true')
  await button.click()
  await expect(button).toHaveAttribute('aria-pressed', 'false')
  await page.reload(); await login(page)
  await expect(button).toHaveAttribute('aria-pressed', 'false')
})

test('copy includes original translation and attribution', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: {
      writeText: async (text: string) => { (window as any).__copiedQuote = text },
    } })
  })
  await page.goto(url); await login(page)
  const english = await page.locator('#dailyQuoteText').innerText()
  await page.locator('[data-quote-action=copy]').click()
  await expect(page.locator('#dailyQuoteFeedback')).toContainText('已复制')
  const copied = await page.evaluate(() => (window as any).__copiedQuote)
  expect(copied).toContain(english)
  expect(copied).toContain(await page.locator('#dailyQuoteChinese').innerText())
  expect(copied).toContain(await page.locator('#dailyQuoteAuthor').innerText())
})

test('clipboard failure offers a manual copy dialog', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: {
      writeText: async () => { throw new DOMException('Not allowed', 'NotAllowedError') },
    } })
    document.execCommand = () => false
  })
  await page.goto(url); await login(page)
  await page.locator('[data-quote-action=copy]').click()
  await expect(page.locator('#dailyQuoteCopyDialog')).toBeVisible()
  await expect(page.locator('#dailyQuoteCopyText')).not.toHaveValue('')
  await page.getByRole('button', { name: '关闭复制窗口' }).click()
  await expect(page.locator('#dailyQuoteCopyDialog')).toBeHidden()
})

test('speech reads English on demand and stops on navigation', async ({ page }) => {
  await page.addInitScript(() => {
    class Utterance { text: string; lang = ''; onend: (() => void) | null = null; onerror: ((event: any) => void) | null = null; constructor(text: string) { this.text = text } }
    Object.defineProperty(window, 'SpeechSynthesisUtterance', { configurable: true, value: Utterance })
    Object.defineProperty(window, 'speechSynthesis', { configurable: true, value: {
      getVoices: () => [{ lang: 'en-US' }],
      speak: (utterance: Utterance) => { (window as any).__spoken = { text: utterance.text, lang: utterance.lang } },
      cancel: () => { (window as any).__cancelled = true },
    } })
  })
  await page.goto(url); await login(page)
  await page.locator('[data-quote-action=speak]').click()
  await expect(page.locator('[data-quote-action=speak]')).toHaveAttribute('aria-pressed', 'true')
  const spoken = await page.evaluate(() => (window as any).__spoken)
  expect(spoken.lang).toBe('en-US')
  expect(spoken.text).toBe(await page.locator('#dailyQuoteText').innerText())
  await page.locator('#view-today .hero-ctas [data-goto=reading]').click()
  await expect(page.locator('[data-quote-action=speak]')).toHaveAttribute('aria-pressed', 'false')
  expect(await page.evaluate(() => (window as any).__cancelled)).toBe(true)
})

test('unsupported speech and blocked storage remain usable', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(window, 'speechSynthesis', { configurable: true, value: undefined })
    Storage.prototype.getItem = () => { throw new DOMException('Blocked', 'SecurityError') }
    Storage.prototype.setItem = () => { throw new DOMException('Blocked', 'SecurityError') }
  })
  const errors: string[] = []
  page.on('pageerror', e => errors.push(e.message))
  await page.goto(url); await login(page)
  await expect(page.locator('[data-quote-action=speak]')).toBeDisabled()
  await page.locator('[data-quote-action=favorite]').click()
  await expect(page.locator('#dailyQuoteFeedback')).toContainText('本次')
  await expect(page.locator('[data-quote-action=favorite]')).toHaveAttribute('aria-pressed', 'true')
  expect(errors).toEqual([])
})

test('selecting a quote word opens local translation and can be closed', async ({ page }) => {
  await page.goto(url); await login(page)
  const selected = await page.locator('#dailyQuoteText').evaluate(element => {
    const node = element.firstChild!
    const text = node.textContent ?? ''
    const match = /\b(?:know|self|good|brevity|moment|mistake)\b/i.exec(text)!
    const range = document.createRange()
    range.setStart(node, match.index); range.setEnd(node, match.index + match[0].length)
    const selection = window.getSelection()!
    selection.removeAllRanges(); selection.addRange(range)
    element.dispatchEvent(new Event('pointerup', { bubbles: true }))
    return match[0]
  })
  await expect(page.locator('#dailyQuoteSelection')).toBeVisible()
  await expect(page.locator('#dailyQuoteSelectedWord')).toHaveText(selected)
  await expect(page.locator('#dailyQuoteDefinition')).toContainText(/[\u4e00-\u9fff]/)
  await page.getByRole('button', { name: '关闭名言释义' }).click()
  await expect(page.locator('#dailyQuoteSelection')).toBeHidden()
})

test('layout retains inert modules and supports reduced motion', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto(url); await login(page)
  await expect(page.locator('#dailyQuoteText')).toBeVisible()
  expect(await page.locator('#dailyQuoteText').evaluate(e => getComputedStyle(e).fontFamily)).toContain('Georgia')
  expect(await page.locator('#dailyQuoteText').evaluate(e => parseFloat(getComputedStyle(e).fontSize))).toBeGreaterThanOrEqual(27)
  await expect(page.locator('#view-today .tile')).toHaveCount(6)
  await expect(page.locator('#view-today .pick-card')).toHaveCount(0)
  for (const tile of await page.locator('#view-today .tile').all()) {
    await tile.click()
    await expect(page.locator('#view-today')).toBeVisible()
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(await page.locator('.hero-quote').evaluate(e => getComputedStyle(e).animationName)).toBe('none')
})

test('late clipboard rejection cannot open a dialog after leaving home', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: {
      writeText: () => new Promise((_, reject) => { (window as any).__rejectCopy = reject }),
    } })
    document.execCommand = () => false
  })
  await page.goto(url); await login(page)
  await page.locator('[data-quote-action=copy]').click()
  await page.locator('#view-today .hero-ctas [data-goto=reading]').click()
  await page.evaluate(async () => {
    (window as any).__rejectCopy(new Error('Denied'))
    await new Promise(resolve => setTimeout(resolve, 100))
  })
  await expect(page.locator('#view-reading')).toBeVisible()
  await expect(page.locator('#dailyQuoteCopyDialog')).toBeHidden()
})

test('date rollback invalidates a pending provider response', async ({ page }) => {
  await page.addInitScript(() => {
    (window as any).LinguaLoopQuoteProvider = {
      getDailyQuote: (date: string) => date === '2026-10-14'
        ? new Promise(resolve => { (window as any).__resolveQuote = resolve })
        : Promise.resolve((window as any).LinguaLoopDailyQuotes.selectLocalDailyQuote(date)),
    }
  })
  await page.goto(url); await login(page)
  const original = await page.locator('#dailyQuoteText').innerText()
  await page.clock.setSystemTime(new Date('2026-10-13T16:01:00Z'))
  await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')))
  await page.waitForFunction(() => typeof (window as any).__resolveQuote === 'function')
  await page.clock.setSystemTime(initialTime)
  await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')))
  await page.evaluate(async () => {
    (window as any).__resolveQuote((window as any).LinguaLoopDailyQuotes.selectLocalDailyQuote('2026-10-14'))
    await new Promise(resolve => setTimeout(resolve, 100))
  })
  await expect(page.locator('#dailyQuoteDate')).toHaveAttribute('datetime', '2026-10-13')
  await expect(page.locator('#dailyQuoteText')).toHaveText(original)
})

test('corrupt saved data is ignored and favorites stay scoped to the demo account', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('lingua-loop.quotes.favorites.v1:kevin', '{broken')
    localStorage.setItem('lingua-loop.quotes.translation.v1', '{broken')
  })
  await page.goto(url); await login(page)
  await expect(page.locator('#dailyQuoteChinese')).toBeVisible()
  await expect(page.locator('[data-quote-action=favorite]')).toHaveAttribute('aria-pressed', 'false')
  await page.locator('[data-quote-action=favorite]').click()
  await page.locator('#lg-user').evaluate((e: HTMLInputElement) => { e.value = 'alice' })
  await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')))
  await expect(page.locator('[data-quote-action=favorite]')).toHaveAttribute('aria-pressed', 'false')
  await page.locator('[data-quote-action=favorite]').click()
  const saved = await page.evaluate(() => ['kevin','alice'].map(account => JSON.parse(localStorage.getItem('lingua-loop.quotes.favorites.v1:' + account) ?? '[]').length))
  expect(saved).toEqual([1,1])
})
test('selection outside the quote is ignored and unknown phrases are explicit', async ({ page }) => {
  await page.goto(url); await login(page)
  await page.locator('#dailyQuoteText').evaluate(element => {
    const range = document.createRange()
    range.setStart(element.firstChild!, 0); range.setEnd(element.firstChild!, 12)
    const selection = getSelection()!
    selection.removeAllRanges(); selection.addRange(range)
    element.dispatchEvent(new Event('pointerup', { bubbles: true }))
  })
  await expect(page.locator('#dailyQuoteDefinition')).toContainText('暂未收录')
  await page.locator('#view-today h1').evaluate(element => {
    const range = document.createRange(); range.selectNodeContents(element)
    const selection = getSelection()!
    selection.removeAllRanges(); selection.addRange(range)
    document.dispatchEvent(new Event('selectionchange'))
  })
  await expect(page.locator('#dailyQuoteSelection')).toBeHidden()
})

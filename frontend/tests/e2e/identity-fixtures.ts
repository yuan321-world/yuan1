import { expect, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { appendFileSync } from 'node:fs'
export async function mockIdentity(page: Page, anonymous = false, admin = false) {
  const user = { id: 'mock-identity', username: 'mock_learner', displayName: 'Mock 学习者', roles: admin ? ['USER', 'ADMIN'] : ['USER'] }
  await page.route('**/api/v1/auth/me', route => route.fulfill(anonymous ? { status: 401, json: { code: 'UNAUTHENTICATED' } } : { status: 200, json: user }))
  await page.route('**/api/v1/admin/identity', route => route.fulfill({ status: 200, json: user }))
}
export async function realAccount(page: Page) {
  const username = 'e2e_' + randomUUID().replaceAll('-', '').slice(0, 16)
  const password = randomUUID() + 'aA!'
  appendFileSync('../.tool-cache/phase1-browser-fixtures.txt', username + '\n')
  const token = async () => (await (await page.request.get('/api/v1/auth/csrf')).json()).token as string
  expect((await page.request.post('/api/v1/auth/register', { headers: { 'X-CSRF-TOKEN': await token() }, data: { username, password } })).status()).toBe(201)
  expect((await page.request.post('/api/v1/auth/login', { headers: { 'X-CSRF-TOKEN': await token() }, data: { username, password, rememberMe: false } })).status()).toBe(200)
  return { username, password }
}

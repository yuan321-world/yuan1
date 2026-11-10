import { expect, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { appendFileSync } from 'node:fs'
export async function mockIdentity(page: Page, anonymous = false, admin = false) {
  const vocabularyEmpty={today:'2026-10-19',weekStart:'2026-10-19',days:Array.from({length:7},(_,i)=>({date:`2026-10-${19+i}`,newLearned:0,reviewed:0})),totalLearned:0,totalReviewPasses:0,due:0,paused:0,completedRounds:0,nextDueAt:null}
  await page.route('**/api/v1/vocabulary/learning/stats',r=>r.fulfill({json:vocabularyEmpty}))
  await page.route('**/api/v1/vocabulary/learning/overview',r=>r.fulfill({json:{candidates:0,unlearned:0,due:0,nextDueAt:null,newToday:0,reviewToday:0,settings:{newGoal:20,reviewGoal:50},sessionId:null,source:{kind:'PERSONAL',bookId:null,title:'我的阅读生词',available:true,reason:''}}}))
  await page.route('**/api/v1/vocabulary/learning/session',r=>r.fulfill({json:null}))
  await page.route('**/api/v1/vocabulary/learning/wordbooks',r=>r.fulfill({json:[]}))
  await page.route('**/api/v1/vocabulary/learning/candidates?*',r=>r.fulfill({json:{items:[],page:1,pageSize:20,total:0}}))
  await page.route('**/api/v1/vocabulary/learning/words?*',r=>r.fulfill({json:{items:[],page:1,pageSize:20,total:0}}))
  await page.route('**/api/v1/reading/articles/*/versions/*/checkin',r=>r.fulfill({json:{readCompleted:false,practiceCompleted:false,hasQuiz:false,articleQualified:false,qualifiedDate:null,todayChecked:false}}))
  await page.route('**/api/v1/reading/articles/*/versions/*/practice',route=>route.fulfill({json:{published:null,current:null,first:null,latest:null}}))
  await page.route('**/api/v1/reading/articles/*/versions/*/practice/history?*',route=>route.fulfill({json:{items:[],page:1,pageSize:6,total:0}}))
  await page.route('**/api/v1/reading/articles/*/versions/*/quiz',route=>route.fulfill({json:null}))
  await page.route('**/api/v1/reading/articles/*/versions/*/writing-materials/states',route=>route.fulfill({json:{}}))
  await page.route('**/api/v1/reading/articles/*/versions/*/writing-materials',route=>route.fulfill({json:[]}))
  await page.route('**/api/v1/reading/articles/*/versions/*/full-translation', r => r.fulfill({json:{status:'MISSING',completed:0,total:0,configured:false,error:null,translation:{available:false,source:'',paragraphs:[],sentences:[]}}}))
  await page.route('**/api/v1/reading/articles/*/versions/*/tools', route => route.fulfill({ json: { related: [], previous: null, next: null, keywords: [], translation: { available: false, source: '', paragraphs: [] } } }))
  await page.route('**/api/v1/reading/articles/*/versions/*/words?*', route => route.fulfill({ json: { items: [], page: 1, pageSize: 10, total: 0 } }))
  await page.route('**/api/v1/reading/favorites/states?*', route => route.fulfill({ json: [] }))
  await page.route('**/api/v1/reading/favorites?*', route => route.fulfill({ json: { items: [], page: 1, pageSize: 6, total: 0 } }))
  // Automatic practice scoring must never escape Mock tests into a real paid provider.
  await page.route('**/api/v1/practice/subjective-reviews**',r=>r.request().method()==='POST'?r.fulfill({status:503,json:{code:'PRACTICE_AI_UNAVAILABLE',message:'Mock scoring is disabled'}}):r.fulfill({json:[]}))
  // Keep ordinary Mock navigation isolated from the real reading backend.
  await page.route('**/api/v1/reading/recommendation', route => route.fulfill({ json: { date: '2026-10-16', article: null } }))
  await page.route('**/api/v1/reading/categories', route => route.fulfill({ json: [] }))
  await page.route('**/api/v1/reading/articles?*', route => route.fulfill({ json: { items: [], page: 1, pageSize: 20, total: 0 } }))
  await page.route('**/api/v1/reading/history?*', route => route.fulfill({ json: { items: [], page: 1, pageSize: 10, total: 0 } }))
  await page.route('**/api/v1/vocabulary/words?*', route => route.fulfill({ json: { items: [], page: 1, pageSize: 20, total: 0 } }))
  const user = { id: 'mock-identity', username: 'mock_learner', displayName: 'Mock 学习者', roles: admin ? ['USER', 'ADMIN'] : ['USER'] }
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { token: 'mock-csrf', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/v1/auth/refresh', route => route.fulfill(anonymous ? { status: 401, json: { code: 'UNAUTHENTICATED' } } : { json: { user, accessToken: 'mock-access', tokenType: 'Bearer', expiresIn: 600 } }))
  await page.route('**/api/v1/auth/me', route => route.fulfill(anonymous ? { status: 401, json: { code: 'UNAUTHENTICATED' } } : { status: 200, json: user }))
  await page.route('**/api/v1/admin/identity', route => route.fulfill({ status: 200, json: user }))
}
export async function realAccount(page: Page) {
  await page.goto('/login')
  const username = 'e2e_' + randomUUID().replaceAll('-', '').slice(0, 16)
  const password = randomUUID() + 'aA!'
  const email = `${username}@example.test`
  appendFileSync('../.tool-cache/phase1-browser-fixtures.txt', username + '\n')
  const mailboxUrl = process.env.E2E_MAILBOX_URL
  if (!mailboxUrl) throw new Error('E2E_MAILBOX_URL is required for real email verification')
  const token = async () => (await (await page.request.get('/api/v1/auth/csrf')).json()).token as string
  const challengeResponse = await page.request.post('/api/v1/auth/email-codes', {
    headers: { 'X-CSRF-TOKEN': await token() }, data: { purpose: 'REGISTER', email, username },
  })
  expect(challengeResponse.status()).toBe(202)
  const challengeBody = await challengeResponse.json() as { challengeId?: unknown }
  let mailboxCode: string | null = null
  await expect.poll(async () => {
    const response = await page.request.get(`${mailboxUrl.replace(/\/$/, '')}/code?email=${encodeURIComponent(email)}`)
    if (!response.ok()) return null
    const body = await response.json() as { code?: unknown }
    mailboxCode = typeof body.code === 'string' ? body.code : null
    return mailboxCode
  }, { timeout: 15_000, message: 'The local SMTP test mailbox should receive the registration code' }).toMatch(/.+/)
  expect((await page.request.post('/api/v1/auth/register', {
    headers: { 'X-CSRF-TOKEN': await token() }, data: { username, password, email, code: mailboxCode!, challengeId: challengeBody.challengeId as string },
  })).status()).toBe(201)
  expect((await page.request.post('/api/v1/auth/login', { headers: { 'X-CSRF-TOKEN': await token(), Origin: new URL(page.url()).origin }, data: { username, password, rememberMe: false } })).status()).toBe(200)
  return { username, password }
}

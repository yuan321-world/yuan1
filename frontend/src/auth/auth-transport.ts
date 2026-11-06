import { ApiError, requestJson } from '../api/client'
import { acceptAccess, advanceGeneration, broadcastIdentityChange, clearAccess, getAccessToken, getGeneration } from './token-session'

let refreshing: Promise<void> | null = null
let refreshingGeneration = -1
let localQueue: Promise<unknown> = Promise.resolve()
let mutations = 0
const changed = () => new ApiError(409, { code: 'ACCOUNT_CHANGED', message: '登录状态已变化，请重试' })
// Same-tab serialization remains available when Web Locks is unsupported.
function credentialLock<T>(work: () => Promise<T>): Promise<T> {
  const pending = localQueue.catch(() => {}).then(() => {
    if (typeof navigator !== 'undefined' && navigator.locks) return navigator.locks.request('lingua-loop-credentials-v1', work)
    return work()
  })
  localQueue = pending.catch(() => {})
  return pending
}
export async function csrfOptions(body?: unknown): Promise<RequestInit> {
  const csrf = await requestJson<{ token: string; headerName: string }>('/api/v1/auth/csrf')
  if (csrf.headerName !== 'X-CSRF-TOKEN' || typeof csrf.token !== 'string' || !csrf.token) throw new TypeError('页面凭据格式不正确')
  return { method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token }, body: body === undefined ? undefined : JSON.stringify(body) }
}
interface TokenResponse { accessToken: string; expiresIn: number; tokenType: string; user: unknown }
function accept(value: TokenResponse, generation: number) {
  if (!value || value.tokenType !== 'Bearer' || typeof value.accessToken !== 'string' || !value.user || typeof value.user !== 'object') throw new TypeError('登录响应格式不正确')
  if (!acceptAccess(value.accessToken, value.expiresIn, generation)) throw changed()
}
async function refreshLocked(generation: number) {
  if (generation !== getGeneration()) throw changed()
  try {
    const options = await csrfOptions()
    if (generation !== getGeneration()) throw changed()
    const value = await requestJson<TokenResponse>('/api/v1/auth/refresh', options)
    accept(value, generation)
  } catch (error) {
    if (generation === getGeneration() && error instanceof ApiError && error.status === 401) clearAccess()
    throw error
  }
}
export function restoreAccess(): Promise<void> {
  if (mutations) return Promise.reject(changed())
  const generation = getGeneration()
  if (refreshing && refreshingGeneration === generation) return refreshing
  refreshingGeneration = generation
  const pending = credentialLock(async () => {
    if (generation !== getGeneration() || mutations) throw changed()
    await refreshLocked(generation)
  })
  refreshing = pending
  void pending.finally(() => { if (refreshing === pending) refreshing = null }).catch(() => {})
  return pending
}
// Password changes also delete credential cookies: hold the same cross-tab lock as login/refresh/logout.
export async function changePasswordCurrent(body: unknown): Promise<void> {
  mutations++
  const generation = advanceGeneration()
  try {
    await credentialLock(async () => {
      if (generation !== getGeneration()) throw changed()
      if (!getAccessToken()) await refreshLocked(generation)
      const options = await csrfOptions(body)
      const send = () => {
        if (generation !== getGeneration()) throw changed()
        const headers = new Headers(options.headers)
        headers.set('Authorization', `Bearer ${getAccessToken()}`)
        return requestJson<void>('/api/v1/account/password', { ...options, headers })
      }
      try { await send() } catch (error) {
        if (!(error instanceof ApiError) || error.status !== 401 || error.code !== 'ACCESS_TOKEN_EXPIRED') throw error
        await refreshLocked(generation)
        await send()
      }
      if (generation !== getGeneration()) throw changed()
      clearAccess(); broadcastIdentityChange('logout')
    })
  } finally { mutations-- }
}
export async function authenticatedJson<T>(path: string, options: RequestInit = {}, expectedGeneration = getGeneration()): Promise<T> {
  if (mutations || expectedGeneration !== getGeneration()) throw changed()
  if (!getAccessToken()) await restoreAccess()
  const generation = expectedGeneration
  const originalToken = getAccessToken()
  const send = () => {
    if (generation !== getGeneration() || mutations) throw changed()
    const headers = new Headers(options.headers)
    headers.set('Authorization', `Bearer ${getAccessToken()}`)
    return requestJson<T>(path, { ...options, headers })
  }
  try { return await send() } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401 || error.code !== 'ACCESS_TOKEN_EXPIRED') throw error
    if (generation !== getGeneration() || mutations) throw changed()
    // Another request may have completed the shared refresh before this expired response arrived.
    if (getAccessToken() === originalToken) await restoreAccess()
    return send()
  }
}
export async function loginCurrent(body: unknown): Promise<unknown> {
  mutations++
  const generation = advanceGeneration()
  try {
    return await credentialLock(async () => {
      if (generation !== getGeneration()) throw changed()
      const value = await requestJson<TokenResponse>('/api/v1/auth/login', await csrfOptions(body))
      accept(value, generation)
      broadcastIdentityChange('account-change')
      return value.user
    })
  } finally { mutations-- }
}
export async function logoutCurrent(): Promise<void> {
  mutations++
  const generation = advanceGeneration()
  try {
    await credentialLock(async () => {
      if (generation !== getGeneration()) throw changed()
      const options = await csrfOptions()
      const headers = new Headers(options.headers)
      if (getAccessToken()) headers.set('Authorization', `Bearer ${getAccessToken()}`)
      await requestJson<void>('/api/v1/auth/logout', { ...options, headers })
      if (generation === getGeneration()) { clearAccess(); broadcastIdentityChange('logout') }
    })
  } finally { mutations-- }
}

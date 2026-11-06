import { authenticatedJson, csrfOptions } from '../auth/auth-transport'
import { getGeneration, getAccessToken } from '../auth/token-session'
import { ApiError, requestJson } from './client'
export async function publicReadingGet<T>(path: string, signal?: AbortSignal): Promise<T> {
  const generation = getGeneration()
  const value = getAccessToken() ? await authenticatedJson<T>(path, { signal }, generation) : await requestJson<T>(path, { signal })
  assertGeneration(generation); return value
}
function assertGeneration(generation: number) { if (generation !== getGeneration()) throw new ApiError(409, { code: 'ACCOUNT_CHANGED', message: '账号已变化，请重新加载' }) }
export async function businessGet<T>(path: string, signal?: AbortSignal) {
  const generation = getGeneration()
  const result = await authenticatedJson<T>(path, { signal }, generation)
  assertGeneration(generation); return result
}
export async function businessWrite<T>(path: string, body?: unknown, method = 'POST', signal?: AbortSignal, isCurrent?: () => boolean): Promise<T> {
  const generation = getGeneration()
  const options = await csrfOptions(body)
  if (isCurrent && !isCurrent()) throw new ApiError(409, { code: 'CONTEXT_CHANGED', message: '选中内容已变化，请重新操作' })
  const result = await authenticatedJson<T>(path, { ...options, method, signal }, generation)
  assertGeneration(generation); return result
}

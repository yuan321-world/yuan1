import { ApiError, requestJson } from './client'
export interface UserDto { id: string; username: string; displayName: string; roles: ('USER' | 'ADMIN')[] }
function user(value: unknown): UserDto {
  if (!value || typeof value !== 'object') throw new Error('账号响应格式不正确')
  const u = value as Record<string, unknown>
  if (typeof u.id !== 'string' || typeof u.username !== 'string' || typeof u.displayName !== 'string'
    || !Array.isArray(u.roles) || !u.roles.every(r => r === 'USER' || r === 'ADMIN')) throw new Error('账号响应格式不正确')
  return u as unknown as UserDto
}
export async function me() { return user(await requestJson<unknown>('/api/v1/auth/me')) }
export async function write<T>(path: string, body?: unknown, method = 'POST'): Promise<T> {
  const csrf = await requestJson<{ token: string; headerName: string }>('/api/v1/auth/csrf')
  if (csrf.headerName !== 'X-CSRF-TOKEN' || typeof csrf.token !== 'string') throw new Error('页面凭据格式不正确')
  return requestJson<T>(path, { method, headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token }, body: body === undefined ? undefined : JSON.stringify(body) })
}
export async function login(username: string, password: string, rememberMe: boolean) { return user(await write<unknown>('/api/v1/auth/login', { username, password, rememberMe })) }
export async function register(username: string, password: string, displayName: string) { return user(await write<unknown>('/api/v1/auth/register', { username, password, ...(displayName.trim() ? { displayName } : {}) })) }
export async function logout() { await write<void>('/api/v1/auth/logout') }
export async function profile(displayName: string) { return user(await write<unknown>('/api/v1/account/profile', { displayName }, 'PATCH')) }
export async function password(currentPassword: string, newPassword: string) { await write<void>('/api/v1/account/password', { currentPassword, newPassword }) }
export async function adminIdentity() { return user(await requestJson<unknown>('/api/v1/admin/identity')) }
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return Object.values(error.fieldErrors ?? {}).join('；') || error.publicMessage || (error.status === 401 ? '登录已失效，请重新登录' : error.status === 403 ? '页面凭据或访问权限已失效，请重试' : '服务暂不可用，请稍后重试')
  return '无法连接服务，请检查连接后重试'
}

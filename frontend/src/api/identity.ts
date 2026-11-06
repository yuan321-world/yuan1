import { authenticatedJson, changePasswordCurrent, csrfOptions, loginCurrent, logoutCurrent } from '../auth/auth-transport'
import { getGeneration } from '../auth/token-session'
import { ApiError, requestJson } from './client'
export interface UserDto {
  id: string
  username: string
  displayName: string
  roles: ('USER' | 'ADMIN')[]
  /** Optional while older mock fixtures are migrated to the email contract. */
  email?: string | null
  emailVerified?: boolean
}
export interface EmailCodeChallenge { requestState: 'ACCEPTED'; challengeId: string; expiresIn: number; resendAfter: number }
export type EmailDeliveryState = 'QUEUED' | 'SMTP_ACCEPTED' | 'FAILED' | 'EXPIRED' | 'NOT_SENT'
export type EmailCodePurpose = 'REGISTER' | 'RESET'
function user(value: unknown): UserDto {
  if (!value || typeof value !== 'object') throw new Error('账号响应格式不正确')
  const u = value as Record<string, unknown>
  if (typeof u.id !== 'string' || typeof u.username !== 'string' || typeof u.displayName !== 'string'
    || !Array.isArray(u.roles) || !u.roles.every(r => r === 'USER' || r === 'ADMIN')
    || (u.email !== undefined && u.email !== null && typeof u.email !== 'string')
    || (u.emailVerified !== undefined && typeof u.emailVerified !== 'boolean')) throw new Error('账号响应格式不正确')
  return u as unknown as UserDto
}
export async function me() { return user(await authenticatedJson<unknown>('/api/v1/auth/me')) }
export async function write<T>(path: string, body?: unknown, method = 'POST'): Promise<T> {
  const generation = getGeneration()
  const options = { ...await writeOptions(body), method }
  return path.startsWith('/api/v1/account/') ? authenticatedJson<T>(path, options, generation) : requestJson<T>(path, options)
}
export async function login(username: string, password: string, rememberMe: boolean) { return user(await loginCurrent({ username, password, rememberMe })) }
export async function register(username: string, password: string, displayName: string, email: string, code: string, challengeId: string) {
  return user(await write<unknown>('/api/v1/auth/register', {
    username, password, email, code, challengeId,
    ...(displayName.trim() ? { displayName } : {}),
  }))
}
export async function emailCode(purpose: EmailCodePurpose, email: string, username?: string) {
  return emailCodeChallenge(await requestJson<unknown>('/api/v1/auth/email-codes', await writeOptions({
    purpose, email, ...(username === undefined ? {} : { username }),
  })))
}
export async function passwordReset(username: string, email: string, challengeId: string, code: string, newPassword: string) {
  await write<void>('/api/v1/auth/password-reset', { username, email, challengeId, code, newPassword })
}
export async function accountEmailCode(email: string, currentPassword: string) {
  const generation = getGeneration()
  return emailCodeChallenge(await authenticatedJson<unknown>('/api/v1/account/email-codes', await writeOptions({ email, currentPassword }), generation))
}
export async function accountEmailDelivery(challengeId: string, signal?: AbortSignal): Promise<EmailDeliveryState> {
  const value = await authenticatedJson<{ state: unknown }>(`/api/v1/account/email-codes/${encodeURIComponent(challengeId)}`, { signal })
  if (!value || !['QUEUED', 'SMTP_ACCEPTED', 'FAILED', 'EXPIRED', 'NOT_SENT'].includes(String(value.state))) throw new Error('发送状态格式不正确')
  return value.state as EmailDeliveryState
}
export async function bindEmail(email: string, currentPassword: string, challengeId: string, code: string) {
  return user(await write<unknown>('/api/v1/account/email', { email, currentPassword, challengeId, code }))
}
export async function logout() { await logoutCurrent() }
export async function profile(displayName: string) { return user(await write<unknown>('/api/v1/account/profile', { displayName }, 'PATCH')) }
export async function password(currentPassword: string, newPassword: string) { await changePasswordCurrent({ currentPassword, newPassword }) }
export async function adminIdentity() { return user(await authenticatedJson<unknown>('/api/v1/admin/identity')) }

const writeOptions = csrfOptions
function emailCodeChallenge(value: unknown): EmailCodeChallenge {
  if (!value || typeof value !== 'object') throw new Error('验证码响应格式不正确')
  const challenge = value as Record<string, unknown>
  if (challenge.requestState !== 'ACCEPTED' || typeof challenge.challengeId !== 'string' || !challenge.challengeId
    || !Number.isInteger(challenge.expiresIn) || Number(challenge.expiresIn) <= 0
    || !Number.isInteger(challenge.resendAfter) || Number(challenge.resendAfter) < 0) throw new Error('验证码响应格式不正确')
  return challenge as unknown as EmailCodeChallenge
}
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return Object.values(error.fieldErrors ?? {}).join('；') || error.publicMessage || (error.status === 401 ? '登录已失效，请重新登录' : error.status === 403 ? '页面凭据或访问权限已失效，请重试' : error.status === 429 ? '请求过于频繁，请稍后重试' : '服务暂不可用，请稍后重试')
  return '无法连接服务，请检查连接后重试'
}

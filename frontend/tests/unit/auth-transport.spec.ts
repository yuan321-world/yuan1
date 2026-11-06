import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError, requestJson } from '../../src/api/client'
import { authenticatedJson, restoreAccess, logoutCurrent, changePasswordCurrent, loginCurrent } from '../../src/auth/auth-transport'
import { acceptAccess, clearAccess, getAccessToken, getGeneration } from '../../src/auth/token-session'
vi.mock('../../src/api/client', async original => ({ ...await original<typeof import('../../src/api/client')>(), requestJson: vi.fn() }))
beforeEach(() => { vi.resetAllMocks(); clearAccess() })
const payload = { user: { id: '1', username: 'a', displayName: 'A', roles: ['USER'] }, accessToken: 'mock-new-access', tokenType: 'Bearer', expiresIn: 600 }
function csrf() { return { token: 'csrf', headerName: 'X-CSRF-TOKEN' } }
it('parallel expired requests share one refresh and retry only once', async () => {
  acceptAccess('old', 600, getGeneration())
  let refreshes = 0
  vi.mocked(requestJson).mockImplementation(async (path, options) => {
    if (path.endsWith('/csrf')) return csrf()
    if (path.endsWith('/refresh')) { refreshes++; return payload }
    if (new Headers(options?.headers).get('Authorization') === 'Bearer old') throw new ApiError(401, { code: 'ACCESS_TOKEN_EXPIRED' })
    return { ok: true }
  })
  await Promise.all([authenticatedJson('/api/v1/account/profile'), authenticatedJson('/api/v1/auth/me')])
  expect(refreshes).toBe(1)
})
it('business 401 and 503 never replay a write', async () => {
  acceptAccess('old', 600, getGeneration())
  for (const error of [new ApiError(401, { code: 'BAD_CREDENTIALS' }), new ApiError(503)]) {
    vi.mocked(requestJson).mockRejectedValueOnce(error)
    await expect(authenticatedJson('/api/v1/account/password', { method: 'POST' })).rejects.toBe(error)
  }
  expect(requestJson).toHaveBeenCalledTimes(2)
  expect(getAccessToken()).toBe('old')
})
it('late refresh cannot restore access after logout and refresh 503 preserves identity', async () => {
  let finish!: (v: unknown) => void
  vi.mocked(requestJson).mockImplementation(async path => {
    if (path.endsWith('/csrf')) return csrf()
    if (path.endsWith('/refresh')) return new Promise(resolve => { finish = resolve })
    return undefined
  })
  const pending = restoreAccess()
  await vi.waitFor(() => expect(finish).toBeTypeOf('function'))
  const logout = logoutCurrent()
  finish(payload)
  await expect(pending).rejects.toBeInstanceOf(ApiError)
  await logout
  expect(getAccessToken()).toBeNull()
  acceptAccess('preserved', 600, getGeneration())
  vi.mocked(requestJson).mockImplementation(async path => { if (path.endsWith('/csrf')) return csrf(); throw new ApiError(503) })
  await expect(restoreAccess()).rejects.toBeInstanceOf(ApiError)
  expect(getAccessToken()).toBe('preserved')
})
it('password change serializes with login and cannot clear a newer account', async () => {
  acceptAccess('account-a', 600, getGeneration())
  let release!: () => void
  let logins = 0
  vi.mocked(requestJson).mockImplementation(async path => {
    if (path.endsWith('/csrf')) return csrf()
    if (path.endsWith('/password')) return new Promise<void>(resolve => { release = resolve })
    if (path.endsWith('/login')) { logins++; return { ...payload, accessToken: 'account-b' } }
    return undefined
  })
  const password = changePasswordCurrent({ currentPassword: 'old', newPassword: 'new' })
  const outcome = expect(password).rejects.toMatchObject({ code: 'ACCOUNT_CHANGED' })
  await vi.waitFor(() => expect(release).toBeTypeOf('function'))
  const login = loginCurrent({ username: 'b', password: 'password' })
  expect(logins).toBe(0)
  release()
  await outcome
  await login
  expect(getAccessToken()).toBe('account-b')
})
it('expired password-change access refreshes inside its lock without recursive locking', async () => {
  acceptAccess('old', 600, getGeneration())
  let attempts = 0, refreshes = 0
  vi.mocked(requestJson).mockImplementation(async path => {
    if (path.endsWith('/csrf')) return csrf()
    if (path.endsWith('/refresh')) { refreshes++; return payload }
    if (path.endsWith('/password') && ++attempts === 1) throw new ApiError(401, { code: 'ACCESS_TOKEN_EXPIRED' })
    return undefined
  })
  await changePasswordCurrent({ currentPassword: 'old-password', newPassword: 'new-password' })
  expect([attempts, refreshes]).toEqual([2, 1])
  expect(getAccessToken()).toBeNull()
})

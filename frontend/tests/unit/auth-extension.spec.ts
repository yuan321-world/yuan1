import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../src/api/client'
import { bindEmail, emailCode, passwordReset, profile, register } from '../../src/api/identity'
import { validatePassword } from '../../src/auth/password-policy'
import { acceptAccess, clearAccess, getGeneration } from '../../src/auth/token-session'

function response(body: unknown, status = 200, headers?: HeadersInit): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(headers),
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response
}

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })

describe('extended identity API', () => {
  it('never sends an earlier account write after identity changes during CSRF lookup', async () => {
    clearAccess(); acceptAccess('account-a', 600, getGeneration())
    let release!: (v: Response) => void
    let writes = 0
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith('/csrf')) return new Promise<Response>(resolve => { release = resolve })
      writes++
      return response({ id: '2', username: 'b', displayName: 'Name from A', roles: ['USER'] })
    }))
    const pending = profile('Name from A')
    await vi.waitFor(() => expect(release).toBeTypeOf('function'))
    clearAccess(); acceptAccess('account-b', 600, getGeneration())
    release(response({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }))
    await expect(pending).rejects.toMatchObject({ code: 'ACCOUNT_CHANGED' })
    expect(writes).toBe(0)
  })
  it('does not accept a challenge without an explicit accepted request state', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => String(input).endsWith('/csrf')
      ? response({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
      : response({ challengeId: 'opaque', expiresIn: 300, resendAfter: 60 }, 202)))
    await expect(emailCode('RESET', 'r@example.com', 'reader')).rejects.toThrow('验证码响应格式不正确')
  })
  it('requests a registration code with CSRF and submits its challenge with email', async () => {
    const calls: Array<{ path: string; init?: RequestInit }> = []
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input); calls.push({ path, init })
      if (path === '/api/v1/auth/csrf') return response({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
      if (path === '/api/v1/auth/email-codes') return response({ challengeId: 'challenge-1', requestState: 'ACCEPTED', expiresIn: 300, resendAfter: 60 }, 202)
      if (path === '/api/v1/auth/register') return response({ id: '1', username: 'reader', displayName: 'Reader', roles: ['USER'], email: 'r@example.com', emailVerified: true }, 201)
      throw new Error(`Unexpected request ${path}`)
    }))

    await expect(emailCode('REGISTER', 'r@example.com', 'reader')).resolves.toEqual({ challengeId: 'challenge-1', requestState: 'ACCEPTED', expiresIn: 300, resendAfter: 60 })
    const created = await register('reader', 'passphrase', '', 'r@example.com', '123456', 'challenge-1')
    expect(calls[1]?.init?.headers).toMatchObject({ 'X-CSRF-TOKEN': 'csrf' })
    expect(JSON.parse(String(calls[1]?.init?.body))).toEqual({ purpose: 'REGISTER', email: 'r@example.com', username: 'reader' })
    expect(JSON.parse(String(calls[3]?.init?.body))).toEqual({ username: 'reader', password: 'passphrase', email: 'r@example.com', code: '123456', challengeId: 'challenge-1' })
    expect(created).toMatchObject({ email: 'r@example.com', emailVerified: true })
  })

  it('uses the verified username/email reset contract and confirms email binding with current password', async () => {
    clearAccess(); acceptAccess('mock-access', 600, getGeneration())
    const bodies: Array<unknown> = []
    let requestPath = ''
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path === '/api/v1/auth/csrf') return response({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
      requestPath = path
      bodies.push(init?.body ? JSON.parse(String(init.body)) : undefined)
      if (path.endsWith('/auth/password-reset')) return response(undefined, 204)
      return response({ id: '1', username: 'reader', displayName: 'Reader', roles: ['USER'], email: 'r@example.com', emailVerified: true })
    }))

    await passwordReset('reader', 'r@example.com', 'challenge-2', '654321', 'new-passphrase')
    expect(requestPath).toBe('/api/v1/auth/password-reset')
    expect(bodies[0]).toEqual({ username: 'reader', email: 'r@example.com', challengeId: 'challenge-2', code: '654321', newPassword: 'new-passphrase' })
    const bound = await bindEmail('r@example.com', 'current-passphrase', 'challenge-3', '111222')
    expect(requestPath).toBe('/api/v1/account/email')
    expect(bodies[1]).toEqual({ email: 'r@example.com', currentPassword: 'current-passphrase', challengeId: 'challenge-3', code: '111222' })
    expect(bound.emailVerified).toBe(true)
  })

  it('exposes server Retry-After seconds for a local resend cooldown', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ code: 'RATE_LIMITED' }, 429, { 'Retry-After': '37' })))
    await expect(emailCode('RESET', 'r@example.com', 'reader')).rejects.toMatchObject({ retryAfterSeconds: 37 } satisfies Partial<ApiError>)
  })
})

describe('password input policy', () => {
  it('counts Unicode characters and UTF-8 bytes and rejects controls or unpaired surrogates', () => {
    expect(validatePassword('Abcdef1!')).toBeNull()
    expect(validatePassword('🪷'.repeat(18))).toBeNull()
    expect(validatePassword('🪷'.repeat(19))).toContain('72')
    expect(validatePassword('a'.repeat(65))).toContain('64')
    expect(validatePassword('€'.repeat(23) + 'a'.repeat(3))).toBeNull()
    expect(validatePassword('€'.repeat(25))).toContain('72')
    expect(validatePassword('abc\u0007defg')).toContain('控制字符')
    expect(validatePassword('abc\ud800defg')).toContain('有效字符')
  })
})

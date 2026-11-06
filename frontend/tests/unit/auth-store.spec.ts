import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../src/stores/auth'
import * as api from '../../src/api/identity'
import { ApiError } from '../../src/api/client'
vi.mock('../../src/api/identity', () => ({ me: vi.fn(), login: vi.fn(), logout: vi.fn(), profile: vi.fn(), bindEmail: vi.fn(), password: vi.fn() }))
const user: api.UserDto = { id: '12', username: 'learner', displayName: '学习者', roles: ['USER'] }
beforeEach(() => { setActivePinia(createPinia()); vi.resetAllMocks() })
it('a stale password completion cannot clear a newer account', async () => {
  let release!: () => void
  vi.mocked(api.password).mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
  vi.mocked(api.login).mockResolvedValueOnce({ ...user, id: 'new-account' })
  const store = useAuthStore(); store.user = user
  const pending = store.changePassword('old-password', 'new-password')
  await store.signIn('new-account', 'password', false)
  release()
  await expect(pending).resolves.toBe(false)
  expect(store.user?.id).toBe('new-account')
})
it('restores real identity and treats only 401 as anonymous', async () => {
  const store = useAuthStore()
  vi.mocked(api.me).mockResolvedValueOnce(user)
  await store.restore(); expect(store.user).toEqual(user)
  vi.mocked(api.me).mockRejectedValueOnce(new ApiError(503))
  await expect(store.restore(true)).rejects.toBeInstanceOf(ApiError)
  expect(store.user).toEqual(user)
  vi.mocked(api.me).mockRejectedValueOnce(new ApiError(401))
  await store.restore(true); expect(store.user).toBeNull()
})
it('late restore cannot restore a session after logout', async () => {
  let resolve!: (value: typeof user) => void
  vi.mocked(api.me).mockImplementationOnce(() => new Promise(r => { resolve = r }))
  vi.mocked(api.logout).mockResolvedValueOnce(undefined)
  const store = useAuthStore(); const pending = store.restore()
  await store.signOut(); resolve(user); await pending
  expect(store.user).toBeNull()
})
it('late profile response cannot restore a session after logout', async () => {
  let resolve!: (value: typeof user) => void
  vi.mocked(api.profile).mockImplementationOnce(() => new Promise(r => { resolve = r }))
  vi.mocked(api.logout).mockResolvedValueOnce(undefined)
  const store = useAuthStore(); store.user = user
  const pending = store.saveProfile('新名字')
  await store.signOut(); resolve({ ...user, displayName: '新名字' }); await pending
  expect(store.user).toBeNull()
})
it('updates verified email from the server and ignores a late binding response after logout', async () => {
  let resolve!: (value: typeof user) => void
  vi.mocked(api.bindEmail).mockImplementationOnce(() => new Promise(r => { resolve = r }))
  vi.mocked(api.logout).mockResolvedValueOnce(undefined)
  const store = useAuthStore(); store.user = user
  const pending = store.bindEmail('reader@example.com', 'current-password', 'challenge', '123456')
  await store.signOut()
  resolve({ ...user, email: 'reader@example.com', emailVerified: true })
  await expect(pending).resolves.toBe(false)
  expect(store.user).toBeNull()
})

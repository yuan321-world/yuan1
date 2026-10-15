import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../src/stores/auth'
import * as api from '../../src/api/identity'
import { ApiError } from '../../src/api/client'
vi.mock('../../src/api/identity', () => ({ me: vi.fn(), login: vi.fn(), logout: vi.fn(), profile: vi.fn() }))
const user = { id: '12', username: 'learner', displayName: '学习者', roles: ['USER'] as ('USER' | 'ADMIN')[] }
beforeEach(() => { setActivePinia(createPinia()); vi.resetAllMocks() })
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

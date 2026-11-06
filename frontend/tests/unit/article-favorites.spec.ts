import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../src/stores/auth'
import { useArticleFavorites } from '../../src/stores/article-favorites'
import { clearAccess } from '../../src/auth/token-session'
import * as api from '../../src/api/article-favorites'
vi.mock('../../src/api/article-favorites', () => ({ favoriteStates: vi.fn(), setArticleFavorite: vi.fn() }))
beforeEach(() => { setActivePinia(createPinia()); clearAccess(); vi.resetAllMocks(); useAuthStore().user = { id: 'one', username: 'one', displayName: 'One', roles: ['USER'] } })
it('failed mutation retains the server state and exposes a local retry error', async () => {
  const store = useArticleFavorites(); store.values['10'] = false
  vi.mocked(api.setArticleFavorite).mockRejectedValueOnce(new TypeError('network'))
  await store.set('10', true)
  expect(store.values['10']).toBe(false); expect(store.errors['10']).toBeTruthy(); expect(store.busy['10']).toBe(false)
})
it('a late state read cannot overwrite a completed favorite mutation', async () => {
  let resolve!: (ids: string[]) => void
  vi.mocked(api.favoriteStates).mockImplementationOnce(() => new Promise(r => { resolve = r }))
  const store = useArticleFavorites(); store.load(['10']); await Promise.resolve()
  vi.mocked(api.setArticleFavorite).mockResolvedValueOnce({ articleId: '10', favorite: true })
  await store.set('10', true); resolve([]); await Promise.resolve(); await Promise.resolve()
  expect(store.values['10']).toBe(true)
})
it('changing identity clears all cached states and ignores an old response', async () => {
  let resolve!: (ids: string[]) => void
  vi.mocked(api.favoriteStates).mockImplementationOnce(() => new Promise(r => { resolve = r }))
  const store = useArticleFavorites(); store.load(['10']); await Promise.resolve()
  clearAccess(); useAuthStore().user = { id: 'two', username: 'two', displayName: 'Two', roles: ['USER'] }
  resolve(['10']); await Promise.resolve(); await Promise.resolve()
  expect(store.values['10']).toBeUndefined()
})

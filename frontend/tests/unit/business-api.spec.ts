import { beforeEach, expect, it, vi } from 'vitest'
import { requestJson } from '../../src/api/client'
import { businessWrite } from '../../src/api/business'
import { clearAccess, acceptAccess, getGeneration } from '../../src/auth/token-session'
vi.mock('../../src/api/client', async original => ({ ...await original<typeof import('../../src/api/client')>(), requestJson: vi.fn() }))
beforeEach(() => { vi.resetAllMocks(); clearAccess(); acceptAccess('mock-a', 600, getGeneration()) })
it('does not send a stale word selection after the CSRF wait', async () => {
  let release!: (v: unknown) => void, current = true
  vi.mocked(requestJson).mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
  const pending = businessWrite('/api/v1/vocabulary/words', { entryId: '1' }, 'POST', undefined, () => current)
  await vi.waitFor(() => expect(release).toBeTypeOf('function'))
  current = false; release({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
  await expect(pending).rejects.toMatchObject({ code: 'CONTEXT_CHANGED' })
  expect(requestJson).toHaveBeenCalledTimes(1)
})
it('does not submit an old article form under a new account after waiting for CSRF', async () => {
  let release!: (v: unknown) => void
  vi.mocked(requestJson).mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
  const pending = businessWrite('/api/v1/admin/articles', { title: 'Old account draft' })
  await vi.waitFor(() => expect(release).toBeTypeOf('function'))
  clearAccess(); acceptAccess('mock-b', 600, getGeneration())
  release({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
  await expect(pending).rejects.toMatchObject({ code: 'ACCOUNT_CHANGED' })
  expect(requestJson).toHaveBeenCalledTimes(1)
})
it('does not deliver a stale business response after an account change', async () => {
  let finish!: (v: unknown) => void
  vi.mocked(requestJson).mockResolvedValueOnce({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
  vi.mocked(requestJson).mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  const pending = businessWrite('/api/v1/admin/articles', { title: 'Draft' })
  await vi.waitFor(() => expect(finish).toBeTypeOf('function'))
  clearAccess(); acceptAccess('mock-b', 600, getGeneration()); finish({ id: '10' })
  await expect(pending).rejects.toMatchObject({ code: 'ACCOUNT_CHANGED' })
})

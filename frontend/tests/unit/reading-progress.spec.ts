import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { useReadingProgress, type ProgressUpdate } from '../../src/reading/useReadingProgress'
import { ApiError } from '../../src/api/client'
import { clearAccess } from '../../src/auth/token-session'
import type { ReadingProgress } from '../../src/api/reading'
const initial: ReadingProgress = { id: '1', articleId: '10', articleVersionId: '11', sentenceKey: 'p0001-s0001', offset: 0, status: 'READING', lockVersion: 0, updatedAt: '2026-10-16T00:00:00Z', completedAt: null }
beforeEach(() => { vi.useFakeTimers(); clearAccess() })
afterEach(() => vi.useRealTimers())
it('serializes saves and never acknowledges a newer unsent position', async () => {
  const finishes: Array<(p: ReadingProgress) => void> = []
  const save = vi.fn((_input: ProgressUpdate, _signal?: AbortSignal) => new Promise<ReadingProgress>(resolve => finishes.push(resolve)))
  const state = useReadingProgress(initial, save, async () => initial)
  state.setPosition('p0001-s0002', 0); const first = state.flush()
  await Promise.resolve()
  state.setPosition('p0001-s0003', 0)
  let departed = false
  const departure = state.flush().then(() => { departed = true })
  finishes[0]!({ ...initial, sentenceKey: 'p0001-s0002', lockVersion: 1 })
  for (let i = 0; i < 5; i++) await Promise.resolve()
  expect(save).toHaveBeenCalledTimes(2)
  expect(state.state.value).not.toBe('saved')
  expect(save.mock.calls[1]?.[0]).toMatchObject({ expectedLockVersion: 1, sentenceKey: 'p0001-s0003' })
  expect(departed).toBe(false)
  finishes[1]!({ ...initial, sentenceKey: 'p0001-s0003', lockVersion: 2 })
  await first; await departure
  expect(state.state.value).toBe('saved')
  state.dispose()
})
it('requires an explicit reload after a conflict and ignores a disposed late response', async () => {
  const save = vi.fn().mockRejectedValue(new ApiError(409, { code: 'READING_PROGRESS_CONFLICT' }))
  const reload = vi.fn().mockResolvedValue({ ...initial, lockVersion: 2 })
  const state = useReadingProgress(initial, save, reload)
  state.setPosition('p0001-s0002', 0); await state.flush()
  expect(state.state.value).toBe('conflict')
  await state.retry(); expect(save).toHaveBeenCalledTimes(1)
  await state.reloadLatest(); expect(state.progress.value.lockVersion).toBe(2)
  state.dispose()
  let finish!: (value: ReadingProgress) => void
  const late = useReadingProgress(initial, () => new Promise(resolve => { finish = resolve }), async () => initial)
  late.setPosition('p0001-s0002', 0); const pending = late.flush(); late.dispose()
  finish({ ...initial, lockVersion: 10 }); await pending
  expect(late.progress.value.lockVersion).toBe(0)
})
it('leaves failed writes unsynced and never sends after the account changes', async () => {
  const save = vi.fn().mockRejectedValue(new ApiError(503))
  const state = useReadingProgress(initial, save, async () => initial)
  state.setPosition('p0001-s0002', 0); await state.flush()
  expect(state.state.value).toBe('unsynced')
  expect(save).toHaveBeenCalledTimes(1)
  await vi.advanceTimersByTimeAsync(2500)
  expect(save).toHaveBeenCalledTimes(1)
  state.setPosition('p0001-s0003', 0); clearAccess(); await vi.advanceTimersByTimeAsync(2500)
  expect(save).toHaveBeenCalledTimes(1)
  state.dispose()
})

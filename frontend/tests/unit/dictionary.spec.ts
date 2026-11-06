import { expect, it, vi } from 'vitest'
import { useWordLookup } from '../../src/reading/useWordLookup'
import { clearAccess } from '../../src/auth/token-session'
import type { LookupResult } from '../../src/api/dictionary'
const context = { articleId: '10', versionId: '11', sentenceKey: 'p0001-s0001', startOffset: 0, endOffset: 5, surfaceForm: 'study' }
it('discards old lookup results after selection and identity changes', async () => {
  const finishes: Array<(value: LookupResult) => void> = []
  const fetch = vi.fn(() => new Promise<LookupResult>(resolve => finishes.push(resolve)))
  const state = useWordLookup(fetch)
  const first = state.lookup(context)
  const second = state.lookup({ ...context, surfaceForm: 'learn' })
  finishes[0]!({ found: false, candidates: [] }); await first
  expect(state.busy.value).toBe(true)
  clearAccess(); finishes[1]!({ found: true, candidates: [] }); await second
  expect(state.result.value).toBeNull()
  state.close(); expect(state.context.value).toBeNull()
})

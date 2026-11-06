import { ref } from 'vue'
import { ApiError } from '../api/client'
import { getGeneration } from '../auth/token-session'
import type { ReadingProgress } from '../api/reading'
export interface ProgressUpdate { sentenceKey: string; offset: number; completed: boolean; expectedLockVersion: number }
export function useReadingProgress(initial: ReadingProgress, save: (input: ProgressUpdate, signal?: AbortSignal) => Promise<ReadingProgress>, reload: () => Promise<ReadingProgress>) {
  const progress = ref({ ...initial })
  const state = ref<'idle' | 'saving' | 'saved' | 'unsynced' | 'conflict'>('idle')
  const error = ref<unknown>(null)
  const generation = getGeneration()
  let desired = { sentenceKey: initial.sentenceKey, offset: initial.offset, completed: initial.status === 'COMPLETED' }
  let revision = 0, acknowledged = 0, disposed = false
  let timer: ReturnType<typeof setTimeout> | undefined, inflight: Promise<void> | null = null, controller: AbortController | undefined
  const valid = () => !disposed && generation === getGeneration()
  function schedule() { clearTimeout(timer); timer = setTimeout(() => { void flush() }, 2000) }
  function setPosition(sentenceKey: string, offset: number) {
    if (!valid() || state.value === 'conflict' || (desired.sentenceKey === sentenceKey && desired.offset === offset)) return
    desired = { ...desired, sentenceKey, offset }; revision++; state.value = inflight ? 'saving' : 'unsynced'; schedule()
  }
  function flush(): Promise<void> {
    clearTimeout(timer)
    if (!valid() || state.value === 'conflict') return Promise.resolve()
    if (inflight) return inflight
    if (revision === acknowledged) return Promise.resolve()
    const pending = (async () => {
      while (valid() && revision > acknowledged) {
        const sentRevision = revision, snapshot = { ...desired, expectedLockVersion: progress.value.lockVersion }
        controller = new AbortController(); state.value = 'saving'; error.value = null
        try {
          const result = await save(snapshot, controller.signal)
          if (!valid()) return
          progress.value = result; acknowledged = sentRevision
          state.value = revision === acknowledged ? 'saved' : 'unsynced'
        } catch (e) {
          if (!valid()) return
          clearTimeout(timer); error.value = e
          state.value = e instanceof ApiError && e.code === 'READING_PROGRESS_CONFLICT' ? 'conflict' : 'unsynced'
          return // A failed write requires an explicit retry, including newer queued positions.
        }
      }
    })()
    inflight = pending
    void pending.finally(() => {
      if (inflight === pending) inflight = null
    }).catch(() => {})
    return pending
  }
  async function markCompleted() {
    if (!valid() || state.value === 'conflict' || desired.completed) return
    desired = { ...desired, completed: true }; revision++; await flush()
  }
  async function reloadLatest() {
    if (!valid() || inflight) return
    try {
      const result = await reload()
      if (!valid()) return
      progress.value = result; desired = { sentenceKey: result.sentenceKey, offset: result.offset, completed: result.status === 'COMPLETED' }
      revision++; acknowledged = revision; state.value = 'saved'; error.value = null
    } catch (e) { if (valid()) { error.value = e; state.value = 'unsynced' } }
  }
  function dispose() { disposed = true; clearTimeout(timer); controller?.abort() }
  return { progress, state, error, setPosition, markCompleted, flush, retry: flush, reloadLatest, dispose }
}

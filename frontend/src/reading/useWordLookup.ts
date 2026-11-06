import { ref, shallowRef } from 'vue'
import { lookupWord, type LookupResult } from '../api/dictionary'
import { errorMessage } from '../api/identity'
import { getGeneration } from '../auth/token-session'
import type { SelectionContext } from './selection'
export function useWordLookup(fetch = lookupWord) {
  const context = shallowRef<SelectionContext | null>(null), result = shallowRef<LookupResult | null>(null), busy = ref(false), error = ref('')
  let request = 0, controller: AbortController | undefined
  function cancel() { request++; controller?.abort(); busy.value = false }
  function select(value: SelectionContext | null) { cancel(); context.value = value; result.value = null; error.value = '' }
  async function lookup(value: SelectionContext) {
    select(value); const current = request, identity = getGeneration()
    controller = new AbortController(); busy.value = true
    try { const response = await fetch(value.surfaceForm, controller.signal); if (current === request && identity === getGeneration()) result.value = response }
    catch (e) { if (current === request && identity === getGeneration() && !(e instanceof DOMException && e.name === 'AbortError')) error.value = errorMessage(e) }
    finally { if (current === request) busy.value = false }
  }
  function close() { select(null) }
  return { context, result, busy, error, select, lookup, close }
}

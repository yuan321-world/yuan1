import { defineStore } from 'pinia'
import { reactive, watch } from 'vue'
import { useAuthStore } from './auth'
import { favoriteStates, setArticleFavorite } from '../api/article-favorites'
import { errorMessage } from '../api/identity'
import { getGeneration } from '../auth/token-session'
export const useArticleFavorites = defineStore('article-favorites', () => {
  const auth = useAuthStore(), values = reactive<Record<string, boolean>>({}), errors = reactive<Record<string, string>>({}), busy = reactive<Record<string, boolean>>({})
  const revisions = new Map<string, number>(), queued = new Set<string>()
  let scheduled = false
  watch(() => auth.user?.id, () => { for (const map of [values, errors, busy]) for (const id of Object.keys(map)) delete map[id]; revisions.clear(); queued.clear() }, { flush: 'sync' })
  function load(ids: string[]) {
    if (!auth.user) return
    ids.forEach(id => { if (values[id] === undefined && !busy[id]) queued.add(id) })
    if (scheduled || !queued.size) return
    scheduled = true
    queueMicrotask(async () => {
      scheduled = false
      const ids = [...queued].slice(0, 50); queued.clear(); if (!ids.length || !auth.user) return
      const generation = getGeneration(), versions = ids.map(id => revisions.get(id) ?? 0)
      try {
        const active = new Set(await favoriteStates(ids))
        if (generation !== getGeneration()) return
        ids.forEach((id, i) => { if ((revisions.get(id) ?? 0) === versions[i]) { values[id] = active.has(id); delete errors[id] } })
      } catch (error) { if (generation === getGeneration()) ids.forEach((id, i) => { if ((revisions.get(id) ?? 0) === versions[i]) errors[id] = errorMessage(error) }) }
    })
  }
  async function set(id: string, active: boolean) {
    if (!auth.user || busy[id]) return
    const generation = getGeneration(); revisions.set(id, (revisions.get(id) ?? 0) + 1); busy[id] = true; delete errors[id]
    try { const result = await setArticleFavorite(id, active); if (generation === getGeneration()) values[id] = result.favorite }
    catch (error) { if (generation === getGeneration()) errors[id] = errorMessage(error) }
    finally { if (generation === getGeneration()) busy[id] = false }
  }
  return { values, errors, busy, load, set }
})

import { ref, shallowRef, onBeforeUnmount } from 'vue'
import { getReadingTools, getArticleWords, type ReadingTools, type ArticleWord } from '../api/reading-tools'
import type { ArticleVersion } from '../api/admin-articles'
import { errorMessage } from '../api/identity'
import { getGeneration } from '../auth/token-session'
export function useReadingTools() {
  const tools = shallowRef<ReadingTools>(), toolsBusy = ref(false), toolsError = ref('')
  const words = shallowRef<ArticleWord[]>([]), wordTotal = ref(0), wordPage = ref(1), wordsBusy = ref(false), wordsError = ref('')
  let version: ArticleVersion | undefined, authenticated = false, scope = 0, wordRequest = 0
  let publicController: AbortController | undefined, wordController: AbortController | undefined
  function reset() { scope++; wordRequest++; publicController?.abort(); wordController?.abort(); tools.value = undefined; toolsError.value = ''; words.value = []; wordTotal.value = 0; wordPage.value = 1; wordsError.value = ''; toolsBusy.value = false; wordsBusy.value = false; version = undefined }
  async function reloadTools() {
    if (!version) return
    const current = scope, identity = getGeneration(), selected = version
    publicController?.abort(); const controller = publicController = new AbortController(); toolsBusy.value = true; toolsError.value = ''
    try { const result = await getReadingTools(selected.articleId, selected.id, controller.signal); if (!controller.signal.aborted && scope === current && identity === getGeneration()) tools.value = result }
    catch (e) { if (!controller.signal.aborted && scope === current && identity === getGeneration()) toolsError.value = errorMessage(e) }
    finally { if (publicController === controller) toolsBusy.value = false }
  }
  async function reloadWords(page = wordPage.value) {
    if (!version || !authenticated) return
    const selected = version, current = ++wordRequest, identity = getGeneration(), currentScope = scope
    wordController?.abort(); const controller = wordController = new AbortController(); wordsBusy.value = true; wordsError.value = ''
    try { const result = await getArticleWords(selected.articleId, selected.id, page, controller.signal); if (current === wordRequest && currentScope === scope && identity === getGeneration()) { words.value = result.items; wordTotal.value = result.total; wordPage.value = page } }
    catch (e) { if (!controller.signal.aborted && current === wordRequest && currentScope === scope && identity === getGeneration()) wordsError.value = errorMessage(e) }
    finally { if (wordController === controller) wordsBusy.value = false }
  }
  function load(value: ArticleVersion, signedIn: boolean) { reset(); version = value; authenticated = signedIn; void reloadTools(); if (signedIn) void reloadWords(1) }
  onBeforeUnmount(reset)
  return { tools, toolsBusy, toolsError, words, wordTotal, wordPage, wordsBusy, wordsError, load, reset, reloadTools, reloadWords }
}

import { onBeforeUnmount, watch, nextTick, type Ref } from 'vue'
import { rangeFromOffsets, normalizeWord, wordAtPoint } from './selection'
type Registry = { set(name: string, value: unknown): void; delete(name: string): void }
function api() { return { registry: (globalThis.CSS as typeof CSS & { highlights?: Registry })?.highlights, Highlight: (globalThis as typeof globalThis & { Highlight?: new (...ranges: Range[]) => unknown }).Highlight } }
export function useReadingHighlights(root: Ref<HTMLElement | undefined>, forms: () => string[], enabled: () => boolean, article: () => { articleId: string; id: string } | undefined) {
  let animation = 0
  function clearHover() { cancelAnimationFrame(animation); api().registry?.delete('reader-hover') }
  function hover(e: MouseEvent) {
    clearHover(); if (!matchMedia('(pointer:fine)').matches || !document.getSelection()?.isCollapsed) return
    const x = e.clientX, y = e.clientY
    animation = requestAnimationFrame(() => { const value = article(), { registry, Highlight } = api(); if (!value || !registry || !Highlight) return; const selection = wordAtPoint(x, y, value.articleId, value.id); if (!selection) return; const sentence = document.getElementById(selection.sentenceKey); if (!sentence || !root.value?.contains(sentence)) return; const range = rangeFromOffsets(sentence, selection.startOffset, selection.endOffset); if (range) registry.set('reader-hover', new Highlight(range)) })
  }
  async function keywords() {
    await nextTick(); const { registry, Highlight } = api(); registry?.delete('reader-keywords'); if (!registry || !Highlight || !enabled()) return
    const set = new Set(forms().map(s => normalizeWord(s))), ranges: Range[] = []
    root.value?.querySelectorAll<HTMLElement>('[data-sentence]').forEach(sentence => { for (const match of (sentence.textContent ?? '').matchAll(/[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*/g)) if (set.has(normalizeWord(match[0]))) { const range = rangeFromOffsets(sentence, match.index, match.index + match[0].length); if (range) ranges.push(range) } })
    if (ranges.length) registry.set('reader-keywords', new Highlight(...ranges))
  }
  watch([root, forms, enabled, article], () => { clearHover(); void keywords() }, { deep: true })
  onBeforeUnmount(() => { clearHover(); api().registry?.delete('reader-keywords') })
  return { hover, clearHover }
}

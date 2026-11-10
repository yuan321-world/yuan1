<script setup lang="ts">
import PronunciationControls from '@/components/common/PronunciationControls.vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type CSSProperties } from 'vue'
import PhraseSaveButton from '@/components/vocabulary/PhraseSaveButton.vue'
import type { LookupResult } from '@/api/dictionary'
const props = defineProps<{ word: string; result: LookupResult | null; busy: boolean; error: string; example?: string; anchor?: () => DOMRect | null }>()
const emit = defineEmits<{ close: []; retry: [] }>()
const panel = ref<HTMLElement>(), closeButton = ref<HTMLButtonElement>()
const selectedEntry = ref<string>()
const activeCandidate = computed(() => props.result?.candidates.find(candidate => candidate.entryId === selectedEntry.value) ?? props.result?.candidates[0])
const panelStyle = ref<CSSProperties>({})
let resizeObserver: ResizeObserver | undefined, frame = 0
function positionPanel() {
  if (!panel.value) return
  if (window.innerWidth <= 600) { panelStyle.value = {}; return }
  const margin = 16, gap = 10, width = panel.value.getBoundingClientRect().width
  const maximum = Math.min(window.innerHeight * .65, 600, window.innerHeight - margin * 2)
  let height = Math.min(panel.value.scrollHeight + 2, maximum)
  let left = (window.innerWidth - width) / 2, top = (window.innerHeight - height) / 2
  const anchor = props.anchor?.()
  if (anchor && anchor.bottom > margin && anchor.top < window.innerHeight - margin) {
    left = Math.max(margin, Math.min(anchor.left, window.innerWidth - width - margin))
    const below = window.innerHeight - anchor.bottom - gap - margin, above = anchor.top - gap - margin
    const useBelow = below >= height || (above < height && below >= above)
    height = Math.min(height, Math.max(80, useBelow ? below : above))
    top = useBelow ? anchor.bottom + gap : anchor.top - gap - height
  }
  panelStyle.value = { position: 'fixed', left: `${left}px`, top: `${Math.max(margin, Math.min(top, window.innerHeight - height - margin))}px`, maxHeight: `${height}px` }
}
function schedulePosition() { cancelAnimationFrame(frame); frame = requestAnimationFrame(positionPanel) }
watch(() => [props.result, props.busy, props.error], async () => { await nextTick(); schedulePosition() })
watch(() => props.word, () => { selectedEntry.value = undefined; stopSpeech(); speechError.value = '' })
watch(() => activeCandidate.value?.entryId, async () => { stopSpeech(); speechError.value = ''; await nextTick(); schedulePosition() })
const speechError = ref('')
let speaking = false, speechGeneration = 0
function stopSpeech() {
  // Invalidate first: cancel() may synchronously dispatch the old utterance's error.
  speechGeneration++
  const wasSpeaking = speaking; speaking = false
  if (wasSpeaking && 'speechSynthesis' in window) speechSynthesis.cancel()
}
function speak(word: string, accent: 'en-GB' | 'en-US') {
  stopSpeech(); speechError.value = ''
  if (!('speechSynthesis' in window) || typeof SpeechSynthesisUtterance === 'undefined') { speechError.value = '当前浏览器不支持合成朗读'; return }
  const current = speechGeneration
  try {
    const utterance = new SpeechSynthesisUtterance(word); utterance.lang = accent; speaking = true
    utterance.onstart = () => { if (current === speechGeneration) speechError.value = '' }
    utterance.onend = () => { if (current === speechGeneration) speaking = false }
    utterance.onerror = event => {
      if (current !== speechGeneration) return
      speaking = false
      if (event.error === 'canceled' || event.error === 'interrupted') return
      speechError.value = '朗读暂不可用，请检查浏览器语音设置'
    }
    speechSynthesis.speak(utterance)
  } catch {
    if (current === speechGeneration) { speaking = false; speechError.value = '朗读暂不可用，请检查浏览器语音设置' }
  }
}
let previous: HTMLElement | null = null
function keydown(event: KeyboardEvent) {
  // The confirmation dialog owns keyboard focus while it is above this lookup panel.
  if (event.target instanceof Element && event.target.closest('.el-message-box,.phrase-save-dialog')) return
  if (event.key === 'Escape') { event.preventDefault(); emit('close') }
  if (event.key !== 'Tab') return
  const nodes = Array.from(panel.value?.querySelectorAll<HTMLElement>('button:not(:disabled),a[href],summary,[tabindex="0"]') ?? []).filter(node => node.getClientRects().length)
  const first = nodes[0], last = nodes.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
onMounted(async () => {
  previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
  document.addEventListener('keydown', keydown, true)
  window.addEventListener('resize', schedulePosition); window.addEventListener('scroll', schedulePosition, true)
  await nextTick(); positionPanel(); closeButton.value?.focus({ preventScroll: true })
  resizeObserver = new ResizeObserver(schedulePosition); if (panel.value) resizeObserver.observe(panel.value)
})
onBeforeUnmount(() => { cancelAnimationFrame(frame); resizeObserver?.disconnect(); window.removeEventListener('resize', schedulePosition); window.removeEventListener('scroll', schedulePosition, true); document.removeEventListener('keydown', keydown, true); stopSpeech(); if (previous?.isConnected) previous.focus({ preventScroll: true }) })
</script>
<template><Teleport to="body"><div class="lookup-backdrop" @click.self="emit('close')"><section ref="panel" class="word-lookup-panel compact-lookup" :style="panelStyle" @toggle.capture="schedulePosition" role="dialog" aria-modal="true" aria-labelledby="lookup-heading">
  <header><h2 id="lookup-heading" lang="en">{{ word }}</h2><button ref="closeButton" class="lookup-close" aria-label="关闭查词" @click="emit('close')">×</button></header>
  <p v-if="busy" role="status">正在查词…</p>
  <div v-else-if="error" role="alert"><p>{{ error }}</p><button class="button-secondary" @click="emit('retry')">重试查询</button></div>
  <div v-else-if="result && !result.found"><p>本地词典暂未收录这个词。</p><slot name="missing" /></div>
  <div v-else class="lookup-result">
  <div v-if="(result?.candidates.length ?? 0) > 1" class="lookup-entry-choices" role="group" aria-label="选择释义词条"><button v-for="entry in result?.candidates ?? []" :key="entry.entryId" type="button" :aria-label="'查看词条：' + entry.lemma" :aria-pressed="entry.entryId === activeCandidate?.entryId" @click="selectedEntry = entry.entryId"><span lang="en">{{ entry.lemma }}</span><small>{{ entry.senses[0]?.partOfSpeech }}</small></button></div>
  <div v-for="candidate in activeCandidate ? [activeCandidate] : []" :key="candidate.entryId" class="dictionary-candidate">
    <p v-if="candidate.lemma.toLowerCase() !== word.toLowerCase()" class="lookup-lemma">词形原型 <strong lang="en">{{ candidate.lemma }}</strong></p>
    <PronunciationControls class="lookup-pronunciations" :phonetic="candidate.phonetic" uk-label="英式合成朗读" us-label="美式合成朗读" @speak="speak(candidate.lemma,$event)"/>
    <ol class="lookup-senses"><li v-for="(sense, index) in candidate.senses" :key="index"><span>{{ sense.partOfSpeech }}</span> {{ sense.definitionZh }}</li></ol>
    <p v-if="speechError" role="status">{{ speechError }}</p>
    <details class="lookup-fixed-examples" :open="!!candidate.examples?.length"><summary>例句 <small>固定双语例句</small></summary><ol v-if="candidate.examples?.length" class="lookup-example-list"><li v-for="item in candidate.examples" :key="item.text"><p lang="en">{{item.text}}</p><p lang="zh-CN">{{item.translationZh}}</p></li></ol><p v-else>该词的固定双语例句待补充。</p></details>
    <details class="lookup-phrases"><summary>短语搭配</summary><ul v-if="candidate.phrases?.length" class="lookup-phrase-list"><li v-for="phrase in candidate.phrases" :key="phrase.text"><div class="lookup-phrase-copy"><strong lang="en">{{ phrase.text }}</strong><span>{{ phrase.translationZh }}</span></div><PhraseSaveButton :text="phrase.text" :meaning="phrase.translationZh" :note="'词典搭配：'+candidate.lemma"/></li></ul><p v-else>暂无收录短语。</p></details>
    <details class="lookup-synonyms"><summary>近义词</summary><template v-if="candidate.synonyms?.length"><p class="lookup-lexical-hint">按词义分组，替换前请确认语境。</p><ul class="lookup-synonym-groups"><li v-for="(group, index) in candidate.synonyms" :key="index"><p><span>{{ group.partOfSpeech }}</span> <span lang="en">{{ group.meaningEn }}</span></p><div lang="en"><span v-for="word in group.words" :key="word" class="lookup-related-word">{{ word }}</span></div></li></ul></template><p v-else>暂无可用近义词。</p></details>
    <details v-if="example" class="lookup-example"><summary>当前文章原句</summary><p lang="en">{{ example }}</p></details>
    <details v-if="candidate.senses.some(sense => !!sense.definitionEn)" class="lookup-english"><summary>英文释义</summary><p v-for="(definition, index) in [...new Set(candidate.senses.map(sense => sense.definitionEn).filter(Boolean))]" :key="index" lang="en">{{ definition }}</p></details>
    <slot name="candidate" :candidate="candidate" />
    <details class="lookup-source lookup-attribution"><summary>来源与语音说明</summary><p v-if="candidate.phoneticSourceNote">{{ candidate.phoneticSourceNote }}</p><p>{{ candidate.sourceNote }}</p><p v-if="candidate.exampleSourceNote">{{ candidate.exampleSourceNote }}</p><p v-if="candidate.lexicalSourceNote">{{ candidate.lexicalSourceNote }}</p><small>朗读使用浏览器合成语音。</small></details>
  </div>
  </div>
</section></div></Teleport></template>

<style scoped>.lookup-phrase-list>li{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:start;gap:8px;padding:9px 0}.lookup-phrase-copy{min-width:0;overflow-wrap:anywhere}.lookup-phrase-copy>span{display:block;margin-top:3px;font-size:12px;line-height:1.6}.lookup-phrase-list :deep(.phrase-save){align-self:start;max-width:100px;gap:3px}.lookup-phrase-list :deep(.phrase-save-note){font-size:10px;line-height:1.4}</style>

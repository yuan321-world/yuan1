<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getFullArticleTranslation, prepareFullArticleTranslation, type FullArticleTranslation, type ReadingTools } from '@/api/reading-tools'
import { errorMessage } from '@/api/identity'
import { useAuthStore } from '@/stores/auth'
import { useAuthDialogStore } from '@/stores/auth-dialog'

const props = defineProps<{article: string; version: string; reference?: ReadingTools['translation']; bilingual: boolean}>()
const emit = defineEmits<{translation: [value: ReadingTools['translation']]}>()
const auth = useAuthStore(), login = useAuthDialogStore()
const state = ref<FullArticleTranslation>(), busy = ref(false), error = ref(''), expanded = ref(false)
const translation = computed(() => props.reference?.available ? props.reference : state.value?.translation ?? props.reference)
const complete = computed(() => !!translation.value?.available)
const working = computed(() => ['QUEUED', 'RUNNING'].includes(state.value?.status ?? ''))
let generation = 0, controller: AbortController | undefined, timer: ReturnType<typeof setTimeout> | undefined
function cancel() { generation++; controller?.abort(); clearTimeout(timer); busy.value = false }
async function load(prepare = false) {
  cancel()
  const current = generation
  controller = new AbortController(); busy.value = true; error.value = ''
  try {
    const result = await (prepare ? prepareFullArticleTranslation : getFullArticleTranslation)(props.article, props.version, controller.signal)
    if (current !== generation) return
    state.value = result
    emit('translation', result.translation)
    if (['QUEUED', 'RUNNING'].includes(result.status)) timer = setTimeout(() => void load(), 2000)
  } catch (e) {
    if (current === generation && !(e instanceof DOMException && e.name === 'AbortError')) error.value = errorMessage(e)
  } finally { if (current === generation) busy.value = false }
}
function prepare() { if (!auth.user) { login.request(); return }; void load(true) }
watch(() => [props.article, props.version, auth.user?.id], () => {
  cancel(); state.value = undefined; error.value = ''; expanded.value = false
  if (!props.reference?.available) void load()
}, { immediate: true })
watch(() => props.bilingual, value => { if (value && !complete.value && !working.value) void load() })
onBeforeUnmount(cancel)
</script>
<template>
  <details class="reader-full-translation" :open="expanded" @toggle="expanded = ($event.target as HTMLDetailsElement).open">
    <summary>查看全文中文翻译 <small v-if="complete">已齐备</small><small v-else-if="working">准备中</small></summary>
    <div class="full-translation-content">
      <p v-if="complete" class="reader-translation-source">{{ translation?.source || '参考译文' }}</p>
      <p v-else-if="working" role="status">正在准备译文 · {{ state?.completed ?? 0 }} / {{ state?.total ?? 0 }} 段，完成后自动显示。</p>
      <p v-else-if="busy" role="status">正在加载译文…</p>
      <p v-else-if="state?.status === 'FAILED'" role="status">已完成 {{ state.completed }} / {{ state.total }} 段。{{ state.error || '暂未完成，请稍后重试。' }}</p>
      <p v-else-if="!complete && !error">{{ state?.configured ? '译文尚未准备，准备后可在全文翻译和英中对照中使用。' : '全文翻译暂时不可用，请稍后再试。' }}</p>
      <p v-if="error" role="alert">{{ error }} <button type="button" class="text-button" @click="load()">重新加载</button></p>
      <button v-if="!complete && !working && state?.configured" type="button" class="button-secondary" :disabled="busy" @click="prepare">{{ !auth.user ? '登录后准备全文翻译' : state.status === 'FAILED' ? '重试全文翻译' : '准备全文翻译' }}</button>
      <p v-for="paragraph in translation?.paragraphs ?? []" :key="paragraph.key" class="full-translation-paragraph">{{ paragraph.text }}</p>
    </div>
  </details>
</template>
<style scoped>
summary{display:flex;align-items:center;gap:10px}summary::before{content:'▸';font-size:11px}details[open] summary::before{content:'▾'}summary small{margin-left:auto;color:var(--text-muted,#6a7565);font-weight:400;font-size:12px}.full-translation-content{padding:0 14px 12px}.full-translation-content p{line-height:1.65;font-size:14px;margin:10px 0}.full-translation-content .full-translation-paragraph{font-size:15px}.full-translation-content button{margin:4px 0}
</style>

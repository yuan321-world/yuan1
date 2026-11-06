<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { notifySuccess } from '@/feedback/notify'
import 'element-plus/es/components/message-box/style/css'
import { vocabularyWords, vocabularyDetail, cancelWord, type CollectedWord, type CollectedDetail } from '@/api/vocabulary'
import { joinLearning } from '@/api/vocabulary-learning'
import { errorMessage } from '@/api/identity'
const words = ref<CollectedWord[]>([]), page = ref(1), total = ref(0), busy = ref(true), error = ref('')
const selected = ref<CollectedDetail>(), sourcePage = ref(1), detailBusy = ref(false), detailError = ref(''), cancelBusy = ref(false)
let listController: AbortController | undefined, detailController: AbortController | undefined, mutation: AbortController | undefined
const joined = ref<Record<string, boolean>>({}), joinBusy = ref(''), joinError = ref<Record<string, string>>({})
async function addToLearning(word: CollectedWord) {
 if (joinBusy.value) return; joinBusy.value = word.id; joinError.value[word.id] = ''
 try { await joinLearning([word.id]); if (!disposed) joined.value[word.id] = true }
 catch (e) { if (!disposed) joinError.value[word.id] = errorMessage(e) }
 finally { if (!disposed) joinBusy.value = '' }
}
let generation = 0, detailGeneration = 0, disposed = false
async function load() {
  listController?.abort(); listController = new AbortController(); const current = ++generation
  busy.value = true; error.value = ''
  try { const result = await vocabularyWords(page.value, listController.signal); if (!disposed && current === generation) { words.value = result.items; total.value = result.total } }
  catch (e) { if (!disposed && current === generation && !(e instanceof DOMException && e.name === 'AbortError')) error.value = errorMessage(e) }
  finally { if (!disposed && current === generation) busy.value = false }
}
async function show(id: string, reset = true) {
  if (reset) sourcePage.value = 1
  detailController?.abort(); detailController = new AbortController(); const current = ++detailGeneration
  selected.value = undefined; detailBusy.value = true; detailError.value = ''
  try { const result = await vocabularyDetail(id, sourcePage.value, detailController.signal); if (!disposed && current === detailGeneration) selected.value = result }
  catch (e) { if (!disposed && current === detailGeneration && !(e instanceof DOMException && e.name === 'AbortError')) detailError.value = errorMessage(e) }
  finally { if (!disposed && current === detailGeneration) detailBusy.value = false }
}
async function cancel(word: CollectedWord) {
  if (cancelBusy.value) return
  try { await ElMessageBox.confirm(`取消收藏 ${word.lemma}？阅读来源会保留，再次收藏时可恢复；已加入学习的复习记录也会保留，可在背单词页暂停。`, '取消收藏', { confirmButtonText: '确认取消收藏', cancelButtonText: '保留' }) } catch { return }
  if (disposed) return
  cancelBusy.value = true; error.value = ''; mutation = new AbortController()
  try { await cancelWord(word.id, mutation.signal); if (disposed) return; detailGeneration++; detailController?.abort(); detailBusy.value = false; if (selected.value?.word.id === word.id) selected.value = undefined; if (words.value.length === 1 && page.value > 1) page.value--; notifySuccess('已取消收藏'); await load() }
  catch (e) { if (!disposed) error.value = errorMessage(e) }
  finally { if (!disposed) cancelBusy.value = false }
}
onMounted(load)
onBeforeUnmount(() => { disposed = true; generation++; detailGeneration++; listController?.abort(); detailController?.abort(); mutation?.abort() })
</script>
<template><section class="reading-page"><header class="reading-page-header"><div><h1>笔记本</h1><p>收下阅读中遇见的词，也保留遇见它的那句话。</p></div><RouterLink class="button-secondary" to="/reading">去阅读</RouterLink></header><div v-if="error" class="reading-notice" role="alert"><p>{{ error }}</p><button class="button-secondary" @click="load">重新加载</button></div><p v-else-if="busy" class="reading-empty" role="status">正在加载收藏…</p><div v-else-if="!words.length" class="reading-empty"><h2>还没有收藏的生词</h2><p>阅读时查询一个单词，再点击“收藏生词”，它就会出现在这里。</p></div><div v-else class="notebook-words"><article v-for="word in words" :key="word.id" class="notebook-word"><div><h2><button @click="show(word.id)">{{ word.lemma }}</button></h2><p>{{ word.senses[0]?.partOfSpeech }} {{ word.senses[0]?.definitionZh }}</p></div><div class="reading-actions"><button class="button-secondary" :disabled="!!joinBusy || joined[word.id]" @click="addToLearning(word)">{{ joined[word.id] ? '已加入学习' : '加入学习' }}</button><span v-if="joinError[word.id]" role="alert">{{ joinError[word.id] }}</span><button class="button-secondary" @click="show(word.id)">查看来源</button><button class="button-secondary" :disabled="cancelBusy" @click="cancel(word)">取消收藏</button></div></article></div><nav v-if="total > 20" class="reading-pagination" aria-label="生词分页"><button class="button-secondary" :disabled="busy || page <= 1" @click="page--; load()">上一页</button><span>第 {{ page }} 页</span><button class="button-secondary" :disabled="busy || page * 20 >= total" @click="page++; load()">下一页</button></nav><p v-if="detailBusy" role="status">正在加载原文来源…</p><div v-if="detailError" class="reading-notice" role="alert">{{ detailError }}</div><section v-if="selected" class="notebook-detail" aria-labelledby="notebook-detail-title"><h2 id="notebook-detail-title">{{ selected.word.lemma }}</h2><p v-for="(sense,index) in selected.word.senses" :key="index">{{ sense.partOfSpeech }} {{ sense.definitionZh }}<br><span lang="en">{{ sense.definitionEn }}</span></p><p class="reading-meta">{{ selected.word.sourceNote }}</p><h3>阅读来源</h3><article v-for="source in selected.sources.items" :key="source.id" class="notebook-source"><p lang="en">{{ source.sentence }}</p><RouterLink :to="{ path: `/reading/${source.articleId}`, query: { version: source.articleVersionId, sentence: source.sentenceKey, start: source.startOffset, end: source.endOffset } }">{{ source.title }} · {{ source.surfaceForm }} ↗</RouterLink></article><nav v-if="selected.sources.total > 10" class="reading-pagination" aria-label="来源分页"><button class="button-secondary" :disabled="detailBusy || sourcePage <= 1" @click="sourcePage--; show(selected!.word.id, false)">上一页</button><span>第 {{ sourcePage }} 页</span><button class="button-secondary" :disabled="detailBusy || sourcePage * 10 >= selected.sources.total" @click="sourcePage++; show(selected!.word.id, false)">下一页</button></nav></section></section></template>

<script setup lang="ts">
import PhraseSaveButton from '@/components/vocabulary/PhraseSaveButton.vue'
import {phraseSourceFromRange} from '@/vocabulary/phrase-source'
import { computed, nextTick, onBeforeUnmount, watch, ref, shallowRef, onMounted } from 'vue'
import { useRoute, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { readingVersion, currentArticle, startReading, saveProgress, type ReadingDetail } from '@/api/reading'
import { useReadingProgress } from '@/reading/useReadingProgress'
import { ElMessageBox } from 'element-plus'
import { notifySuccess } from '@/feedback/notify'
import 'element-plus/es/components/message-box/style/css'
import { sourceLabel } from '@/api/admin-articles'
import { errorMessage } from '@/api/identity'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useAuthDialogStore } from '@/stores/auth-dialog'
import ReadingCheckinCard from '@/components/reading/ReadingCheckinCard.vue'
import ArticleQuizPanel from '@/components/reading/ArticleQuizPanel.vue'
import ArticleFavoriteButton from '@/components/reading/ArticleFavoriteButton.vue'
import ArticleBody from '@/components/reading/ArticleBody.vue'
import WordLookupPanel from '@/components/reading/WordLookupPanel.vue'
import SelectionGrammarPanel from '@/components/reading/SelectionGrammarPanel.vue'
import type {SelectionMode} from '@/api/selection-translation'
import {localSelectionWord} from '@/reading/local-selection-word'
import { useSelectionTranslation } from '@/reading/useSelectionTranslation'
import { placeSelectionAction,lastVisibleSelectionRect,articleSelectionText,bindSettledSelection } from '@/reading/selection-action'
import { selectionFromRange, wordAtPoint, rangeFromOffsets } from '@/reading/selection'
import { useWordLookup } from '@/reading/useWordLookup'
import { collectWord, cancelWord } from '@/api/vocabulary'
import { lookupPhrase, type DictionaryCandidate, type PhraseLookupResult, type LookupResult } from '@/api/dictionary'
import type { ReadingKeyword, ArticleWord } from '@/api/reading-tools'
import ReadingToolbar from '@/components/reading/ReadingToolbar.vue'
import ReadingRail from '@/components/reading/ReadingRail.vue'
import ArticleWritingPanel from '@/components/reading/ArticleWritingPanel.vue'
import { createReaderSettings } from '@/reading/settings'
import { useReadingTools } from '@/reading/useReadingTools'
import { useReadingHighlights } from '@/reading/useReadingHighlights'
import { normalizeWord } from '@/reading/selection'
const checkinRevision = ref(0)
const auth = useAuthStore(), authDialog = useAuthDialogStore(), settings = ref(createReaderSettings())
const extra = useReadingTools()
const { tools, toolsBusy, toolsError, words, wordTotal, wordPage, wordsBusy, wordsError } = extra
const wordsOpen = ref(false), readingRoot = ref<HTMLElement>(), selectedSentence = ref(''), sentenceText = ref(''), sentenceDialog = ref<HTMLDialogElement>()
const translationMap = computed(() => Object.fromEntries((tools.value?.translation.paragraphs ?? []).map(p => [p.key, p.text])))
const readerStyle = computed(() => ({ '--reading-font-size': settings.value.fontSize + 'px', '--reading-line-height': settings.value.lineHeight, '--reader-font': settings.value.font === 'serif' ? 'var(--serif)' : 'var(--sans)', '--reader-align': settings.value.align }))
function closeWords() { wordsOpen.value = false; document.querySelector<HTMLButtonElement>('.reader-words-toggle')?.focus({ preventScroll: true }) }
const route = useRoute(), detail = ref<ReadingDetail>(), error = ref(''), busy = ref(true)
let controller: AbortController | undefined, generation = 0
const tracking = shallowRef<ReturnType<typeof useReadingProgress>>()
const lookup = useWordLookup(), lookupOpen = ref(false)
const localWord=ref(''),localResult=shallowRef<LookupResult|null>(null)
const localExample=computed(()=>detail.value?.version.content.paragraphs.flatMap(p=>p.sentences).find(s=>s.text.includes(localWord.value))?.text)
function closeLocal(){localWord.value='';localResult.value=null}
const lookupExample = computed(() => detail.value?.version.content.paragraphs.flatMap(p => p.sentences).find(s => s.key === lookup.context.value?.sentenceKey)?.text)
const collectBusy = ref(false), collectError = ref(''), collectingEntry = ref<string | null>(null)
const highlights = useReadingHighlights(readingRoot, () => tools.value?.keywords.flatMap(k => k.forms) ?? [], () => settings.value.keywords, () => detail.value?.version)
let collectController: AbortController | undefined, collectGeneration = 0
function openKeyword(keyword: ReadingKeyword) {
  const version = detail.value?.version
  if (!version) return
  const forms = new Set(keyword.forms.map(f => normalizeWord(f)))
  for (const paragraph of version.content.paragraphs) for (const sentence of paragraph.sentences) for (const match of sentence.text.matchAll(/[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*/g)) {
    if (!forms.has(normalizeWord(match[0]))) continue
    lookup.select({ articleId: version.articleId, versionId: version.id, sentenceKey: sentence.key, startOffset: match.index, endOffset: match.index + match[0].length, surfaceForm: match[0] }); openLookup(); return
  }
}
function openSaved(item: ArticleWord) {
  if (item.source.articleVersionId !== detail.value?.version.id) return
  lookup.select({ articleId: item.source.articleId, versionId: item.source.articleVersionId, sentenceKey: item.source.sentenceKey, startOffset: item.source.startOffset, endOffset: item.source.endOffset, surfaceForm: item.source.surfaceForm }); openLookup()
}
const selectionMode=ref<SelectionMode>('TRANSLATION')
const selectionLabel=computed(()=>selectionMode.value==='GRAMMAR'?'解析':'翻译')
const sentenceAnswer = computed(() => {
  if(selectionMode.value==='GRAMMAR')return undefined
  const exact = tools.value?.translation.sentences?.find(s => s.sourceText === sentenceText.value)
  if (exact) return exact.text
  const paragraph = detail.value?.version.content.paragraphs.find(p => p.sentences.map(s => s.text).join(' ') === sentenceText.value)
  return paragraph ? translationMap.value[paragraph.key] : undefined
})
const phraseResult = ref<PhraseLookupResult | null>(null), phraseBusy = ref(false), phraseError = ref('')
const onlineTranslation=useSelectionTranslation()
watch(()=>auth.user?.id,key=>{closeLocal();onlineTranslation.bindIdentity(key??'guest');sentenceDialog.value?.close();sentenceText.value='';cancelPhrase()})
let phraseController: AbortController | undefined, phraseGeneration = 0
function cancelPhrase() { phraseGeneration++; phraseController?.abort(); phraseBusy.value = false;onlineTranslation.cancel() }
async function querySelectedPhrase() {
  cancelPhrase(); phraseResult.value = null; phraseError.value = '';onlineTranslation.clear()
  if (sentenceAnswer.value) return
  const text = sentenceText.value, current = phraseGeneration
  if(selectionMode.value==='GRAMMAR'){
   if(!auth.user)return
   if(Array.from(text).length>1000){phraseError.value='语法解析每次最多1000字，请缩小选区。';return}
   const version=detail.value?.version;if(version)await onlineTranslation.start(version.articleId,version.id,text.replace(/\s+/g,' ').trim(),false,'GRAMMAR')
   return
  }
  const offline=text.length<=100&&/^[A-Za-z]+(?:['’-][A-Za-z]+)*(?:\s+[A-Za-z]+(?:['’-][A-Za-z]+)*){1,5}$/.test(text)
  phraseController=new AbortController();phraseBusy.value=true
  try {
   const local=await localSelectionWord(text,phraseController.signal);if(current!==phraseGeneration)return;if(local){localWord.value=text;localResult.value=local;sentenceDialog.value?.close();return}
   if(offline){const result=await lookupPhrase(text,phraseController.signal);if(current!==phraseGeneration)return;phraseResult.value=result;if(result.found)return}
   if(current!==phraseGeneration||!auth.user)return
   if(Array.from(text).length>1000){phraseError.value='实时翻译每次最多1000字，请缩小选区。';return}
   const version=detail.value?.version;if(version)await onlineTranslation.start(version.articleId,version.id,text.replace(/\s+/g,' ').trim())
  } catch (e) { if(current===phraseGeneration&&!(e instanceof DOMException&&e.name==='AbortError'))phraseError.value=errorMessage(e) }
  finally { if(current===phraseGeneration)phraseBusy.value=false }
}
function translateSelection(mode:SelectionMode='TRANSLATION') { selectionMode.value=mode;sentenceText.value = selectedSentence.value; sentenceDialog.value?.showModal(); void querySelectedPhrase() }
function loginForSelection(){sentenceDialog.value?.close();authDialog.request(route.fullPath)}
async function retryOnline(){const version=detail.value?.version;if(version)await onlineTranslation.start(version.articleId,version.id,sentenceText.value.replace(/\s+/g,' ').trim(),onlineTranslation.task.value?.status==='FAILED',selectionMode.value)}
async function collect(candidate: DictionaryCandidate) {
  if (collectBusy.value || !lookup.context.value || !lookup.result.value?.candidates.includes(candidate)) return
  if (!auth.user) { closeLookup(); authDialog.request(); return }
  const selection = lookup.context.value, current = ++collectGeneration
  collectController = new AbortController(); collectBusy.value = true; collectError.value = ''; collectingEntry.value = candidate.entryId
  const valid = () => current === collectGeneration && lookupOpen.value && lookup.context.value === selection && detail.value?.version.id === selection.versionId && !!lookup.result.value?.candidates.includes(candidate)
  try {
    const result = await collectWord({ articleVersionId: selection.versionId, sentenceKey: selection.sentenceKey, startOffset: selection.startOffset, endOffset: selection.endOffset, entryId: candidate.entryId }, collectController.signal, valid)
    if (!valid()) return
    lookup.result.value = { ...lookup.result.value!, candidates: lookup.result.value!.candidates.map(c => c === candidate ? { ...c, collection: { wordId: result.word.id, active: true } } : c) }
    notifySuccess('已收藏到生词本'); void extra.reloadWords(1)
  } catch (e) { if (valid()) collectError.value = errorMessage(e) }
  finally { if (current === collectGeneration) collectBusy.value = false }
}
async function cancelCollection(candidate: DictionaryCandidate) {
  if (collectBusy.value || !lookup.context.value || !candidate.collection?.active || !lookup.result.value?.candidates.includes(candidate)) return
  const selection = lookup.context.value, wordId = candidate.collection.wordId, current = ++collectGeneration
  collectBusy.value = true; collectError.value = ''; collectingEntry.value = candidate.entryId
  const valid = () => current === collectGeneration && lookupOpen.value && lookup.context.value === selection && detail.value?.version.id === selection.versionId && !!lookup.result.value?.candidates.includes(candidate)
  try {
    try { await ElMessageBox.confirm(`取消收藏 ${candidate.lemma}？阅读来源会保留，再次收藏时可恢复。`, '取消收藏', { confirmButtonText: '确认取消收藏', cancelButtonText: '保留' }) } catch { return }
    if (!valid()) return
    collectController = new AbortController()
    await cancelWord(wordId, collectController.signal, valid)
    if (!valid()) return
    lookup.result.value = { ...lookup.result.value!, candidates: lookup.result.value!.candidates.map(c => c === candidate ? { ...c, collection: { wordId, active: false } } : c) }
    notifySuccess('已取消收藏'); void extra.reloadWords(1)
  } catch (e) { if (valid()) collectError.value = errorMessage(e) }
  finally { if (current === collectGeneration) collectBusy.value = false }
}
function closeLookup() { collectGeneration++; collectController?.abort(); collectBusy.value = false; collectError.value = ''; collectingEntry.value = null; lookupOpen.value = false; lookup.close() }
const selectionButton = ref<HTMLElement>(), selectionPosition = ref<{left:number;top:number}|null>(null)
let selectionRange:Range|null=null,disposeSelection:(()=>void)|undefined
const selectionActionStyle = computed(()=>selectionPosition.value?{left:selectionPosition.value.left+'px',top:selectionPosition.value.top+'px'}:{})
function localAnchor(){return selectionRange?.getBoundingClientRect()??null}
function positionSelectionAction(){
 if(!selectionRange){selectionPosition.value=null;return}
 const rects=Array.from(selectionRange.getClientRects()).filter(r=>r.width>0&&r.height>0),rect=lastVisibleSelectionRect(rects,innerWidth,innerHeight)??selectionRange.getBoundingClientRect()
 selectionPosition.value=placeSelectionAction(rect,selectionButton.value?.offsetWidth??180,selectionButton.value?.offsetHeight??44,innerWidth,innerHeight)
}
function captureSelection() {
  if(sentenceDialog.value?.open)return
  selectedSentence.value = '';selectionRange=null;selectionPosition.value=null
  if (lookupOpen.value || localWord.value || !detail.value) return
  const selection = document.getSelection()
  const range = selection?.rangeCount ? selection.getRangeAt(0) : null
  const inside = range && range.startContainer.parentElement?.closest('.article-body')
  const sameBody=inside&&inside===range?.endContainer.parentElement?.closest('.article-body')
    const word = sameBody && range ? selectionFromRange(range, detail.value.version.articleId, detail.value.version.id) : null
  lookup.select(word)
  if (!word && sameBody && range && !range.collapsed && readingRoot.value?.contains(range.commonAncestorContainer)) {
    const text = articleSelectionText(range,inside!)
    if (text.length > 0 && text.length <= 2000) selectedSentence.value = text
  }
  if(range&&(word||selectedSentence.value)){selectionRange=range.cloneRange();positionSelectionAction();void nextTick(positionSelectionAction)}
}
function translateMissingWord(){const value=lookup.context.value?.surfaceForm;if(!value)return;closeLookup();selectedSentence.value=value;translateSelection()}
function openLookup() { if (lookup.context.value) { lookupOpen.value = true; void lookup.lookup(lookup.context.value) } }
function lookupAnchor(): DOMRect | null {
  const context = lookup.context.value
  if (!context) return null
  const sentence = document.getElementById(context.sentenceKey)
  return sentence ? rangeFromOffsets(sentence, context.startOffset, context.endOffset)?.getBoundingClientRect() ?? null : null
}
function clickWord(event: MouseEvent) {
  if (!detail.value || !matchMedia('(pointer: fine)').matches || !document.getSelection()?.isCollapsed) return
  const value = wordAtPoint(event.clientX, event.clientY, detail.value.version.articleId, detail.value.version.id)
  if (value) { lookup.select(value); openLookup() }
}
let observer: IntersectionObserver | undefined
async function reloadProgress() {
  try { await ElMessageBox.confirm('重新加载会放弃本页尚未保存的位置，是否继续？', '读取最新进度', { confirmButtonText: '重新加载', cancelButtonText: '取消' }); await tracking.value?.reloadLatest(); restorePosition() } catch { /* cancelled */ }
}
function restorePosition() {
  const progress = tracking.value?.progress.value
  if (!progress) return
  scrollPosition(progress.sentenceKey, progress.offset)
}
function scrollPosition(sentence: string, start = 0, end = start) {
  const firstSentence = detail.value?.version.content.paragraphs[0]?.sentences[0]?.key
  // A saved position at the very beginning should retain the article heading and tools.
  if (sentence === firstSentence && start === 0 && end === 0) {
    window.scrollTo({ top: 0, behavior: 'instant' })
    return
  }
  const element = document.getElementById(sentence)
  if (!element) return
  element.scrollIntoView({ block: 'start' })
  const range = rangeFromOffsets(element, start, end), bounds = range?.getBoundingClientRect()
  if (bounds && bounds.height > 0) window.scrollBy({ top: bounds.top - 125 })
}
function pageHidden() { if (document.visibilityState === 'hidden') void tracking.value?.flush() }
onMounted(() => { document.addEventListener('visibilitychange', pageHidden); disposeSelection=bindSettledSelection(captureSelection,()=>{if(lookupOpen.value||localWord.value||sentenceDialog.value?.open)return;selectedSentence.value='';lookup.select(null);selectionRange=null;selectionPosition.value=null});window.addEventListener('scroll',positionSelectionAction,true);window.addEventListener('resize',positionSelectionAction) })
onBeforeRouteLeave(async () => { await tracking.value?.flush() })
onBeforeRouteUpdate(async () => { await tracking.value?.flush() })
async function load() {
  cancelPhrase(); closeLookup(); closeLocal(); extra.reset(); selectedSentence.value = '';selectionRange=null;selectionPosition.value=null; sentenceDialog.value?.close()
  observer?.disconnect(); tracking.value?.dispose(); tracking.value = undefined
  controller?.abort(); controller = new AbortController(); const current = ++generation
  busy.value = true; error.value = ''; detail.value = undefined
  try {
    const id = String(route.params.articleId), version = typeof route.query.version === 'string' ? route.query.version : null
    let result: ReadingDetail = version ? await readingVersion(id, version, controller.signal) : auth.user ? await startReading(id, controller.signal) : await currentArticle(id, controller.signal)
    if (auth.user && !result.progress) {
      const started = await startReading(id, controller.signal)
      if (started.version.id !== result.version.id) throw new ApiError(409, { code: 'ARTICLE_VERSION_CONFLICT', message: '文章版本已更新，请返回列表重新选择' })
      result = started
    }
    if (current !== generation) return
    detail.value = result; busy.value = false; extra.load(result.version, !!auth.user)
    if (!auth.user) return
    const vid = result.version.id, aid = result.version.articleId
    tracking.value = useReadingProgress(result.progress!, (input, signal) => saveProgress(vid, input, signal), async () => {
      const latest = await readingVersion(aid, vid)
      if (!latest.progress) throw new ApiError(404, { code: 'READING_NOT_FOUND', message: '阅读记录不存在' })
      return latest.progress
    })
    await nextTick()
    const sentence = typeof route.query.sentence === 'string' ? route.query.sentence : result.progress?.sentenceKey
    if ((version || route.query.sentence || (result.progress?.lockVersion ?? 0) > 0) && sentence && result.version.content.paragraphs.some(p => p.sentences.some(s => s.key === sentence))) scrollPosition(sentence, Number(route.query.start ?? result.progress?.offset ?? 0), Number(route.query.end ?? route.query.start ?? result.progress?.offset ?? 0))
    if (current !== generation) return
    observer = new IntersectionObserver(entries => {
      const visible = entries.filter(e => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0]
      if (visible) tracking.value?.setPosition((visible.target as HTMLElement).dataset.sentence!, 0)
    }, { rootMargin: '-110px 0px -55% 0px', threshold: 0 })
    document.querySelectorAll('.article-body [data-sentence]').forEach(node => observer!.observe(node))
  } catch (e) { if (current === generation && !(e instanceof DOMException && e.name === 'AbortError')) error.value = errorMessage(e) }
  finally { if (current === generation) busy.value = false }
}
watch(() => [route.params.articleId, route.query.version, route.query.sentence, route.query.start, route.query.end], load, { immediate: true })
onBeforeUnmount(() => { cancelPhrase(); generation++; controller?.abort(); observer?.disconnect(); tracking.value?.dispose(); closeLookup(); document.removeEventListener('visibilitychange', pageHidden); disposeSelection?.();window.removeEventListener('scroll',positionSelectionAction,true);window.removeEventListener('resize',positionSelectionAction) })
</script>
<template><section class="reading-page reading-workspace">
<RouterLink class="reading-back" to="/reading">← 返回文章列表</RouterLink>
<p v-if="busy" class="reading-empty" role="status">正在打开文章…</p>
<div v-else-if="error" class="reading-notice" role="alert"><p>{{ error }}</p><button class="button-secondary" @click="load">重新加载</button></div>
<div v-else-if="detail" class="reader-columns">
<article class="reading-sheet">
<header class="article-heading"><ArticleFavoriteButton :article-id="detail.version.articleId" :title="detail.version.title" /><span class="reading-tag">{{ detail.version.category.name }} · {{ detail.version.difficulty }}</span><h1>{{ detail.version.title }}</h1><p class="reader-title-zh">{{ detail.version.titleZh }}</p><p class="reading-meta">{{ detail.version.author }} · {{ sourceLabel(detail.version.sourceType) }} · {{ detail.version.wordCount }} 词 · 约 {{ detail.version.estimatedMinutes }} 分钟（估算）</p><a v-if="detail.version.sourceUrl" :href="detail.version.sourceUrl" target="_blank" rel="noopener noreferrer">查看原始来源</a><p class="reading-meta">{{ detail.version.usageNote }}</p></header>
<ReadingToolbar v-model:settings="settings" :keyword-count="tools?.keywords.length ?? 0" :words-open="wordsOpen" @toggle-words="wordsOpen = !wordsOpen" />
<p class="reading-lookup-hint">点击单词查词；手机上选中一个完整单词，再点击“查询选中单词”。选中多个单词后，可点击“翻译”查看中文，或点击“解析”了解语法结构。</p>
<p v-if="!auth.user" class="reading-guest-note">游客可阅读与查词，阅读位置不会保存到账号。<button type="button" class="text-button" @click="authDialog.request()">登录后同步</button></p>
<p v-if="settings.bilingual && !tools?.translation.available" class="reader-local-message" role="status">{{ toolsBusy ? '正在加载参考译文…' : toolsError ? '参考译文加载失败，可以在阅读工具中重试。' : '此文章版本暂未准备参考译文，实时翻译尚未启用。' }}</p>
<p v-if="settings.bilingual && tools?.translation.available" class="reader-translation-source">{{ tools.translation.source }}</p>
<div ref="readingRoot" class="reading-article-content" :style="readerStyle" @click="clickWord" @mousemove="highlights.hover" @mouseleave="highlights.clearHover"><ArticleBody :content="detail.version.content" :translations="translationMap" :bilingual="settings.bilingual" /></div>
<div v-if="tracking" class="reading-progress-bar"><p role="status" aria-live="polite">{{ { idle: '阅读位置会自动保存', saving: '正在保存…', saved: '进度已保存', unsynced: '进度尚未同步', conflict: '其他页面已更新进度，请加载最新位置' }[tracking.state.value] }}</p><div class="reading-actions"><button v-if="tracking.state.value === 'unsynced'" class="button-secondary" @click="tracking.retry">重试保存</button><button v-if="tracking.state.value === 'conflict'" class="button-secondary" @click="reloadProgress">加载最新进度</button><button class="button-secondary" :disabled="tracking.progress.value.status === 'COMPLETED' || tracking.state.value === 'saving' || tracking.state.value === 'conflict'" @click="tracking.markCompleted">{{ tracking.progress.value.status === 'COMPLETED' ? '已读完' : '标记为已读' }}</button></div></div>
<details class="reader-full-translation"><summary>查看全文中文翻译</summary><div v-if="tools?.translation.available"><p class="reader-translation-source">{{ tools.translation.source }}</p><p v-for="paragraph in tools.translation.paragraphs" :key="paragraph.key">{{ paragraph.text }}</p></div><p v-else>{{ toolsBusy ? '正在加载…' : '此文章版本暂未准备参考译文，实时翻译尚未启用。' }}</p></details>
<div id="reader-writing-mobile" class="reader-writing-mobile" /><ArticleQuizPanel :article="detail.version.articleId" :version="detail.version.id" @completed="checkinRevision++" /><ReadingCheckinCard :article="detail.version.articleId" :version="detail.version.id" :revision="checkinRevision" :read-status="tracking?.progress.value.status" /><nav v-if="tools" class="reader-neighbors" aria-label="相邻文章"><RouterLink v-if="tools.previous" :to="'/reading/' + tools.previous.id"><small>← 上一篇</small>{{ tools.previous.title }}</RouterLink><span v-else>已经是第一篇</span><RouterLink v-if="tools.next" :to="'/reading/' + tools.next.id"><small>下一篇 →</small>{{ tools.next.title }}</RouterLink><span v-else>已经是最后一篇</span></nav>
</article>
<ReadingRail :tools="tools" :busy="toolsBusy" :error="toolsError" :words="words" :total="wordTotal" :page="wordPage" :words-busy="wordsBusy" :words-error="wordsError" :authenticated="!!auth.user" :open="wordsOpen" @close="closeWords" @responsive="wordsOpen = $event" @retry-tools="extra.reloadTools()" @retry-words="extra.reloadWords()" @page="extra.reloadWords($event)" @keyword="openKeyword" @word="openSaved" @login="authDialog.request()"><template #writing><ArticleWritingPanel :article="detail.version.articleId" :version="detail.version.id" /></template></ReadingRail>
</div>
<button v-if="lookup.context.value && !lookupOpen && !localWord && selectionPosition" ref="selectionButton" :style="selectionActionStyle" @pointerdown.prevent class="button-secondary selection-lookup-button" @click="openLookup">查询选中单词：{{ lookup.context.value.surfaceForm }}</button>
<div v-else-if="selectedSentence && !lookupOpen && !localWord && selectionPosition" ref="selectionButton" :style="selectionActionStyle" @pointerdown.prevent class="selection-lookup-button selection-action-toolbar" role="group" aria-label="选文操作"><button type="button" aria-label="翻译" @click="translateSelection('TRANSLATION')">翻译</button><button type="button" aria-label="解析选中内容" @click="translateSelection('GRAMMAR')">解析</button><PhraseSaveButton :text="selectedSentence" :source="detail?phraseSourceFromRange(selectionRange,detail.version):undefined"/></div>
<WordLookupPanel v-if="lookupOpen && lookup.context.value" :anchor="lookupAnchor" :example="lookupExample" :word="lookup.context.value.surfaceForm" :result="lookup.result.value" :busy="lookup.busy.value" :error="lookup.error.value" @close="closeLookup" @retry="openLookup"><template #missing><button type="button" class="button-secondary" @click="translateMissingWord">翻译</button></template><template #candidate="{ candidate }"><div class="lookup-collection"><button class="button-secondary" :disabled="collectBusy" @click="candidate.collection?.active ? cancelCollection(candidate) : collect(candidate)">{{ !auth.user ? '登录后保存生词' : candidate.collection?.active ? '取消收藏' : candidate.collection ? '恢复收藏' : '收藏生词' }}</button><p v-if="collectError && collectingEntry === candidate.entryId" role="alert">{{ collectError }}</p></div></template></WordLookupPanel>
<WordLookupPanel v-if="localWord && localResult" :word="localWord" :result="localResult" :busy="false" error="" :example="localExample" :anchor="localAnchor" @close="closeLocal" @retry="closeLocal"/>
<dialog ref="sentenceDialog" class="reader-sentence-dialog" :class="{ 'is-grammar':selectionMode==='GRAMMAR' }" @close="cancelPhrase"><header><h2>{{selectionMode==='GRAMMAR'?'语法解析':'选词与选段释义'}}</h2><button type="button" :aria-label="selectionMode==='GRAMMAR'?'关闭语法解析':'关闭选段翻译'" @click="sentenceDialog?.close()">×</button></header><p lang="en">{{sentenceText}}</p><p v-if="sentenceAnswer">{{sentenceAnswer}}</p><p v-else-if="phraseResult?.found && phraseResult.phrase">{{phraseResult.phrase.translationZh}}</p><p v-else-if="phraseBusy||onlineTranslation.busy.value" role="status">{{onlineTranslation.busy.value?'正在'+selectionLabel+'选中内容…':'正在查询本地释义…'}}</p><div v-else-if="phraseError" role="alert"><p>{{phraseError}}</p><button type="button" class="button-secondary" @click="querySelectedPhrase">重试查询</button></div><template v-else-if="onlineTranslation.task.value?.status==='SUCCEEDED'"><SelectionGrammarPanel v-if="selectionMode==='GRAMMAR' && onlineTranslation.task.value.analysis" :analysis="onlineTranslation.task.value.analysis"/><p v-else class="reader-online-translation">{{onlineTranslation.task.value.translation}}</p></template><div v-else-if="onlineTranslation.error.value" role="alert"><p>{{onlineTranslation.error.value}}</p><button type="button" class="button-secondary" @click="retryOnline">确认{{selectionLabel}}结果</button></div><div v-else-if="onlineTranslation.task.value?.status==='FAILED'" role="alert"><p>实时{{selectionLabel}}未完成（{{onlineTranslation.task.value.errorCode}}）。</p><p class="reader-translation-source">再次{{selectionLabel}}会发起新的请求，并计入{{selectionMode==='GRAMMAR'?'今日AI额度':'本月翻译字符额度'}}。</p><button type="button" class="button-secondary" @click="retryOnline">重试实时{{selectionLabel}}</button></div><div v-else-if="onlineTranslation.waiting.value"><p>{{selectionLabel}}仍在处理中，可稍后查询。</p><button type="button" class="button-secondary" @click="retryOnline">查看{{selectionLabel}}结果</button></div><template v-else><p class="reader-local-message">{{selectionMode==='GRAMMAR'?'语法解析将分析所选英文的结构。':'所选内容未匹配到离线短语或已准备的完整句段译文。'}}{{!auth.user?'登录后可使用实时'+selectionLabel+'，也可切换英中对照查看上下文。':'请缩小选区或稍后重试。'}}</p><button v-if="!auth.user" type="button" class="button-secondary" @click="loginForSelection">登录后实时{{selectionLabel}}</button></template><p class="reader-translation-source">{{sentenceAnswer?tools?.translation.source:phraseResult?.found?phraseResult.sourceNote:onlineTranslation.task.value?onlineTranslation.task.value.model==='baidu-general'?'本次 '+onlineTranslation.task.value.sourceCharacters+' 字符':'DeepSeek 机器'+selectionLabel+' · 与作文批改共用今日AI额度':selectionMode==='GRAMMAR'?'点击解析后使用 DeepSeek（需登录，计入今日AI额度）':'点击后先查询本地，未收录时使用百度翻译（需登录）'}}</p><p v-if="onlineTranslation.task.value && onlineTranslation.task.value.model!=='baidu-general'" class="reader-translation-source">{{onlineTranslation.task.value.budgetStatus==='RESERVED'?'本地额度预留中，不代表供应商已扣费':onlineTranslation.task.value.budgetStatus==='UNKNOWN'?'调用费用待确认，保留本地预留':onlineTranslation.task.value.budgetStatus==='RELEASED'?'本次未发送，已释放本地预留':'按已知用量结算；复用结果不会重复调用'}} · {{(onlineTranslation.task.value.estimatedCostMicrocny/1000000).toFixed(4)}}元</p></dialog>
</section></template>

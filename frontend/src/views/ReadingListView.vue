<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { readingCategories, readingArticles, readingHistory, readingRecommendation, type ArticleSummary } from '@/api/reading'
import { favoriteArticles } from '@/api/article-favorites'
import { sourceLabel, type Category } from '@/api/admin-articles'
import { errorMessage } from '@/api/identity'
import { useAuthStore } from '@/stores/auth'
import { useAuthDialogStore } from '@/stores/auth-dialog'
import {useRoute} from 'vue-router'
import ReadingRecords from '@/components/reading/ReadingRecords.vue'
import ArticleFavoriteButton from '@/components/reading/ArticleFavoriteButton.vue'
const records=ref<InstanceType<typeof ReadingRecords>>(),route=useRoute()
const auth = useAuthStore(), dialog = useAuthDialogStore()
const groups = ref<Category[]>([]), items = ref<ArticleSummary[]>([]), keyword = ref(''), category = ref(''), difficulty = ref(''), sort = ref('newest'), page = ref(1), total = ref(0), busy = ref(false), error = ref('')
const tab = ref<'all' | 'favorites' | 'history'>('all'), recommended = ref<ArticleSummary | null>(null), recommendationError = ref(''), categoryError = ref('')
const historyTimes = ref<Record<string, string>>({})
let controller: AbortController | undefined, generation = 0, disposed = false
function query() {
  const result = new URLSearchParams({ page: String(page.value), pageSize: tab.value === 'history' ? '10' : '6', sort: sort.value === 'recent' && tab.value !== 'history' ? 'newest' : sort.value })
  if (keyword.value.trim()) result.set('keyword', keyword.value.trim())
  if (category.value) result.set('categoryId', category.value)
  if (difficulty.value) result.set('difficulty', difficulty.value)
  return result
}
async function load(reset = false) {
  if (reset) page.value = 1
  controller?.abort(); const current = ++generation
  controller = new AbortController(); error.value = ''; items.value = []; total.value = 0
  if (tab.value !== 'all' && !auth.user) { busy.value = false; return }
  busy.value = true
  try {
    if (tab.value === 'history') {
      const result = await readingHistory(query(), controller.signal)
      if (current !== generation) return
      historyTimes.value = Object.fromEntries(result.items.map(i => [i.article.versionId, i.progress.updatedAt]))
      items.value = result.items.map(i => i.article); total.value = result.total
    } else {
      const result = await (tab.value === 'favorites' ? favoriteArticles(query(), controller.signal) : readingArticles(query(), controller.signal))
      if (current !== generation) return
      items.value = result.items; total.value = result.total
    }
  } catch (e) { if (current === generation && !(e instanceof DOMException && e.name === 'AbortError')) error.value = errorMessage(e) }
  finally { if (current === generation) busy.value = false }
}
async function loadRecommendation() {
  recommendationError.value = ''
  try { const result = await readingRecommendation(); if (!disposed) recommended.value = result.article }
  catch { if (!disposed) recommendationError.value = '每日精选暂不可用，仍可浏览下方文章。' }
}
async function loadCategories() {
  categoryError.value = ''
  try { const result = await readingCategories(); if (!disposed) groups.value = result }
  catch { if (!disposed) categoryError.value = '分类暂不可用，可以先搜索文章。' }
}
function clearFilters() { keyword.value = ''; category.value = ''; difficulty.value = ''; sort.value = tab.value === 'history' ? 'recent' : 'newest'; void load(true) }
function switchTab(value: typeof tab.value) { if (tab.value === value) return; const wasHistory = tab.value === 'history'; tab.value = value; if (value === 'history') sort.value = 'recent'; else if (wasHistory && sort.value === 'recent') sort.value = 'newest'; void load(true) }
function tabKey(event: KeyboardEvent, index: number) {
  const buttons = Array.from((event.currentTarget as HTMLElement).parentElement!.querySelectorAll<HTMLButtonElement>('[role=tab]'))
  const direction = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0
  const next = direction ? (index + direction + 3) % 3 : event.key === 'Home' ? 0 : event.key === 'End' ? 2 : -1
  if (next >= 0) { event.preventDefault(); buttons[next]?.click(); buttons[next]?.focus() }
}
function articlePath(item: ArticleSummary) { return `/reading/${item.id}${item.progress ? `?version=${item.versionId}&sentence=${item.progress.sentenceKey}` : ''}` }
function recentTime(item: ArticleSummary) { const value = historyTimes.value[item.versionId]; return value ? new Date(value).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '' }
watch(() => auth.user?.id, () => { void load(true); void loadRecommendation() })
watch(()=>route.query.records,value=>{if(value==='1')records.value?.show()})
onMounted(() => { if(route.query.records==='1')records.value?.show(); void loadCategories(); void loadRecommendation(); void load() })
onBeforeUnmount(() => { disposed = true; generation++; controller?.abort() })
</script>
<template><section class="reading-page reading-library"><ReadingRecords ref="records"/>
  <header class="reading-page-header"><div><h1>外刊精读</h1><p>读一篇文章，让英语成为日常的一部分。</p></div><span class="reading-editorial-note">原创练习 · B1–B2 · 来源明确</span></header>
  <section v-if="recommended" class="reading-featured" aria-label="每日精选">
    <div><span class="reading-featured-label">每日精选 <small>从一篇开始</small></span><h2>{{ recommended.title }}</h2><p>{{ recommended.titleZh }}</p><span class="reading-meta">{{ recommended.category.name }} · {{ recommended.difficulty }} · {{ recommended.wordCount }} 词 · 约 {{ recommended.estimatedMinutes }} 分钟</span></div>
    <div class="reading-featured-action"><p lang="en">{{ recommended.summary }}</p><RouterLink class="button-primary" :to="articlePath(recommended)">读这一篇 →</RouterLink></div>
  </section>
  <p v-if="recommendationError" class="reading-local-notice" role="status">{{ recommendationError }} <button type="button" class="text-button" @click="loadRecommendation">重试精选</button></p>
  <form class="reading-library-filters" @submit.prevent="load(true)">
    <div class="reading-search-row"><label class="reading-search"><span class="reading-sr-only">搜索文章</span><svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/></svg><input v-model="keyword" maxlength="80" placeholder="搜索英文标题、中文译名或关键词"></label>
    <select v-model="difficulty" aria-label="CEFR难度" @change="load(true)"><option value="">全部难度</option><option>B1</option><option>B2</option></select>
    <select v-model="sort" aria-label="文章排序" @change="load(true)"><option v-if="tab === 'history'" value="recent">最近阅读</option><option value="newest">最新发布</option><option value="short">篇幅从短到长</option><option value="long">篇幅从长到短</option></select>
    <button class="button-primary" :disabled="busy">搜索</button></div>
    <div class="reading-category-row" role="group" aria-label="文章分类"><button type="button" :aria-pressed="!category" @click="category = ''; load(true)">全部主题</button><button v-for="group in groups" :key="group.id" type="button" :aria-pressed="category === group.id" @click="category = group.id; load(true)">{{ group.name }}</button><button type="button" class="text-button reading-clear" @click="clearFilters">清空筛选</button></div>
    <p v-if="categoryError" class="reading-local-notice" role="status">{{ categoryError }} <button type="button" class="text-button" @click="loadCategories">重试分类</button></p>
  </form>
  <div class="reading-list-heading"><div class="reading-tabs" role="tablist" aria-label="文章列表"><button v-for="(item, index) in [{ id: 'all' as const, title: '全部文章' }, { id: 'favorites' as const, title: '我的收藏' }, { id: 'history' as const, title: '阅读历史' }]" :id="`reading-tab-${item.id}`" :key="item.id" type="button" role="tab" :aria-selected="tab === item.id" :tabindex="tab === item.id ? 0 : -1" aria-controls="reading-panel" @keydown="tabKey($event, index)" @click="switchTab(item.id)">{{ item.title }}</button></div><button type="button" class="reading-records-button" @click="records?.show($event)">学习记录</button><span class="reading-list-count">{{ busy ? '加载中…' : `共 ${total} 篇` }}</span></div>
  <div id="reading-panel" role="tabpanel" :aria-labelledby="`reading-tab-${tab}`" :aria-busy="busy">
    <div v-if="tab !== 'all' && !auth.user" class="reading-empty"><h2>把阅读留在自己的账户里</h2><p>登录后查看{{ tab === 'favorites' ? '收藏的文章' : '自己的阅读历史' }}。</p><button type="button" class="button-primary" @click="dialog.request()">登录后查看</button></div>
    <div v-else-if="error" class="reading-notice" role="alert"><p>{{ error }}</p><button class="button-secondary" @click="load()">重新加载</button></div>
    <p v-else-if="busy" class="reading-empty" role="status">正在寻找好文章…</p>
    <div v-else-if="!items.length" class="reading-empty"><h2>{{ tab === 'history' ? keyword || category || difficulty ? '没有找到匹配的阅读记录' : '还没有阅读记录' : keyword || category || difficulty ? '没有找到匹配的文章' : tab === 'favorites' ? '还没有收藏的文章' : '文章正在准备中' }}</h2><p>{{ tab === 'history' ? keyword || category || difficulty ? '试试其他条件，或清空筛选查看全部阅读记录。' : '从一篇感兴趣的文章开始吧。' : keyword || category || difficulty ? '试试更简短的关键词，或清空筛选条件。' : tab === 'favorites' ? '遇到想读的文章，点一下右上角的收藏图标。' : '文章发布后，会出现在这里。' }}</p></div>
    <template v-else><p v-if="tab === 'history'" class="reading-meta">继续原来的文章版本和位置，可使用上方条件筛选阅读记录。</p><div class="reading-card-grid"><article v-for="item in items" :key="item.versionId" class="reading-card">
      <ArticleFavoriteButton :article-id="item.id" :title="item.title" @changed="tab === 'favorites' && load()" />
      <div class="reading-card-top"><span class="reading-tag">{{ item.category.name }}</span><span class="reading-difficulty">{{ item.difficulty }}</span></div>
      <h2><RouterLink :to="articlePath(item)">{{ item.title }}</RouterLink></h2><p class="reading-card-zh">{{ item.titleZh }}</p><p class="reading-summary" lang="en">{{ item.summary }}</p>
      <div class="reading-card-meta">{{ sourceLabel(item.sourceType) }} · {{ item.wordCount }} 词 · 约 {{ item.estimatedMinutes }} 分钟</div>
      <footer><span class="reading-card-status" :class="{ 'is-reading': item.progress?.status === 'READING', 'is-completed': item.progress?.status === 'COMPLETED' }"><i></i>{{ item.progress?.status === 'COMPLETED' ? '已完成' : item.progress ? '阅读中' : '未开始' }}</span><RouterLink :to="articlePath(item)">{{ item.progress?.status === 'COMPLETED' ? '再次阅读' : item.progress ? '继续阅读' : '开始阅读' }} →</RouterLink></footer>
      <p v-if="tab === 'history'" class="reading-history-time">最近阅读 · {{ recentTime(item) }}</p>
    </article></div></template>
  </div>
  <nav v-if="total > (tab === 'history' ? 10 : 6)" class="reading-pagination" aria-label="阅读列表分页"><button class="button-secondary" :disabled="busy || page <= 1" @click="page--; load()">上一页</button><span>第 {{ page }} 页</span><button class="button-secondary" :disabled="busy || page * (tab === 'history' ? 10 : 6) >= total" @click="page++; load()">下一页</button></nav>
</section></template>

<style scoped>.reading-records-button{border:1px solid var(--border,var(--line));border-radius:18px;background:transparent;color:var(--text-secondary,#777080);padding:7px 12px;font:inherit;font-size:13px;white-space:nowrap;margin-left:12px;cursor:pointer}.reading-list-count{margin-left:auto}@media(max-width:600px){.reading-list-heading{flex-wrap:wrap;gap:8px}.reading-records-button{margin-left:0}}</style>

<script setup lang="ts">
import type { ReadingTools, ReadingKeyword, ArticleWord } from '@/api/reading-tools'
defineProps<{ tools?: ReadingTools; busy: boolean; error: string; words: ArticleWord[]; total: number; page: number; wordsBusy: boolean; wordsError: string; authenticated: boolean }>()
const emit = defineEmits<{ retryTools: []; retryWords: []; page: [value: number]; keyword: [value: ReadingKeyword]; word: [value: ArticleWord]; login: [] }>()
</script>
<template><aside class="reader-rail">
 <section class="reader-rail-card reader-related" :aria-busy="busy"><div class="reader-rail-heading"><h2>更多文章</h2><span v-if="busy && tools" class="reader-refresh-status" role="status">正在更新…</span><button v-else-if="error && tools" type="button" class="text-button reader-refresh-status" :title="error" aria-label="重新加载阅读工具" @click="emit('retryTools')">刷新失败 · 重试</button></div><p v-if="busy && !tools" role="status">正在加载…</p><div v-else-if="error && !tools" role="status"><p>{{ error }}</p><button type="button" class="text-button" @click="emit('retryTools')">重新加载阅读工具</button></div><template v-else><RouterLink v-for="article in tools?.related ?? []" :key="article.id" :to="`/reading/${article.id}`"><strong>{{ article.titleZh || article.title }}</strong><small>{{ article.sourceType === 'ORIGINAL' ? 'Lingua Loop 原创' : article.author }} · {{ article.wordCount }} 词 · {{ article.difficulty }}</small></RouterLink><p v-if="!tools?.related.length" class="reader-rail-note">暂无其他已发布文章。</p></template></section>
 <section id="reader-vocabulary" class="reader-rail-card reader-vocabulary" aria-labelledby="reader-vocabulary-heading"><h2 id="reader-vocabulary-heading">本篇词汇</h2><p class="reader-rail-note">把词义和读到它的那句话，一起留下。</p>
 <h3>精选重点词 <small>{{ tools?.keywords.length ?? 0 }} 词</small></h3><p v-if="!tools?.keywords.length" class="reader-rail-note">本篇暂无需要优先学习的重点词，仍可点击正文查词。</p>
 <div class="reader-keyword-list"><div v-for="keyword in tools?.keywords ?? []" :key="keyword.entryId" class="reader-keyword-row"><span class="reader-word-initial">{{ keyword.lemma[0]?.toUpperCase() }}</span><button type="button" :aria-label="`查看重点词：${keyword.lemma}`" @click="emit('keyword', keyword)"><strong>{{ keyword.lemma }}</strong><small>{{ keyword.senses[0]?.partOfSpeech }} {{ keyword.senses[0]?.definitionZh }}</small></button><span v-if="words.some(i => i.word.entryId === keyword.entryId)" class="reader-saved-badge">已存</span></div></div>
 <p class="reader-rail-note">{{ tools?.keywordSource ? '来自 '+tools.keywordSource+'，仅高亮本篇精选词。' : '文章精选词；登录后按当前学习来源匹配。' }}</p>
 <h3>已保存生词 <small class="article-word-total">{{ total }} 个</small></h3><p v-if="!authenticated" class="reader-rail-note">登录后查看本篇保存的生词。<button type="button" class="text-button" @click="emit('login')">登录后查看</button></p><p v-else-if="wordsBusy" role="status">正在加载生词…</p><div v-else-if="wordsError" role="alert"><p>{{ wordsError }}</p><button type="button" class="text-button" @click="emit('retryWords')">重试本篇生词</button></div><template v-else><p v-if="!words.length" class="reader-rail-note">还没有保存本篇生词，点击正文中的单词开始积累。</p><button v-for="item in words" :key="item.word.id" type="button" class="reader-saved-word" @click="emit('word', item)"><strong>{{ item.word.lemma }}</strong><small>{{ item.word.senses[0]?.partOfSpeech }} {{ item.word.senses[0]?.definitionZh }}</small><small>原文：{{ item.source.surfaceForm }}</small></button><nav v-if="total > 10" class="reader-word-pages" aria-label="本篇生词分页"><button type="button" :disabled="wordsBusy || page <= 1" @click="emit('page', page - 1)">上一页</button><span>{{ page }}</span><button type="button" :disabled="wordsBusy || page * 10 >= total" @click="emit('page', page + 1)">下一页</button></nav></template>
 </section>
</aside></template>

<style scoped>
.reader-rail-heading{display:flex;align-items:center;justify-content:space-between;gap:8px;min-height:22px;margin-bottom:12px}
.reader-rail-heading h2{margin:0;flex-shrink:0}
.reader-refresh-status{font-size:10px;line-height:18px;color:var(--muted);white-space:nowrap}
.reader-rail-heading button.reader-refresh-status{min-height:0;padding:0;border:0;background:transparent;cursor:pointer;color:var(--accent-dark)}
</style>

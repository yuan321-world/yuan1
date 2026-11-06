<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getArticle, previewArticle, sourceLabel, type ArticleVersion } from '@/api/admin-articles'
import { errorMessage } from '@/api/identity'
import ArticleBody from '@/components/reading/ArticleBody.vue'
const route = useRoute(), article = ref<ArticleVersion>(), error = ref(''), busy = ref(false)
async function load() {
  busy.value = true; error.value = ''
  try { const id = String(route.params.id); const version = typeof route.query.version === 'string' ? route.query.version : (await getArticle(id)).draftVersionId; article.value = await previewArticle(id, version) }
  catch (e) { error.value = errorMessage(e) } finally { busy.value = false }
}
onMounted(load)
</script>
<template><section class="reading-page"><RouterLink class="reading-back" :to="`/admin/articles/${route.params.id}/edit`">← 返回编辑</RouterLink><p class="reading-meta">管理预览 · 不代表文章已发布</p><div v-if="error" class="reading-notice" role="alert"><p>{{ error }}</p><button class="button-secondary" @click="load">重新加载</button></div><p v-else-if="busy" role="status">正在加载预览…</p><article v-else-if="article" class="reading-sheet"><header class="article-heading"><span class="reading-tag">{{ article.category.name }} · {{ article.difficulty }}</span><h1>{{ article.title }}</h1><p>{{ article.titleZh }}</p><p class="reading-meta">{{ article.author }} · {{ sourceLabel(article.sourceType) }} · {{ article.wordCount }} 词 · 约 {{ article.estimatedMinutes }} 分钟（估算）</p><a v-if="article.sourceUrl" :href="article.sourceUrl" target="_blank" rel="noopener noreferrer">原始来源</a><p class="reading-meta">{{ article.usageNote }}</p></header><ArticleBody :content="article.content" /></article></section></template>

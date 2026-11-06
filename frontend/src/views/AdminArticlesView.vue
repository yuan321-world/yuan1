<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { categories, listArticles, type Category, type AdminArticle } from '@/api/admin-articles'
import { errorMessage } from '@/api/identity'
import ArticleImportPanel from '@/components/reading/ArticleImportPanel.vue'
const items = ref<AdminArticle[]>([]), groups = ref<Category[]>([])
const keyword = ref(''), category = ref(''), status = ref(''), page = ref(1), total = ref(0), busy = ref(false), error = ref('')
let controller: AbortController | undefined
let generation = 0
async function load(reset = false) {
  if (reset) page.value = 1
  controller?.abort(); controller = new AbortController(); const current = ++generation
  busy.value = true; error.value = ''
  try {
    const query = new URLSearchParams({ page: String(page.value), pageSize: '20' })
    if (keyword.value.trim()) query.set('keyword', keyword.value.trim())
    if (category.value) query.set('categoryId', category.value)
    if (status.value) query.set('status', status.value)
    const result = await listArticles(query, controller.signal)
    if (current !== generation) return
    items.value = result.items; total.value = result.total
  } catch (e) { if (current === generation && !(e instanceof DOMException && e.name === 'AbortError')) error.value = errorMessage(e) }
  finally { if (current === generation) busy.value = false }
}
onMounted(async () => { try { groups.value = await categories() } catch (e) { error.value = errorMessage(e) }; await load() })
onBeforeUnmount(() => { generation++; controller?.abort() })
</script>
<template>
  <section class="reading-page">
    <header class="reading-page-header"><div><h1>文章管理</h1><p>从一份草稿开始，校对之后再发布。</p></div><RouterLink class="button-primary" to="/admin/articles/new">创建文章</RouterLink></header>
    <ArticleImportPanel @imported="load(true)" />
    <form class="reading-filters" @submit.prevent="load(true)">
      <label>标题搜索<input v-model="keyword" maxlength="80" placeholder="查找英文标题"></label>
      <label>分类<select v-model="category"><option value="">全部分类</option><option v-for="item in groups" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
      <label>状态<select v-model="status"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option><option value="UNLISTED">已下架</option></select></label>
      <button class="button-secondary" :disabled="busy">筛选</button>
    </form>
    <div v-if="error" class="reading-notice" role="alert"><p>{{ error }}</p><button class="button-secondary" @click="load()">重新加载</button></div>
    <p v-else-if="busy" role="status" class="reading-empty">正在加载文章…</p>
    <div v-else-if="!items.length" class="reading-empty"><h2>{{ keyword || category || status ? '没有匹配的文章' : '还没有文章' }}</h2><p>创建草稿后，可以在这里编辑和预览。</p></div>
    <div v-else class="admin-article-list"><article v-for="item in items" :key="item.id" class="admin-article-row">
      <div><span class="reading-tag">{{ item.status === 'DRAFT' ? '草稿' : item.status === 'PUBLISHED' ? '已发布' : '已下架' }}</span><h2>{{ item.draft.title }}</h2><p>{{ item.draft.titleZh }}</p><small>{{ item.draft.category.name }} · {{ item.draft.difficulty }} · {{ item.draft.wordCount }} 词 · 草稿版本 {{ item.draft.versionNo }}</small></div>
      <div class="reading-actions"><RouterLink class="button-secondary" :to="{path:`/admin/articles/${item.id}/quiz`,query:{version:item.publishedVersionId||item.draftVersionId}}">管理题目</RouterLink><RouterLink class="button-secondary" :to="{path:`/admin/articles/${item.id}/writing`,query:{version:item.publishedVersionId||item.draftVersionId}}">写作素材</RouterLink><RouterLink class="button-secondary" :to="`/admin/articles/${item.id}/preview?version=${item.draftVersionId}`">预览</RouterLink><RouterLink class="button-secondary" :to="`/admin/articles/${item.id}/edit`">编辑草稿</RouterLink></div>
    </article></div>
    <nav v-if="total > 20" class="reading-pagination" aria-label="文章分页"><button class="button-secondary" :disabled="busy || page <= 1" @click="page--; load()">上一页</button><span>第 {{ page }} 页</span><button class="button-secondary" :disabled="busy || page * 20 >= total" @click="page++; load()">下一页</button></nav>
  </section>
</template>

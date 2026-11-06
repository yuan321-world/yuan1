<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAuthDialogStore } from '@/stores/auth-dialog'
import { useArticleFavorites } from '@/stores/article-favorites'
const props = defineProps<{ articleId: string; title: string }>()
const emit = defineEmits<{ changed: [value: boolean] }>()
const auth = useAuthStore(), dialog = useAuthDialogStore(), favorites = useArticleFavorites(), route = useRoute()
function load() { favorites.load([props.articleId]) }
onMounted(load); watch(() => props.articleId, load)
watch(() => favorites.values[props.articleId], (value, previous) => { if (value !== undefined && previous !== undefined && value !== previous) emit('changed', value) })
function toggle() {
  if (!auth.user) { dialog.request('', { articleId: props.articleId, route: route.fullPath }); return }
  if (favorites.values[props.articleId] === undefined) { load(); return }
  void favorites.set(props.articleId, !favorites.values[props.articleId])
}
</script>
<template><div class="article-favorite-control">
  <button type="button" class="article-favorite-button" :class="{ 'is-favorite': favorites.values[articleId] }" :aria-label="`${favorites.values[articleId] ? '取消收藏' : '收藏文章'}：${title}`" :aria-pressed="Boolean(auth.user && favorites.values[articleId])" :disabled="favorites.busy[articleId] || Boolean(auth.user && favorites.values[articleId] === undefined && !favorites.errors[articleId])" @click="toggle">
    <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M6 4h12v17l-6-4-6 4z"/></svg>
  </button><p v-if="favorites.errors[articleId]" class="article-favorite-error" role="alert">{{ favorites.errors[articleId] }}<button v-if="favorites.values[articleId] === undefined" type="button" class="text-button" @click="load">重试读取状态</button><button v-else type="button" class="text-button" @click="favorites.set(articleId, !favorites.values[articleId])">重试操作</button></p>
</div></template>

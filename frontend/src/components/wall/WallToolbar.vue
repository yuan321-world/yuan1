<script setup lang="ts">
import { useWallStore } from '@/stores/wall'
const store=useWallStore()
defineEmits<{compose:[];mine:[];locate:[];info:[]}>()
</script>
<template><div class="wall-dock"><form class="wall-dock-search" @submit.prevent="store.load(true)"><input v-model="store.keyword" aria-label="在留言墙里搜索" placeholder="搜索留言或昵称"><button aria-label="搜索留言">⌕</button></form><button type="button" @click="$emit('locate')">⌖ <span>定位</span></button><button type="button" :aria-pressed="store.mine" @click="$emit('mine')">☆ 我的</button><button class="wall-help" type="button" @click="$emit('info')">说明</button><button type="button" class="wall-add button-primary" aria-label="写留言" @click="$emit('compose')">＋</button><slot /></div></template>

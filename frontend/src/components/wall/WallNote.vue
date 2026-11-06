<script setup lang="ts">
import type { WallMessage } from '@/wall/types'
import { categories,noteColors } from '@/wall/types'
import WallFormattedText from './WallFormattedText.vue'
defineProps<{note:WallMessage;liking?:boolean}>()
defineEmits<{open:[note:WallMessage];like:[note:WallMessage]}>()
</script>
<template>
 <article class="wall-note" :class="{announcement:note.kind==='ANNOUNCEMENT',hidden:note.status==='HIDDEN'}" :style="{background:noteColors[note.color]??noteColors.yellow}" :data-id="note.id">
  <div class="wall-note-top"><span class="wall-tag">{{categories[note.category]??'学习交流'}}</span><span v-if="note.author.isAdmin" class="wall-admin-badge">管理员</span><span v-else-if="note.owned">我的便签</span></div>
  <h3 v-if="note.title" class="wall-announcement-title">{{note.title}}</h3><p class="wall-note-text"><WallFormattedText :text="note.text" :announcement="note.kind==='ANNOUNCEMENT'" /></p>
  <p class="wall-note-meta">{{note.author.displayName}} · {{new Date(note.createdAt).toLocaleDateString('zh-CN')}}<span v-if="note.editedByAdmin"> · 管理员已编辑</span></p>
  <p v-if="note.status==='HIDDEN'" class="wall-hidden-reason">已隐藏 · {{note.authorReason}}</p>
  <div class="wall-note-actions"><div class="wall-note-counts"><button type="button" class="wall-note-like" :aria-label="note.liked?'取消点赞':'点赞'" :aria-pressed="note.liked" :disabled="liking||note.status==='HIDDEN'" @click.stop="$emit('like',note)">{{note.liked?'♥':'♡'}} {{note.likes}}</button><span>· 讨论 {{note.commentCount}}</span></div><button type="button" @click="$emit('open',note)">展开 ↗</button></div>
 </article>
</template>

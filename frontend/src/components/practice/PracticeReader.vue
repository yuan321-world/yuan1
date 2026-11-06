<script setup lang="ts">
import {computed} from 'vue'
import type {ArticleVersion} from '@/api/admin-articles'
import PracticeSelectionArea from './PracticeSelectionArea.vue'
const props=defineProps<{article:ArticleVersion}>()
const paragraphs=computed(()=>props.article.content.paragraphs)
</script>
<template><article class="practice-reader"><PracticeSelectionArea :article="article"/><h2 lang="en">{{article.title}}</h2><p class="practice-hint">选中单词可查词 · 文章版本 {{article.versionNo}}</p><div class="practice-material"><p v-for="(p,i) in paragraphs" :key="p.key" lang="en"><small>第 {{i+1}} 段</small><span data-practice-source>{{p.sentences.map(s=>s.text).join(' ')}}</span></p></div></article></template>


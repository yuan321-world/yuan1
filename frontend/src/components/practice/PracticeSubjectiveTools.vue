<script setup lang="ts">
import {ref,watch} from 'vue'
import type {ReviewSource} from '@/api/practice-review'
import PracticeReviewPanel from './PracticeReviewPanel.vue'
import '@/styles/practice-review.css'
const props=defineProps<{source?:ReviewSource;writing:boolean;wordCount:number;reference?:string;notes?:string}>()
const opened=ref<'reference'|'notes'|null>(null)
const toggle=(tab:'reference'|'notes')=>{opened.value=opened.value===tab?null:tab}
watch(()=>`${props.source?.sourceType}:${props.source?.attemptId}:${props.source?.slot}`,()=>{opened.value=null})
</script>
<template>
 <div class="subjective-tools">
  <PracticeReviewPanel v-if="source" :source="source" compact>
   <template #tools><button :aria-expanded="opened==='reference'" @click="toggle('reference')">{{writing?'参考范文':'参考译文'}}</button><button :aria-expanded="opened==='notes'" @click="toggle('notes')">解析</button></template>
   <template #word-count><span class="subjective-tools-count">{{writing?'我的作文':'我的译文'}} {{wordCount}} 词</span></template>
   <template #reference><div v-if="opened" class="subjective-tools-reference"><h3>{{opened==='reference'?(writing?'参考范文':'参考译文'):'对照要点'}}</h3><p :class="{'review-english':opened==='reference'}">{{opened==='reference'?reference:notes}}</p><small>参考表达并非唯一答案，不代表官方评分。</small></div></template>
  </PracticeReviewPanel>
  <div v-else class="subjective-tools-row"><button disabled title="提交后查看">{{writing?'参考范文':'参考译文'}}</button><button disabled title="提交后查看">解析</button><button disabled class="subjective-ai-button" title="提交后可申请批改">AI 批改</button><span class="subjective-tools-count">{{writing?'我的作文':'我的译文'}} {{wordCount}} 词</span><small class="subjective-tools-hint">提交后解锁</small></div>
 </div>
</template>

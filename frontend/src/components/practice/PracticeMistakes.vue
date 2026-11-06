<script setup lang="ts">
import type {Mistake} from '@/api/practice'
defineProps<{items:Mistake[];busy:boolean}>();const emit=defineEmits<{retry:[source:string];view:[source:string]}>()
</script>
<template><div class="practice-rows"><article v-for="m in items" :key="m.questionId" class="practice-card"><span class="practice-tag">{{m.status==='PENDING'?'待订正':'已订正'}} · 错误 {{m.wrongCount}} 次</span><h3>{{m.stem}}</h3><p class="practice-hint">{{m.title}} · 正确答案 {{m.correctOption}}</p><p class="practice-mistake-explanation">{{m.explanation}}</p><div class="practice-actions"><button :disabled="busy" @click="emit('view',m.sourceAttemptId)">查看原练习</button><button :disabled="busy" @click="emit('retry',m.sourceAttemptId)">{{m.readingType==='LISTENING'?'重练整套听力':m.readingType==='MATCH'||m.readingType==='CLOZE'?'重练本组（含错题）':'重做该轮错题'}}</button></div></article></div></template>

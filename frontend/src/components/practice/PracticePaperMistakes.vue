<script setup lang="ts">
import type {PaperMistake} from '@/api/practice-paper'
defineProps<{items:PaperMistake[];busy:boolean}>()
const emit=defineEmits<{review:[item:PaperMistake];view:[item:PaperMistake]}>()
const labels:Record<string,string>={LISTENING:'听力',CLOZE:'选词填空',MATCH:'长篇匹配',DETAILED:'仔细阅读'}
const scope=(type:string)=>type==='LISTENING'?'重练整套听力':type==='CLOZE'?'重练本组（含错空）':type==='MATCH'?'重练本组（含错题）':'重做错题'
</script>
<template><div class="practice-rows paper-mistake-rows"><article v-for="item in items" :key="item.setVersionId+':'+item.questionId" class="practice-card paper-mistake-card">
 <span class="practice-tag">{{item.status==='PENDING'?'待订正':'已订正'}} · 错误 {{item.wrongCount}} 次</span>
 <h3>第{{item.originalNumber}}题 · {{labels[item.readingType]}}</h3><p>{{item.stem.replace(/^\d+[.、]\s*/,'')}}</p>
 <p class="practice-hint">{{item.paperTitle}}</p><p class="practice-hint">最近作答：{{item.submittedChoice||'未答'}} · 参考：{{item.correctOption}}</p>
 <p class="practice-mistake-explanation">{{item.explanation}}</p>
 <div class="practice-actions"><button @click="emit('view',item)">查看原卷</button><button :disabled="busy||item.status==='RESOLVED'" @click="emit('review',item)">{{scope(item.readingType)}}</button></div>
</article></div></template>

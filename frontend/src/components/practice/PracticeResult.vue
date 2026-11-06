<script setup lang="ts">
import type {Attempt} from '@/api/practice'
const props=defineProps<{attempt:Attempt;busy:boolean}>();const emit=defineEmits<{retry:[]}>()
function number(id:string,i:number){return props.attempt.reviewSource?.originalNumbers[id]??i+1}
function stem(q:Attempt['questions'][number]){return props.attempt.reviewSource?.originalNumbers[q.id]?q.stem.replace(/^\d+[.、]\s*/,''):q.stem}
</script>
<template><section class="practice-panel"><h2>本次练习结果</h2><p>正确 {{attempt.correctCount}} / {{attempt.total}} · 正确率 {{Math.round((attempt.correctCount||0)/attempt.total*100)}}%</p><p class="practice-hint">仅表示本次练习表现，不是考试标准分。旧轮次和发布版本保持独立。</p><button :disabled="busy||attempt.correctCount===attempt.total" @click="emit('retry')">重做本轮错题</button><article v-for="(q,i) in attempt.questions" :key="q.id" class="practice-preview-question"><h3>{{number(q.id,i)}}. {{stem(q)}}</h3><p v-for="(option,n) in q.options" :key="n">{{'ABCD'[n]}}. {{option}}</p><p class="practice-tag">你的答案：{{attempt.answers.find(a=>a.questionId===q.id)?.submittedChoice||'未答'}} · {{attempt.answers.find(a=>a.questionId===q.id)?.correct?'正确':'错误'}}</p><p class="practice-explanation">正确答案：{{attempt.answers.find(a=>a.questionId===q.id)?.correctOption}}<br>{{attempt.answers.find(a=>a.questionId===q.id)?.explanation}}</p></article></section></template>

<script setup lang="ts">
import {computed} from 'vue'
import {objectiveScore} from '@/practice/scoring'
const props=defineProps<{attempt:{total:number;correctCount?:number}}>()
const score=computed(()=>objectiveScore(props.attempt))
</script>
<template><section class="practice-score-summary" aria-label="本次练习评分"><div><span>练习参考分</span><strong>{{score===null?'—':score}}<small> / 100</small></strong></div><p>正确 {{attempt.correctCount??'—'}} / {{attempt.total}} · 正确率 {{score===null?'—':score+'%'}}<small>按本次题目正确比例计分，未答题计零分；非考试标准分。</small></p></section></template>
<style scoped>
.practice-score-summary{display:flex;align-items:center;gap:24px;flex-wrap:wrap;padding:18px 22px;margin:14px 0;border:1px solid var(--border-color,var(--line));border-radius:18px;background:var(--surface,#faf8ef)}.practice-score-summary>div{display:grid;gap:4px;font-size:13px;color:var(--muted)}.practice-score-summary strong{font-size:36px;line-height:1.2;color:var(--primary,#4d663e)}.practice-score-summary strong small{font-size:16px;font-weight:400}.practice-score-summary p{margin:0;font-size:14px}.practice-score-summary p small{display:block;color:var(--muted);font-size:12px;margin-top:5px}@media(max-width:480px){.practice-score-summary{gap:12px;padding:14px}.practice-score-summary strong{font-size:30px}}
</style>

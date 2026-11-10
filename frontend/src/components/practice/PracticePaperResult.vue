<script setup lang="ts">
import {computed} from 'vue'
import type {PaperAttempt} from '@/api/practice-paper'
import {slotLabels} from '@/api/practice-paper'
import type {ReviewSource} from '@/api/practice-review'
import {usePracticeReview} from '@/stores/practice-review'
import {useAutomaticPracticeReview} from '@/stores/practice-auto-review'
import {paperScore,paperWeights} from '@/practice/scoring'
const props=defineProps<{attempt:PaperAttempt}>();defineEmits<{review:[];restart:[];mistakes:[]}>()
const reviews=usePracticeReview(),automatic=useAutomaticPracticeReview()
const source=(slot:string):ReviewSource=>({sourceType:'PAPER',attemptId:props.attempt.id,slot:slot as ReviewSource['slot']})
const record=(slot:string)=>reviews.state(source(slot)).records.find(r=>r.status==='SUCCEEDED'&&r.result)
const scores=computed(()=>paperScore(props.attempt,Object.fromEntries(['WRITING','TRANSLATION'].flatMap(slot=>{const r=record(slot);return r?.result?[[slot,r.result.score]]:[]}))))
const cetReferenceScore = computed(() => scores.value.total === null ? null : Math.round(scores.value.total * 710 / 100))
function status(slot:string){const state=reviews.state(source(slot)),job=automatic.job(source(slot));if(record(slot))return 'AI参考评分';if(state.records.some(r=>r.status==='RUNNING'))return '正在评分';if(state.records.some(r=>r.status==='QUEUED'))return '评分排队中';if(job?.status==='STOPPED')return job.message||'评分未完成';if(state.error)return state.error;return '等待自动评分'}
const active=(slot:string)=>reviews.state(source(slot)).records.some(r=>r.status==='QUEUED'||r.status==='RUNNING')
const retryable=(slot:string)=>!record(slot)&&!active(slot)&&automatic.job(source(slot))?.status==='STOPPED'
const failed=computed(()=>props.attempt.components.some(c=>!c.objective&&c.text?.trim()&&retryable(c.slot)))
</script>
<template>
<section class="paper-panel paper-scored-result">
 <h1>本次整卷已完成</h1>
 <div class="paper-score-top">
  <div class="paper-score-values">
   <div class="paper-score-percent">
    <span>整卷练习参考分</span>
    <strong :class="{ 'is-pending': scores.total === null }">{{ scores.total ?? (failed ? '评分未完成' : '评分中') }}<small> / 100</small></strong>
   </div>
   <div class="paper-score-cet">
    <span>四六级参考分</span>
    <strong>{{ cetReferenceScore ?? '—' }}<small> / 710</small></strong>
   </div>
  </div>
  <p>客观题 {{ scores.objective ?? '—' }} / 70
   <small>{{ scores.total === null ? '写作、翻译评分完成后汇总，可在下方查看进度或重试。' : '710 分制参考分按本卷百分制成绩等比例换算。' }}</small>
  </p>
 </div>
 <div v-for="c in attempt.components" :key="c.slot" class="paper-result-row"><div class="paper-result-name"><strong>{{slotLabels[c.slot]}}</strong><small v-if="c.objective">正确 {{c.objective.correctCount??'—'}} / {{c.objective.total}} · 未答 {{c.objective.answers.filter(a=>!a.submittedChoice).length}} 题</small><small v-else>{{c.wordCount}}词 · {{!c.text?.trim()?'未作答，计零分':status(c.slot)}}</small></div><div class="paper-result-score"><strong>{{scores.parts[c.slot]===null?'待评分':scores.parts[c.slot]??'—'}}<small> / {{paperWeights[c.slot]}}</small></strong><button v-if="!c.objective&&c.text?.trim()&&retryable(c.slot)" :disabled="reviews.state(source(c.slot)).requesting" @click="automatic.retry(source(c.slot))">重试评分</button></div></div>
 <details class="paper-score-rule"><summary>参考分计算说明</summary><p>听力35分、选词填空5分、长篇匹配10分、两篇仔细阅读各10分、写作15分、翻译15分。客观题按各部分正确比例计分，未答题计零分。写作、翻译使用AI学习参考分，批改失败时保留已出成绩，不生成总分。</p><p>710 分制参考分等于百分制总分乘以 7.1，四舍五入至整数。正式考试的报道分按试卷等值和常模转换计算。<a href="https://cet.neea.edu.cn/html1/folder/19081/5124-1.htm" target="_blank" rel="noopener noreferrer">查看官方计分说明</a></p></details>
 <div class="paper-actions"><button class="paper-primary" @click="$emit('review')">查看作答与参考</button><button v-if="attempt.components.some(c=>c.objective&&(c.objective.correctCount||0)<c.objective.total)" @click="$emit('mistakes')">复习本卷错题</button><button @click="$emit('restart')">重新练习（保留本次记录）</button></div></section></template>
<style scoped>
.paper-score-top {
  margin: 16px 0;
  padding: 18px 20px;
  border-radius: 16px;
  background: var(--primary-soft, #edf1e4);
}
.paper-score-values {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.paper-score-values > div {
  display: grid;
  align-content: start;
  gap: 6px;
  font-size: 13px;
}
.paper-score-cet {
  padding-inline-start: 14px;
  border-inline-start: 1px solid var(--line);
}
.paper-score-values strong {
  font-size: clamp(24px, 4vw, 34px);
  line-height: 1.2;
  color: var(--primary, #4d663e);
  font-variant-numeric: tabular-nums;
}
.paper-score-values strong.is-pending { font-size: 17px; }
.paper-score-values strong small { font-size: 14px; font-weight: 400; white-space: nowrap; }
.paper-score-top p {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 14px;
  margin: 12px 0 0;
  font-size: 14px;
}
.paper-score-top p small { color: var(--muted); font-size: 12px; }
.paper-score-rule a { color: var(--accent-dark); text-decoration: underline; }
.paper-scored-result .paper-result-row{padding:14px 0;gap:14px;align-items:center}.paper-result-name{display:grid;gap:5px;min-width:0}.paper-result-name>small{font-size:12px;color:var(--muted);overflow-wrap:anywhere}.paper-result-score{display:flex;align-items:center;justify-content:flex-end;gap:10px;flex-wrap:wrap;text-align:right;flex-shrink:0}.paper-result-score>strong{font-size:20px;color:var(--primary,#4d663e)}.paper-result-score small{font-size:12px;font-weight:400;color:var(--muted)}.paper-result-score button{font-size:12px;min-height:32px;padding:5px 9px}.paper-score-rule{font-size:12px;color:var(--muted);margin:14px 0}.paper-score-rule summary{cursor:pointer}.paper-score-rule p{line-height:1.7}@media(max-width:480px){.paper-score-top{padding:14px}.paper-scored-result .paper-result-row{align-items:start}.paper-result-score{max-width:110px}.paper-result-score>strong{font-size:17px}}
</style>

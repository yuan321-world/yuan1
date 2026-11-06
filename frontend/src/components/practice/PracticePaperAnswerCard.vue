<script setup lang="ts">
import {computed} from 'vue'
import type {ComponentAttempt,PaperResponse} from '@/api/practice-paper'
import {slots,slotLabels} from '@/api/practice-paper'

const props=defineProps<{components:ComponentAttempt[];responses:PaperResponse[];activeSlot:string;currentQuestionId:string|null;disabled:boolean;done:boolean}>()
const emit=defineEmits<{navigate:[slot:string,id?:string];submit:[]}>()
const ordered=computed(()=>slots.flatMap(slot=>props.components.filter(c=>c.slot===slot)))
const label=(slot:string)=>slot==='LISTENING'?'听力':slotLabels[slot]||slot
const number=(slot:string,index:number)=>({LISTENING:1,CLOZE:26,MATCH:36,DETAILED_1:46,DETAILED_2:51}[slot]||1)+index
const response=(slot:string)=>props.responses.find(r=>r.slot===slot)
const answered=(c:ComponentAttempt)=>c.objective?c.objective.questions.filter(q=>!!response(c.slot)?.choices[q.id]).length:response(c.slot)?.text.trim()?1:0
const total=(c:ComponentAttempt)=>c.objective?.questions.length??1
const answeredTotal=computed(()=>ordered.value.reduce((sum,c)=>sum+answered(c),0))
const totalCount=computed(()=>ordered.value.reduce((sum,c)=>sum+total(c),0))
</script>

<template>
 <section class="paper-answer-card" aria-label="整卷答题卡">
  <header class="paper-card-heading"><h2>题目</h2><span>已答 {{answeredTotal}} / {{totalCount}}</span></header>
  <nav class="paper-card-sections" aria-label="整卷部分">
   <button v-for="(c,index) in ordered" :key="c.slot" :aria-label="label(c.slot)" :aria-pressed="activeSlot===c.slot" @click="emit('navigate',c.slot)"><span>{{index+1}}</span><strong>{{label(c.slot)}}</strong><small>{{answered(c)}}/{{total(c)}}</small></button>
  </nav>
  <nav class="paper-card-numbers" aria-label="全部题号">
   <div v-for="c in ordered" :key="c.slot" class="paper-card-number-group">
    <p>{{label(c.slot)}}</p>
    <div v-if="c.objective" class="paper-numbers">
     <button v-for="(q,index) in c.objective.questions" :key="q.id" :aria-label="'第'+number(c.slot,index)+'题'" :aria-pressed="activeSlot===c.slot&&currentQuestionId===q.id" :class="{answered:!!response(c.slot)?.choices[q.id]}" @click="emit('navigate',c.slot,q.id)">{{number(c.slot,index)}}</button>
    </div>
    <button v-else class="paper-card-subjective" :aria-label="'定位'+label(c.slot)" :class="{answered:!!response(c.slot)?.text.trim()}" @click="emit('navigate',c.slot)">{{label(c.slot)}} {{c.slot==='WRITING'?'W':'T'}}</button>
   </div>
  </nav>
  <button class="paper-primary" :disabled="disabled" @click="emit('submit')">{{done?'查看交卷结果':'交卷并查看结果'}}</button>
  <p class="paper-card-footnote">写作与翻译保留原文，不计客观正确数。</p>
 </section>
</template>

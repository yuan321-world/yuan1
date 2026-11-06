<script setup lang="ts">
import type {PracticeQuestionInput} from '@/api/practice'
import {questionProblems} from '@/practice/admin-draft'
defineProps<{questions:PracticeQuestionInput[];wordBank:string[];reviewed:string[];busy:boolean;index:number}>()
const emit=defineEmits<{bank:[index:number,value:string];change:[key:string];review:[key:string,checked:boolean];navigate:[index:number]}>()
</script>
<template><section class="practice-panel cloze-admin-editor">
 <h2 id="wordBank" tabindex="-1">共享词库 · A–O</h2><p class="practice-hint">15词中选10词，每个词最多使用一次。修改词库后需重新校对全部空位。</p>
 <fieldset :disabled="busy" class="cloze-admin-bank"><label v-for="(_,n) in wordBank" :key="n">{{String.fromCharCode(65+n)}}<input :aria-label="'词库 '+String.fromCharCode(65+n)" :value="wordBank[n]" maxlength="100" @input="emit('bank',n,($event.target as HTMLInputElement).value)"></label></fieldset>
 <h2>空位答案与解析</h2><nav aria-label="空位编辑导航" class="practice-question-nav"><button v-for="(q,n) in questions" :key="q.key" :aria-current="index===n?'step':undefined" :title="questionProblems(q,reviewed.includes(q.key)).join(' · ')||'已校对'" @click="emit('navigate',n)">{{q.stem}}<small v-if="questionProblems(q,reviewed.includes(q.key)).length"> · 待核</small></button></nav>
 <article v-for="(q,n) in questions" :id="'cloze-edit-'+q.key" :key="q.key" class="cloze-admin-gap" :class="{'is-current':index===n}">
  <h3>第 {{q.stem}} 空 <small>{{questionProblems(q,reviewed.includes(q.key)).join(' · ')||'已校对'}}</small></h3><fieldset :disabled="busy">
  <label>正确词语<select :id="'cloze-answer-'+q.key" v-model="q.correctOption" @change="emit('change',q.key)"><option :value="null">未设置答案</option><option v-for="(word,i) in wordBank" :key="i" :value="String.fromCharCode(65+i)">{{String.fromCharCode(65+i)}}. {{word||'词语待填写'}}</option></select></label>
  <label>解析与原文依据<textarea :id="'cloze-explanation-'+q.key" v-model="q.explanation" rows="3" maxlength="4000" @input="emit('change',q.key)"/></label>
  <label class="practice-checkbox"><input type="checkbox" :checked="reviewed.includes(q.key)" @change="emit('review',q.key,($event.target as HTMLInputElement).checked)">已核对第 {{q.stem}} 空答案、语法与上下文依据</label></fieldset>
 </article>
</section></template>
<style scoped>
.cloze-admin-bank{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.cloze-admin-bank label{display:flex;gap:8px;align-items:center}.cloze-admin-bank input{width:100%;min-width:0}.cloze-admin-gap{padding:20px 0;border-top:1px solid var(--border-color,var(--line));scroll-margin-top:24px}.cloze-admin-gap.is-current h3{color:var(--accent)}.cloze-admin-gap h3 small{font-size:12px;font-weight:400;color:#817984}.cloze-admin-gap fieldset{display:grid;gap:14px;min-width:0}.cloze-admin-gap textarea{width:100%;box-sizing:border-box}.practice-question-nav{display:flex;flex-wrap:wrap;gap:6px;margin:16px 0}.practice-question-nav button[aria-current]{border-color:var(--accent);background:var(--accent-soft)}@media(max-width:600px){.cloze-admin-bank{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>

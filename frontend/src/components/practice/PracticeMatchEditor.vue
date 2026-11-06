<script setup lang="ts">
import type {PracticeQuestionInput} from '@/api/practice'
import {questionProblems} from '@/practice/admin-draft'
defineProps<{questions:PracticeQuestionInput[];letters:string[];reviewed:string[];busy:boolean;index:number}>()
const emit=defineEmits<{change:[key:string];review:[key:string,checked:boolean];navigate:[index:number]}>()
</script>
<template><section class="practice-panel match-admin-editor">
 <h2>匹配陈述、答案与解析</h2><p class="practice-hint">固定10题（36–45）。每题选择对应段落，同一段可以作为多题的答案。</p><p v-if="!letters.length" class="practice-notice">请先按 [A]、[B] 顺序录入分段原文，段落间保留空行。</p>
 <nav aria-label="匹配题编辑导航" class="practice-question-nav"><button v-for="(q,n) in questions" :key="q.key" :disabled="busy" :aria-current="index===n?'step':undefined" :title="questionProblems(q,reviewed.includes(q.key)).join(' · ')||'已校对'" @click="emit('navigate',n)">{{36+n}}<small v-if="questionProblems(q,reviewed.includes(q.key)).length"> · 待核</small></button></nav>
 <article v-for="(q,n) in questions" :key="q.key" class="match-admin-question" :class="{'is-current':index===n}"><h3>第 {{36+n}} 题 <small>{{questionProblems(q,reviewed.includes(q.key)).join(' · ')||'已校对'}}</small></h3><fieldset :disabled="busy">
 <label>完整陈述<textarea :id="'match-stem-'+q.key" v-model="q.stem" rows="3" maxlength="2000" @input="emit('change',q.key)"/></label>
 <label>正确段落<select :id="'match-correctOption-'+q.key" v-model="q.correctOption" @change="emit('change',q.key)"><option :value="null">未设置答案</option><option v-if="q.correctOption&&!letters.includes(q.correctOption)" :value="q.correctOption">{{q.correctOption}} · 原文不存在，需修正</option><option v-for="letter in letters" :key="letter" :value="letter">{{letter}} 段</option></select></label>
 <label>解析与原文依据<textarea :id="'match-explanation-'+q.key" v-model="q.explanation" rows="3" maxlength="4000" @input="emit('change',q.key)"/></label>
 <label class="practice-checkbox"><input :id="'match-review-'+q.key" type="checkbox" :checked="reviewed.includes(q.key)" @change="emit('review',q.key,($event.target as HTMLInputElement).checked)">已核对第 {{36+n}} 题陈述、对应段落与解析</label>
 </fieldset></article>
</section></template>
<style scoped>
.practice-question-nav{display:flex;flex-wrap:wrap;gap:6px;margin:16px 0}.practice-question-nav button[aria-current]{border-color:var(--accent);background:var(--accent-soft)}.match-admin-question{padding:20px 0;border-top:1px solid var(--line);scroll-margin-top:24px}.match-admin-question.is-current h3{color:var(--accent)}.match-admin-question h3 small{font-size:12px;font-weight:400;color:#817984}.match-admin-question fieldset{display:grid;gap:14px;min-width:0}.match-admin-question textarea{width:100%;box-sizing:border-box}
</style>

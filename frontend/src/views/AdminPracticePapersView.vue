<script setup lang="ts">
import {ref,onMounted} from 'vue'
import {adminPapers,type PaperView} from '@/api/practice-paper'
import {errorMessage} from '@/api/identity'
import '@/styles/practice-paper.css'
const rows=ref<PaperView[]>([]),busy=ref(false),error=ref('')
async function load(){busy.value=true;error.value='';try{rows.value=await adminPapers()}catch(e){error.value=errorMessage(e)}finally{busy.value=false}}
onMounted(load)
</script>
<template><section class="practice-page paper-page"><RouterLink to="/admin/practice">← 返回专项题组</RouterLink><header class="paper-heading"><h1>整卷管理</h1><p>引用已发布题组版本，校对组合来源后再发布。</p><RouterLink class="paper-primary" to="/admin/practice/papers/new">新建整卷</RouterLink></header><p v-if="busy" role="status">正在加载…</p><p v-if="error" class="paper-error" role="alert">{{error}} <button @click="load">重试</button></p><article v-for="p in rows" :key="p.id" class="paper-panel paper-row"><div><h2>{{p.title}}</h2><p>{{p.year}}年{{p.month}}月 · {{p.status==='PUBLISHED'?'已发布':p.status==='UNLISTED'?'已下架':'草稿'}} · 发布版本 {{p.revision}}</p><p class="paper-muted">{{p.sourceNote}}</p></div><RouterLink class="paper-primary" :to="'/admin/practice/papers/'+p.id">编辑整卷</RouterLink></article><p v-if="!busy&&!rows.length&&!error">暂无整卷，先新建并选择七个组成部分。</p></section></template>

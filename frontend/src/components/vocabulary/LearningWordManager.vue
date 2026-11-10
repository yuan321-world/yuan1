<script setup lang="ts">
import {ref,onMounted,onBeforeUnmount} from 'vue'
import {learningWords,setLearningActive,setLearningProficiency,type LearningWord} from '@/api/vocabulary-learning'
import {getGeneration} from '@/auth/token-session'
import {errorMessage} from '@/api/identity'
const props=defineProps<{busy?:boolean}>()
const emit=defineEmits<{changed:[];busy:[value:boolean]}>()
const items=ref<LearningWord[]>([]),page=ref(1),total=ref(0),loading=ref(false),saving=ref(false),error=ref('')
let disposed=false,sequence=0,controller:AbortController|undefined
async function load(){const n=++sequence,owner=getGeneration();controller?.abort();controller=new AbortController();loading.value=true;error.value='';try{const data=await learningWords(page.value,controller.signal);if(!disposed&&n===sequence&&owner===getGeneration()){items.value=data.items;total.value=data.total}}catch(e){if(!disposed&&n===sequence&&owner===getGeneration()&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e)}finally{if(n===sequence)loading.value=false}}
async function change(word:LearningWord){if(props.busy||saving.value)return;const owner=getGeneration();saving.value=true;emit('busy',true);error.value='';try{if(word.proficient)await setLearningProficiency(word.id,false);else await setLearningActive(word.id,!word.active);if(disposed||owner!==getGeneration())return;await load();emit('changed')}catch(e){if(!disposed&&owner===getGeneration())error.value=errorMessage(e)}finally{saving.value=false;if(!disposed)emit('busy',false)}}
onMounted(load);onBeforeUnmount(()=>{disposed=true;sequence++;controller?.abort();emit('busy',false)})
</script>
<template><section class="vn-learning" aria-label="全部学习词" :aria-busy="loading||saving"><p class="vn-count">包括词书中学过和已取消收藏的词；暂停不删除学习记录。</p><p v-if="error" role="alert">{{error}} <button :disabled="loading||saving" @click="load">重试</button></p><p v-if="loading" role="status">正在加载学习词…</p><p v-else-if="!items.length">暂无已加入的学习词。</p><article v-for="word in items" :key="word.id" class="vn-row"><div><strong lang="en">{{word.lemma}}</strong><p>{{word.meaning}}</p><small>{{word.proficient?'熟练免复习':!word.active?'学习已暂停':word.stage?'学习中':'待新学'}}</small></div><button :disabled="busy||saving||loading" @click="change(word)">{{word.proficient?'恢复复习':word.active?'暂停学习':'恢复学习'}}</button></article><nav v-if="total>20" aria-label="全部学习词分页"><button :disabled="loading||saving||page<=1" @click="page--;load()">上一页</button><span>第{{page}}页</span><button :disabled="loading||saving||page*20>=total" @click="page++;load()">下一页</button></nav></section></template>
<style scoped>
.vn-learning .vn-count{margin:8px 0 6px;font-size:12px;line-height:1.45;color:#6f786d}
.vn-learning .vn-row{display:grid;grid-template-columns:minmax(0,1fr) auto;justify-content:space-between;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid var(--border,#e4e7e0)}
.vn-learning .vn-row>div{min-width:0}
.vn-learning .vn-row strong{font-size:16px;line-height:1.2;overflow-wrap:anywhere}
.vn-learning p{margin:3px 0;font-size:12px;line-height:1.45;color:#6f786d;overflow-wrap:anywhere}
.vn-learning small{font-size:11px;line-height:1.35;color:#6f786d}
.vn-learning button{font:inherit;font-size:12px;border:1px solid #d8ded1;background:transparent;color:#536b43;border-radius:10px;padding:7px 10px;white-space:nowrap}
.vn-learning button:disabled{opacity:.5}
.vn-learning nav{display:flex;gap:12px;align-items:center;margin-top:14px;font-size:12px}
@media(max-width:420px){.vn-learning .vn-row{gap:8px}.vn-learning button{font-size:11px;padding:6px 8px}}
</style>

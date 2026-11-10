<script setup lang="ts">
import PronunciationControls from '@/components/common/PronunciationControls.vue'
import WordDetails from './WordDetails.vue'
import VocabularyDetailDialog from './VocabularyDetailDialog.vue'
import LearningWordManager from './LearningWordManager.vue'
import {computed,nextTick,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import {useAuthStore} from '@/stores/auth'
import {getGeneration} from '@/auth/token-session'
import {errorMessage} from '@/api/identity'
import {searchVocabularyWords,vocabularyDetail,cancelWord,type CollectedWord,type CollectedDetail,type DifficultyState} from '@/api/vocabulary'
import {lookupWord,type DictionaryCandidate} from '@/api/dictionary'
import {joinLearning} from '@/api/vocabulary-learning'
import {businessWrite} from '@/api/business'
const props=defineProps<{busy?:boolean}>()
const emit=defineEmits<{changed:[];busy:[value:boolean]}>()
const auth=useAuthStore(),items=ref<CollectedWord[]>([]),total=ref(0),page=ref(1),query=ref(''),filter=ref('ALL'),loading=ref(false),error=ref(''),rowBusy=ref(''),notice=ref('')
const drawer=ref<InstanceType<typeof VocabularyDetailDialog>>(),selected=ref<CollectedDetail>(),entry=ref<DictionaryCandidate>(),detailBusy=ref(false),detailError=ref(''),sourcePage=ref(1),voiceError=ref('')
let disposed=false,listSequence=0,detailSequence=0,voiceSequence=0,listController:AbortController|undefined,detailController:AbortController|undefined,mutation:AbortController|undefined,returnFocus:HTMLElement|null=null
const managerOpen=ref(false),managerBusy=ref(false)
const detailEntry=computed<DictionaryCandidate|undefined>(()=>entry.value??(selected.value?{...selected.value.word,collection:{wordId:selected.value.word.id,active:true}}:undefined))
const blocked=computed(()=>props.busy||!!rowBusy.value||managerBusy.value)
function managerState(value:boolean){managerBusy.value=value;emit('busy',value)}
function toggleManager(event:Event){const section=event.target as HTMLDetailsElement;if(managerBusy.value&&!section.open){section.open=true;return}managerOpen.value=section.open}
function status(word:CollectedWord){const l=word.learning;return l?.proficient?'熟练 · 免复习':!l||!l.personalEnrolled&&l.stage===0?'未加入学习':!l.active?'学习已暂停':l.stage===0?'待新学':l.dueAt&&Date.parse(l.dueAt)<=Date.now()?'已到期':'学习中'}
function joined(word:CollectedWord){return !!word.learning&&(word.learning.personalEnrolled||word.learning.stage>0)}
async function load(){const n=++listSequence,identity=getGeneration();listController?.abort();listController=new AbortController();loading.value=true;error.value='';try{const result=await searchVocabularyWords(page.value,query.value,filter.value,listController.signal);if(!disposed&&n===listSequence&&identity===getGeneration()){items.value=result.items;total.value=result.total}}catch(e){if(!disposed&&n===listSequence&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e)}finally{if(n===listSequence)loading.value=false}}
function search(){page.value=1;void load()}
async function show(word:CollectedWord,event?:Event,reset=true){if(reset){sourcePage.value=1;returnFocus=event?.currentTarget instanceof HTMLElement?event.currentTarget:null}const n=++detailSequence,identity=getGeneration();detailController?.abort();detailController=new AbortController();detailBusy.value=true;detailError.value='';voiceError.value='';if(reset){selected.value=undefined;entry.value=undefined}await nextTick();if(!drawer.value?.open)drawer.value?.showModal();try{const [result,lookup]=await Promise.all([vocabularyDetail(word.id,sourcePage.value,detailController.signal),lookupWord(word.lemma,detailController.signal)]);if(!disposed&&n===detailSequence&&identity===getGeneration()){selected.value=result;entry.value=lookup.candidates.find(e=>e.entryId===word.entryId)}}catch(e){if(!disposed&&n===detailSequence&&!(e instanceof DOMException&&e.name==='AbortError'))detailError.value=errorMessage(e)}finally{if(n===detailSequence)detailBusy.value=false}}
function closeDetail(){detailSequence++;detailController?.abort();detailBusy.value=false;stopVoice();if(returnFocus?.isConnected)returnFocus.focus({preventScroll:true})}
function stopVoice(){voiceSequence++;window.speechSynthesis?.cancel()}
function speak(lang:string){voiceError.value='';if(!window.speechSynthesis){voiceError.value='浏览器暂不支持朗读。';return}stopVoice();const n=voiceSequence,u=new SpeechSynthesisUtterance(selected.value?.word.lemma||'');u.lang=lang;u.onerror=e=>{if(n===voiceSequence&&!['canceled','interrupted'].includes(e.error))voiceError.value='朗读暂不可用，可以重试。'};speechSynthesis.speak(u)}
async function change(word:CollectedWord,kind:'join'|'cancel'|'proficiency'){if(blocked.value)return;const identity=getGeneration();rowBusy.value=word.id;notice.value='';error.value='';emit('busy',true);mutation=new AbortController();try{if(kind==='join')await joinLearning([word.id],mutation.signal);else if(kind==='cancel')await cancelWord(word.id,mutation.signal,()=>!disposed&&identity===getGeneration());else if(word.learning)await businessWrite(`/api/v1/vocabulary/learning/words/${encodeURIComponent(word.learning.id)}/proficiency`,{proficient:!word.learning.proficient},'PUT',mutation.signal,()=>!disposed&&identity===getGeneration());if(disposed||identity!==getGeneration())return;notice.value=kind==='join'?'已加入学习，原有进度保持不变。':kind==='cancel'?'已取消收藏，来源和已加入学习的复习记录保留。':word.learning?.proficient?'已恢复原学习安排。':'已标记熟练，不再自动复习。';if(kind==='cancel'){drawer.value?.close();if(items.value.length===1&&page.value>1)page.value--}await load();if(selected.value?.word.id===word.id&&kind!=='cancel'){const updated=items.value.find(w=>w.id===word.id);if(updated)selected.value={...selected.value,word:updated};else drawer.value?.close()}emit('changed')}catch(e){if(!disposed&&identity===getGeneration()&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e)}finally{if(!disposed){rowBusy.value='';emit('busy',false)}}}
async function changeDifficulty(value:boolean|null){const word=selected.value?.word;if(!word?.learning||blocked.value)return;const identity=getGeneration();rowBusy.value=word.id;emit('busy',true);mutation=new AbortController();try{const result=await businessWrite<DifficultyState>(`/api/v1/vocabulary/learning/words/${encodeURIComponent(word.learning.id)}/difficulty`,{difficult:value},'PUT',mutation.signal,()=>!disposed&&identity===getGeneration());if(disposed||identity!==getGeneration())return;if(selected.value?.word.id===word.id)selected.value={...selected.value,difficulty:result};await load();emit('changed')}catch(e){if(!disposed&&identity===getGeneration())error.value=errorMessage(e)}finally{if(!disposed){rowBusy.value='';emit('busy',false)}}}
watch(()=>auth.user?.id,()=>{listSequence++;detailSequence++;listController?.abort();detailController?.abort();mutation?.abort();drawer.value?.close();items.value=[];total.value=0;selected.value=undefined;query.value='';filter.value='ALL';page.value=1;error.value='';notice.value='';if(auth.user)void load()},{flush:'sync'})
defineExpose({saveBeforeLeave:async()=>!rowBusy.value&&!managerBusy.value})
onMounted(load);onBeforeUnmount(()=>{disposed=true;listSequence++;detailSequence++;listController?.abort();detailController?.abort();mutation?.abort();stopVoice();emit('busy',false)})
</script>
<template><section class="vn-library" aria-label="生词本"><header class="vn-heading"><div><h2>把有用的词，留在这里。</h2><p>收藏与加入学习分别管理，保留每次遇见的原句。</p></div><RouterLink to="/reading">去阅读积累 ↗</RouterLink></header>
<form class="vn-toolbar" @submit.prevent="search"><label><span class="vn-sr">搜索单词或中文释义</span><input v-model="query" maxlength="80" type="search" placeholder="搜索单词或中文释义"></label><select v-model="filter" aria-label="筛选学习状态" @change="search"><option value="ALL">全部状态</option><option value="UNJOINED">未加入学习</option><option value="LEARNING">已加入学习</option><option value="DUE">已到期</option><option value="DIFFICULT">难词</option><option value="PROFICIENT">熟练免复习</option></select><button type="submit" :disabled="loading">搜索</button></form>
<p v-if="notice" role="status" class="vn-notice">{{ notice }}</p><div v-if="error" role="alert" class="vn-error">{{ error }} <button @click="load">重新加载</button></div><p class="vn-count">{{ total }} 个收藏词 <span>收藏不会自动加入学习</span></p><p v-if="loading" role="status">正在加载生词本…</p><p v-else-if="!items.length" class="vn-empty">还没有匹配的收藏。在阅读或学习中点击“加入生词本”，它会出现在这里。</p>
<article v-for="word in items" v-else :key="word.id" class="vn-row"><div><button class="vn-word" @click="show(word,$event)">{{ word.lemma }}</button><small class="vn-phonetic">{{ word.phonetic }}</small><p>{{ word.senses[0]?.partOfSpeech }} {{ word.senses[0]?.definitionZh }}</p><small>{{ status(word) }}</small></div><div class="vn-actions"><button :disabled="blocked||joined(word)" @click="change(word,'join')">{{ joined(word)?'已加入学习':'加入学习' }}</button><button @click="show(word,$event)">详情与来源 ↗</button><button :disabled="blocked" @click="change(word,'cancel')">取消收藏</button></div></article>
<nav v-if="total>20" class="vn-pagination" aria-label="生词本分页"><button :disabled="loading||page<=1" @click="page--;load()">上一页</button><span>第 {{ page }} 页</span><button :disabled="loading||page*20>=total" @click="page++;load()">下一页</button></nav>
<details class="vn-learning-management" @toggle="toggleManager"><summary>全部学习词 · 暂停与恢复</summary><LearningWordManager v-if="managerOpen" :busy="busy||!!rowBusy" @busy="managerState" @changed="load();emit('changed')"/></details>
<VocabularyDetailDialog ref="drawer" title="生词详情" close-label="关闭生词详情" :busy="!!rowBusy" @close="closeDetail">
<template #heading><div v-if="selected" class="vn-detail-heading"><h2 lang="en">{{ selected.word.lemma }}</h2><PronunciationControls class="vn-detail-pronunciation" :phonetic="entry?.phonetic||selected.word.phonetic" uk-label="英式朗读" us-label="美式朗读" @speak="speak"/></div><h2 v-else>生词详情</h2></template>
<p v-if="detailBusy" role="status">正在加载词条与来源…</p><p v-if="detailError||error" role="alert">{{ detailError||error }}</p>
<template v-if="selected">
<p v-if="voiceError" role="status" class="vn-detail-muted">{{ voiceError }}</p>
<WordDetails v-if="detailEntry" :entry="detailEntry" compact all-senses/>
<details v-if="selected.difficulty" class="vn-detail-section"><summary>难词管理 · {{selected.difficulty.difficult?'难词':'普通词'}}</summary><p>最近 {{selected.difficulty.attempts}} 次有效尝试中，{{selected.difficulty.assistedAttempts}} 次有错误或辅助；达到3次自动标记难词。</p><div class="vn-actions"><button :disabled="blocked" @click="changeDifficulty(!selected.difficulty.difficult)">{{selected.difficulty.difficult?'取消难词标记':'标记为难词'}}</button><button v-if="selected.difficulty.manualOverride!==null" :disabled="blocked" @click="changeDifficulty(null)">恢复自动判定</button></div><small>难词标签不会暂停复习或改变已有阶段。</small></details>
<details class="vn-detail-section"><summary>收藏来源（{{ (selected.sources.total||0)+(selected.origins?.length||0) }}）</summary><section class="vn-detail-sources"><article v-for="source in selected.sources.items" :key="source.id"><p lang="en">{{ source.sentence }}</p><RouterLink :to="{path:`/reading/${source.articleId}`,query:{version:source.articleVersionId,sentence:source.sentenceKey,start:source.startOffset,end:source.endOffset}}" target="_blank">{{ source.title }} · {{ source.surfaceForm }} ↗</RouterLink></article><article v-for="origin in selected.origins" :key="origin.id"><strong>{{ origin.kind==='BOOK'?'词书来源':origin.kind==='DICTIONARY'?'查词收藏':'学习中收藏' }}</strong><p v-if="origin.title!=='查词收藏'">{{ origin.title }}</p></article><p v-if="!selected.sources.total&&!selected.origins?.length">没有已保存的来源记录。</p><nav v-if="selected.sources.total>10" class="vn-pagination" aria-label="收藏来源分页"><button :disabled="detailBusy||sourcePage<=1" @click="sourcePage--;show(selected.word,undefined,false)">上一页</button><span>第 {{ sourcePage }} 页</span><button :disabled="detailBusy||sourcePage*10>=selected.sources.total" @click="sourcePage++;show(selected.word,undefined,false)">下一页</button></nav></section></details>
<details class="vn-detail-section"><summary>词典资料来源</summary><p>{{ selected.word.sourceNote }}</p><p>{{ entry?.exampleSourceNote }}</p><p>{{ entry?.lexicalSourceNote }}</p></details>
</template>
<template #footer><template v-if="selected"><button v-if="selected.word.learning" :disabled="blocked" @click="change(selected.word,'proficiency')">{{ selected.word.learning.proficient?'恢复复习':'标记熟练 · 免复习' }}</button><button class="vd-primary" :disabled="blocked||joined(selected.word)" @click="change(selected.word,'join')">{{ joined(selected.word)?'已加入学习':'加入学习' }}</button></template></template>
</VocabularyDetailDialog></section></template>
<style scoped>
.vn-detail-pronunciation{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-top:9px;color:#788074;font-size:13px}.vn-detail-pronunciation span{margin-right:6px}.vn-detail-section{border-top:1px solid #dce1d3;padding:10px 0;margin-top:12px;font-size:13px;line-height:1.7}.vn-detail-sources article{padding:10px 12px;margin-top:8px;border-radius:10px;background:#eef2e6}.vn-detail-sources p{margin:4px 0}.vn-detail-sources a{color:#587344}.vn-detail-muted{font-size:13px;color:#788074}

.vn-learning-management{margin-top:22px;border-top:1px solid #e2e6dd;padding-top:12px}.vn-learning-management>summary{cursor:pointer;color:#536b43;font-size:13px}

.vn-difficulty{padding:16px;background:#eef2e5;border-radius:12px;font-size:13px;line-height:1.8;margin:22px 0}.vn-difficulty p{margin:8px 0}.vn-difficulty small{color:#747a70}

.vn-library{color:var(--vl-ink,#293027)}
.vn-heading{display:flex;justify-content:space-between;align-items:center;gap:18px;margin:20px 0}
.vn-heading h2{font-size:22px;margin:0 0 8px}
.vn-heading p,.vn-heading a{font-size:13px;color:#73796e}
.vn-toolbar{display:flex;gap:10px;margin:20px 0 14px}
.vn-toolbar label{flex:1;min-width:0}
.vn-toolbar input,.vn-toolbar select{width:100%;border:1px solid #dde2d4;border-radius:12px;background:#fcfbf7;padding:11px 13px;font:inherit;font-size:13px}
.vn-toolbar select{width:auto}
.vn-library button{cursor:pointer;font:inherit;font-size:12px;padding:8px 11px;border:1px solid #dce2d2;border-radius:22px;background:transparent;color:#526a43}
.vn-library button:disabled{opacity:.5;cursor:default}
.vn-library button:focus-visible,.vn-library a:focus-visible{outline:3px solid #829b6a;outline-offset:3px}
.vn-count{display:flex;justify-content:space-between;gap:8px;color:#747a70;font-size:12px}
.vn-row{display:flex;justify-content:space-between;align-items:center;gap:16px;min-width:0;padding:11px 14px;background:#fcfbf7;border:1px solid #e3e6db;border-radius:12px;margin:8px 0}
.vn-row>div:first-child{min-width:0}
.vn-row p{font-size:12px;line-height:1.45;margin:4px 0 3px;overflow-wrap:anywhere}
.vn-row small,.vn-phonetic{color:#747a70;font-size:11px;line-height:1.35}
.vn-library .vn-word{font:bold 20px/1.2 Georgia,serif;padding:0;border:0;border-radius:0;color:#293027}
.vn-phonetic{display:inline-block;margin-left:8px;vertical-align:baseline}
.vn-actions{display:flex;align-items:center;gap:6px;flex-wrap:wrap}
.vn-library .vn-actions button{font-size:11px;padding:6px 9px}
.vn-notice{padding:10px 13px;background:#edf2e5;border-radius:12px;font-size:13px}
.vn-error{padding:10px;color:#964237;font-size:13px}
.vn-empty{padding:65px 25px;text-align:center;font-size:14px;color:#747a70;line-height:1.9}
.vn-pagination{display:flex;justify-content:center;gap:15px;align-items:center;margin:18px 0;font-size:13px}
.vn-drawer{position:fixed;left:auto;right:0;top:0;bottom:0;margin:0;width:min(580px,100%);max-width:100%;height:100dvh;max-height:100dvh;box-sizing:border-box;border:1px solid #e3e6db;border-radius:22px 0 0 22px;padding:42px 32px;background:#fcfbf7;overflow:auto;color:#293027}
.vn-drawer::backdrop{background:#26302045;backdrop-filter:blur(3px)}
.vn-library .vn-close{position:absolute;top:18px;right:18px;font-size:22px;padding:4px 12px}
.vn-drawer h2{font:bold 37px Georgia,serif;overflow-wrap:anywhere;margin:25px 0 12px}
.vn-drawer>.vn-phonetic{margin:0 0 22px}
.vn-drawer .vn-actions{margin:18px 0}
.vn-sense{font-size:18px;line-height:1.9}
.vn-drawer details,.vn-sources{border-top:1px solid #e2e6d9;padding-top:18px;margin-top:24px;font-size:14px;line-height:1.9}
.vn-drawer summary{color:#526a43;cursor:pointer}
.vn-sources article{padding:14px 18px;background:#f0f3e8;border-radius:12px;margin:12px 0}
.vn-sources a{color:#526a43}
.vn-sources h3{font-size:17px}
.vn-sr{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
@media(max-width:700px){
  .vn-heading{display:block}
  .vn-heading h2{font-size:19px}
  .vn-row{display:block;padding:11px 12px}
  .vn-actions{margin-top:8px}
  .vn-toolbar{gap:7px;flex-wrap:wrap}
  .vn-toolbar input{font-size:12px}
  .vn-toolbar select{max-width:125px;font-size:12px}
  .vn-toolbar label{flex-basis:100%}
  .vn-phonetic{display:inline-block;margin:0 0 0 7px}
  .vn-row p{overflow-wrap:anywhere}
  .vn-drawer{width:100%;padding:36px 23px;border-radius:0}
  .vn-count{font-size:11px}
}
</style>

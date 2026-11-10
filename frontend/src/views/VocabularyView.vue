<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import {useRoute,useRouter,onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import WordNotebook from '@/components/vocabulary/WordNotebook.vue'
import DictionarySearchButton from '@/components/vocabulary/DictionarySearchButton.vue'
import PhraseBook from '@/components/vocabulary/PhraseBook.vue'
import {useAuthStore} from '@/stores/auth'
import {useVocabularyLearning} from '@/stores/vocabulary-learning'
import {useVocabularyRefresh} from '@/vocabulary/useVocabularyRefresh'
import LearningDashboard from '@/components/vocabulary/LearningDashboard.vue'
import LearningCard from '@/components/vocabulary/LearningCard.vue'
import LearningQueue from '@/components/vocabulary/LearningQueue.vue'
import LearningResult from '@/components/vocabulary/LearningResult.vue'
import WordSearchDialog from '@/components/vocabulary/WordSearchDialog.vue'
import '@/styles/vocabulary.css'
const auth=useAuthStore(),store=useVocabularyLearning(),dashboard=ref<InstanceType<typeof LearningDashboard>>()
const route=useRoute(),router=useRouter(),phraseBook=ref<InstanceType<typeof PhraseBook>>(),phraseBusy=ref(false),dashboardBusy=ref(false),notebookBusy=ref(false),tabBusy=ref(false),searchBusy=ref(false),wordSearch=ref<InstanceType<typeof WordSearchDialog>>()
const tab=computed(()=>route.query.tab==='words'?'words':route.query.tab==='phrases'?'phrases':'today')
async function recordDictionaryLookup(ids:string[]){
 const session=store.session,current=session?.current
 if(!store.inStudy||session?.flowVersion!=='v2'||session.kind!=='REVIEW'||session.mode!=='SPELLING'||session.phase!=='QUESTION'||!current?.entryId||!ids.includes(current.entryId))return
 if(store.pending)await store.retry()
 if(store.pending||store.error)throw new Error('请先重试并保存当前学习操作。')
 if(store.session?.current?.assisted)return
 await store.answer({action:'LOOKUP'})
 if(store.pending||store.error||!store.session?.current?.assisted)throw new Error(store.error||'查词辅助记录尚未保存，请重试。')
}
async function saveBeforeLeave(){if(store.busy||store.pending||dashboardBusy.value||notebookBusy.value||searchBusy.value)return false;if(tab.value==='phrases'&&phraseBook.value&&!await phraseBook.value.saveBeforeLeave())return false;if(store.inStudy&&store.session?.status==='ACTIVE'){await store.update('state',{status:'PAUSED'});return !store.pending&&!store.error&&(store.session?.status as string)==='PAUSED'}return true}
async function switchTab(value:string){if(value===tab.value||tabBusy.value)return;tabBusy.value=true;try{if(!await saveBeforeLeave())return;await router.replace({path:'/vocabulary',query:{...route.query,result:undefined,tab:value}})}finally{tabBusy.value=false}}
onBeforeRouteLeave(saveBeforeLeave)
onBeforeRouteUpdate(async(to,from)=>to.query.tab===from.query.tab||await saveBeforeLeave())
async function home(){const {result:_,...query}=route.query;await router.replace({query});store.inStudy=false;await store.load();store.inStudy=false}
watch(()=>auth.user?.id,()=>{if(auth.user)void store.load()},{flush:'sync'})
useVocabularyRefresh(()=>store.refreshOverview(),()=>!!auth.user&&!store.busy&&!store.pending&&!store.inStudy,()=>store.overview?.nextDueAt)
function keyboard(event:KeyboardEvent){if(tab.value!=='today')return;if(event.target instanceof Element&&event.target.closest('input,button,textarea,a,select,summary,dialog')||store.busy||store.pending||!store.inStudy||!store.session)return;const s=store.session;if(event.key==='Enter'&&s.phase==='DEFINITION'){event.preventDefault();void store.update('next')}else if(s.mode==='RECOGNITION'&&s.phase==='QUESTION'&&/^[1-4]$/.test(event.key)){const option=s.current?.options[Number(event.key)-1];if(option){event.preventDefault();void store.answer({action:'CHOICE',optionId:option.id})}}}
watch(()=>store.session?.status,status=>{
 if(status==='COMPLETED'&&store.inStudy&&route.query.result!==store.session?.id){
  void router.replace({query:{...route.query,result:store.session?.id}})
 }
})
async function loadPage(){
 await store.load()
 if(tab.value==='today'&&typeof route.query.result==='string')await store.resume(route.query.result,true)
}
onMounted(()=>{void loadPage();document.addEventListener('keydown',keyboard)});onBeforeUnmount(()=>{document.removeEventListener('keydown',keyboard);store.dispose()})
</script>
<template><main class="vl-page"><header v-if="!store.inStudy||tab!=='today'" class="vl-page-heading"><div><h1>背单词</h1><p>遇见、收藏，再一点点记牢。</p></div><div class="vl-heading-actions"><DictionarySearchButton :disabled="store.busy||!!store.pending||searchBusy" @click="wordSearch?.open($event)"/><button v-if="tab==='today'&&store.overview" @click="dashboard?.openProgress($event)">今日进度</button></div></header><nav v-if="!store.inStudy||tab!=='today'" class="vl-tabs" aria-label="词汇中心" :aria-busy="tabBusy"><button v-for="item in [{id:'today',label:'今日学习'},{id:'words',label:'生词本'},{id:'phrases',label:'词组本'}]" :key="item.id" :aria-current="tab===item.id?'page':undefined" :disabled="store.busy||!!store.pending||phraseBusy||dashboardBusy||notebookBusy||tabBusy" @click="switchTab(item.id)">{{ item.label }}</button></nav><section v-if="tab==='words'"><WordNotebook :busy="store.busy||!!store.pending" @changed="store.refreshOverview()" @busy="notebookBusy=$event"/></section><section v-else-if="tab==='phrases'"><PhraseBook ref="phraseBook" :before-practice="saveBeforeLeave" @busy="phraseBusy=$event"/></section><template v-else><header v-if="store.inStudy&&store.session?.status!=='COMPLETED'" class="vl-page-heading"><div><h1>{{ store.inStudy?(store.session?.kind==='REVIEW'?'到期复习':'新词学习'):'背单词' }}</h1><p>遇见一个词，再多记住一点。</p></div><div class="vl-heading-actions"><DictionarySearchButton :disabled="store.busy||!!store.pending||searchBusy" @click="wordSearch?.open($event)"/><button v-if="store.inStudy&&store.session?.status==='ACTIVE'" :disabled="store.busy||!!store.pending" @click="store.update('state',{status:'PAUSED'})">退出并保存</button><button v-else-if="store.overview&&!store.inStudy" @click="dashboard?.openProgress($event)">今日进度</button></div></header>
<div v-if="store.error" class="vl-notice" role="alert"><p>{{ store.error }}</p><button :disabled="store.busy" @click="store.retry()">{{ store.pending?'重试刚才的操作':'恢复最新进度' }}</button></div><p v-if="store.busy&&!store.overview" role="status" class="vl-muted">正在加载学习记录…</p>
<template v-if="store.inStudy&&store.session"><LearningResult v-if="store.session.status==='COMPLETED'" :session="store.session" @home="home"/><div v-else-if="store.session.current" class="vl-study-grid"><LearningCard :owner="auth.user?.id||''" :session="store.session" :busy="store.busy||!!store.pending" @answer="store.answer" @next="store.update('next')"/><LearningQueue :session="store.session"/></div></template>
<template v-else-if="store.overview"><p v-if="store.refreshError" class="vl-notice" role="alert">学习概览刷新失败：{{ store.refreshError }} <button :disabled="store.busy||!!store.pending" @click="store.refreshOverview()">重试刷新概览</button></p><LearningDashboard ref="dashboard" :overview="store.overview" :session="store.session" :busy="store.busy||!!store.pending" @start="store.start" @resume="store.resume" @changed="store.load()" @busy="dashboardBusy=$event"/></template></template><WordSearchDialog ref="wordSearch" :before-reveal="recordDictionaryLookup" :reviewing="store.inStudy&&store.session?.kind==='REVIEW'&&store.session.mode==='SPELLING'" :assisted="store.session?.current?.assisted" @busy="searchBusy=$event" @collected="store.refreshOverview()"/></main></template>

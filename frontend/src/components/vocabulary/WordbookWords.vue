<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,ref,watch} from 'vue'
import {wordbookWords,setBookWordProficiency,type WordbookWords as WordPage,type WordbookWord,type WordbookState} from '@/api/vocabulary-learning'
import {collectFromDictionary,cancelWord} from '@/api/vocabulary'
import {lookupWord,type DictionaryCandidate} from '@/api/dictionary'
import {getGeneration} from '@/auth/token-session'
import {useAuthStore} from '@/stores/auth'
import {errorMessage} from '@/api/identity'
import VocabularyDetailDialog from './VocabularyDetailDialog.vue'
import PronunciationControls from '@/components/common/PronunciationControls.vue'
import WordDetails from './WordDetails.vue'

const props=defineProps<{sourceTitle:string;sourceBook?:string}>()
const emit=defineEmits<{changed:[];busy:[value:boolean]}>()
const auth=useAuthStore(),dialog=ref<HTMLDialogElement>(),detail=ref<InstanceType<typeof VocabularyDetailDialog>>()
const book=ref(''),visible=ref(false),data=ref<WordPage>(),items=ref<WordbookWord[]>([]),page=ref(1)
const query=ref(''),state=ref<WordbookState>('ALL'),sort=ref<'BOOK'|'ALPHA'>('BOOK'),collected=ref(false),showMeaning=ref(true)
const loading=ref(false),saving=ref(false),error=ref(''),notice=ref(''),entry=ref<DictionaryCandidate>(),selected=ref<WordbookWord>(),detailLoading=ref(false),detailError=ref(''),voiceError=ref('')
const tabs=[{id:'ALL',label:'全部',count:'total'},{id:'NEW',label:'未学习',count:'unlearned'},{id:'REVIEW',label:'复习中',count:'reviewing'},{id:'PROFICIENT',label:'已熟练',count:'proficient'}] as const
const isCurrent=computed(()=>props.sourceBook===book.value)
let sequence=0,detailSequence=0,voiceSequence=0,controller:AbortController|undefined,detailController:AbortController|undefined,mutation:AbortController|undefined,timer:ReturnType<typeof setTimeout>|undefined,opener:HTMLElement|null=null,disposed=false
function valid(n:number,g:number){return !disposed&&visible.value&&n===sequence&&g===getGeneration()}
async function load(more=false){
 if(!visible.value)return
 const n=++sequence,g=getGeneration();controller?.abort();controller=new AbortController();loading.value=true;error.value=''
 const next=more?page.value+1:1
 try{const result=await wordbookWords(book.value,{q:query.value.trim(),status:state.value,collectedOnly:collected.value,sort:sort.value,page:next},controller.signal);if(valid(n,g)){data.value=result;items.value=more?[...items.value,...result.items]:result.items;page.value=next}}
 catch(e){if(valid(n,g)&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e)}
 finally{if(valid(n,g))loading.value=false}
}
async function open(id:string){
 if(saving.value)return
 visible.value=false;cancelLoads();book.value=id;query.value='';state.value='ALL';sort.value='BOOK';collected.value=false;showMeaning.value=true;data.value=undefined;items.value=[];page.value=1;error.value='';notice.value=''
 opener=document.activeElement instanceof HTMLElement?document.activeElement:null;visible.value=true
 await nextTick();if(!visible.value)return;dialog.value?.showModal();void load()
}
function cancelLoads(){sequence++;controller?.abort();detailSequence++;detailController?.abort();if(timer)clearTimeout(timer)}
function stopVoice(){voiceSequence++;window.speechSynthesis?.cancel()}
function closeDetail(){detailSequence++;detailController?.abort();detailLoading.value=false;entry.value=undefined;selected.value=undefined;stopVoice()}
function closed(){visible.value=false;cancelLoads();detail.value?.close();closeDetail();items.value=[];data.value=undefined;loading.value=false;opener?.isConnected&&opener.focus({preventScroll:true})}
function filters(){if(!visible.value||saving.value)return;if(timer)clearTimeout(timer);items.value=[];void load()}
watch([state,sort,collected],filters,{flush:'sync'})
watch(query,()=>{if(!visible.value||saving.value)return;sequence++;controller?.abort();if(timer)clearTimeout(timer);loading.value=true;items.value=[];timer=setTimeout(()=>void load(),220)},{flush:'sync'})
function clear(){visible.value=false;query.value='';state.value='ALL';sort.value='BOOK';collected.value=false;visible.value=true;filters()}
function status(word:WordbookWord){
 if(word.state==='NEW')return '未学习'
 if(word.state==='PROFICIENT')return '已熟练 · 免复习'
 if(!word.reviewActive||!word.dueAt)return '复习中'
 const date=new Date(word.dueAt);if(date.getTime()<=Date.now())return '待复习'
 return date.toLocaleDateString('zh-CN',{timeZone:'Asia/Shanghai',month:'numeric',day:'numeric'})+' 复习'
}
async function show(word:WordbookWord){
 selected.value=word;entry.value=undefined;detailError.value='';voiceError.value='';notice.value='';detailLoading.value=true
 const n=++detailSequence,g=getGeneration();detailController?.abort();detailController=new AbortController();detail.value?.showModal()
 try{const result=await lookupWord(word.lemma,detailController.signal);if(n===detailSequence&&g===getGeneration()&&!disposed){entry.value=result.candidates.find(e=>e.entryId===word.entryId);if(!entry.value)detailError.value='词条暂不可用，请重试。'}}
 catch(e){if(n===detailSequence&&g===getGeneration()&&!disposed&&!(e instanceof DOMException&&e.name==='AbortError'))detailError.value=errorMessage(e)}
 finally{if(n===detailSequence)detailLoading.value=false}
}
async function change(word:WordbookWord,kind:'collection'|'proficiency'){
 if(saving.value||loading.value)return
 saving.value=true;emit('busy',true);mutation=new AbortController();const g=getGeneration(),owner=auth.user?.id;const current=()=>g===getGeneration()&&owner===auth.user?.id&&!disposed;error.value='';detailError.value='';notice.value=''
 try{
  if(kind==='collection'){
   if(word.collected&&word.collectionId)await cancelWord(word.collectionId,mutation.signal)
   else{const result=await collectFromDictionary(word.lemma,word.entryId,mutation.signal);if(!current())return;word.collectionId=result.word.id}
   if(!current())return;word.collected=!word.collected;notice.value=word.collected?'已加入生词本。':'已取消收藏，学习进度保留。'
  }else{
   const proficient=word.state!=='PROFICIENT';const result=await setBookWordProficiency(book.value,word.entryId,proficient,mutation.signal)
   if(!current())return
   Object.assign(word,result.word);if(data.value)data.value.counts=result.counts
   notice.value=proficient?'已标记熟练，不再自动复习。':'已恢复原学习状态。'
  }
  if(collected.value||state.value!=='ALL')await load()
  emit('changed')
 }catch(e){if(current()&&!(e instanceof DOMException&&e.name==='AbortError')){error.value=errorMessage(e);detailError.value=error.value}}
 finally{if(!disposed){saving.value=false;emit('busy',false)}}
}
function speak(lang:string){
 voiceError.value='';stopVoice();if(!window.speechSynthesis){voiceError.value='浏览器暂不支持朗读。';return}
 const n=voiceSequence,u=new SpeechSynthesisUtterance(selected.value?.lemma||'');u.lang=lang;u.onerror=e=>{if(n===voiceSequence&&!['canceled','interrupted'].includes(e.error))voiceError.value='朗读暂不可用，可以重试。'};speechSynthesis.speak(u)
}
watch(()=>auth.user?.id,()=>{mutation?.abort();dialog.value?.close();closed();saving.value=false;emit('busy',false)},{flush:'sync'})
onBeforeUnmount(()=>{disposed=true;cancelLoads();mutation?.abort();stopVoice();emit('busy',false)})
defineExpose({open})
</script>

<template>
 <Teleport to="body">
  <dialog ref="dialog" class="wbw-dialog" aria-label="词书词表" @close="closed" @cancel="saving&&$event.preventDefault()">
   <header class="wbw-heading"><div><h2>{{ data?.title||'词书词表' }}</h2><p>{{ isCurrent?'当前学习词书':'仅查看' }}<template v-if="data"> · {{ data.counts.total.toLocaleString() }} 词 · 已覆盖 {{ data.counts.reviewing+data.counts.proficient }} 词</template></p><small v-if="!isCurrent">当前学习来源：{{ sourceTitle }}</small></div><button aria-label="关闭词表" :disabled="saving" @click="dialog?.close()">×</button></header>
   <section class="wbw-filters" aria-label="词表筛选">
    <div class="wbw-tabs" role="group" aria-label="学习状态"><button v-for="tab in tabs" :key="tab.id" :aria-pressed="state===tab.id" :disabled="saving" @click="state=tab.id">{{ tab.label }} <small>{{ data?.counts[tab.count]??'—' }}</small></button></div>
    <div class="wbw-search"><input v-model="query" type="search" maxlength="80" aria-label="搜索英文或中文" placeholder="搜索英文单词或中文释义" :disabled="saving"><select v-model="sort" aria-label="词表排序" :disabled="saving"><option value="BOOK">词书顺序</option><option value="ALPHA">字母顺序</option></select></div>
    <div class="wbw-tools"><label><input v-model="collected" type="checkbox" :disabled="saving">只看已收藏</label><label><input v-model="showMeaning" type="checkbox">显示中文</label><button :disabled="saving" @click="clear">清空筛选</button></div>
   </section>
   <div class="wbw-scroll" :aria-busy="loading">
    <p v-if="error" role="alert">{{ error }} <button :disabled="loading||saving" @click="load()">重新加载词表</button></p>
    <p v-if="notice" role="status" class="wbw-notice">{{ notice }}</p>
    <p v-if="loading" role="status" class="wbw-message">正在加载词表…</p>
    <p v-else-if="!items.length&&!error" class="wbw-message">没有匹配的单词，可以修改搜索或清空筛选。</p>
    <div class="wbw-list" aria-label="词书单词列表">
     <article v-for="word in items" :key="word.entryId" class="wbw-row"><button class="wbw-word" :aria-label="'查看单词 '+word.lemma" @click="show(word)"><strong lang="en">{{ word.lemma }}</strong><span v-if="showMeaning">{{ word.meaning }}</span></button><small class="wbw-state">{{ status(word) }}</small><button class="wbw-star" :aria-label="(word.collected?'取消收藏 ':'收藏 ')+word.lemma" :aria-pressed="word.collected" :disabled="saving||loading" @click="change(word,'collection')">{{ word.collected?'★':'☆' }}</button></article>
    </div>
    <footer v-if="data" class="wbw-more"><span>已显示 {{ items.length }} / {{ data.total }} 词</span><button v-if="page*data.pageSize<data.total" :disabled="loading||saving" @click="load(true)">加载更多</button></footer>
   </div>
   <p class="wbw-footnote">浏览和收藏不算学过。同一个单词的学习状态跨词书共享。</p>
  </dialog>
 </Teleport>
 <VocabularyDetailDialog ref="detail" :title="selected?.lemma||'单词详情'" :busy="saving" @close="closeDetail">
  <template #heading><h2 lang="en">{{ selected?.lemma||'单词详情' }}</h2><PronunciationControls v-if="entry" :phonetic="entry.phonetic" @speak="speak"/></template>
  <p v-if="detailLoading" role="status">正在加载词条…</p><p v-if="detailError" role="alert">{{ detailError }} <button v-if="selected" :disabled="saving" @click="show(selected)">重试</button></p><p v-if="voiceError" role="status">{{ voiceError }}</p>
  <p v-if="notice" role="status">{{ notice }}</p>
  <WordDetails v-if="entry" :entry="entry" compact all-senses/>
  <template #footer><template v-if="selected"><button :disabled="saving||loading" @click="change(selected,'proficiency')">{{ selected.state==='PROFICIENT'?'恢复学习':'标记熟练 · 免复习' }}</button><button class="vd-primary" :disabled="saving||loading" @click="change(selected,'collection')">{{ selected.collected?'取消收藏':'+ 加入生词本' }}</button></template></template>
 </VocabularyDetailDialog>
</template>

<style scoped>
.wbw-dialog{position:fixed;inset:0;margin:auto;box-sizing:border-box;width:min(1000px,calc(100vw - 32px));height:min(820px,calc(100dvh - 48px));max-width:calc(100vw - 32px);max-height:calc(100dvh - 48px);border:1px solid var(--line,#dce1d3);border-radius:22px;background:var(--bg,#f6f6ed);color:var(--ink,#293329);padding:0;overflow:hidden}
.wbw-dialog[open]{display:flex;flex-direction:column}.wbw-dialog::backdrop{background:#26302566;backdrop-filter:blur(3px)}
.wbw-dialog button,.wbw-dialog input,.wbw-dialog select{font:inherit;color:inherit}.wbw-dialog button{cursor:pointer;border:1px solid var(--line,#dce1d3);border-radius:99px;padding:8px 14px;background:var(--surface,#faf9f2)}.wbw-dialog button:disabled{opacity:.55;cursor:default}.wbw-dialog :focus-visible{outline:2px solid var(--accent,#587344);outline-offset:2px}
.wbw-heading{display:flex;justify-content:space-between;gap:16px;padding:22px 26px 12px;flex-shrink:0}.wbw-heading h2{font-size:24px;margin:0;overflow-wrap:anywhere}.wbw-heading p,.wbw-heading small{font-size:12px;color:var(--muted);margin:5px 0}.wbw-heading>button{align-self:flex-start;border:0;font-size:24px;padding:0 5px;background:transparent}
.wbw-filters{padding:4px 26px 12px;border-bottom:1px solid var(--line);flex-shrink:0}.wbw-tabs{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:12px}.wbw-tabs button{border-color:transparent;background:transparent}.wbw-tabs [aria-pressed=true]{background:var(--accent,#4a653d);color:white}.wbw-tabs small{opacity:.8;margin-left:3px;font-size:12px}
.wbw-search{display:flex;gap:10px}.wbw-search input,.wbw-search select{min-width:0;border:1px solid var(--line);border-radius:12px;background:var(--surface);padding:10px 12px;font-size:14px}.wbw-search input{width:100%;flex:1}.wbw-search select{max-width:125px}.wbw-tools{display:flex;align-items:center;gap:18px;margin-top:10px;font-size:12px;color:var(--muted)}.wbw-tools label{display:flex;gap:6px;align-items:center}.wbw-tools input{accent-color:var(--accent)}.wbw-tools button{margin-left:auto;padding:3px 0;border:0;background:transparent;color:var(--accent-dark)}
.wbw-scroll{flex:1;min-height:0;overflow:auto;padding:0 26px;overscroll-behavior:contain;scrollbar-gutter:stable}.wbw-message{color:var(--muted);padding:28px 0;text-align:center;font-size:14px}.wbw-notice{color:var(--accent-dark);font-size:13px}.wbw-list{background:var(--surface)}.wbw-row{display:flex;align-items:center;gap:12px;min-height:64px;border-bottom:1px solid var(--line)}.wbw-dialog .wbw-word{display:grid;grid-template-columns:minmax(100px,1fr) minmax(0,1.5fr);align-items:center;gap:16px;flex:1;min-width:0;text-align:left;padding:12px 0;background:transparent;border:0;border-radius:0}.wbw-word strong{font-size:19px;overflow-wrap:anywhere}.wbw-word span{color:var(--muted);font-size:13px;overflow:hidden;text-overflow:ellipsis;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}.wbw-state{font-size:12px;color:var(--accent-dark);flex-shrink:0}.wbw-dialog .wbw-star{color:var(--accent);font-size:23px;background:transparent;border:0;padding:8px;flex-shrink:0}.wbw-more{display:flex;align-items:center;justify-content:center;gap:16px;padding:18px 0;color:var(--muted);font-size:12px}.wbw-footnote{padding:10px 26px;margin:0;border-top:1px solid var(--line);color:var(--muted);font-size:11px;flex-shrink:0}
@media(max-width:600px){.wbw-dialog{width:calc(100vw - 20px);max-width:calc(100vw - 20px);height:calc(100dvh - 24px);max-height:calc(100dvh - 24px);border-radius:18px}.wbw-heading{padding:16px 14px 10px}.wbw-heading h2{font-size:20px}.wbw-filters{padding:0 14px 10px}.wbw-tabs{gap:2px}.wbw-tabs button{font-size:12px;padding:7px 8px}.wbw-tabs small{font-size:10px}.wbw-scroll{padding:0 14px}.wbw-tools{gap:10px}.wbw-tools button{font-size:11px}.wbw-search{gap:6px}.wbw-search select{max-width:102px;padding:8px 4px;font-size:12px}.wbw-dialog .wbw-word{display:flex;align-items:flex-start;flex-direction:column;gap:3px}.wbw-word strong{font-size:17px}.wbw-row{gap:4px;min-height:65px}.wbw-state{max-width:77px;font-size:10px}.wbw-word span{font-size:12px}.wbw-footnote{padding:10px 14px;font-size:10px}}
</style>

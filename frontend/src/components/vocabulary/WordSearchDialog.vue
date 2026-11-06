<script setup lang="ts">
import PronunciationControls from '@/components/common/PronunciationControls.vue'
import {computed,nextTick,onBeforeUnmount,ref,watch} from 'vue'
import {lookupWord,lookupPhrase,searchDictionary,type DictionaryCandidate,type DictionarySearchItem,type DictionaryPhrase} from '@/api/dictionary'
import {createPhrase,phraseList} from '@/api/vocabulary-phrases'
import {collectFromDictionary} from '@/api/vocabulary'
import {errorMessage} from '@/api/identity'
import {useAuthStore} from '@/stores/auth'
import WordDetails from './WordDetails.vue'
import PhraseExamples from './PhraseExamples.vue'

const props=defineProps<{beforeReveal:(ids:string[])=>Promise<void>;reviewing?:boolean;assisted?:boolean}>()
const emit=defineEmits<{collected:[];busy:[value:boolean]}>()
const auth=useAuthStore()
const dialog=ref<HTMLDialogElement>(),field=ref<HTMLInputElement>()
const query=ref(''),items=ref<DictionarySearchItem[]>([]),entry=ref<DictionaryCandidate>()
const phrase=ref<DictionaryPhrase>(),phraseSaved=ref(false)
const loading=ref(false),saving=ref(false),error=ref(''),voiceError=ref(''),notice=ref('')
type RecentWord={word:string;entryId:string}
const recent=ref<RecentWord[]>([]),active=ref(0)
let controller:AbortController|undefined,timer:ReturnType<typeof setTimeout>|undefined,sequence=0
const composing=ref(false)
let opener:HTMLElement|null=null
const historyKey=computed(()=>`lingua-dictionary-recent:${auth.user?.id||'guest'}`)
const valid=computed(()=>/^[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*(?:\s+[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*){0,9}$/.test(query.value.trim())&&query.value.trim().length<=80)
function revealIds(item:DictionarySearchItem){return item.relatedEntryIds??(item.kind==='PHRASE'?[]:[item.entryId])}

function readHistory(){
  try{
    const stored=JSON.parse(localStorage.getItem(historyKey.value)||'[]')
    recent.value=Array.isArray(stored)?stored.filter((v:unknown):v is RecentWord=>!!v&&typeof v==='object'&&'word' in v&&typeof v.word==='string'&&/^[A-Za-z' -]{1,80}$/.test(v.word)&&'entryId' in v&&typeof v.entryId==='string').slice(0,8):[]
  }catch{recent.value=[]}
}
function remember(word:string,entryId:string){
  recent.value=[{word,entryId},...recent.value.filter(item=>item.word!==word)].slice(0,8)
  try{localStorage.setItem(historyKey.value,JSON.stringify(recent.value))}catch{/* history is optional */}
}
function clearHistory(){recent.value=[];try{localStorage.removeItem(historyKey.value)}catch{/* optional storage */}}
function cancel(){sequence++;controller?.abort();if(timer)clearTimeout(timer);loading.value=false}
async function open(event:Event){
  opener=event.currentTarget as HTMLElement
  error.value='';notice.value='';readHistory()
  dialog.value?.showModal()
  if(!query.value.trim()&&recent.value.length){
    loading.value=true
    const current=sequence;controller=new AbortController()
    try{
      const ids:string[]=[]
      for(const item of recent.value){
        if(item.word.includes(' ')){
          const result=await searchDictionary(item.word,controller.signal)
          ids.push(...result.items.filter(found=>found.lemma===item.word).flatMap(revealIds))
        }else ids.push(item.entryId)
      }
      if(current===sequence)await props.beforeReveal(ids)
    }
    catch(e){if(current===sequence)error.value=errorMessage(e)}
    finally{if(current===sequence)loading.value=false}
  }else if(query.value.trim())await search()
  await nextTick();field.value?.focus()
}
function closed(){
  cancel()
  query.value=''
  items.value=[]
  entry.value=undefined
  phrase.value=undefined;phraseSaved.value=false
  active.value=0
  composing.value=false
  error.value=''
  notice.value=''
  voiceError.value=''
  window.speechSynthesis?.cancel()
  opener?.isConnected&&opener.focus({preventScroll:true})
}
async function search(){
  if(!dialog.value?.open||!valid.value||composing.value)return
  cancel();const current=sequence;controller=new AbortController();loading.value=true;error.value=''
  try{
    const result=await searchDictionary(query.value.trim(),controller.signal)
    if(current!==sequence)return
    await props.beforeReveal(result.items.flatMap(revealIds))
    if(current===sequence){items.value=result.items;active.value=0}
  }catch(e){if(current===sequence&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e)}
  finally{if(current===sequence)loading.value=false}
}
watch(query,()=>{
  cancel();items.value=[];entry.value=undefined;phrase.value=undefined;phraseSaved.value=false;error.value='';notice.value='';active.value=0
  if(valid.value&&!composing.value){loading.value=true;timer=setTimeout(search,200)}
})
async function choose(item:DictionarySearchItem){
  cancel();const current=sequence;controller=new AbortController();loading.value=true;error.value='';notice.value=''
  try{
    if(item.kind==='PHRASE'){
      await props.beforeReveal(revealIds(item))
      if(current!==sequence)return
      const result=await lookupPhrase(item.lemma,controller.signal)
      if(current!==sequence)return
      if(!result.phrase)throw new Error('词组暂不可用，请重新搜索。')
      let saved=false
      if(auth.user){const existing=await phraseList(item.lemma,false,1,controller.signal);saved=existing.items.some(p=>p.text.toLowerCase()===item.lemma.toLowerCase()&&p.active)}
      if(current!==sequence)return
      phrase.value=result.phrase;phraseSaved.value=saved;remember(item.lemma,item.entryId);return
    }
    const result=await lookupWord(item.canonicalLemma||item.lemma,controller.signal)
    if(current!==sequence)return
    await props.beforeReveal(result.candidates.map(candidate=>candidate.entryId))
    if(current!==sequence)return
    entry.value=result.candidates.find(candidate=>candidate.entryId===item.entryId)
    if(!entry.value)throw new Error('词条暂不可用，请重新搜索。')
    remember(entry.value.lemma,entry.value.entryId)
  }catch(e){if(current===sequence&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e)}
  finally{if(current===sequence)loading.value=false}
}
function move(delta:number){if(!items.value.length)return;active.value=(active.value+delta+items.value.length)%items.value.length}
async function enter(){if(items.value[active.value])await choose(items.value[active.value]!);else await search()}
async function back(){entry.value=undefined;phrase.value=undefined;notice.value='';voiceError.value='';await nextTick();field.value?.focus()}
async function collectPhrase(){
  if(!phrase.value||saving.value)return
  const selected=phrase.value,owner=auth.user?.id
  saving.value=true;emit('busy',true);error.value=''
  try{
    await createPhrase({text:selected.text,meaningZh:selected.translationZh})
    if(auth.user?.id===owner&&phrase.value===selected&&dialog.value?.open){phraseSaved.value=true;notice.value='已加入词组本';emit('collected')}
  }catch(e){if(auth.user?.id===owner&&dialog.value?.open)error.value=errorMessage(e)}
  finally{saving.value=false;emit('busy',false)}
}
async function collect(){
  if(!entry.value||saving.value)return
  const selected=entry.value,owner=auth.user?.id
  saving.value=true;emit('busy',true);error.value=''
  try{
    const result=await collectFromDictionary(selected.lemma,selected.entryId)
    if(auth.user?.id===owner&&entry.value===selected&&dialog.value?.open){entry.value={...selected,collection:{wordId:result.word.id,active:true}};notice.value='已加入生词本';emit('collected')}
  }catch(e){if(auth.user?.id===owner&&dialog.value?.open)error.value=errorMessage(e)}
  finally{saving.value=false;emit('busy',false)}
}
function speak(lang:string){
  voiceError.value=''
  if(!window.speechSynthesis){voiceError.value='浏览器暂不支持朗读。';return}
  speechSynthesis.cancel()
  const utterance=new SpeechSynthesisUtterance(phrase.value?.text||entry.value?.lemma||'')
  utterance.lang=lang
  const preferred=speechSynthesis.getVoices().find(voice=>voice.lang.replace('_','-').toLowerCase()===lang.toLowerCase())
  if(preferred)utterance.voice=preferred
  utterance.onerror=e=>{if(!['canceled','interrupted'].includes(e.error))voiceError.value='朗读暂不可用，可以重试。'}
  speechSynthesis.speak(utterance)
}
watch(()=>auth.user?.id,()=>{dialog.value?.close();cancel();query.value='';items.value=[];entry.value=undefined;recent.value=[]})
onBeforeUnmount(()=>{cancel();window.speechSynthesis?.cancel()})
defineExpose({open})
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialog" class="word-search-dialog" aria-label="查单词" @close="closed" @cancel="saving&&$event.preventDefault()">
      <header class="ws-toolbar">
        <button v-if="entry||phrase" type="button" aria-label="返回搜索结果" @click="back">‹</button>
        <label class="ws-field">
          <span aria-hidden="true">⌕</span>
          <input ref="field" v-model="query" type="search" role="combobox" aria-label="搜索英文单词" aria-controls="ws-results" :aria-expanded="!entry&&!phrase&&items.length>0" :aria-activedescendant="!entry&&!phrase&&items.length?'ws-option-'+active:undefined" placeholder="搜索英文单词或词组" maxlength="80" autocomplete="off" spellcheck="false" @keydown.down.prevent="move(1)" @keydown.up.prevent="move(-1)" @keydown.enter.prevent="enter" @compositionstart="composing=true" @compositionend="composing=false;search()">
        </label>
        <button type="button" aria-label="关闭查词" :disabled="saving" @click="dialog?.close()">×</button>
      </header>
      <div class="ws-body" :aria-busy="loading"><p v-if="reviewing" role="status" class="ws-muted">{{ assisted?'当前词已记为辅助，稍后会再练。':'复习时查到当前词会记为辅助。' }}</p>
        <p v-if="error" role="alert" class="ws-error">{{ error }} <button v-if="valid" @click="search">重试</button></p>
        <p v-if="loading" role="status" class="ws-loading">正在搜索…</p>
        <template v-else-if="phrase">
          <div class="ws-entry-heading"><div><h2 lang="en">{{ phrase.text }}</h2><span class="ws-kind">词组</span></div><PronunciationControls @speak="speak"/></div>
          <p class="ws-phrase-meaning">{{ phrase.translationZh }}</p>
          <PhraseExamples :text="phrase.text"/>
          <p v-if="voiceError" role="status" class="ws-muted">{{ voiceError }}</p>
          <footer class="ws-footer"><span role="status">{{ notice }}</span><button class="ws-primary" :disabled="saving||phraseSaved" @click="collectPhrase">{{ phraseSaved?'已加入词组本':saving?'正在加入…':'+ 加入词组本' }}</button></footer>
        </template>
        <template v-else-if="entry">
          <div class="ws-entry-heading">
            <div><h2 lang="en">{{ entry.lemma }}</h2></div>
            <PronunciationControls :phonetic="entry.phonetic" @speak="speak"/>
          </div>
          <p v-if="voiceError" role="status" class="ws-muted">{{ voiceError }}</p>
          <WordDetails :entry="entry" compact all-senses/>
          <footer class="ws-footer"><span role="status">{{ notice }}</span><button class="ws-primary" :disabled="saving||entry.collection?.active" @click="collect">{{ entry.collection?.active?'已加入生词本':saving?'正在加入…':'+ 加入生词本' }}</button></footer>
        </template>
        <template v-else-if="!query.trim()&&!error">
          <div class="ws-recent-heading"><h2>最近查词</h2><button v-if="recent.length" @click="clearHistory">清空</button></div>
          <div class="ws-recent"><button v-for="word in recent" :key="word.word" @click="query=word.word">{{ word.word }}</button></div>
          <p v-if="!recent.length" class="ws-muted">输入英文单词或词组，查看释义并收藏。</p>
        </template>
        <p v-else-if="!valid" class="ws-muted">请输入英文单词或词组，最多80个字符。</p>
        <template v-else>
          <ul id="ws-results" role="listbox" aria-label="单词搜索结果">
            <li v-for="(item,index) in items" :key="item.entryId+':'+item.lemma">
              <button :id="'ws-option-'+index" role="option" :aria-selected="index===active" @click="choose(item)" @pointerenter="active=index">
                <strong lang="en">{{ item.lemma }} <small v-if="item.kind==='PHRASE'" class="ws-kind">词组</small><small v-else-if="item.kind==='FORM'" class="ws-kind">词形 · {{ item.canonicalLemma }}</small></strong><span>{{ item.partOfSpeech }} {{ item.definitionZh }}</span>
              </button>
            </li>
          </ul>
          <p v-if="!items.length&&!error" class="ws-muted">本地词典暂未收录这个词，请检查拼写。</p>
        </template>
      </div>
    </dialog>
  </Teleport>
</template>

<style scoped>
.word-search-dialog{--vl-green:#4a653d;--vl-line:#dce1d3;--vl-soft:#788074;--vl-pale:#eef2e6;width:min(480px,calc(100vw - 28px));height:min(500px,calc(100dvh - 56px));max-height:calc(100dvh - 56px);padding:0;border:1px solid var(--border,#dce1d3);border-radius:22px;background:var(--surface,#faf9f2);color:var(--text,#293329);overflow:hidden}
.word-search-dialog[open]{display:flex;flex-direction:column}
.word-search-dialog::backdrop{background:#26302555;backdrop-filter:blur(3px)}
.word-search-dialog button{font:inherit;cursor:pointer;border:1px solid var(--border,#dce1d3);border-radius:12px;background:transparent;color:inherit;padding:8px 12px}
.word-search-dialog button:disabled{opacity:.6;cursor:default}
.word-search-dialog button:focus-visible,.word-search-dialog input:focus-visible{outline:2px solid #7e9867;outline-offset:2px}
.ws-toolbar{flex-shrink:0;display:flex;gap:8px;padding:12px;border-bottom:1px solid var(--border,#dce1d3);align-items:center}
.ws-toolbar>button{border:0;font-size:24px;padding:2px 6px}
.ws-field{display:flex;align-items:center;gap:8px;min-width:0;flex:1;padding:8px 12px;border:1px solid var(--border,#dce1d3);border-radius:99px;background:var(--surface-2,#f0f2e8)}
.ws-field input{width:100%;min-width:0;border:0;outline:none;background:transparent;font:inherit;color:inherit}
.ws-body{flex:1;min-height:0;overflow:auto;padding:14px 18px;overscroll-behavior:contain}
.ws-body h2{font-size:20px;margin:0}
.ws-loading{margin:0;padding:18px 0;text-align:center;color:#788074;font-size:14px}
.ws-muted{color:#788074;font-size:13px;line-height:1.7}
.ws-error{color:#a33c37;font-size:13px;line-height:1.6}
.ws-kind{font:12px/1.5 sans-serif;color:#627457;background:#edf1e6;padding:2px 6px;border-radius:6px;white-space:nowrap}.ws-phrase-meaning{font-size:17px;line-height:1.8;overflow-wrap:anywhere}
.ws-entry-heading{display:grid;gap:8px;margin:3px 0 16px}
.ws-entry-heading>div:first-child{display:flex;align-items:baseline;gap:12px;min-width:0;flex-wrap:wrap}
.ws-entry-heading h2{font:600 30px Georgia,serif;overflow-wrap:anywhere}
.ws-voices{display:flex;gap:6px;flex-shrink:0}
.ws-voices button{border-radius:50%;padding:5px 8px;font-size:13px}
.ws-body ul{list-style:none;padding:0;margin:0}
.ws-body [role=option]{display:block;text-align:left;width:100%;border:0;padding:12px 10px;border-radius:10px}
.ws-body [role=option][aria-selected=true]{background:#eaf0e2}
.ws-body [role=option] strong{display:block;font-size:18px}
.ws-body [role=option] span{display:block;color:#788074;font-size:13px;margin-top:5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.ws-footer{display:flex;justify-content:space-between;align-items:center;gap:10px;margin-top:18px;padding:10px 0}
.ws-footer span{font-size:13px;color:#587344}
.ws-footer .ws-primary{background:#4a653d;color:#fff;border-color:#4a653d;border-radius:99px}
.ws-recent-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}
.ws-recent-heading h2{font-size:14px;color:#788074}
.ws-recent-heading button{border:0;font-size:12px;color:#587344}
.ws-recent{display:flex;gap:8px;flex-wrap:wrap}
.ws-body :deep(.vl-dictionary-summary){display:block}.ws-body :deep(.vl-senses){text-align:left}
.ws-body :deep(.vl-definition-meaning){font-size:16px;line-height:1.6;margin:6px 0}
.ws-body :deep(.vl-fixed-examples){margin-top:16px}
.ws-body :deep(.vl-fixed-examples h3){font-size:13px;color:#587344}
.ws-body :deep(.vl-example){margin:8px 0;padding:6px 0;border:0;background:transparent}
.ws-body :deep(.vl-example p){font:18px/1.55 Georgia,serif;margin:0}
.ws-body :deep(.vl-example span){display:block;font-size:13px;line-height:1.6;color:#788074}
.ws-body :deep(.vl-dictionary-more){border-top:1px solid #dce1d3;padding:8px 0;margin-top:12px}
.ws-body :deep(summary){cursor:pointer;color:#587344;font-size:13px}
.ws-body :deep(.vl-related-words){font-size:13px}
@media(max-width:520px){.word-search-dialog{--vl-green:#4a653d;--vl-line:#dce1d3;--vl-soft:#788074;--vl-pale:#eef2e6;width:calc(100vw - 24px);height:min(480px,calc(100dvh - 40px));max-height:calc(100dvh - 40px);border-radius:18px}.ws-toolbar{padding:12px}.ws-body{padding:14px 16px}}
</style>

<script setup lang="ts">
import PhraseSaveButton from '@/components/vocabulary/PhraseSaveButton.vue'
import {phraseSourceFromRange} from '@/vocabulary/phrase-source'
import {computed,inject,onMounted,onBeforeUnmount,ref,watch,nextTick} from 'vue'
import type {ArticleVersion} from '@/api/admin-articles'
import {useAuthStore} from '@/stores/auth'
import {lookupWord,lookupPhrase,type LookupResult} from '@/api/dictionary'
import {selectionReference,type PracticeSelectionContext} from '@/api/selection-translation'
import {localSelectionWord} from '@/reading/local-selection-word'
import {useSelectionTranslation} from '@/reading/useSelectionTranslation'
import {placeSelectionAction,lastVisibleSelectionRect,completeWordSelection,practiceSelectionText,bindSettledSelection,sourceWordAtPoint} from '@/reading/selection-action'
import {practiceSelectionContextKey} from '@/practice/selection-context'
import {errorMessage} from '@/api/identity'
import WordLookupPanel from '@/components/reading/WordLookupPanel.vue'
import '@/styles/reading.css'
import '@/styles/reading-detail.css'
const props=defineProps<{article:ArticleVersion}>()
const auth=useAuthStore(),provided=inject(practiceSelectionContextKey,undefined),marker=ref<HTMLElement>(),button=ref<HTMLElement>(),dialog=ref<HTMLDialogElement>(),slot=ref('')
const context=computed<PracticeSelectionContext>(()=>provided?.value.paperAttemptId?{paperAttemptId:provided.value.paperAttemptId,slot:slot.value}:{practiceAttemptId:provided?.value.practiceAttemptId})
const enabled=computed(()=>!!provided?.value.enabled&&!!auth.user&&!!(context.value.practiceAttemptId||context.value.paperAttemptId&&slot.value))
const partial=ref(false)
const selected=ref(''),snapshot=ref(''),position=ref<{left:number;top:number}|null>(null),text=ref(''),source=ref(''),busy=ref(false),error=ref(''),word=ref(''),wordResult=ref<LookupResult|null>(null),wordBusy=ref(false),wordError=ref(''),opened=ref(false)
const online=useSelectionTranslation();let range:Range|null=null,root:HTMLElement|undefined,controller:AbortController|undefined,generation=0,disposeSelection:(()=>void)|undefined
const style=computed(()=>position.value?{left:position.value.left+'px',top:position.value.top+'px'}:{})
const single=computed(()=>/^[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*$/.test(selected.value)&&selected.value.length<=60)
function wordAnchor(){return range?.getBoundingClientRect()??null}
function place(){if(!range){position.value=null;return}const rect=lastVisibleSelectionRect(Array.from(range.getClientRects()),innerWidth,innerHeight);position.value=rect?placeSelectionAction(rect,button.value?.offsetWidth??160,button.value?.offsetHeight??44,innerWidth,innerHeight):null}
function capture(){
 if(dialog.value?.open||word.value)return
 selected.value='';range=null;position.value=null
 const selection=getSelection(),chosen=selection?.rangeCount?selection.getRangeAt(0):null;if(!chosen||chosen.collapsed||!root)return
 if(!root.contains(chosen.startContainer)||!root.contains(chosen.endContainer))return
 const start=chosen.startContainer.parentElement?.closest('[data-practice-source]'),end=chosen.endContainer.parentElement?.closest('[data-practice-source]');if(!start||!end)return
 if(Array.from(root.querySelectorAll('button:not([data-practice-gap]),input,textarea,select')).some(el=>chosen.intersectsNode(el)))return
 const value=practiceSelectionText(chosen,root);if(!value||Array.from(value).length>1000||!/[A-Za-z]/.test(value))return
 const canonical=props.article.content.paragraphs.flatMap(p=>p.sentences.map(s=>s.text)).join(' ').replace(/\s+/g,' ').trim();if(!canonical.includes(value))return
 selected.value=value;partial.value=single.value&&!completeWordSelection(chosen);if((!single.value||partial.value)&&!enabled.value){selected.value='';return}range=chosen.cloneRange();place();void nextTick(place)
}
function cancel(){generation++;controller?.abort();busy.value=false;online.cancel()}
function closeWord(){cancel();word.value='';wordResult.value=null;wordBusy.value=false;wordError.value=''}
async function openWord(){if(!single.value&&!word.value)return;const value=word.value||selected.value;cancel();const current=generation;word.value=value;wordResult.value=null;wordBusy.value=true;wordError.value='';controller=new AbortController();try{const result=await lookupWord(value,controller.signal);if(current===generation)wordResult.value=result}catch(e){if(current===generation)wordError.value=errorMessage(e)}finally{if(current===generation)wordBusy.value=false}}
const wordExample=computed(()=>props.article.content.paragraphs.flatMap(p=>p.sentences).find(s=>s.text.includes(word.value||snapshot.value))?.text)
function clickWord(event:MouseEvent){if(!root||word.value||dialog.value?.open||!getSelection()?.isCollapsed)return;const chosen=sourceWordAtPoint(event.clientX,event.clientY,root);if(!chosen)return;const value=chosen.toString();const full=props.article.content.paragraphs.flatMap(p=>p.sentences.map(s=>s.text)).join(' ');if(!full.includes(value))return;range=chosen;selected.value=value;partial.value=false;void openWord()}
async function translateMissing(){const value=word.value;closeWord();selected.value=value;await translate()}
function selectedWord(){if(word.value||dialog.value?.open)return;capture();if(single.value&&!partial.value)void openWord()}
async function translate(){snapshot.value=selected.value;opened.value=true;dialog.value?.showModal();await query()}
function closed(){opened.value=false;cancel()}
async function query(){
 cancel();online.clear();text.value='';source.value='';error.value='';if(!enabled.value)return;busy.value=true;const current=generation;controller=new AbortController();const signal=controller.signal
 try{const local=await localSelectionWord(snapshot.value,signal);if(current!==generation)return;if(local){wordResult.value=local;word.value=snapshot.value;wordError.value='';wordBusy.value=false;dialog.value?.close();opened.value=false;return}
  const reference=await selectionReference({articleId:props.article.articleId,versionId:props.article.id,text:snapshot.value,mode:'TRANSLATION',...context.value},signal);if(current!==generation)return;if(reference.text){text.value=reference.text;source.value=reference.source||'参考译文';return}
  if(/^[A-Za-z]+(?:['’-][A-Za-z]+)*(?:\s+[A-Za-z]+(?:['’-][A-Za-z]+)*){1,5}$/.test(snapshot.value)){const phrase=await lookupPhrase(snapshot.value,signal);if(current!==generation)return;if(phrase.found&&phrase.phrase){text.value=phrase.phrase.translationZh;source.value=phrase.sourceNote;return}}
  if(current===generation)await online.start(props.article.articleId,props.article.id,snapshot.value,false,'TRANSLATION',context.value)
 }catch(e){if(current===generation&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e)}finally{if(current===generation)busy.value=false}
}
async function confirmResult(){if(enabled.value)await online.start(props.article.articleId,props.article.id,snapshot.value,online.task.value?.status==='FAILED','TRANSLATION',context.value)}
function reset(){cancel();online.clear();opened.value=false;dialog.value?.close();closeWord();selected.value='';range=null;position.value=null;online.bindIdentity(auth.user?.id||'guest')}
watch(()=>[auth.user?.id,props.article.id,JSON.stringify(provided?.value)],reset,{immediate:true})
onMounted(()=>{root=marker.value?.parentElement??undefined;root?.addEventListener('click',clickWord);slot.value=root?.closest<HTMLElement>('[data-slot]')?.dataset.slot||'';disposeSelection=bindSettledSelection(capture,()=>{if(word.value||dialog.value?.open)return;selected.value='';position.value=null;range=null},selectedWord);window.addEventListener('scroll',place,true);window.addEventListener('resize',place)})
onBeforeUnmount(()=>{reset();root?.removeEventListener('click',clickWord);disposeSelection?.();window.removeEventListener('scroll',place,true);window.removeEventListener('resize',place)})
</script>
<template><span ref="marker" hidden></span><Teleport to="body">
 <div v-if="selected&&position&&!word&&!opened" ref="button" class="selection-lookup-button selection-action-toolbar" :style="style" @pointerdown.prevent><button type="button" class="button-secondary" @click="single&&!partial?openWord():translate()">{{single&&!partial?'查询选中单词：'+selected:'翻译'}}</button><PhraseSaveButton v-if="enabled" :text="selected" :source="phraseSourceFromRange(range,article,context)"/></div>
 <WordLookupPanel v-if="word" :word="word" :example="wordExample" :anchor="wordAnchor" :result="wordResult" :busy="wordBusy" :error="wordError" @close="closeWord" @retry="openWord"><template #missing><button v-if="enabled" type="button" class="button-secondary" @click="translateMissing">翻译</button></template></WordLookupPanel>
 <dialog ref="dialog" class="reader-sentence-dialog practice-selected-dialog" @close="closed"><header><h2>选词与选段释义</h2><button type="button" aria-label="关闭选段翻译" @click="dialog?.close()">×</button></header><p lang="en">{{snapshot.replace(/\{\{\d+\}\}/g,'_____')}}</p>
 <p v-if="text">{{text}}</p><p v-else-if="busy||online.busy.value" role="status">正在翻译选中内容…</p>
 <div v-else-if="error" role="alert"><p>{{error}}</p><button type="button" class="button-secondary" @click="query">重试查询</button></div>
 <p v-else-if="online.task.value?.status==='SUCCEEDED'">{{online.task.value.translation}}</p>
 <div v-else-if="online.error.value" role="alert"><p>{{online.error.value}}</p><button type="button" class="button-secondary" @click="confirmResult">确认翻译结果</button></div>
 <div v-else-if="online.task.value?.status==='FAILED'" role="alert"><p>翻译未完成（{{online.task.value.errorCode}}）。重新翻译会发起新请求并计入字符额度。</p><button type="button" class="button-secondary" @click="confirmResult">重新翻译</button></div>
 <div v-else-if="online.waiting.value"><p>翻译仍在处理中。</p><button type="button" class="button-secondary" @click="confirmResult">查看翻译结果</button></div>
 <p v-if="source" class="reader-translation-source">{{source}}</p><p v-else-if="online.task.value" class="reader-translation-source">本次 {{online.task.value.sourceCharacters}} 字符</p>
 </dialog></Teleport></template>

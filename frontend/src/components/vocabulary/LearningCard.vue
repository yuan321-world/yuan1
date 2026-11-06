<script setup lang="ts">
import PronunciationControls from '@/components/common/PronunciationControls.vue'
import {computed,nextTick,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import type {Command,Session} from '@/api/vocabulary-learning'
import {retainedSpellingPrefix,validateSpellingPrefix} from '@/vocabulary/spelling'
import {collectFromLearning} from '@/api/vocabulary'
import {errorMessage} from '@/api/identity'
import {getGeneration} from '@/auth/token-session'
import WordDetails from './WordDetails.vue'
const props=defineProps<{session:Session;busy:boolean;owner:string}>()
const emit=defineEmits<{answer:[value:Partial<Command>];next:[]}>()
const spelling=ref(''),localError=ref(''),hadError=ref(false),pasted=ref(false),input=ref<HTMLInputElement>(),voiceError=ref(''),collectBusy=ref(false),collectMessage=ref('')
let composing=false,submitted=false,timer:ReturnType<typeof setTimeout>|undefined,disposed=false,voiceSequence=0
const current=computed(()=>props.session.current!),definition=computed(()=>props.session.phase==='DEFINITION'),isSpelling=computed(()=>props.session.mode==='SPELLING'),v2=computed(()=>props.session.flowVersion==='v2'),copy=computed(()=>v2.value&&isSpelling.value&&props.session.kind==='NEW')
const typingTarget = computed(() => current.value.spellingTarget || (copy.value ? current.value.lemma : '') || '')
const compact = computed(() => definition.value && !copy.value)
const card = ref<HTMLElement>()
const phonetic = computed(() => isSpelling.value && !definition.value && !copy.value ? '' : current.value.phonetic || current.value.detail?.phonetic)
watch(() => current.value?.id, async (id, previous) => {
  if (!id || !previous || isSpelling.value) return
  await nextTick()
  if (card.value && card.value.getBoundingClientRect().top < 0) card.value.scrollIntoView({block:'start'})
})
watch(compact, async revealed => {
  if (!revealed) return
  await nextTick()
  card.value?.querySelector('.vl-compact-actions .vl-primary')?.scrollIntoView({ block: 'nearest' })
})
const attemptKey=computed(()=>`${props.owner}:${props.session.id}:${current.value?.id}:${v2.value?current.value?.attemptId:props.session.version}`)
const storageKey=computed(()=>`lingua-vocabulary-input:${attemptKey.value}`)
function persist(){try{sessionStorage.setItem(storageKey.value,JSON.stringify({value:spelling.value,hadError:hadError.value,pasted:pasted.value}))}catch{/* optional draft */}}
watch(attemptKey,async()=>{spelling.value='';localError.value='';hadError.value=false;pasted.value=false;submitted=false;composing=false;voiceError.value='';collectMessage.value='';voiceSequence++;window.speechSynthesis?.cancel();try{const saved=JSON.parse(sessionStorage.getItem(storageKey.value)||'null');if(saved&&typeof saved.value==='string'){spelling.value=retainedSpellingPrefix(saved.value,current.value?.spellingTarget||'');hadError.value=!!saved.hadError;pasted.value=!!saved.pasted}}catch{/* optional draft */}await nextTick();input.value?.focus({preventScroll:true})},{immediate:true})
watch(()=>props.busy,async busy=>{if(!busy){submitted=false;await nextTick();if(!definition.value)input.value?.focus({preventScroll:true})}})
function check(){if(composing||props.busy||submitted||definition.value)return;const target=current.value.spellingTarget||'',status=validateSpellingPrefix(spelling.value,target)
 if(status==='INVALID'){spelling.value=retainedSpellingPrefix(spelling.value,target);hadError.value=true;localError.value='这个字母不对，已保留正确部分。';persist();if(v2.value){submitted=true;emit('answer',{action:'ERROR'})}return}
 localError.value='';persist();if(status==='COMPLETE'){submitted=true;emit('answer',{action:'SPELLING',spelling:spelling.value,hadSpellingError:hadError.value,...(v2.value?{assisted:pasted.value}:{})})}
}
function paste(){if(props.busy||definition.value)return;pasted.value=true;hadError.value=true;persist()}
function hint(){if(props.busy)return;persist();submitted=true;emit('answer',{action:'HINT',spelling:spelling.value})}
function stopAuto(){if(timer)clearTimeout(timer);timer=undefined}
function schedule(){stopAuto();if(props.busy||!definition.value||!copy.value||document.hidden)return;const key=`${attemptKey.value}:${props.session.version}`;timer=setTimeout(()=>{if(!disposed&&!document.hidden&&!document.querySelector('dialog[open]')&&!props.busy&&definition.value&&`${attemptKey.value}:${props.session.version}`===key)emit('next')},0)}
watch(()=>[props.busy,props.session.version],schedule,{immediate:true})
function visibility(){if(document.hidden)stopAuto();else schedule()}
async function collect(){if(collectBusy.value||!current.value.entryId)return;const generation=getGeneration(),id=current.value.id;collectBusy.value=true;collectMessage.value='';try{await collectFromLearning(props.session.id,current.value.entryId);if(!disposed&&generation===getGeneration()&&id===current.value.id)collectMessage.value='已加入生词本'}catch(e){if(!disposed&&generation===getGeneration()&&id===current.value.id)collectMessage.value=errorMessage(e)}finally{collectBusy.value=false}}
function speak(locale: 'en-GB' | 'en-US'){voiceError.value='';if(!window.speechSynthesis){voiceError.value='浏览器暂不支持朗读。';return}const sequence=++voiceSequence;speechSynthesis.cancel();const voice=new SpeechSynthesisUtterance(current.value.lemma||current.value.spellingTarget||'');voice.lang=locale;const preferred=speechSynthesis.getVoices().find(item=>item.lang.replace('_','-').toLowerCase()===locale.toLowerCase());if(preferred)voice.voice=preferred;voice.onerror=e=>{if(sequence===voiceSequence&&e.error!=='canceled'&&e.error!=='interrupted')voiceError.value='合成朗读暂不可用，仍可继续练习。'};speechSynthesis.speak(voice)}
onMounted(()=>document.addEventListener('visibilitychange',visibility))
onBeforeUnmount(()=>{disposed=true;stopAuto();voiceSequence++;window.speechSynthesis?.cancel();document.removeEventListener('visibilitychange',visibility)})
</script>
<template><section ref="card" class="vl-card" :class="{ 'vl-compact-card': compact, 'vl-look-card': !isSpelling || compact }" aria-label="当前单词" :aria-busy="busy"><div class="vl-progress-label"><span>本轮已完成 {{ session.passed }} / {{ session.total }}</span><span>{{ copy?'新词跟打':isSpelling?'复习默写':'看词学习' }}</span></div><progress :value="session.passed" :max="session.total" aria-label="本轮通过进度"/>
<div class="vl-card-head">
  <div v-if="!copy" class="vl-word-heading"><h2>{{ isSpelling&&!definition?'根据中文，写出英文':current.lemma }}</h2></div>
  <PronunciationControls class="vl-pronunciations" :phonetic="phonetic" @speak="speak"/>
</div><p v-if="voiceError" role="status" class="vl-muted">{{ voiceError }}</p>
<template v-if="!definition||copy"><template v-if="isSpelling"><p v-if="!copy" class="vl-spelling-meaning"><small>{{ current.partOfSpeech }}</small> {{ current.meaning }}</p><form @submit.prevent="check"><label class="vl-sr" for="vl-spelling">输入英文拼写</label><div class="vl-typing" :class="{'vl-copy':copy}" :style="{'--word-length':typingTarget.length||5}"><span v-if="copy" class="vl-ghost" aria-hidden="true">{{ typingTarget }}</span><input id="vl-spelling" ref="input" v-model="spelling" class="vl-spelling-input" :class="{'is-invalid':localError}" :placeholder="copy?'':'_'.repeat(typingTarget.length)" :disabled="busy&&!copy" :readonly="copy&&(busy||definition)" :aria-invalid="!!localError" aria-describedby="vl-spelling-error vl-spelling-help" autocomplete="off" autocapitalize="none" autocorrect="off" :spellcheck="false" maxlength="64" lang="en" @input="check" @paste="paste" @keydown="($event.repeat||busy)&&$event.preventDefault()" @compositionstart="composing=true" @compositionend="composing=false;check()"></div><p id="vl-spelling-error" class="vl-input-error" role="alert">{{ localError }}</p><div v-if="!copy&&v2" class="vl-spelling-actions"><button type="button" :disabled="busy" @click="hint">提示一个字母</button><button type="button" :disabled="busy" @click="emit('answer',{action:'UNKNOWN'})">查看释义</button></div><p id="vl-spelling-help" class="vl-muted">{{ copy?'照着英文连续跟打，整轮完成后一起看释义。':'保留正确字母；提示、输错或粘贴后会再练一次。' }}</p><p v-if="!copy&&current.hintCount" class="vl-hint" role="status">本次使用了辅助 · 提示：{{ current.hintPrefix }}</p></form></template>
<template v-else><p v-if="session.kind==='NEW'&&v2" class="vl-spelling-meaning">{{ current.partOfSpeech }} {{ current.meaning }}</p><div class="vl-binary"><button :disabled="busy" @click="emit('answer',{action:'UNKNOWN'})">不认识</button><button class="vl-primary" :disabled="busy" @click="emit('answer',{action:'KNOWN'})">{{ session.kind==='NEW'&&v2?'学会了':'认识' }}</button></div><details v-if="current.options.length&&(!v2||session.kind==='REVIEW')" :open="!v2"><summary>选择中文释义</summary><div class="vl-choices"><button v-for="(option,index) in current.options" :key="option.id" :disabled="busy" @click="emit('answer',{action:'CHOICE',optionId:option.id})"><small>{{ 'ABCD'[index] }}</small>{{ option.text }}</button></div></details></template></template>
<section v-else-if="!copy" class="vl-definition" aria-label="单词释义"><div class="vl-result-note" role="status"><strong>{{ current.outcome?.correct?'本轮通过':'先记住这一次' }}</strong><span>{{ current.outcome?.correct?'看完释义，再继续。':'这一次使用辅助或出现错误，继续后在队尾再练。' }}</span></div>
  <template v-if="compact">
    <component :is="current.detail ? WordDetails : 'div'" :key="attemptKey" v-bind="current.detail ? {entry: current.detail, compact: true, exampleSource: current.source} : {}">
      <template v-if="!current.detail"><p v-for="(sense,index) in current.definition?.senses.slice(0,2)" :key="index" class="vl-definition-meaning">{{ sense.partOfSpeech }} {{ sense.definitionZh }}</p></template>
      <div class="vl-compact-actions"><div v-if="v2" class="vl-study-tools"><button :disabled="busy||collectBusy||!current.entryId" @click="collect">{{ collectBusy?'正在加入…':'加入生词本' }}</button><button :disabled="busy" @click="emit('answer',{action:'PROFICIENT'})">标记熟练 · 免复习</button><p class="vl-muted" role="status">{{ collectMessage }}</p></div><div class="vl-next-row"><p>{{ current.outcome?.dueAt?'下次复习：'+new Date(current.outcome.dueAt).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai'}):'点击继续，保留本轮学习记录。' }}</p><button class="vl-primary" :disabled="busy" @click="emit('next')">{{ current.outcome?.correct?'下一个':'继续，稍后再练' }}</button></div></div>
    </component>
    <details v-if="current.source && (!current.detail || current.detail.examples?.length)" :key="attemptKey" class="vl-dictionary-more"><summary>阅读原句</summary><blockquote v-if="current.source" class="vl-example"><p lang="en">{{ current.source.sentence }}</p><RouterLink :to="{path:`/reading/${current.source.articleId}`,query:{version:current.source.articleVersionId,sentence:current.source.sentenceKey}}" target="_blank">阅读原句 · {{ current.source.title }} ↗</RouterLink></blockquote></details>
  </template>
  <template v-else><WordDetails v-if="current.detail" :entry="current.detail"/><template v-else><p v-for="(sense,index) in current.definition?.senses" :key="index" class="vl-definition-meaning">{{ sense.partOfSpeech }} {{ sense.definitionZh }}</p><p class="vl-muted">{{ current.definition?.sourceNote }}</p></template><blockquote v-if="current.source" class="vl-example"><p lang="en">{{ current.source.sentence }}</p><RouterLink :to="{path:`/reading/${current.source.articleId}`,query:{version:current.source.articleVersionId,sentence:current.source.sentenceKey}}" target="_blank">阅读原句 · {{ current.source.title }} ↗</RouterLink></blockquote><div class="vl-next-row"><p>{{ current.outcome?.dueAt?'下次复习：'+new Date(current.outcome.dueAt).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai'}):'点击继续，保留本轮学习记录。' }}</p><button class="vl-primary" :disabled="busy" @click="emit('next')">{{ current.outcome?.correct?'下一个':'继续，稍后再练' }}</button></div></template>
</section>
<div v-if="v2 && !compact" class="vl-study-tools"><button :disabled="busy||collectBusy||!current.entryId" @click="collect">{{ collectBusy?'正在加入…':'加入生词本' }}</button><button :disabled="busy" @click="emit('answer',{action:'PROFICIENT'})">标记熟练 · 免复习</button><p class="vl-muted" role="status">{{ collectMessage }}</p></div></section></template>

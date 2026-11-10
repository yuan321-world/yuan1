<script setup lang="ts">
import PracticeSelectionArea from './PracticeSelectionArea.vue'
import {computed,onMounted,onBeforeUnmount,ref,watch,nextTick} from 'vue'
import type {Attempt,PlaybackState} from '@/api/practice'
import {practiceAudioTicket} from '@/api/practice'
import {errorMessage} from '@/api/identity'
import PracticeListeningClipAudio from './PracticeListeningClipAudio.vue'
import {listeningClipCue} from '@/practice/listening-clips'
const props=defineProps<{attempt:Attempt;choices:Record<string,string|null>;flags:Record<string,boolean>;currentId:string;playback:PlaybackState;disabled:boolean;paperLayout?:boolean;audioTicketProvider?:()=>Promise<import('@/api/practice').AudioTicket>}>()
const emit=defineEmits<{change:[changes:{questionId:string;choice:string|null;flagged?:boolean}[]];navigate:[id:string];playback:[value:PlaybackState,immediate:boolean]}>()
const audio=ref<HTMLAudioElement>(),src=ref(''),mediaError=ref(''),loading=ref(false),ready=ref(false),loopA=ref<number|null>(null),loopB=ref<number|null>(null),position=ref(0)
const audioSha256=ref<string>()
const clipAudios=new Map<string,InstanceType<typeof PracticeListeningClipAudio>>()
function registerClip(key:string,element:unknown){if(element)clipAudios.set(key,element as InstanceType<typeof PracticeListeningClipAudio>);else clipAudios.delete(key)}
function pauseClips(except?:string){for(const [key,player] of clipAudios)if(key!==except)player.pause()}
function clipStarted(key:string){audio.value?.pause();pauseClips(key)}
const overview=ref<HTMLDetailsElement>(),questionArea=ref<HTMLElement>(),mobileNumbersOpen=ref(false)
const question=computed(()=>props.attempt.questions.find(q=>q.id===props.currentId)||props.attempt.questions[0])
const complete=computed(()=>props.attempt.status==='COMPLETED')
const clips=computed(()=>{
 const result=new Map<string,{group?:{key:string;questionKeys:string[]};title:string;questions:Attempt['questions']}>()
 for(const q of props.attempt.questions){
  const group=props.attempt.listening?.groups.find(g=>g.questionKeys.includes(q.key)),key=group?'group:'+group.key:'question:'+q.id
  if(!result.has(key))result.set(key,{group,title:group?.title||'听力题目',questions:[]})
  result.get(key)!.questions.push(q)
 }
 return [...result.values()]
})
const clipIndex=computed(()=>Math.max(0,clips.value.findIndex(c=>c.questions.some(q=>q.id===question.value?.id))))
const shownClips=computed(()=>clips.value.map((clip,index)=>({...clip,key:clip.group?.key||clip.questions[0]?.id||String(index),cue:listeningClipCue(audioSha256.value,clip.group)})))
function answerFor(id:string){return props.attempt.answers.find(a=>a.questionId===id)}
function questionNumber(id:string){return props.attempt.questions.findIndex(q=>q.id===id)+1}

const numberSections=computed(()=>{
 const groups=props.attempt.listening?.groups||[]
 const sections=new Map<string,{question:Attempt['questions'][number];number:number}[]>()
 for(const [i,q] of props.attempt.questions.entries()){
  const group=groups.find(g=>g.questionKeys.includes(q.key)),label=group?group.key+' '+group.title:''
  const title=/短篇新闻|新闻|news/i.test(label)?'短篇新闻':/长对话|conversation/i.test(label)?'长对话':/听力篇章|听力短文|短文|passage/i.test(label)?'听力短文':group?.title||'其他题目'
  if(!sections.has(title))sections.set(title,[])
  sections.get(title)!.push({question:q,number:i+1})
 }
 return [...sections].map(([title,items])=>({title,items}))
})
let disposed=false,expires=0,generation=0,recovering=false
function pause(){pauseClips();audio.value?.pause();record(true)}
function record(immediate=false){const element=audio.value;if(!element||!ready.value)return;position.value=element.currentTime;if(loopA.value!==null&&loopB.value!==null&&element.currentTime>=loopB.value)element.currentTime=loopA.value;emit('playback',{positionSeconds:element.currentTime,rate:element.playbackRate},immediate)}
async function loadAudio(){if(loading.value)return;const n=++generation;loading.value=true;ready.value=false;mediaError.value='';const previous=audio.value?.currentTime||props.playback.positionSeconds;audio.value?.pause();try{const ticket=await (props.audioTicketProvider?props.audioTicketProvider():practiceAudioTicket(props.attempt.id));if(disposed||n!==generation)return;const url=new URL(ticket.url,location.origin);if(url.origin!==location.origin||!url.pathname.startsWith('/api/v1/practice/audio/'))throw new Error('音频地址无效');audioSha256.value=ticket.sha256;position.value=previous;expires=Date.parse(ticket.expiresAt);src.value=url.pathname+url.search}catch(error){if(!disposed)mediaError.value=errorMessage(error)}finally{if(!disposed)loading.value=false}}
function metadata(){const element=audio.value;if(!element)return;element.currentTime=Math.min(position.value,Number.isFinite(element.duration)?element.duration:position.value);element.playbackRate=props.playback.rate||1;element.pause();ready.value=true;mediaError.value='';recovering=false}
function failed(){ready.value=false;if(Date.now()>expires&&!recovering){recovering=true;void loadAudio()}else mediaError.value='音频暂时无法播放，请重新获取音频。已保存答案不受影响。'}
function rate(event:Event){const value=Number((event.target as HTMLSelectElement).value);if(audio.value)audio.value.playbackRate=value;emit('playback',{positionSeconds:audio.value?.currentTime||props.playback.positionSeconds,rate:value},true)}
function move(delta:number){const element=audio.value;if(!element||!ready.value)return;element.currentTime=Math.max(0,Math.min(element.duration||Infinity,element.currentTime+delta));record(true)}
function activate(id:string){if(id!==props.currentId)emit('navigate',id)}
function flag(id:string){emit('change',[{questionId:id,choice:props.choices[id]??null,flagged:!props.flags[id]}]);activate(id)}
function choose(id:string,value:string|null){emit('change',[{questionId:id,choice:value}]);activate(id)}
function navigate(id:string){mobileNumbersOpen.value=false;emit('navigate',id);void nextTick(()=>{const target=[...(questionArea.value?.querySelectorAll<HTMLElement>('[data-question-id]')||[])].find(e=>e.dataset.questionId===id);target?.querySelector('h3')?.scrollIntoView?.({block:'center',behavior:'smooth'})})}
function moveClip(delta:number){const next=clips.value[clipIndex.value+delta]?.questions[0];if(next)navigate(next.id)}
function overviewNavigate(id:string){navigate(id);if(overview.value)overview.value.open=false}

function overviewStem(stem:string){return stem.replace(/^\s*\d+\s*[.、．)]\s*/, '')}
function visibility(){if(document.hidden)pause()}
const format=(value:number)=>`${Math.floor(value/60)}:${String(Math.floor(value%60)).padStart(2,'0')}`
watch(()=>props.attempt.id,()=>{generation++;src.value='';audioSha256.value=undefined;loopA.value=null;loopB.value=null;void loadAudio()})
defineExpose({pause})
onMounted(()=>{document.addEventListener('visibilitychange',visibility);void loadAudio()})
onBeforeUnmount(()=>{pause();disposed=true;generation++;src.value='';document.removeEventListener('visibilitychange',visibility)})
</script>
<template>
 <section class="practice-listening-player" :class="{'practice-listening-paper':paperLayout}">
  <div class="practice-panel listening-audio">
   <h2>整套听力</h2><p class="practice-hint">一份完整音频，按题号连续作答；播放结束不会自动交卷。</p>
   <audio ref="audio" aria-label="整套听力音频" :src="src||undefined" controls preload="metadata" @play="pauseClips()" @loadedmetadata="metadata" @timeupdate="record(false)" @pause="record(true)" @seeked="record(true)" @error="failed"/>
   <div class="practice-actions"><button :disabled="!ready" @click="move(-5)">后退5秒</button><button :disabled="!ready" @click="move(5)">前进5秒</button><label>倍速<select aria-label="听力倍速" :value="playback.rate" @change="rate"><option v-for="value in [0.5,0.75,1,1.25,1.5,2]" :key="value" :value="value">{{value}}×</option></select></label><button :disabled="!ready" @click="loopA=audio?.currentTime??null;loopB=null">设置A点</button><button :disabled="!ready||loopA===null||(audio?.currentTime||0)<=loopA" @click="loopB=audio?.currentTime??null">设置B点</button><button v-if="loopA!==null" @click="loopA=null;loopB=null">取消循环</button></div>
   <p v-if="loopA!==null" class="practice-hint">循环：{{format(loopA)}} {{loopB===null?'（待设置B点）':'至 '+format(loopB)}}</p>
   <p v-if="loading" role="status">正在获取音频…</p><p v-if="mediaError" class="practice-error" role="alert">{{mediaError}} <button @click="loadAudio">重新获取音频</button></p>
  </div>
  <div ref="questionArea" class="listening-workspace" v-if="question">
   <div class="listening-clips"><section v-for="clip in shownClips" :key="clip.key" class="practice-panel listening-clip-panel"><header class="listening-clip-heading"><h2>{{clip.title}}</h2><p class="practice-hint">{{questionNumber(clip.questions[0]?.id||'')}}–{{questionNumber(clip.questions.at(-1)?.id||'')}} 题</p></header>
    <PracticeListeningClipAudio v-if="clip.cue&&src" :key="clip.cue.key" :ref="element=>registerClip(clip.key,element)" :src="src" :start="clip.cue.start" :end="clip.cue.end" :rate="playback.rate" :title="clip.title" @active="clipStarted(clip.key)"/>
    <article v-for="q in clip.questions" :key="q.id" class="listening-question" :data-question-id="q.id">
     <h3>{{questionNumber(q.id)+'. '+overviewStem(q.stem)}}</h3>
     <fieldset :disabled="disabled||complete"><label v-for="(option,i) in q.options" :key="i" class="practice-option"><input type="radio" :name="q.id" :aria-label="String.fromCharCode(65+i)+'. '+option" :checked="choices[q.id]===String.fromCharCode(65+i)" @change="choose(q.id,String.fromCharCode(65+i))"><span>{{String.fromCharCode(65+i)}}. {{option}}</span></label></fieldset>
     <div v-if="!complete" class="practice-actions">
      <button :disabled="disabled" :aria-label="'清空第'+questionNumber(q.id)+'题选项'" @click="choose(q.id,null)">清空本题选项</button>
      <button v-if="!paperLayout" :disabled="disabled" :aria-label="'标记第'+questionNumber(q.id)+'题待检查'" :aria-pressed="!!flags[q.id]" @click="flag(q.id)">{{flags[q.id]?'取消待检查':'标为待检查'}}</button>
     </div>
     <div v-else class="practice-explanation"><p>正确答案：{{answerFor(q.id)?.correctOption}} · {{answerFor(q.id)?.correct?'回答正确':'本题未答对'}}</p><p>{{answerFor(q.id)?.explanation}}</p></div>
    </article>
   </section></div>
   <aside v-if="!paperLayout" class="practice-panel listening-number-panel" aria-label="听力题号导航"><header class="listening-number-heading"><h2>整套题号 <small>{{attempt.questions.filter(q=>choices[q.id]).length}} / {{attempt.questions.length}}</small></h2><button class="listening-number-toggle" :aria-expanded="mobileNumbersOpen" :aria-controls="'listening-numbers-'+attempt.id" @click="mobileNumbersOpen=!mobileNumbersOpen">{{mobileNumbersOpen?'收起题号':'展开题号'}}</button></header><div class="listening-number-content" :class="{'is-open':mobileNumbersOpen}" :id="'listening-numbers-'+attempt.id"><p v-if="complete">正确 {{attempt.correctCount}} / {{attempt.total}}</p><section v-for="section in numberSections" :key="section.title" class="listening-number-section"><h3>{{section.title}}</h3><div class="practice-question-numbers"><button v-for="item in section.items" :key="item.question.id" :aria-label="'第'+item.number+'题'" :aria-pressed="item.question.id===currentId" @click="navigate(item.question.id)">{{item.number}}<small>{{flags[item.question.id]?'待检查':choices[item.question.id]?'已答':'未答'}}</small></button></div></section><div v-if="!paperLayout" class="practice-question-steps"><button :disabled="clipIndex===0" @click="moveClip(-1)">上一片段</button><button :disabled="clipIndex===clips.length-1" @click="moveClip(1)">下一片段</button></div></div></aside>
  </div>
  <details v-if="complete&&!paperLayout" ref="overview" class="practice-panel listening-overview"><summary>全部题目总览 <small>{{attempt.questions.length}}题</small></summary><div class="listening-overview-list" role="region" aria-label="全部听力题目" tabindex="0"><button v-for="(q,i) in attempt.questions" :key="q.id" class="listening-overview-item" :aria-label="'查看第'+(i+1)+'题'" @click="overviewNavigate(q.id)"><span>{{i+1}}.</span><span class="listening-overview-stem">{{overviewStem(q.stem)}}</span><small>{{choices[q.id]||'未答'}}{{flags[q.id]?' · 待检查':''}}</small></button></div></details>
  <details v-if="complete&&attempt.article" class="practice-panel"><summary>配套原文</summary><PracticeSelectionArea :article="attempt.article"/><p data-practice-source v-for="p in attempt.article.content.paragraphs" :key="p.key">{{p.sentences.map(s=>s.text).join(' ')}}</p></details>
 </section>
</template>
<style scoped>
.listening-number-section .practice-question-numbers{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));border:0;margin:0;padding:0}.listening-number-section .practice-question-numbers button{width:100%;max-width:none}.listening-number-section+.listening-number-section{margin-top:22px;padding-top:18px;border-top:1px solid var(--border-color,var(--line))}.listening-number-section h3{margin:0 0 12px;font-size:15px;font-weight:600}.listening-audio{position:sticky;top:12px;z-index:2}.listening-audio audio{width:100%;display:block;margin:14px 0}.listening-audio label{display:flex;gap:8px;align-items:center}.listening-audio select{width:auto;margin:0}.listening-audio .practice-actions{justify-content:flex-start}.listening-clip-heading h2{margin:0}.listening-clip-heading p{margin:8px 0 20px}.listening-question h3{font-size:17px;line-height:1.6;overflow-wrap:anywhere;margin:0 0 16px}.listening-question+.listening-question{margin-top:24px;padding-top:24px;border-top:1px solid var(--practice-line)}.listening-workspace{display:grid;align-items:start;grid-template-columns:minmax(0,1fr) 270px;gap:22px}.listening-workspace h2{margin:16px 0;overflow-wrap:anywhere}.listening-overview-list{max-height:min(320px,50vh);overflow-y:auto;overscroll-behavior:contain;margin-top:14px;padding-right:6px}.listening-overview-item{display:grid;grid-template-columns:28px minmax(0,1fr) auto;gap:10px;align-items:start;width:100%;text-align:left;margin-top:8px;overflow-wrap:anywhere}.listening-overview-item:first-child{margin-top:0}.listening-overview-stem{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;line-height:1.6}.listening-overview-item small{font-size:11px;max-width:70px;line-height:1.6;color:var(--muted)}.listening-overview summary small{margin-left:8px;font-weight:400;color:var(--muted)}.practice-listening-player summary{cursor:pointer}.practice-listening-player p{overflow-wrap:anywhere;line-height:1.8}@media(max-width:800px){.listening-workspace{grid-template-columns:1fr}.listening-audio{position:static}.listening-audio .practice-actions{gap:7px}.listening-audio .practice-actions button{font-size:12px;padding:7px 9px}.listening-workspace .practice-question-numbers{grid-template-columns:repeat(5,minmax(0,1fr))}}

.listening-clips{display:grid;gap:16px;min-width:0;grid-column:1;grid-row:1}
.practice-listening-player:not(.practice-listening-paper) .listening-audio{position:static;padding:18px}
.practice-listening-player:not(.practice-listening-paper) .listening-workspace{grid-template-columns:minmax(0,1fr) 190px;gap:18px}
.practice-listening-player:not(.practice-listening-paper) .listening-number-panel{position:sticky;top:112px;grid-column:2;grid-row:1;max-height:calc(100dvh - 132px);overflow:auto;padding:14px;min-width:0}
.listening-number-heading h2{font-size:16px;margin:0 0 12px}
.listening-number-heading h2 small{font-size:11px;font-weight:400;color:var(--muted);margin-left:6px}
.practice-listening-player .listening-number-toggle{display:none}
.listening-number-content .practice-question-steps{margin-top:12px;display:flex;gap:6px;flex-wrap:wrap}
.listening-number-content .practice-question-steps button{font-size:11px;padding:6px 8px;min-height:32px;flex:1;white-space:nowrap}
.practice-listening-player:not(.practice-listening-paper) .listening-clip-panel{padding:18px;min-width:0;scroll-margin-top:112px}
.listening-question{scroll-margin-top:120px}
.practice-listening-player:not(.practice-listening-paper) .listening-question .practice-option{padding:10px 12px;font-size:16px;line-height:1.5}
.practice-listening-player:not(.practice-listening-paper) .listening-question .practice-option input{min-height:0;flex-shrink:0}
.practice-listening-player:not(.practice-listening-paper) .listening-question .practice-actions{gap:6px}
.practice-listening-player:not(.practice-listening-paper) .listening-question .practice-actions button{font-size:11px;min-height:32px;padding:6px 9px}
@media(max-width:800px){
 .practice-listening-player:not(.practice-listening-paper) .listening-workspace{grid-template-columns:minmax(0,1fr);gap:12px}
 .practice-listening-player:not(.practice-listening-paper) .listening-clips{grid-column:1;grid-row:2;gap:12px}
 .practice-listening-player:not(.practice-listening-paper) .listening-number-panel{position:static;grid-column:1;grid-row:1;max-height:none;overflow:visible;padding:12px 14px}
 .listening-number-heading{display:flex;align-items:center;justify-content:space-between;gap:8px}
 .listening-number-heading h2{margin:0;font-size:15px}
 .practice-listening-player .listening-number-toggle{display:inline-flex;font-size:11px;min-height:30px;padding:5px 8px}
 .listening-number-content{display:none;margin-top:12px}
 .listening-number-content.is-open{display:block}
 .practice-listening-player:not(.practice-listening-paper) .listening-audio,.practice-listening-player:not(.practice-listening-paper) .listening-clip-panel{padding:14px}
}
</style>


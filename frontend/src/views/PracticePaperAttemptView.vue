<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount,onMounted,nextTick,provide} from 'vue'
import {useRoute,useRouter,onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import {practiceSelectionContextKey} from '@/practice/selection-context'
import {usePracticePaperStore} from '@/stores/practice-paper'
import * as api from '@/api/practice-paper'
import PracticeListeningPlayer from '@/components/practice/PracticeListeningPlayer.vue'
import PracticeClozePlayer from '@/components/practice/PracticeClozePlayer.vue'
import PracticeMatchPlayer from '@/components/practice/PracticeMatchPlayer.vue'
import PracticeReader from '@/components/practice/PracticeReader.vue'
import PracticePaperResult from '@/components/practice/PracticePaperResult.vue'
import PracticeSubjectiveTools from '@/components/practice/PracticeSubjectiveTools.vue'
import PracticePaperAnswerCard from '@/components/practice/PracticePaperAnswerCard.vue'
import {errorMessage} from '@/api/identity'
import '@/styles/practice.css'
import '@/styles/practice-paper.css'
const route=useRoute(),router=useRouter(),store=usePracticePaperStore()
const confirm=ref<HTMLDialogElement>(),cardDialog=ref<HTMLDialogElement>(),sheet=ref<HTMLElement>(),listeningPlayer=ref<InstanceType<typeof PracticeListeningPlayer>>()
const matchPlayer=ref<InstanceType<typeof PracticeMatchPlayer>>()
const showResult=ref(false),localError=ref(''),restarting=ref(false),visibleSlot=ref('WRITING'),flags=ref<Record<string,boolean>>({}),playback=ref({positionSeconds:0,rate:1})
const viewport=window.matchMedia('(max-width:900px)'),mobile=ref(viewport.matches)
const a=computed(()=>store.attempt),done=computed(()=>a.value?.status==='COMPLETED')
provide(practiceSelectionContextKey,computed(()=>({paperAttemptId:a.value?.id,enabled:done.value})))
const ordered=computed(()=>api.slots.flatMap(slot=>a.value?.components.filter(c=>c.slot===slot)||[]))
const returnTo=computed(()=>(/^\/practice\/(?:cet|cet4)(?:\?|$)/.test(String(route.query.returnTo||''))?String(route.query.returnTo):'/practice/cet?source=PAST&collection=PAST&section=paper&exam=CET4'))
const partNames:Record<string,string>={WRITING:'Part I · 写作',LISTENING:'Part II · 听力',CLOZE:'Part III · Section A · 选词填空',MATCH:'Part III · Section B · 长篇匹配',DETAILED_1:'Part III · Section C · 仔细阅读 · 第一篇',DETAILED_2:'Part III · Section C · 仔细阅读 · 第二篇',TRANSLATION:'Part IV · 翻译'}
const unmet=computed(()=>ordered.value.map(c=>{const r=response(c.slot);return {slot:c.slot,missing:c.objective?c.objective.questions.filter(q=>!r?.choices[q.id]).length:!r?.text.trim()?1:0}}))
const response=(slot:string)=>store.responses.find(r=>r.slot===slot)
const choices=(slot:string)=>response(slot)?.choices||{}
const wordCount=(slot:string)=>(response(slot)?.text.match(/[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*/g)||[]).length
const currentId=(c:api.ComponentAttempt)=>store.currentSlot===c.slot&&store.currentQuestionId?store.currentQuestionId:c.objective?.questions[0]?.id||''
function totalNumber(slot:string,index:number){return ({LISTENING:1,CLOZE:26,MATCH:36,DETAILED_1:46,DETAILED_2:51}[slot]||1)+index}
function questionRange(c:api.ComponentAttempt){return c.objective?.questions.length?`${totalNumber(c.slot,0)}–${totalNumber(c.slot,c.objective.questions.length-1)} 题`:''}
function activate(slot:string,id:string|null=null){store.navigate(slot,id);visibleSlot.value=slot}
async function scrollToPart(slot:string,id?:string){
 await nextTick()
 const section=[...(sheet.value?.querySelectorAll<HTMLElement>('.paper-part')||[])].find(e=>e.dataset.slot===slot)
 const target=id?[...(section?.querySelectorAll<HTMLElement>('[data-question-id]')||[])].find(e=>e.dataset.questionId===id):section
 target?.scrollIntoView({block:'start',behavior:'instant'})
 if(target instanceof HTMLButtonElement)target.focus({preventScroll:true})
}
async function go(slot:string,id?:string){activate(slot,id||null);showResult.value=false;cardDialog.value?.close();await nextTick();if(slot==='MATCH'&&id)matchPlayer.value?.revealQuestion(id);await scrollToPart(slot,id)}
function change(slot:string,value:{questionId:string;choice:string|null;flagged?:boolean}[]){for(const c of value){if(c.flagged!==undefined)flags.value[c.questionId]=c.flagged;else store.setChoice(slot,c.questionId,c.choice)}}
function setMatchPlayer(element:unknown){matchPlayer.value=element as InstanceType<typeof PracticeMatchPlayer>|undefined}
function setListeningPlayer(element:unknown){listeningPlayer.value=element as InstanceType<typeof PracticeListeningPlayer>|undefined}
function ticket(){return api.paperAudioTicket(a.value!.id)}
function openSubmit(){cardDialog.value?.close();listeningPlayer.value?.pause();if(done.value){showResult.value=true;window.scrollTo({top:0});return}confirm.value?.showModal()}
async function submit(){if(await store.submit()){confirm.value?.close();showResult.value=true;window.scrollTo({top:0})}}
async function reviewQuestions(){showResult.value=false;await scrollToPart(store.currentSlot,store.currentQuestionId||undefined)}
function reviewMistakes(){if(!a.value)return;void router.push({path:'/practice/cet',query:{exam:'CET4',source:'PAST',collection:'PAST',section:'paper',tab:'mistakes',sourceAttemptId:a.value.id}})}
async function restart(){if(!a.value||restarting.value)return;restarting.value=true;localError.value='';try{const p=await api.getPaper(a.value.paperId);const run=await api.startPaper(p.id,p.paperVersionId!,crypto.randomUUID());await router.push({path:'/practice/paper-attempts/'+run.id,query:{returnTo:returnTo.value}})}catch(e){localError.value=errorMessage(e)}finally{restarting.value=false}}
async function guard(){if(store.pendingSubmit||store.submitting)return false;if(store.recoverySnapshot&&!window.confirm('本地恢复副本尚未复制，确定离开？'))return false;if(await store.flush())return true;return window.confirm('内容尚未保存。请先复制备份；确定离开？')}
function beforeUnload(e:BeforeUnloadEvent){if(store.dirty||store.pendingSubmit||store.recoverySnapshot){e.preventDefault();e.returnValue=''}}
let scrollFrame=0
function trackScroll(){if(scrollFrame)return;scrollFrame=requestAnimationFrame(()=>{scrollFrame=0;if(showResult.value)return;let current:string|undefined;for(const section of sheet.value?.querySelectorAll<HTMLElement>('.paper-part')||[]){if(section.getBoundingClientRect().top<=160)current=section.dataset.slot}if(current)visibleSlot.value=current})}
function resized(event:MediaQueryListEvent){mobile.value=event.matches;cardDialog.value?.close()}
watch(()=>route.params.id,async id=>{
 await store.load(String(id));if(a.value?.id!==String(id))return
 showResult.value=done.value;flags.value={};playback.value={positionSeconds:0,rate:1};visibleSlot.value=store.currentSlot
 const target=String(route.query.slot||''),question=String(route.query.questionId||'')
 if(done.value){const component=a.value.components.find(c=>c.slot===target);if(component?.objective?.questions.some(q=>q.id===question))await go(target,question)}
 else await scrollToPart(store.currentSlot,store.currentQuestionId||undefined)
},{immediate:true})
onBeforeRouteLeave(guard);onBeforeRouteUpdate(guard)
onMounted(()=>{window.addEventListener('beforeunload',beforeUnload);window.addEventListener('scroll',trackScroll,{passive:true});viewport.addEventListener('change',resized)})
onBeforeUnmount(()=>{store.dispose();cancelAnimationFrame(scrollFrame);window.removeEventListener('beforeunload',beforeUnload);window.removeEventListener('scroll',trackScroll);viewport.removeEventListener('change',resized)})
</script>

<template>
 <section class="practice-page paper-page paper-attempt paper-continuous">
  <RouterLink :to="returnTo">← 返回整卷列表</RouterLink>
  <p v-if="store.loading" role="status">正在恢复整卷…</p>
  <p v-if="store.error||localError" class="paper-error" role="alert">{{store.error||localError}}
   <button v-if="store.conflict" @click="store.reloadKeepingLocal">重新读取并保留本地副本</button>
   <button v-else-if="store.pendingSubmit" @click="submit">重试确认交卷结果</button>
   <button v-else-if="a" @click="store.flush">重试保存</button>
   <button v-else @click="store.load(String(route.params.id))">重新加载</button>
  </p>
  <section v-if="store.recoverySnapshot" class="paper-panel"><h2>本地恢复副本</h2><p>其他页面已交卷，以下内容没有覆盖服务器记录。请复制需要的文字。</p><textarea aria-label="本地恢复副本" :value="store.recoverySnapshot" readonly></textarea><button @click="store.recoverySnapshot=''">已备份，关闭副本</button></section>
  <template v-if="a">
   <template v-if="showResult&&done"><header class="paper-heading"><h1>{{a.title}}</h1></header><PracticePaperResult :attempt="a" @review="reviewQuestions" @restart="restart" @mistakes="reviewMistakes"/></template>
   <div v-else class="paper-workspace">
    <div ref="sheet" class="paper-content paper-sheet">
     <header class="paper-sheet-heading">
      <h1>{{a.title}}</h1>
      <div class="paper-actions"><span role="status">{{store.status}}</span><button v-if="!done" :disabled="store.saving||store.submitting||!!store.pendingSubmit" @click="store.flush">保存草稿</button></div>
      <details v-if="a.sourceNote" class="paper-source-note"><summary>试卷来源</summary><p>{{a.sourceNote}}</p></details>
     </header>
     <section v-for="c in ordered" :key="c.slot" class="paper-part" :data-slot="c.slot" :aria-labelledby="'paper-title-'+c.slot">
      <header class="paper-part-heading"><h2 :id="'paper-title-'+c.slot">{{partNames[c.slot]}}</h2><span v-if="c.objective">{{questionRange(c)}}</span></header>
      <template v-if="c.prompt">
       <h3 v-if="c.slot==='TRANSLATION'" class="paper-prompt-label">中文原文</h3>
       <p class="paper-prompt" :lang="c.slot==='WRITING'?'en':'zh'">{{c.prompt.prompt}}</p>
       <details v-if="c.prompt.requirements" class="paper-requirements"><summary>题目要求</summary><p>{{c.prompt.requirements}}</p></details>
       <div class="paper-editor-heading"><h3>{{c.slot==='WRITING'?'我的作文':'我的译文'}}</h3><small v-if="c.prompt.minWords">建议 {{c.prompt.minWords}}–{{c.prompt.maxWords}}词</small></div>
       <textarea :aria-label="c.slot==='WRITING'?'英文作文':'英文译文'" :value="response(c.slot)?.text||''" maxlength="20000" lang="en" :readonly="done||!store.canEdit" :placeholder="c.slot==='WRITING'?'在这里写你的英文作文…':'在这里写你的英文译文…'" @focus="activate(c.slot)" @input="store.setText(c.slot,($event.target as HTMLTextAreaElement).value)"></textarea>
       <PracticeSubjectiveTools :source="done?{sourceType:'PAPER',attemptId:a!.id,slot:c.slot as 'WRITING'|'TRANSLATION'}:undefined" :writing="c.slot==='WRITING'" :word-count="wordCount(c.slot)" :reference="c.reference" :notes="c.notes"/>
      </template>
      <PracticeListeningPlayer v-else-if="c.slot==='LISTENING'&&c.objective" :ref="setListeningPlayer" :attempt="c.objective" :choices="choices(c.slot)" :flags="flags" :current-id="currentId(c)" :playback="playback" :audio-ticket-provider="ticket" :disabled="done||!store.canEdit" paper-layout @change="change(c.slot,$event)" @navigate="activate(c.slot,$event)" @playback="playback=$event"/>
      <PracticeClozePlayer v-else-if="c.slot==='CLOZE'&&c.objective?.article" :attempt="c.objective" :choices="choices(c.slot)" :current-id="currentId(c)" :disabled="done||!store.canEdit" paper-layout @change="change(c.slot,$event)" @navigate="activate(c.slot,$event)" @submit="openSubmit"/>
      <PracticeMatchPlayer :ref="setMatchPlayer" v-else-if="c.slot==='MATCH'&&c.objective?.article" :attempt="c.objective" :choices="choices(c.slot)" :flags="flags" :current-id="currentId(c)" :disabled="done||!store.canEdit" paper-layout @change="change(c.slot,$event)" @navigate="activate(c.slot,$event)" @submit="openSubmit"/>
      <template v-else-if="c.slot.startsWith('DETAILED')&&c.objective">
       <PracticeReader v-if="c.objective.article" :article="c.objective.article"/>
       <article v-for="(q,index) in c.objective.questions" :key="q.id" :data-question-id="q.id" class="paper-question">
        <h3>{{totalNumber(c.slot,index)}}. {{q.stem.replace(/^\d+[.、]\s*/,'')}}</h3>
        <fieldset :disabled="done||!store.canEdit"><label v-for="(option,n) in q.options" :key="n" class="practice-option"><input type="radio" :name="q.id" :checked="choices(c.slot)[q.id]===String.fromCharCode(65+n)" @change="activate(c.slot,q.id);store.setChoice(c.slot,q.id,String.fromCharCode(65+n))"><span>{{String.fromCharCode(65+n)}}. {{option}}</span></label><button v-if="!done" @click="store.setChoice(c.slot,q.id,null)">清空本题</button></fieldset>
        <p v-if="done" class="paper-muted paper-explanation">参考：{{c.objective.answers.find(x=>x.questionId===q.id)?.correctOption}} · {{c.objective.answers.find(x=>x.questionId===q.id)?.explanation}}</p>
       </article>
      </template>
      <div class="paper-actions paper-part-step"><button v-if="c.slot!=='WRITING'" @click="go(api.slots[api.slots.indexOf(c.slot as api.Slot)-1]!)">上一部分</button><button @click="c.slot==='TRANSLATION'?openSubmit():go(api.slots[api.slots.indexOf(c.slot as api.Slot)+1]!)">{{c.slot==='TRANSLATION'?(done?'查看结果':'检查并交卷'):'下一部分'}}</button></div>
     </section>
    </div>
    <aside v-if="!mobile" class="paper-panel paper-progress"><PracticePaperAnswerCard :components="ordered" :responses="store.responses" :active-slot="visibleSlot" :current-question-id="store.currentQuestionId" :disabled="store.submitting||store.loading||store.conflict" :done="done" @navigate="go" @submit="openSubmit"/></aside>
    <button v-if="mobile" class="paper-card-toggle" @click="cardDialog?.showModal()">查看全部题号</button>
    <dialog ref="cardDialog" class="paper-card-dialog" aria-label="全部题号"><button class="paper-card-close" @click="cardDialog?.close()">关闭题号面板</button><PracticePaperAnswerCard v-if="mobile" :components="ordered" :responses="store.responses" :active-slot="visibleSlot" :current-question-id="store.currentQuestionId" :disabled="store.submitting||store.loading||store.conflict" :done="done" @navigate="go" @submit="openSubmit"/></dialog>
   </div>
  </template>
  <dialog ref="confirm" class="paper-dialog" @cancel="store.submitting&&$event.preventDefault()"><h2>确认交卷？</h2><p v-for="u in unmet" :key="u.slot">{{api.slotLabels[u.slot]}}：{{u.missing?'未答 '+u.missing+' 题':'已填写'}}</p><p v-if="wordCount('WRITING')<120" class="paper-muted">请留意作文的建议字数；交卷后仍保留原文。</p><p class="paper-muted">确认后本次作答只读，空白题按未作答保留。</p><p v-if="store.error" class="paper-error" role="alert">{{store.error}}</p><div class="paper-actions"><button :disabled="store.submitting||!!store.pendingSubmit" @click="confirm?.close()">返回检查</button><button class="paper-primary" :disabled="store.submitting||store.conflict" @click="submit">确认交卷</button></div></dialog>
 </section>
</template>

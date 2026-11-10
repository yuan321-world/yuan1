<script setup lang="ts">
import PronunciationControls from '@/components/common/PronunciationControls.vue'
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import VocabularyDetailDialog from './VocabularyDetailDialog.vue'
import PhraseExamples from './PhraseExamples.vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getGeneration, onRemoteIdentityChange } from '@/auth/token-session'
import { errorMessage } from '@/api/identity'
import { ApiError } from '@/api/client'
import { phraseList, editPhrase, cancelPhrase, restorePhrase, setPhraseLearning, currentPhraseRound, startPhraseRound, phraseCommand, type Phrase, type PhraseOverview, type PhraseRound, type PhraseCommand } from '@/api/vocabulary-phrases'
const props = defineProps<{ beforePractice?: () => Promise<boolean> }>()
const emit = defineEmits<{ busy: [value: boolean] }>()
const auth = useAuthStore()
const empty = ():PhraseOverview => ({page:1,pageSize:20,total:0,items:[],stats:{collected:0,enrolled:0,independentPasses:0,available:0}})
const data = ref<PhraseOverview>(empty()), page = ref(1)
const busy = ref(false), error = ref(''), query = ref(''), cancelled = ref(false), round = ref<PhraseRound|null>(null), practicing = ref(false), draft = ref('')
const editing = ref<Phrase|null>(null), text = ref(''), meaning = ref(''), note = ref('')
const detailDialog=ref<InstanceType<typeof VocabularyDetailDialog>>(),editingMeaning=ref(false),voiceError=ref('')
let voiceSequence=0
function stopVoice(){voiceSequence++;window.speechSynthesis?.cancel()}
function closeEditor(){editing.value=null;voiceError.value='';stopVoice()}
function speak(lang:string){voiceError.value='';if(!window.speechSynthesis){voiceError.value='浏览器暂不支持朗读。';return}stopVoice();const n=voiceSequence,u=new SpeechSynthesisUtterance(text.value);u.lang=lang;u.onerror=e=>{if(n===voiceSequence&&!['canceled','interrupted'].includes(e.error))voiceError.value='朗读暂不可用，可以重试。'};speechSynthesis.speak(u)}
watch(()=>!!editing.value,async open=>{await nextTick();if(open&&editing.value)detailDialog.value?.showModal();else detailDialog.value?.close()})
let disposed=false, epoch=0, boundOwner=auth.user?.id, boundGeneration=getGeneration()
const controllers = new Set<AbortController>()
watch(busy,v=>emit('busy',v))
const available=computed(()=>data.value.stats.available??data.value.stats.enrolled)
function scheduleText(p:Phrase){if(!p.enrolled)return '';if(p.exempt)return '已保留原复习安排';if(!p.stage)return '尚未学习 · 可开始';if(!p.dueAt)return '等待原复习安排';return `阶段 ${p.stage} · 下次 ${new Date(p.dueAt).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'})}`}
const current=computed(()=>round.value?.items.find(i=>i.phraseId===round.value?.queue[0]))
const pending=ref<{id:string;input:PhraseCommand;owner:string;generation:number;epoch:number}|null>(null)
function identityCurrent(){return !disposed&&!!boundOwner&&auth.user?.id===boundOwner&&getGeneration()===boundGeneration}
function resetIdentity(){epoch++;for(const c of controllers)c.abort();controllers.clear();boundOwner=auth.user?.id;boundGeneration=getGeneration();data.value=empty();page.value=1;query.value='';cancelled.value=false;round.value=null;practicing.value=false;draft.value='';editing.value=null;text.value='';meaning.value='';note.value='';pending.value=null;error.value='';busy.value=false;emit('busy',false)}
type Context={owner:string;generation:number;epoch:number;signal:AbortSignal;current:()=>boolean}
async function run(work:(ctx:Context)=>Promise<void>){if(!identityCurrent()){resetIdentity();return false}if(busy.value)return false;const controller=new AbortController(),owner=boundOwner!,generation=boundGeneration,startEpoch=epoch;const ctx:Context={owner,generation,epoch:startEpoch,signal:controller.signal,current:()=>!controller.signal.aborted&&identityCurrent()&&epoch===startEpoch};controllers.add(controller);busy.value=true;error.value='';try{await work(ctx);return ctx.current()}catch(e){if(ctx.current()&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e);return false}finally{controllers.delete(controller);if(ctx.current())busy.value=false}}
async function refresh(ctx:Context){const result=await phraseList(query.value,cancelled.value,page.value,ctx.signal);if(ctx.current())data.value=result}
async function load(){await run(async ctx=>{await refresh(ctx);if(!ctx.current())return;const result=await currentPhraseRound(ctx.signal);if(ctx.current())round.value=result})}
function syncDraft(){draft.value=current.value?.draft??''}
async function submitPending(ctx:Context){const request=pending.value;if(!request||!ctx.current()||request.owner!==ctx.owner||request.generation!==ctx.generation||request.epoch!==ctx.epoch)throw new Error('账号已变化，请重新加载');try{return await phraseCommand(request.id,request.input,ctx.signal,ctx.current)}catch(e){if(ctx.current()&&pending.value===request&&e instanceof ApiError&&e.status>=400&&e.status<500)pending.value=null;throw e}}
async function command(action:PhraseCommand['action'],extra:Partial<PhraseCommand>={}){if(!round.value)return false;return run(async ctx=>{if(pending.value)throw new Error('请先重试尚未确认的操作');pending.value={id:round.value!.id,input:{requestId:crypto.randomUUID(),expectedVersion:round.value!.version,action,...extra},owner:ctx.owner,generation:ctx.generation,epoch:ctx.epoch};const result=await submitPending(ctx);if(!ctx.current())return;pending.value=null;round.value=result;syncDraft();if(result.status==='COMPLETED')await refresh(ctx)})}
async function retry(){await run(async ctx=>{if(!pending.value)return;const result=await submitPending(ctx);if(!ctx.current())return;pending.value=null;round.value=result;syncDraft();await refresh(ctx)})}
async function saveBeforeLeave(){if(!identityCurrent()){resetIdentity();return true}const startEpoch=epoch;if(busy.value||pending.value)return false;if(round.value?.status==='ACTIVE'){if(current.value&&draft.value!==current.value.draft&&!await command('DRAFT',{draft:draft.value}))return false;if(epoch!==startEpoch||!identityCurrent())return true;if(!await command('SAVE'))return false}if(epoch===startEpoch)practicing.value=false;return true}
defineExpose({saveBeforeLeave})
async function begin(mode:PhraseRound['mode']){const startEpoch=epoch;if(!identityCurrent())return;if(props.beforePractice&&!await props.beforePractice())return;if(epoch!==startEpoch||!identityCurrent())return;await run(async ctx=>{const result=await startPhraseRound(mode,crypto.randomUUID(),10,ctx.signal,ctx.current);if(ctx.current()){round.value=result;practicing.value=true;syncDraft()}})}
async function resume(){const startEpoch=epoch;if(!identityCurrent())return;if(props.beforePractice&&!await props.beforePractice())return;if(epoch!==startEpoch||!identityCurrent())return;if(round.value?.status==='PAUSED'&&!await command('RESUME'))return;if(epoch===startEpoch&&identityCurrent()){practicing.value=true;syncDraft()}}
function showEdit(p:Phrase){if(!identityCurrent())return;editingMeaning.value=false;voiceError.value='';editing.value=p;text.value=p.text;meaning.value=p.meaningZh;note.value=p.note}
async function savePhrase(){const selected=editing.value;if(!selected)return;await run(async ctx=>{await editPhrase(selected.id,{meaningZh:meaning.value,note:note.value},ctx.signal,ctx.current);if(!ctx.current())return;editing.value=null;await refresh(ctx)})}
async function toggleCollect(p:Phrase){await run(async ctx=>{if(p.active)await cancelPhrase(p.id,ctx.signal,ctx.current);else await restorePhrase(p.id,ctx.signal,ctx.current);if(ctx.current())await refresh(ctx)})}
async function learning(p:Phrase,input:{enrolled?:boolean;exempt?:boolean}){await run(async ctx=>{await setPhraseLearning(p.id,input,ctx.signal,ctx.current);if(ctx.current())await refresh(ctx)})}
watch(()=>auth.user,()=>{if(boundOwner!==auth.user?.id||boundGeneration!==getGeneration()){resetIdentity();if(auth.user)void load()}},{flush:'sync'})
const removeRemoteListener=onRemoteIdentityChange(()=>resetIdentity())
onMounted(load)
onBeforeUnmount(()=>{stopVoice();disposed=true;epoch++;for(const c of controllers)c.abort();controllers.clear();pending.value=null;removeRemoteListener();emit('busy',false)})
</script>
<template>
<section class="phrase-book" aria-label="词组本" :aria-busy="busy">
 <div v-if="error" class="phrase-notice" role="alert">{{ error }} <button v-if="pending" @click="retry" :disabled="busy">重试原操作</button><button v-else @click="load" :disabled="busy">重新加载</button></div>
 <template v-if="practicing && round">
  <header class="phrase-toolbar"><div><h2>词组{{ round.mode==='WRITE'?'默写':'回想' }}</h2><p>已通过 {{ round.items.filter(i=>i.passed).length }} / {{ round.items.length }} · 词组独立记录</p></div><button :disabled="busy||!!pending" @click="saveBeforeLeave">保存并返回词组本</button></header>
  <div v-if="current" class="phrase-practice">
   <p>{{ round.mode==='WRITE'?'根据中文，默写完整词组':'看词组，回想中文意思' }}</p>
   <h2 :lang="round.mode==='WRITE'?'zh':'en'">{{ round.mode==='WRITE'?current.meaningZh:current.text }}</h2>
   <template v-if="round.mode==='RECOGNIZE'"><button v-if="!current.revealed" :disabled="busy||!!pending" @click="command('REVEAL')">显示释义</button><template v-else><p>{{ current.meaningZh }}</p><div class="phrase-actions"><button :disabled="busy||!!pending||current.answered" @click="command('PASS',{known:true})">认识，继续检验</button><button :disabled="busy||!!pending||current.answered" @click="command('PASS',{known:false})">想不起来</button></div></template></template>
   <form v-else @submit.prevent="command('PASS',{answer:draft})"><label for="phrase-answer">英文词组</label><input id="phrase-answer" v-model="draft" autocomplete="off" autocapitalize="none" spellcheck="false" maxlength="160" :disabled="busy||!!pending||current.answered"><div class="phrase-actions"><button :disabled="busy||!!pending||current.answered">检查拼写</button><button type="button" :disabled="busy||!!pending||current.answered" @click="command('HINT')">提示</button></div><p v-if="current.revealed" lang="en">{{ current.text }}</p></form>
   <p v-if="current.assisted" role="status">已使用辅助或答错。本次不算独立通过，之后会回队再练。</p><p v-else-if="current.passed" role="status">{{ current.failures ? "本轮有辅助或错误，已完成；复习安排从1天开始。" : round.mode==='WRITE' ? "独立默写通过。" : "已记录回想完成。" }}</p>
   <button v-if="current.revealed" :disabled="busy||!!pending" @click="command('NEXT')">下一词组</button>
  </div>
  <div v-else class="phrase-empty"><h2>本轮词组练习已完成</h2><p>通过 {{ round.items.filter(i=>i.passed).length }} 个，辅助或错误 {{ round.items.reduce((n,i)=>n+i.failures,0) }} 次。</p></div>
 </template>
 <template v-else>
  <header class="phrase-toolbar"><div><h2>词组本</h2><p>收藏 {{ data.stats.collected }} · 参与练习 {{ data.stats.enrolled }} · 独立默写通过 {{ data.stats.independentPasses }} 次 · 可练 {{ available }} 个</p></div></header>
  <div class="phrase-toolbar"><form class="phrase-search" @submit.prevent="page=1;run(refresh)"><label class="phrase-sr" for="phrase-search">搜索词组或中文</label><input id="phrase-search" v-model="query" placeholder="搜索词组或中文释义" maxlength="160"><button :disabled="busy">搜索</button></form><label><input type="checkbox" v-model="cancelled" :disabled="busy||!!pending" @change="page=1;run(refresh)"> 显示已取消收藏</label></div>
  <div class="phrase-actions"><template v-if="round && round.status!=='COMPLETED'"><button :disabled="busy||!!pending" @click="resume">继续保存的词组轮次</button><span>{{ round.items.filter(i=>i.passed).length }} / {{ round.items.length }}</span></template><template v-else><button :disabled="busy||!available||!!pending" @click="begin('RECOGNIZE')">看词组回想</button><button :disabled="busy||!available||!!pending" @click="begin('WRITE')">按中文默写</button></template></div>
  <p class="phrase-caption">收藏与练习分别保存；取消收藏仍保留来源和练习记录。词组不计入今日新单词额度。已学词组到期后再分配，熟练免复习保留原到期时间。</p>
  <p v-if="!busy&&!data.items.length" class="phrase-empty">还没有词组。可以在查词时收藏词组，也可以在阅读中选择固定搭配。</p>
  <div class="phrase-list"><article v-for="p in data.items" :key="p.id" class="phrase-row"><div><h3 lang="en">{{ p.text }}</h3><p>{{ p.meaningZh }}</p><div class="phrase-row-meta"><small>{{ p.active?'已收藏':'已取消收藏' }} · {{ p.exempt?'熟练免复习':p.enrolled?'已加入练习':'未加入练习' }} · {{ p.sources.length }} 个来源</small><p v-if="p.enrolled" class="phrase-caption">{{ scheduleText(p) }}</p></div></div><div class="phrase-actions"><button :disabled="busy||!!pending" @click="showEdit(p)">详情 / 编辑</button><button v-if="!p.enrolled" :disabled="busy||!!pending" @click="learning(p,{enrolled:true})">加入练习</button><button v-else :disabled="busy||!!pending" @click="learning(p,{exempt:!p.exempt})">{{ p.exempt?'恢复练习':'标记熟练' }}</button><button :disabled="busy||!!pending" @click="toggleCollect(p)">{{ p.active?'取消收藏':'恢复收藏' }}</button></div></article></div>
  <nav v-if="data.total>data.pageSize" class="phrase-actions" aria-label="词组分页"><button :disabled="busy||page<=1" @click="page--;run(refresh)">上一页</button><span>第 {{ page }} 页 · 共 {{ data.total }} 个词组</span><button :disabled="busy||page*data.pageSize>=data.total" @click="page++;run(refresh)">下一页</button></nav>
 </template>
 <VocabularyDetailDialog ref="detailDialog" title="词组详情" :busy="busy" close-label="关闭词组详情" @close="closeEditor">
  <template #heading><div v-if="editing" class="phrase-detail-heading"><h2 lang="en">{{ editing.text }}</h2><div class="phrase-detail-meta"><span>词组</span><PronunciationControls uk-label="英式朗读" us-label="美式朗读" @speak="speak"/></div></div><h2 v-else>词组详情</h2></template>
  <p v-if="error" class="phrase-detail-error" role="alert">{{ error }}</p><p v-if="voiceError" role="status" class="phrase-caption">{{ voiceError }}</p>
  <template v-if="editing">
   <div v-if="editing&&!editingMeaning" class="phrase-detail-meaning"><p>{{ meaning }}</p><button @click="editingMeaning=true">编辑释义</button></div>
   <PhraseExamples v-if="editing" :text="editing.text"/>
   <form id="phrase-edit-form" class="phrase-detail-form" @submit.prevent="savePhrase">
    <template v-if="editingMeaning"><label for="phrase-meaning">个人中文释义</label><textarea id="phrase-meaning" v-model="meaning" required maxlength="500" rows="2"/></template>
    <label for="phrase-note">个人备注</label><textarea id="phrase-note" v-model="note" maxlength="2000" rows="2" placeholder="记下用法、易错点或你的记忆方法"/>
   </form>
   <details v-if="editing" class="phrase-detail-sources"><summary>原文来源（{{ editing.sources.length }}）</summary><p v-if="!editing.sources.length">暂无原文来源。</p><article v-for="s in editing.sources" :key="s.id" class="phrase-source"><p lang="en">{{ s.sentence }}</p><RouterLink :to="s.practiceAttemptId ? `/practice/attempts/${s.practiceAttemptId}` : s.paperAttemptId ? `/practice/paper-attempts/${s.paperAttemptId}` : {path:`/reading/${s.articleId}`,query:{version:s.articleVersionId,sentence:s.sentenceKey,start:s.startOffset,end:s.endOffset}}">{{ s.title }} ↗</RouterLink><small>原文不可用时，仍保留此原句快照。</small></article></details>
  </template>
  <template #footer><button class="vd-primary" type="submit" form="phrase-edit-form" :disabled="busy||!!pending">{{ busy?'正在保存…':'保存' }}</button></template>
 </VocabularyDetailDialog>
</section>
</template>
<style scoped>
.phrase-detail-meta{display:flex;align-items:center;gap:8px;margin-top:8px;color:#788074;font-size:13px}.phrase-detail-meta>span{background:#edf1e6;border-radius:6px;padding:2px 6px;margin-right:6px}.phrase-detail-meaning{display:flex;align-items:flex-start;gap:12px;justify-content:space-between}.phrase-detail-meaning p{font-size:16px;line-height:1.65;margin:0;overflow-wrap:anywhere}.phrase-detail-meaning button{white-space:nowrap;font-size:12px!important;padding:4px 8px!important}.phrase-detail-form label{display:block;margin:14px 0 7px;font-size:13px;color:#788074}.phrase-detail-form input,.phrase-detail-form textarea{box-sizing:border-box;width:100%;border:1px solid #dce1d3;border-radius:12px;padding:10px 12px;background:var(--surface-2,#f0f2e8);color:var(--text,#293329);font:inherit;font-size:14px;line-height:1.6;resize:vertical}.phrase-detail-form input:focus-visible,.phrase-detail-form textarea:focus-visible{outline:2px solid #7e9867;outline-offset:2px}.phrase-detail-sources{margin-top:14px;padding-top:10px;border-top:1px solid #dce1d3;font-size:13px;line-height:1.7}.phrase-detail-sources a{color:#587344}.phrase-detail-error{font-size:13px;color:#a33c37}

.phrase-book{color:var(--text-primary,#263831);min-width:0}.phrase-toolbar,.phrase-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.phrase-toolbar{margin-bottom:20px}.phrase-actions{justify-content:flex-start}.phrase-book h2,.phrase-book h3{margin:0 0 8px}.phrase-book p{line-height:1.7}.phrase-book button{border:1px solid #ccd7cd;background:var(--surface);border-radius:8px;padding:9px 14px;color:#315d42;cursor:pointer}.phrase-book button:disabled{opacity:.5;cursor:default}.phrase-book input:not([type=checkbox]),.phrase-book textarea{width:100%;box-sizing:border-box;border:1px solid #ccd7cd;border-radius:8px;padding:11px;font:inherit;background:var(--surface);color:inherit}.phrase-search{display:flex;gap:8px;flex:1;max-width:480px}.phrase-search input{min-width:0}.phrase-search button{white-space:nowrap}.phrase-caption,small{color:#64776a;font-size:13px}.phrase-list{border-top:1px solid #dfe6df;margin-top:22px}.phrase-row{padding:22px 0;border-bottom:1px solid #dfe6df;display:flex;align-items:center;justify-content:space-between;gap:20px}.phrase-row h3{font-size:20px;overflow-wrap:anywhere}.phrase-row p{margin:4px 0 8px}.phrase-empty{text-align:center;padding:42px 12px}.phrase-notice{padding:12px;background:#fff1e7;border-radius:8px;margin-bottom:16px}.phrase-overlay{position:fixed;inset:0;z-index:1000;background:#17291d60;display:flex;justify-content:flex-end}.phrase-dialog{width:min(560px,100%);height:100%;overflow-y:auto;box-sizing:border-box;background:var(--surface);padding:28px}.phrase-dialog label,.phrase-practice label{display:block;margin:15px 0 7px}.phrase-dialog form button{margin-top:18px}.phrase-source{border-top:1px solid #dfe6df;padding:15px 0}.phrase-source small{display:block;margin-top:10px}.phrase-practice{margin:30px auto;max-width:580px;padding:28px;border:1px solid #dfe6df;border-radius:14px;background:var(--surface)}.phrase-practice .phrase-actions{margin:14px 0}.phrase-sr{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}@media(max-width:640px){.phrase-row{align-items:flex-start;flex-direction:column}.phrase-row .phrase-actions{gap:8px}.phrase-book button{padding:9px 10px}.phrase-dialog{padding:20px}.phrase-practice{padding:20px}.phrase-toolbar{gap:16px}.phrase-search{flex-basis:100%}}

/* Compact collection list; practice and detail-dialog controls keep their existing sizing. */
.phrase-book>.phrase-toolbar{margin-bottom:12px;gap:8px}
.phrase-book>.phrase-toolbar h2{font-size:21px;margin-bottom:4px}
.phrase-book>.phrase-toolbar p{font-size:12px;line-height:1.5;margin:4px 0 0}
.phrase-book>.phrase-actions{gap:8px}
.phrase-book>.phrase-actions button,.phrase-row .phrase-actions button{font-size:12px;line-height:1.4;min-height:34px;padding:6px 10px;border-radius:9px}
.phrase-book>.phrase-caption{font-size:11px;line-height:1.6;margin:6px 0 10px;color:#738071}
.phrase-list{margin-top:12px}
.phrase-row{padding:12px 0;gap:14px;min-width:0}
.phrase-row>div:first-child{flex:1;min-width:0}
.phrase-row h3{font-size:18px;line-height:1.3;margin:0 0 3px}
.phrase-row p{font-size:13px;line-height:1.5;margin:2px 0 5px;overflow-wrap:anywhere}
.phrase-row-meta{display:flex;align-items:baseline;flex-wrap:wrap;gap:3px 12px}
.phrase-row-meta small,.phrase-row-meta .phrase-caption{font-size:11px;line-height:1.5;margin:0;color:#738071}
.phrase-row .phrase-actions{gap:6px;flex-shrink:0}
@media(max-width:640px){.phrase-row{gap:8px}.phrase-row .phrase-actions{margin:0}.phrase-row .phrase-actions button{font-size:11px;min-height:32px;padding:6px 9px}.phrase-row-meta{gap:2px 8px}}
</style>

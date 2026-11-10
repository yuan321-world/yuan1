<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import type {Overview,Session,SavedRound,Wordbook} from '@/api/vocabulary-learning'
import {useAuthStore} from '@/stores/auth'
import WordbookSelector from './WordbookSelector.vue'
import LearningSettings from './LearningSettings.vue'
import LearningTrend from './LearningTrend.vue'
const props=defineProps<{overview:Overview;session:Session|null;busy:boolean}>()
const emit=defineEmits<{start:[kind:'NEW'|'REVIEW',mode:'RECOGNITION'|'SPELLING'];changed:[];resume:[id:string];busy:[value:boolean]}>()
const auth = useAuthStore()
const progress = ref<HTMLDialogElement>()
const practice = ref<HTMLDialogElement>()
const plan = ref<HTMLDialogElement>()
const planOpen = ref(false)
const settingsBusy = ref(false)
const sourceBusy = ref(false)
const kind = ref<'NEW'|'REVIEW'>('NEW')
watch(()=>settingsBusy.value||sourceBusy.value,value=>emit('busy',value))
let opener:HTMLElement|null=null
const rounds=computed(()=>props.overview.savedSessions||([] as SavedRound[]))
const books = ref<Wordbook[]>([])
const coverage = computed(() => books.value.find(book => book.id === props.overview.source?.bookId))
const reviews = computed(() => rounds.value.filter(round => round.kind === 'REVIEW'))
const sourceKey = computed(() => props.overview.source?.kind === 'BOOK' ? 'BOOK:' + props.overview.source.bookId : 'PERSONAL')
const savedNew = computed(() => rounds.value.find(round => round.kind === 'NEW' && (!round.sourceKey || round.sourceKey === sourceKey.value)))
const savedReview = computed(() => {
  const scope = props.overview.settings.reviewScope === 'CURRENT' ? 'REVIEW:' + sourceKey.value : 'GLOBAL'
  return reviews.value.find(round => round.sourceKey === scope) ?? reviews.value[0]
})
const otherReviews = computed(() => reviews.value.filter(round => round.id !== savedReview.value?.id))
const newGoalMet = computed(() => props.overview.newToday >= props.overview.settings.newGoal)
const newBlocked = computed(() => !!savedReview.value || (!savedNew.value && (!props.overview.unlearned ||
  props.overview.source?.available === false)))
function roundProgress(round: SavedRound) {
  const mode = round.mode === 'RECOGNITION' ? '看词' : round.kind === 'NEW' ? '跟打' : '默写'
  return mode + ' · ' + round.passed + ' / ' + round.total
}
function openDialog(dialog: HTMLDialogElement | undefined, event?: Event) {
  const trigger = event?.currentTarget ?? document.activeElement
  opener = trigger instanceof HTMLElement ? trigger : null
  dialog?.showModal()
}
function openProgress(event?: Event) {
  openDialog(progress.value, event)
}
function openPlan(event: Event) {
  planOpen.value = true
  openDialog(plan.value, event)
}
function onPlanClose() {
  planOpen.value = false
  close()
}
function onPlanSaved() {
  plan.value?.close()
  emit('changed')
}
function close(){opener?.isConnected&&opener.focus({preventScroll:true})}
function begin(k:'NEW'|'REVIEW',event:Event){if(props.busy||(k==='NEW'&&newBlocked.value))return;const prior=k==='NEW'?savedNew.value:savedReview.value;if(prior){emit('resume',prior.id);return}kind.value=k;openDialog(practice.value,event)}
function choose(mode:'RECOGNITION'|'SPELLING'){practice.value?.close();emit('start',kind.value,mode)}
function date(value:string|null){return value?new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai'}):''}
watch(() => auth.user?.id, () => {
  practice.value?.close()
  progress.value?.close()
  plan.value?.close()
  planOpen.value = false
}, { flush: 'sync' })
defineExpose({openProgress})
</script>
<template>
<section class="vl-dashboard-source">
  <div>
    <strong>{{ overview.source?.title || '我的阅读生词' }}</strong>
    <small>{{ overview.source?.kind === 'BOOK' ? 'ECDICT 参考词表' : '个人阅读积累' }}<template v-if="overview.source?.wordCount != null"> · 共 {{ overview.source.wordCount.toLocaleString() }} 词</template><template v-if="coverage"> · 已学 {{ coverage.learned.toLocaleString() }} 词</template></small>
  </div>
  <WordbookSelector :source="overview.source" :disabled="busy || session?.status === 'ACTIVE'" @catalog="books = $event" @changed="emit('changed')" @busy="sourceBusy = $event"/>
</section>
<p v-if="overview.source && !overview.source.available" role="alert" class="vl-notice">{{ overview.source.reason }}</p>
<div class="vl-dashboard-grid">
  <section class="vl-home-card">
    <header><h2>学新词</h2><div class="vl-dashboard-count"><strong>{{ overview.unlearned.toLocaleString() }}</strong> <small>待学</small></div></header>
    <footer>
      <span v-if="savedReview">请先完成当前复习</span>
      <span v-else-if="savedNew">{{ roundProgress(savedNew) }}</span>
      <span v-else>今日已学 {{ overview.newToday }} / {{ overview.settings.newGoal }} 词<template v-if="newGoalMet"> · 已达标</template></span>
      <button class="vl-primary" :disabled="busy || newBlocked" @click="begin('NEW', $event)">{{ savedNew ? '继续学习' : newGoalMet ? '继续学新词' : '开始学习' }}</button>
    </footer>
  </section>
  <section class="vl-home-card">
    <header><h2>复习</h2><div class="vl-dashboard-count"><strong>{{ overview.due.toLocaleString() }}</strong> <small>到期</small></div></header>
    <footer>
      <span v-if="savedReview">{{ savedReview.sourceTitle }} · {{ roundProgress(savedReview) }}</span>
      <span v-else-if="overview.due">复习目标 {{ overview.settings.reviewGoal }} 词</span>
      <span v-else>暂无到期词<template v-if="overview.nextDueAt"> · 下次 {{ date(overview.nextDueAt) }}</template></span>
      <button class="vl-primary" :disabled="busy || (!savedReview && !overview.due)" @click="begin('REVIEW', $event)">{{ savedReview ? '继续复习' : '开始复习' }}</button>
    </footer>
    <details v-if="otherReviews.length" class="vl-other-rounds">
      <summary>其他复习进度</summary>
      <div v-for="round in otherReviews" :key="round.id" class="vl-saved-round">
        <span>{{ round.sourceTitle }} · {{ roundProgress(round) }}</span>
        <button :disabled="busy" @click="emit('resume', round.id)">继续复习这轮</button>
      </div>
    </details>
  </section>
</div>
<div class="vl-plan-strip"><span><strong>学习计划</strong> 每日新词 {{ overview.settings.newGoal }} · 复习目标 {{ overview.settings.reviewGoal }} · 每组 {{ overview.settings.batchSize || 20 }} 词 · {{ overview.settings.reviewScope === 'CURRENT' ? '当前词书' : '全部来源' }}</span><button @click="openPlan">调整计划</button></div>
<LearningTrend/><dialog ref="practice" class="vl-dashboard-dialog" aria-labelledby="vl-practice-title" @close="close"><header><h2 id="vl-practice-title">选择练习方式</h2><button aria-label="关闭练习选择" @click="practice?.close()">×</button></header><p class="vl-muted">{{ kind==='NEW'?'新词学习':'到期复习' }} · 本轮方式会保存到结束。</p><div class="vl-practice-options"><button @click="choose('RECOGNITION')"><strong>看词模式 →</strong><small>{{ kind==='NEW'?'英文和中文一起理解，手动继续。':'先回忆中文，再查看释义，手动继续。' }}</small></button><button @click="choose('SPELLING')"><strong>{{ kind==='NEW'?'跟打模式':'默写模式' }} →</strong><small>{{ kind==='NEW'?'照着英文连续打完整轮，最后集中看释义。':'根据中文逐字写英文，辅助完成稍后再练。' }}</small></button></div></dialog>
<dialog ref="progress" class="vl-dashboard-dialog vl-summary-dialog" aria-labelledby="vl-today-title" @close="close">
  <header>
    <h2 id="vl-today-title">今天的积累</h2>
    <button type="button" aria-label="关闭今日进度" @click="progress?.close()">×</button>
  </header>
  <dl class="vl-today-metrics">
    <div>
      <dt>新词已通过</dt>
      <dd>{{ overview.newToday.toLocaleString() }} <small>/ {{ overview.settings.newGoal }}</small></dd>
    </div>
    <div>
      <dt>复习已通过</dt>
      <dd>{{ overview.reviewToday.toLocaleString() }} <small>/ {{ overview.settings.reviewGoal }}</small></dd>
    </div>
  </dl>
  <p class="vl-summary-note">待学新词 {{ overview.unlearned.toLocaleString() }} 个 · 到期复习 {{ overview.due.toLocaleString() }} 个</p>
  <p v-if="overview.reservedNew" class="vl-summary-note">已保存轮次预留 {{ overview.reservedNew.toLocaleString() }} 个新词，可继续原轮次。</p>
  <details class="vl-review-rule">
    <summary>复习规则</summary>
    <p>新轮次按 1、3、7、15、30、60、90 天安排复习，旧轮次沿用原规则。到期日北京时间凌晨 5 点开放复习。本轮出错或使用辅助的词，最终通过后次日 5 点再练。</p>
  </details>
</dialog>
<dialog ref="plan" class="vl-dashboard-dialog vl-summary-dialog vl-plan-dialog" aria-labelledby="vl-plan-title"
  @close="onPlanClose" @cancel="settingsBusy && $event.preventDefault()">
  <header>
    <h2 id="vl-plan-title">学习计划</h2>
    <button type="button" aria-label="关闭学习计划" :disabled="settingsBusy" @click="plan?.close()">×</button>
  </header>
  <LearningSettings v-if="planOpen" :settings="overview.settings" @saving="settingsBusy = $event"
    @saved="onPlanSaved" @close="plan?.close()"/>
</dialog>
</template>

<style scoped>
.vl-dashboard-dialog.vl-summary-dialog {
  width: 390px;
  padding: 18px;
  border-color: var(--line);
  border-radius: 18px;
  background: var(--surface);
  color: var(--ink);
}
.vl-dashboard-dialog.vl-plan-dialog {
  width: 490px;
}
.vl-summary-dialog > header {
  gap: 10px;
}
.vl-summary-dialog > header h2 {
  font-size: 18px;
}
.vl-summary-dialog > header button {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 9px;
  font-size: 19px;
  line-height: 1;
}
.vl-today-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 12px 0;
}
.vl-today-metrics > div {
  padding: 9px 10px;
  border-radius: 9px;
  background: var(--surface-3);
}
.vl-today-metrics dt, .vl-today-metrics small {
  font-size: 12px;
  color: var(--muted);
}
.vl-today-metrics dd {
  margin: 4px 0 0;
  font-size: 24px;
  font-weight: 650;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.vl-today-metrics small { font-weight: 500; }
.vl-summary-note, .vl-review-rule {
  font-size: 13px;
  line-height: 1.55;
  color: var(--muted);
}
.vl-summary-note { margin: 5px 0; }
.vl-review-rule {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--line);
}
.vl-review-rule summary {
  cursor: pointer;
  color: var(--accent-dark);
}
.vl-review-rule p { margin: 6px 0 0; }
.vl-plan-dialog :deep(.vl-settings) {
  margin: 0;
  padding: 0;
  border: 0;
}
.vl-plan-dialog :deep(.vl-presets) { margin: 12px 0; }
.vl-plan-dialog :deep(.vl-settings label) { margin-bottom: 10px; }
.vl-plan-dialog :deep(.vl-settings input),
.vl-plan-dialog :deep(.vl-settings select) { margin-top: 5px; }
</style>

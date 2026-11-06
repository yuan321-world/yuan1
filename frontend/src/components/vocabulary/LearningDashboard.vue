<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import type {Overview,Session,SavedRound,Wordbook} from '@/api/vocabulary-learning'
import {useAuthStore} from '@/stores/auth'
import WordbookSelector from './WordbookSelector.vue'
import LearningSettings from './LearningSettings.vue'
import LearningTrend from './LearningTrend.vue'
const props=defineProps<{overview:Overview;session:Session|null;busy:boolean}>()
const emit=defineEmits<{start:[kind:'NEW'|'REVIEW',mode:'RECOGNITION'|'SPELLING'];changed:[];resume:[id:string];busy:[value:boolean]}>()
const auth=useAuthStore(),progress=ref<HTMLDialogElement>(),practice=ref<HTMLDialogElement>(),settingsOpen=ref(false),kind=ref<'NEW'|'REVIEW'>('NEW'),settingsBusy=ref(false),sourceBusy=ref(false)
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
function openProgress(event?:Event){opener=event?.currentTarget as HTMLElement||document.activeElement as HTMLElement;settingsOpen.value=false;progress.value?.showModal()}
function close(){opener?.isConnected&&opener.focus({preventScroll:true})}
function begin(k:'NEW'|'REVIEW',event:Event){if(props.busy||(k==='NEW'&&newBlocked.value))return;const prior=k==='NEW'?savedNew.value:savedReview.value;if(prior){emit('resume',prior.id);return}kind.value=k;opener=event.currentTarget as HTMLElement;practice.value?.showModal()}
function choose(mode:'RECOGNITION'|'SPELLING'){practice.value?.close();emit('start',kind.value,mode)}
function date(value:string|null){return value?new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai'}):''}
watch(()=>auth.user?.id,()=>{practice.value?.close();progress.value?.close();settingsOpen.value=false},{flush:'sync'})
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
<div class="vl-plan-strip"><span><strong>学习计划</strong> 每日新词 {{ overview.settings.newGoal }} · 复习目标 {{ overview.settings.reviewGoal }} · 每组 {{ overview.settings.batchSize || 20 }} 词 · {{ overview.settings.reviewScope === 'CURRENT' ? '当前词书' : '全部来源' }}</span><button @click="openProgress($event);settingsOpen=true">调整计划</button></div>
<LearningTrend/><dialog ref="practice" class="vl-dashboard-dialog" aria-labelledby="vl-practice-title" @close="close"><header><h2 id="vl-practice-title">选择练习方式</h2><button aria-label="关闭练习选择" @click="practice?.close()">×</button></header><p class="vl-muted">{{ kind==='NEW'?'新词学习':'到期复习' }} · 本轮方式会保存到结束。</p><div class="vl-practice-options"><button @click="choose('RECOGNITION')"><strong>看词模式 →</strong><small>{{ kind==='NEW'?'英文和中文一起理解，手动继续。':'先回忆中文，再查看释义，手动继续。' }}</small></button><button @click="choose('SPELLING')"><strong>{{ kind==='NEW'?'跟打模式':'默写模式' }} →</strong><small>{{ kind==='NEW'?'照着英文连续打完整轮，最后集中看释义。':'根据中文逐字写英文，辅助完成稍后再练。' }}</small></button></div></dialog>
<dialog ref="progress" class="vl-dashboard-dialog" aria-labelledby="vl-today-title" @close="close" @cancel="settingsBusy&&$event.preventDefault()"><header><h2 id="vl-today-title">今天的积累</h2><button aria-label="关闭今日进度" :disabled="settingsBusy" @click="progress?.close()">×</button></header><dl class="vl-metrics"><div><dt>新增已通过 / 目标</dt><dd>{{ overview.newToday }} <small>/ {{ overview.settings.newGoal }}</small></dd></div><div><dt>复习已通过 / 目标</dt><dd>{{ overview.reviewToday }} <small>/ {{ overview.settings.reviewGoal }}</small></dd></div></dl><p v-if="overview.reservedNew" class="vl-muted">已保存轮次预留 {{ overview.reservedNew }} 个新词，可直接继续原轮次。</p><p class="vl-muted">待学新词 {{ overview.unlearned }} 个 · 到期复习 {{ overview.due }} 个</p><p class="vl-muted">新规则按1、3、7、15、30、60、90天安排复习；旧轮次保留原规则。到期日北京时间凌晨5点开放复习；本轮出错或使用辅助，最终通过后次日5点再练。</p><button v-if="!settingsOpen" class="vl-text" @click="settingsOpen=true">调整目标</button><LearningSettings v-else :settings="overview.settings" @saving="settingsBusy=$event" @saved="settingsOpen=false;emit('changed')" @close="settingsOpen=false"/></dialog>
</template>

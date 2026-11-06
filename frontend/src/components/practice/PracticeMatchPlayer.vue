<script setup lang="ts">
import PracticeSelectionArea from './PracticeSelectionArea.vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Attempt, PracticePublicQuestion, SavedAnswer } from '@/api/practice'
import { parseMatchBody } from '@/practice/match-material'
import {
  canUndoMatchChange,
  invertMatchChange,
  type MatchChoiceChange,
  type MatchUndoEntry,
} from './match-player-state'

const props = defineProps<{
  attempt: Attempt
  choices: Record<string, string | null>
  flags: Record<string, boolean>
  currentId: string
  disabled: boolean
  paperLayout?: boolean
}>()

const emit = defineEmits<{
  change: [changes: MatchChoiceChange[]]
  navigate: [id: string]
  submit: []
}>()

type MobileTab = 'article' | 'questions'
type QuestionFilter = 'all' | 'unanswered' | 'flagged'

const mobile = ref(false)
const mobileTab = ref<MobileTab>('article')
const filter = ref<QuestionFilter>('all')
const onlyWrong = ref(false)
const activeId = ref(props.currentId || props.attempt.questions[0]?.id || '')
const targetLetter = ref('')
const undoStack = ref<MatchUndoEntry[]>([])
const paragraphElements = new Map<string, HTMLElement>()
const questionButtons = new Map<string, HTMLButtonElement>()
let viewport: MediaQueryList | null = null
let viewportHandler: ((event: MediaQueryListEvent) => void) | null = null

const questions = computed(() => props.attempt.questions.filter(question => question.type === 'MATCH'))
const orderedQuestions = computed(() => questions.value)
const isCompleted = computed(() => props.attempt.status === 'COMPLETED')
const canEdit = computed(() => !isCompleted.value && !props.disabled)
const canNavigate = computed(() => isCompleted.value || !props.disabled)
const activeQuestion = computed(() =>
  orderedQuestions.value.find(question => question.id === activeId.value) ?? orderedQuestions.value[0],
)
const activeIndex = computed(() => activeQuestion.value ? orderedQuestions.value.indexOf(activeQuestion.value) : -1)

function questionNumber(question: PracticePublicQuestion): number {
  return 36 + orderedQuestions.value.findIndex(item => item.id === question.id)
}

function answerFor(questionId: string): SavedAnswer | undefined {
  return props.attempt.answers.find(answer => answer.questionId === questionId)
}

function selectedChoice(questionId: string): string | null {
  const saved = answerFor(questionId)
  if (isCompleted.value) return saved?.submittedChoice ?? saved?.draftChoice ?? props.choices[questionId] ?? null
  const choice = props.choices[questionId]
  if (choice !== undefined || Object.hasOwn(props.choices, questionId)) return choice ?? null
  return saved?.draftChoice ?? null
}

function flagged(questionId: string): boolean {
  if (Object.hasOwn(props.flags, questionId)) return props.flags[questionId] ?? false
  return answerFor(questionId)?.flagged ?? false
}

const choiceMap = computed<Record<string, string | null>>(() => Object.fromEntries(
  orderedQuestions.value.map(question => [question.id, selectedChoice(question.id)]),
))
const answeredCount = computed(() => Object.values(choiceMap.value).filter(Boolean).length)
const activeChoice = computed(() => activeQuestion.value ? selectedChoice(activeQuestion.value.id) : null)
const activeFlag = computed(() => activeQuestion.value ? flagged(activeQuestion.value.id) : false)
const visibleQuestions = computed(() => orderedQuestions.value.filter(question => {
  if (filter.value === 'unanswered') return !selectedChoice(question.id)
  if (filter.value === 'flagged') return flagged(question.id)
  return true
}))
const body = computed(() => props.attempt.article.content.paragraphs
  .map(paragraph => paragraph.sentences.map(sentence => sentence.text).join(' ').trim())
  .join('\n\n'))
const material = computed(() => parseMatchBody(body.value))
const paragraphLetters = computed(() => material.value.paragraphs.map(paragraph => paragraph.letter))
const resultRows = computed(() => orderedQuestions.value.map(question => ({
  question,
  answer: answerFor(question.id),
  choice: answerFor(question.id)?.submittedChoice ?? answerFor(question.id)?.draftChoice ?? selectedChoice(question.id),
})))
const visibleResults = computed(() => onlyWrong.value
  ? resultRows.value.filter(row => row.answer?.correct === false)
  : resultRows.value)

function paragraphQuestionNumbers(letter: string): number[] {
  return orderedQuestions.value
    .filter(question => selectedChoice(question.id) === letter)
    .map(questionNumber)
}

function paragraphUsage(letter: string): string {
  const numbers = paragraphQuestionNumbers(letter)
  return numbers.length ? `已匹配第 ${numbers.join('、')} 题` : '尚无题目匹配此段'
}

function registerParagraph(letter: string, element: Element | null) {
  if (element instanceof HTMLElement) paragraphElements.set(letter, element)
  else paragraphElements.delete(letter)
}

function registerQuestion(questionId: string, element: Element | null) {
  if (element instanceof HTMLButtonElement) questionButtons.set(questionId, element)
  else questionButtons.delete(questionId)
}

function clearUndo() {
  undoStack.value.splice(0)
}

function navigateTo(questionId: string, focus = false) {
  if (!canNavigate.value || !orderedQuestions.value.some(question => question.id === questionId)) return
  activeId.value = questionId
  targetLetter.value = ''
  if (!isCompleted.value && !props.disabled) emit('navigate', questionId)
  if (focus && !mobile.value) void nextTick(() => questionButtons.get(questionId)?.focus({ preventScroll: true }))
}

function navigateBy(offset: number) {
  const question = orderedQuestions.value[activeIndex.value + offset]
  if (question) navigateTo(question.id)
}

function applyChoice(questionId: string, choice: string | null) {
  if (!canEdit.value || (choice !== null && !paragraphLetters.value.includes(choice))) return
  const previousChoice = selectedChoice(questionId)
  if (previousChoice === choice) return
  undoStack.value.push({
    questionId,
    beforeChoice: previousChoice,
    afterChoice: choice,
    beforeFlag: flagged(questionId),
    afterFlag: flagged(questionId),
    includesFlag: false,
  })
  if (undoStack.value.length > 20) undoStack.value.shift()
  emit('change', [{ questionId, choice }])
}

function toggleFlag(questionId: string, nextFlag: boolean) {
  if (!canEdit.value || flagged(questionId) === nextFlag) return
  const choice = selectedChoice(questionId)
  undoStack.value.push({
    questionId,
    beforeChoice: choice,
    afterChoice: choice,
    beforeFlag: flagged(questionId),
    afterFlag: nextFlag,
    includesFlag: true,
  })
  if (undoStack.value.length > 20) undoStack.value.shift()
  emit('change', [{ questionId, choice, flagged: nextFlag }])
}

function clearCurrent() {
  if (!activeQuestion.value || !activeChoice.value) return
  applyChoice(activeQuestion.value.id, null)
}

function undo() {
  if (!canEdit.value) return
  const entry = undoStack.value.at(-1)
  if (!entry) return
  if (!canUndoMatchChange(entry, selectedChoice(entry.questionId), flagged(entry.questionId))) {
    clearUndo()
    return
  }
  undoStack.value.pop()
  emit('change', [invertMatchChange(entry)])
}

async function locateParagraph(letter: string) {
  if (mobile.value) mobileTab.value = 'article'
  await nextTick()
  const element = paragraphElements.get(letter)
  if (!element) return
  targetLetter.value = letter
  await nextTick()
  element.focus({ preventScroll: true })
  element.scrollIntoView?.({ block: 'center', behavior: 'smooth' })
}

function locateSelected() {
  if (activeChoice.value) void locateParagraph(activeChoice.value)
}

function matchFromParagraph(letter: string) {
  if (activeQuestion.value) applyChoice(activeQuestion.value.id, letter)
}

function revealQuestion(questionId: string) {
  filter.value = 'all'
  navigateTo(questionId)
}
defineExpose({revealQuestion})

function openQuestion(questionId: string) {
  navigateTo(questionId)
  mobileTab.value = 'questions'
}

function locateResult(questionId: string, answer: SavedAnswer | undefined) {
  activeId.value = questionId
  mobileTab.value = 'article'
  targetLetter.value = ''
  if (answer?.correctOption) void locateParagraph(answer.correctOption)
}

function submit() {
  if (!canEdit.value) return
  emit('submit')
}

function updateViewport(isMobile: boolean) {
  mobile.value = isMobile
  if (!isMobile) mobileTab.value = 'article'
}

watch(() => props.currentId, id => {
  activeId.value = id || orderedQuestions.value[0]?.id || ''
  targetLetter.value = ''
  if (!props.paperLayout) clearUndo()
}, { flush: 'sync' })

watch(() => `${props.attempt.id}\u0000${props.attempt.setVersionId}\u0000${props.attempt.quizVersionId}`, () => {
  activeId.value = props.currentId || orderedQuestions.value[0]?.id || ''
  mobileTab.value = 'article'
  filter.value = 'all'
  onlyWrong.value = false
  targetLetter.value = ''
  clearUndo()
})

onMounted(() => {
  if (typeof window.matchMedia !== 'function') return
  viewport = window.matchMedia('(max-width: 960px)')
  updateViewport(viewport.matches)
  viewportHandler = event => updateViewport(event.matches)
  viewport.addEventListener('change', viewportHandler)
})

onBeforeUnmount(() => {
  if (viewport && viewportHandler) viewport.removeEventListener('change', viewportHandler)
})
</script>

<template>
  <section class="practice-match-player" :class="{'practice-match-paper-layout':paperLayout}" aria-label="长篇匹配作答区">
    <nav v-if="!paperLayout" class="practice-match-mobile-tabs" aria-label="原文与题目切换">
      <button type="button" :aria-pressed="mobileTab === 'article'" @click="mobileTab = 'article'">阅读原文</button>
      <button type="button" :aria-pressed="mobileTab === 'questions'" @click="mobileTab = 'questions'">题目与匹配</button>
    </nav>

    <div class="practice-match-workspace" :data-tab="mobileTab">
      <article class="practice-match-paper practice-panel" aria-labelledby="match-article-title">
        <PracticeSelectionArea :article="attempt.article"/>
        <header class="practice-match-paper-heading">
          <h2 id="match-article-title" lang="en">{{ attempt.article.title }}</h2>
          <p v-if="!isCompleted">阅读原文，再为当前题选择对应段落；一个段落可以匹配多题。</p>
          <p v-else>本轮已交卷。结果只显示服务端返回的判分和解析。</p>
        </header>

        <nav class="practice-match-paragraph-nav" aria-label="定位原文段落">
          <button
            v-for="paragraph in material.paragraphs"
            :key="paragraph.letter"
            type="button"
            :aria-pressed="targetLetter === paragraph.letter"
            :aria-label="`定位${paragraph.letter}段原文`"
            :data-locate-paragraph="paragraph.letter"
            @click="locateParagraph(paragraph.letter)"
          >{{ paragraph.letter }}</button>
        </nav>

        <section v-if="activeQuestion" class="practice-match-reading-current" aria-label="当前题目原文定位栏">
          <p aria-label="当前题干">当前第 {{ questionNumber(activeQuestion) }} 题：{{ activeQuestion.stem }}</p>
          <div class="practice-match-reading-actions">
            <button type="button" :disabled="activeIndex <= 0 || !canNavigate" @click="navigateBy(-1)">上一题</button>
            <button type="button" :disabled="activeIndex >= orderedQuestions.length - 1 || !canNavigate" @click="navigateBy(1)">下一题</button>
            <button type="button" :disabled="!canNavigate" @click="openQuestion(activeQuestion.id)">查看题目</button>
          </div>
        </section>

        <p v-if="material.problem" class="practice-match-material-problem" role="alert">{{ material.problem }}</p>
        <div class="practice-match-passage" lang="en">
          <section
            v-for="(paragraph, index) in material.paragraphs"
            :key="`${paragraph.letter}-${index}`"
            :ref="element => registerParagraph(paragraph.letter, element as Element | null)"
            class="practice-match-paragraph"
            :class="{ target: targetLetter === paragraph.letter, selected: activeChoice === paragraph.letter }"
            :data-paragraph="paragraph.letter"
            :data-target="targetLetter === paragraph.letter ? 'true' : undefined"
            tabindex="-1"
            :aria-labelledby="`match-paragraph-title-${paragraph.letter}`"
          >
            <header>
              <h3 :id="`match-paragraph-title-${paragraph.letter}`">Paragraph {{ paragraph.letter }}</h3>
              <button
                type="button"
                class="practice-match-select-paragraph"
                :disabled="!canEdit || !activeQuestion"
                :aria-pressed="activeChoice === paragraph.letter"
                :data-match-paragraph="paragraph.letter"
                @click="matchFromParagraph(paragraph.letter)"
              >匹配当前题</button>
            </header>
            <p data-practice-source>{{ paragraph.text }}</p>
            <small class="practice-match-usage">{{ paragraphUsage(paragraph.letter) }}</small>
            <div v-if="paragraphQuestionNumbers(paragraph.letter).length" class="practice-match-used-questions" aria-label="已匹配的题目">
              <button
                v-for="number in paragraphQuestionNumbers(paragraph.letter)"
                :key="number"
                type="button"
                :disabled="!canNavigate"
                @click="openQuestion(orderedQuestions[number - 36]!.id)"
              >{{ number }}</button>
            </div>
          </section>
        </div>
        <p class="practice-match-paper-note">段落定位不会修改答案。选择段落后仍停留在当前题。</p>
      </article>

      <div v-if="paperLayout" class="practice-match-paper-questions">
        <label class="practice-match-filter">显示题目<select v-model="filter" aria-label="筛选题目"><option value="all">全部题目</option><option value="unanswered">仅未答</option><option value="flagged">仅待检查</option></select></label>
        <article v-for="question in visibleQuestions" :key="question.id" :data-question-id="question.id" class="practice-match-paper-question">
          <h3>{{questionNumber(question)}}. {{question.stem.replace(/^\d+[.、]\s*/, '')}}</h3>
          <label>匹配段落<select :aria-label="'第'+questionNumber(question)+'题匹配段落'" :value="selectedChoice(question.id)||''" :disabled="!canEdit" @change="navigateTo(question.id);applyChoice(question.id,($event.target as HTMLSelectElement).value||null)"><option value="">请选择</option><option v-for="letter in paragraphLetters" :key="letter" :value="letter">{{letter}}</option></select></label>
          <button :disabled="!selectedChoice(question.id)" @click="locateParagraph(selectedChoice(question.id)!)">定位所选段落</button>
          <label><input type="checkbox" :aria-label="'标记第'+questionNumber(question)+'题待检查'" :checked="flagged(question.id)" :disabled="!canEdit" @change="toggleFlag(question.id,($event.target as HTMLInputElement).checked)">待检查</label>
        </article>
        <p v-if="!visibleQuestions.length">没有符合条件的题目，请切回“全部题目”。</p>
        <button type="button" :disabled="!canEdit||undoStack.length===0" @click="undo">撤销上一步</button>
      </div>
      <aside v-if="!paperLayout" class="practice-match-questions practice-panel" aria-labelledby="match-question-panel-title">
        <h2 id="match-question-panel-title">题目与匹配</h2>
        <p class="practice-match-question-hint">选段后留在当前题；已选段落可以继续用于其他题。</p>
        <label class="practice-match-filter">
          显示题目
          <select v-model="filter" :disabled="disabled && !isCompleted" aria-label="筛选题目">
            <option value="all">全部题目</option>
            <option value="unanswered">仅未答</option>
            <option value="flagged">仅待检查</option>
          </select>
        </label>
        <nav class="practice-match-question-nav" aria-label="组内题号导航">
          <button
            v-for="question in visibleQuestions"
            :key="question.id"
            :ref="element => registerQuestion(question.id, element as Element | null)"
            type="button"
            :class="{ active: question.id === activeId, answered: !!selectedChoice(question.id), flagged: flagged(question.id) }"
            :aria-current="question.id === activeId ? 'step' : undefined"
            :aria-pressed="question.id === activeId"
            :aria-label="`第${questionNumber(question)}题，${selectedChoice(question.id) ? `已选${selectedChoice(question.id)}段` : '未答'}${flagged(question.id) ? '，待检查' : ''}`"
            :data-question-id="question.id"
            :disabled="!canNavigate"
            @click="navigateTo(question.id)"
          >{{ questionNumber(question) }}</button>
        </nav>
        <p v-if="visibleQuestions.length === 0" class="practice-match-empty-nav">没有符合条件的题目，请切回“全部题目”。</p>

        <template v-if="activeQuestion">
          <section class="practice-match-current-question" aria-live="polite">
            <h3>第 {{ questionNumber(activeQuestion) }} 题</h3>
            <p class="practice-match-statement" aria-label="当前题干">{{ activeQuestion.stem }}</p>
            <p class="practice-match-choice-summary">
              {{ activeChoice ? `当前选择：${activeChoice} 段` : '当前尚未选择匹配段落' }}
            </p>
            <div class="practice-match-letters" role="group" aria-label="为当前题选择匹配段落">
              <button
                v-for="letter in paragraphLetters"
                :key="letter"
                type="button"
                :aria-pressed="activeChoice === letter"
                :aria-label="`匹配${letter}段`"
                :disabled="!canEdit"
                @click="applyChoice(activeQuestion.id, letter)"
              >{{ letter }}</button>
            </div>
            <button type="button" class="practice-match-locate-choice" :disabled="!activeChoice" @click="locateSelected">定位所选段落</button>
            <label class="practice-match-flag">
              <input
                type="checkbox"
                aria-label="标记待检查"
                :checked="activeFlag"
                :disabled="!canEdit"
                @change="toggleFlag(activeQuestion.id, ($event.target as HTMLInputElement).checked)"
              >
              标记待检查
            </label>
            <div class="practice-match-step">
              <button type="button" :disabled="activeIndex <= 0 || !canNavigate" @click="navigateBy(-1)">上一题</button>
              <button type="button" :disabled="activeIndex >= orderedQuestions.length - 1 || !canNavigate" @click="navigateBy(1)">下一题</button>
            </div>
            <div class="practice-match-edit-actions">
              <button type="button" :disabled="!canEdit || !activeChoice" @click="clearCurrent">清空当前题</button>
              <button type="button" aria-label="撤销上一步" :disabled="!canEdit || undoStack.length === 0" @click="undo">撤销上一步</button>
            </div>
            <button v-if="!isCompleted" type="button" class="practice-match-submit" :disabled="!canEdit" @click="submit">整组交卷</button>
            <p class="practice-match-progress">已匹配 {{ answeredCount }} / {{ orderedQuestions.length }} 题</p>
          </section>
        </template>
      </aside>
    </div>

    <details v-if="!paperLayout" class="practice-match-overview practice-panel">
      <summary>全部 {{ orderedQuestions.length }} 题总览</summary>
      <div class="practice-match-overview-list">
        <article v-for="question in orderedQuestions" :key="question.id" class="practice-match-overview-row">
          <strong>{{ questionNumber(question) }}</strong>
          <div>
            <p>{{ question.stem }}</p>
            <small>当前选择：{{ selectedChoice(question.id) ?? '未答' }}{{ flagged(question.id) ? ' · 待检查' : '' }}</small>
          </div>
          <button type="button" :disabled="!canNavigate" @click="openQuestion(question.id)">{{ isCompleted ? '查看' : '编辑' }}第{{ questionNumber(question) }}题</button>
        </article>
      </div>
    </details>

    <section v-if="isCompleted" class="practice-match-results practice-panel" aria-labelledby="match-results-title">
      <header class="practice-match-results-heading">
        <div>
          <h2 id="match-results-title">本轮结果</h2>
          <p>{{ attempt.correctCount ?? resultRows.filter(row => row.answer?.correct === true).length }} / {{ orderedQuestions.length }} 题正确</p>
        </div>
        <label><input v-model="onlyWrong" type="checkbox">只看错题</label>
      </header>
      <p v-if="visibleResults.length === 0" class="practice-match-empty-results">没有错题。</p>
      <article v-for="row in visibleResults" :key="row.question.id" class="practice-match-result-row">
        <strong class="practice-match-result-number">{{ questionNumber(row.question) }}</strong>
        <div>
          <p class="practice-match-result-statement">{{ row.question.stem }}</p>
          <p :class="{ correct: row.answer?.correct === true, incorrect: row.answer?.correct === false }">
            <strong>你的选择：</strong>{{ row.choice ?? '未答' }} 段
            <template v-if="row.answer?.correct === true"> · 匹配正确</template>
            <template v-else-if="row.answer?.correct === false"> · 答错</template>
            <template v-if="row.answer?.correctOption"> · 正确段落 {{ row.answer.correctOption }}</template>
          </p>
          <p v-if="row.answer?.explanation" class="practice-match-explanation">{{ row.answer.explanation }}</p>
        </div>
        <button
          type="button"
          :disabled="!row.answer?.correctOption"
          :data-result-question="row.question.id"
          @click="locateResult(row.question.id, row.answer)"
        >{{ row.answer?.correctOption ? `定位${row.answer.correctOption}段原文` : '原文定位不可用' }}</button>
      </article>
    </section>

    <div v-if="mobile && canEdit && !paperLayout" class="practice-match-mobile-bar">
      <span>{{ answeredCount }} / {{ orderedQuestions.length }} 已匹配</span>
      <button type="button" @click="mobileTab = 'questions'">题目与匹配</button>
      <button type="button" class="primary" @click="submit">整组交卷</button>
    </div>
  </section>
</template>

<style scoped>
.practice-match-player{min-width:0;width:100%;color:var(--ink,#18181b)}
.practice-match-workspace{display:grid;grid-template-columns:minmax(0,1fr) 350px;gap:26px;align-items:start}
.practice-match-paper{min-width:0;margin:0;padding:29px 36px;background:var(--surface);border:1px solid var(--practice-line, var(--line));border-radius:15px}
.practice-match-paper-heading{border-bottom:1px solid var(--practice-line, var(--line));padding-bottom:16px;margin-bottom:18px}
.practice-match-paper-heading h2{font-size:20px;line-height:1.55;margin:0 0 6px;overflow-wrap:anywhere}
.practice-match-paper-heading p,.practice-match-paper-note{font-size:12px;line-height:1.8;color:var(--muted);margin:0}
.practice-match-paragraph-nav{display:flex;flex-wrap:wrap;gap:6px;margin:15px 0 21px}
.practice-match-player button,.practice-match-player select{font:inherit}
.practice-match-player button{min-height:38px;padding:7px 11px;border:1px solid var(--practice-line, var(--line));border-radius:9px;background:var(--surface);color:inherit;cursor:pointer;touch-action:manipulation}
.practice-match-player button:disabled{opacity:.5;cursor:default}
.practice-match-player button:focus-visible,.practice-match-player select:focus-visible,.practice-match-player input:focus-visible{outline:3px solid var(--accent);outline-offset:3px}
.practice-match-paragraph-nav button{min-height:35px;padding:5px 11px;font-size:12px}
.practice-match-paragraph-nav button[aria-pressed=true]{background:var(--accent-soft);border-color:var(--line-strong);color:var(--accent)}
.practice-match-reading-current{position:relative;padding:14px 16px;background:var(--surface-2);border-radius:10px;margin-bottom:22px;overflow-wrap:anywhere}
.practice-match-reading-current>p{font-size:13px;line-height:1.8;margin:0 0 8px}
.practice-match-reading-actions{display:flex;flex-wrap:wrap;gap:8px}
.practice-match-reading-actions button{min-height:32px;padding:5px 10px;font-size:12px}
.practice-match-material-problem{padding:12px 14px;border-radius:9px;background:#fff0f5;color:#8d3156;font-size:13px;line-height:1.7}
.practice-match-passage{max-width:790px;margin:auto;font:var(--practice-reading-size,20px)/var(--practice-reading-line,2.1) Georgia,"Times New Roman",serif;overflow-wrap:anywhere}
.practice-match-paragraph{border-bottom:1px solid var(--surface-3);padding:18px 0 21px;scroll-margin:22px;border-radius:5px}
.practice-match-paragraph:last-child{border-bottom:0}
.practice-match-paragraph>header{display:flex;align-items:center;justify-content:space-between;gap:15px}
.practice-match-paragraph h3{font:600 15px/1.5 "Segoe UI","Microsoft YaHei",sans-serif;margin:0;color:#66556b}
.practice-match-paragraph>p{margin:14px 0;font:inherit;line-height:inherit}
.practice-match-paragraph.target{background:#fdf3f7;box-shadow:0 0 0 12px #fdf3f7}
.practice-match-paragraph.selected>header h3{color:var(--accent)}
.practice-match-select-paragraph{font-size:12px;min-height:34px!important;padding:6px 10px!important;white-space:nowrap}
.practice-match-player .practice-match-select-paragraph{font:12px/1.5 "Segoe UI","Microsoft YaHei",sans-serif}
.practice-match-usage{display:block;min-height:20px;font:12px/1.8 "Segoe UI","Microsoft YaHei",sans-serif;color:var(--muted)}
.practice-match-used-questions{display:flex;gap:5px;margin-top:5px}
.practice-match-used-questions button{min-width:34px;min-height:30px;padding:4px 7px;font-size:11px;color:var(--accent)}
.practice-match-paper-note{border-top:1px solid var(--practice-line, var(--line));padding-top:17px;margin-top:22px}
.practice-match-questions{position:sticky;top:20px;max-height:calc(100dvh - 40px);overflow:auto;min-width:0;padding:23px;background:var(--surface)}
.practice-match-questions h2{font-size:18px;margin:0 0 9px}
.practice-match-question-hint,.practice-match-progress,.practice-match-empty-nav{font-size:12px;line-height:1.8;color:var(--muted);margin:0 0 14px}
.practice-match-filter{display:block;font-size:12px;color:var(--muted)}
.practice-match-filter select{display:block;width:100%;margin-top:6px;padding:8px 10px;border:1px solid var(--practice-line, var(--line));border-radius:9px;background:var(--surface);color:inherit}
.practice-match-question-nav{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:6px;margin:13px 0 17px}
.practice-match-question-nav button{position:relative;min-width:0;padding:7px 3px;font-size:12px}
.practice-match-question-nav button.active{background:var(--accent);color:white;border-color:var(--accent)}
.practice-match-question-nav button.answered:not(.active){background:var(--accent-soft);color:var(--accent)}
.practice-match-question-nav button.flagged::after{content:"";position:absolute;right:4px;top:4px;width:5px;height:5px;background:#b35d24;border-radius:50%}
.practice-match-empty-nav{margin:0 0 13px}
.practice-match-current-question{border-top:1px solid var(--practice-line, var(--line));padding-top:17px}
.practice-match-current-question h3{font-size:15px;margin:0 0 12px}
.practice-match-statement{font:18px/1.8 Georgia,"Times New Roman",serif;overflow-wrap:anywhere;margin:0 0 8px}
.practice-match-choice-summary{font-size:12px;color:var(--accent);min-height:22px;margin:0}
.practice-match-letters{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:7px;margin:14px 0}
.practice-match-letters button[aria-pressed=true]{background:var(--accent-soft);border-color:var(--line-strong);color:var(--accent)}
.practice-match-locate-choice{font-size:12px}
.practice-match-flag{display:flex;align-items:center;gap:9px;margin:16px 0;font-size:13px}
.practice-match-flag input,.practice-match-results-heading input{width:17px;height:17px;margin:0;accent-color:var(--accent)}
.practice-match-step{display:flex;justify-content:space-between;gap:8px;margin:15px 0}
.practice-match-step button{font-size:12px;padding:7px 10px}
.practice-match-edit-actions{display:grid;grid-template-columns:1fr 1fr;gap:7px}
.practice-match-edit-actions button{font-size:12px;padding:7px 5px}
.practice-match-submit,.practice-match-mobile-bar .primary{width:100%;margin:10px 0 8px;background:var(--accent)!important;color:#fff!important;border-color:var(--accent)!important}
.practice-match-progress{margin:0;min-height:20px}
.practice-match-overview,.practice-match-results{margin-top:27px;padding:25px 29px}
.practice-match-overview summary{font-size:18px;font-weight:600;cursor:pointer}
.practice-match-overview-list{margin-top:20px}
.practice-match-overview-row,.practice-match-result-row{display:grid;grid-template-columns:45px minmax(0,1fr) 125px;gap:15px;align-items:start;border-top:1px solid var(--practice-line, var(--line));padding:18px 0}
.practice-match-overview-row>strong,.practice-match-result-number{font-size:14px;padding-top:5px}
.practice-match-overview-row p,.practice-match-result-row p{margin:0;font-size:14px;line-height:1.8;overflow-wrap:anywhere}
.practice-match-overview-row small{display:block;margin-top:6px;font-size:12px;color:var(--muted)}
.practice-match-overview-row button,.practice-match-result-row>button{font-size:12px;padding:6px 9px}
.practice-match-results-heading{display:flex;align-items:center;justify-content:space-between;gap:14px;margin-bottom:15px}
.practice-match-results-heading h2{font-size:20px;margin:0}
.practice-match-results-heading p{font-size:12px;color:var(--muted);margin:5px 0 0}
.practice-match-results-heading label{display:flex;align-items:center;gap:7px;font-size:13px}
.practice-match-result-row>div{min-width:0}
.practice-match-result-row .practice-match-result-statement{margin-bottom:6px}
.practice-match-result-row p.correct{color:#315e45}
.practice-match-result-row p.incorrect{color:var(--accent)}
.practice-match-explanation{padding:12px 14px;background:#fdf1f6;border-radius:9px;margin-top:10px!important;font-size:13px!important;color:#85405e}
.practice-match-empty-results{font-size:13px;color:var(--muted);padding:15px 0}
.practice-match-mobile-tabs,.practice-match-mobile-bar{display:none}
@media(max-width:1100px){.practice-match-workspace{grid-template-columns:minmax(0,1fr) 315px;gap:18px}.practice-match-paper{padding:26px}.practice-match-questions{padding:20px}}
@media(min-width:961px){:global(.practice-study-attempt.practice-reader-focus .practice-match-workspace){grid-template-columns:minmax(0,920px);justify-content:center}:global(.practice-study-attempt.practice-reader-focus .practice-match-questions){display:none}}
@media(max-width:960px){
  .practice-match-workspace{display:block}
  .practice-match-mobile-tabs{display:flex;gap:10px;margin:18px 0}
  .practice-match-mobile-tabs button{flex:1}
  .practice-match-mobile-tabs button[aria-pressed=true]{background:var(--accent-soft);color:var(--accent)}
  .practice-match-workspace[data-tab="questions"]>.practice-match-paper,.practice-match-workspace[data-tab="article"]>.practice-match-questions{display:none}
  .practice-match-questions{position:static;max-height:none}
  .practice-match-paper{padding:26px}
  .practice-match-reading-current{position:sticky;top:0;z-index:1;box-shadow:0 3px 9px #22222208}
  .practice-match-mobile-bar{position:fixed;left:0;right:0;bottom:0;z-index:3;display:flex;align-items:center;gap:9px;padding:10px max(14px,env(safe-area-inset-right)) calc(10px + env(safe-area-inset-bottom)) max(14px,env(safe-area-inset-left));border-top:1px solid var(--practice-line, var(--line));background:var(--surface)}
  .practice-match-mobile-bar>span{font-size:11px;color:var(--muted);white-space:nowrap}
  .practice-match-mobile-bar button{flex:1;min-width:0;min-height:42px;padding:8px 5px;font-size:12px;line-height:1.3}
  .practice-match-mobile-bar .primary{width:auto;margin:0}
  .practice-match-player{padding-bottom:74px}
  .practice-match-overview,.practice-match-results{margin-top:18px;padding:21px 19px}
}
@media(max-width:560px){
  .practice-match-paper{padding:22px 19px}
  .practice-match-questions{padding:22px 19px}
  .practice-match-paragraph>header{flex-wrap:wrap;gap:8px}
  .practice-match-paragraph>p{font-size:var(--practice-reading-size,19px);line-height:var(--practice-reading-line,2.2)}
  .practice-match-overview,.practice-match-results{padding:22px 18px}
  .practice-match-overview-row,.practice-match-result-row{grid-template-columns:30px minmax(0,1fr);gap:10px}
  .practice-match-overview-row>button,.practice-match-result-row>button{grid-column:2;justify-self:start}
  .practice-match-overview-row p,.practice-match-result-row p{font-size:13px}
  .practice-match-results-heading{align-items:flex-start}
  .practice-match-reading-current{padding:12px}
  .practice-match-paper-heading h2{font-size:18px}
}
</style>

<script setup lang="ts">
import PracticeSelectionArea from './PracticeSelectionArea.vue'
import PracticeLetterPicker from './PracticeLetterPicker.vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Attempt, PracticePublicQuestion, SavedAnswer } from '@/api/practice'
import {
  confirmedClozeMove,
  invertClozeChanges,
  planClozeSelection,
  type ClozeChoiceChange,
} from './cloze-player-state'

const props = defineProps<{
  attempt: Attempt
  choices: Record<string, string | null>
  currentId: string
  disabled: boolean
  paperLayout?: boolean
}>()

const emit = defineEmits<{
  change: [changes: ClozeChoiceChange[]]
  navigate: [id: string]
  submit: []
}>()

const bankDialog = ref<HTMLDialogElement | null>(null)
const letterPicker = ref<InstanceType<typeof PracticeLetterPicker>>()
const moveDialog = ref<HTMLDialogElement | null>(null)
const unusedFilter = ref<HTMLInputElement | null>(null)
const mobile = ref(false)
const onlyUnused = ref(false)
const onlyWrong = ref(false)
const activeId = ref(props.currentId || props.attempt.questions[0]?.id || '')
const pendingMove = ref<{ targetId: string; sourceId: string; letter: string; inline: boolean } | null>(null)
const returnFocus = ref<HTMLElement | null>(null)
const suppressBankCloseFocus = ref(false)
const bankIsModal = ref(false)
const undoStack: Array<{ before: Record<string, string | null>; changes: ClozeChoiceChange[] }> = []
const gapElements = new Map<string, HTMLButtonElement>()
const wordElements = new Map<string, HTMLButtonElement>()
let viewport: MediaQueryList | null = null
let viewportHandler: ((event: MediaQueryListEvent) => void) | null = null

const questions = computed(() => props.attempt.questions.filter(question => question.type === 'CLOZE'))
const questionByNumber = computed(() => new Map(
  questions.value
    .map(question => [question.stem.trim(), question] as const)
    .filter(([number]) => /^\d+$/.test(number)),
))
const orderedQuestions = computed(() => [...questions.value].sort((left, right) => {
  const leftNumber = Number(left.stem)
  const rightNumber = Number(right.stem)
  if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) return leftNumber - rightNumber
  return props.attempt.questions.indexOf(left) - props.attempt.questions.indexOf(right)
}))
const wordBank = computed(() => props.attempt.wordBank ?? [])
const isCompleted = computed(() => props.attempt.status === 'COMPLETED')
const canEdit = computed(() => !isCompleted.value && !props.disabled)

function answerFor(questionId: string): SavedAnswer | undefined {
  return props.attempt.answers.find(answer => answer.questionId === questionId)
}

function selectedChoice(questionId: string): string | null {
  const choice = props.choices[questionId]
  if (choice !== undefined || Object.hasOwn(props.choices, questionId)) return choice ?? null
  return answerFor(questionId)?.draftChoice ?? null
}

const choiceMap = computed<Record<string, string | null>>(() => Object.fromEntries(
  questions.value.map(question => [question.id, selectedChoice(question.id)]),
))
const answeredCount = computed(() => Object.values(choiceMap.value).filter(Boolean).length)
const currentQuestion = computed(() => questions.value.find(question => question.id === activeId.value) ?? orderedQuestions.value[0])
const currentNumber = computed(() => currentQuestion.value?.stem.trim() ?? '')
const currentAnswer = computed(() => currentQuestion.value ? selectedChoice(currentQuestion.value.id) : null)
const pendingMoveCopy = computed(() => {
  const move = pendingMove.value
  if (!move) return ''
  const source = questions.value.find(question => question.id === move.sourceId)?.stem ?? ''
  const target = questions.value.find(question => question.id === move.targetId)?.stem ?? ''
  return `“${move.letter} · ${letterWord(move.letter)}”已填在第 ${source} 空。确认后会清空原空并填入第 ${target} 空。`
})
const currentContext = computed(() => {
  if (!currentNumber.value) return ''
  for (const paragraph of props.attempt.article.content.paragraphs) {
    for (const sentence of paragraph.sentences) {
      if (sentence.text.includes(`{{${currentNumber.value}}}`)) {
        return sentence.text.replace(/\{\{(\d+)\}\}/g, (_marker, number: string) => number === currentNumber.value ? '_____' : `[${number}]`)
      }
    }
  }
  return ''
})

const words = computed(() => wordBank.value.map((word, index) => {
  const letter = String.fromCharCode(65 + index)
  const usedBy = Object.entries(choiceMap.value).find(([, choice]) => choice === letter)?.[0] ?? null
  return { letter, word, usedBy, current: currentAnswer.value === letter }
}))
const visibleWords = computed(() => onlyUnused.value ? words.value.filter(word => !word.usedBy) : words.value)
const letterOptions = computed(() => words.value.map(({ letter, word, usedBy }) => ({
  letter,
  label: `${letter}，${word}${usedBy ? `，已用于第${questions.value.find(question => question.id === usedBy)?.stem}空` : ''}`,
  used: !!usedBy,
})))
const completedAnswers = computed(() => orderedQuestions.value.map(question => ({
  question,
  answer: answerFor(question.id),
  choice: answerFor(question.id)?.submittedChoice ?? answerFor(question.id)?.draftChoice ?? selectedChoice(question.id),
})))
const visibleResults = computed(() => onlyWrong.value
  ? completedAnswers.value.filter(row => row.answer?.correct === false)
  : completedAnswers.value)

function letterWord(letter: string | null | undefined): string {
  if (!letter) return ''
  const index = letter.charCodeAt(0) - 65
  return index >= 0 && index < wordBank.value.length ? wordBank.value[index] ?? '' : ''
}

function gapLabel(question: PracticePublicQuestion): string {
  const choice = isCompleted.value
    ? answerFor(question.id)?.submittedChoice ?? answerFor(question.id)?.draftChoice ?? selectedChoice(question.id)
    : selectedChoice(question.id)
  if (!isCompleted.value) return choice ? `第${question.stem}空，已选 ${choice} ${letterWord(choice)}` : `第${question.stem}空，未作答`

  const answer = answerFor(question.id)
  const correct = answer?.correctOption
  return answer?.correct === false
    ? `第${question.stem}空，错误，所选 ${choice ?? '未答'} ${letterWord(choice)}，正确答案 ${correct ?? ''} ${letterWord(correct)}`
    : `第${question.stem}空，正确答案 ${choice ?? correct ?? ''} ${letterWord(choice ?? correct)}`
}

function gapText(question: PracticePublicQuestion): string {
  const choice = isCompleted.value
    ? answerFor(question.id)?.submittedChoice ?? answerFor(question.id)?.draftChoice ?? selectedChoice(question.id)
    : selectedChoice(question.id)
  if (!isCompleted.value) return choice ? `${choice} ${letterWord(choice)}` : '?'

  const answer = answerFor(question.id)
  const correct = answer?.correctOption
  if (answer?.correct === false) return `${choice ? `${choice} ${letterWord(choice)}` : '未答'} → ${correct ? `${correct} ${letterWord(correct)}` : '正确答案'}`
  return `${choice ?? correct ?? '已答'} ${letterWord(choice ?? correct)}`
}

function gapClass(question: PracticePublicQuestion) {
  const answer = answerFor(question.id)
  return {
    current: question.id === activeId.value,
    filled: !!selectedChoice(question.id),
    right: isCompleted.value && answer?.correct === true,
    wrong: isCompleted.value && answer?.correct === false,
  }
}

function paragraphSegments(text: string) {
  const segments: Array<{ text?: string; question?: PracticePublicQuestion }> = []
  const pattern = /\{\{(\d+)\}\}/g
  let cursor = 0
  for (const match of text.matchAll(pattern)) {
    const start = match.index ?? 0
    const question = questionByNumber.value.get(match[1] ?? '')
    if (start > cursor) segments.push({ text: text.slice(cursor, start) })
    if (question) segments.push({ question })
    else segments.push({ text: match[0] })
    cursor = start + match[0].length
  }
  if (cursor < text.length || segments.length === 0) segments.push({ text: text.slice(cursor) })
  return segments
}

const paragraphs = computed(() => props.attempt.article.content.paragraphs.map(paragraph => ({
  key: paragraph.key,
  sentences: paragraph.sentences.map(sentence => ({
    key: sentence.key,
    segments: paragraphSegments(sentence.text),
  })),
})))

function registerGap(questionId: string, element: Element | null) {
  if (element instanceof HTMLButtonElement) gapElements.set(questionId, element)
  else gapElements.delete(questionId)
}

function registerWord(letter: string, element: Element | null) {
  if (element instanceof HTMLButtonElement) wordElements.set(letter, element)
  else wordElements.delete(letter)
}

async function focusGap(questionId: string, scroll = false) {
  await nextTick()
  const target = gapElements.get(questionId)
  if (!target) return
  target.focus({ preventScroll: true })
  if (scroll) target.scrollIntoView?.({ block: 'center', behavior: 'smooth' })
}

function navigateTo(questionId: string, focus = false) {
  if (!questions.value.some(question => question.id === questionId)) return
  activeId.value = questionId
  if (!isCompleted.value && !props.disabled) emit('navigate', questionId)
  if (focus && !mobile.value) void focusGap(questionId, true)
}

function setCurrentFromGap(question: PracticePublicQuestion, element: HTMLButtonElement) {
  returnFocus.value = element
  activeId.value = question.id
  if (!isCompleted.value && !props.disabled) emit('navigate', question.id)
  if (canEdit.value) void nextTick(() => letterPicker.value?.open(element))
}

function updateBankMode(isMobile: boolean) {
  const dialog = bankDialog.value
  mobile.value = isMobile && !props.paperLayout
  isMobile = mobile.value
  if (!dialog) return

  if (isMobile) {
    if (dialog.open) {
      suppressBankCloseFocus.value = true
      dialog.close()
    }
    bankIsModal.value = false
    dialog.removeAttribute('open')
    return
  }

  if (dialog.open && bankIsModal.value) {
    suppressBankCloseFocus.value = true
    dialog.close()
  }
  bankIsModal.value = false
  dialog.setAttribute('open', '')
}

function openBank() {
  const dialog = bankDialog.value
  if (!dialog || !mobile.value || !canEdit.value || dialog.open) return
  dialog.showModal()
  bankIsModal.value = true
  void nextTick(() => bankDialog.value?.querySelector<HTMLButtonElement>('[aria-current="step"]')?.focus())
}

function closeBank(restore = true) {
  const dialog = bankDialog.value
  if (!dialog?.open || !mobile.value) return
  dialog.close()
  bankIsModal.value = false
  if (restore) void nextTick(() => (returnFocus.value ?? gapElements.get(activeId.value))?.focus())
}

function onBankClose() {
  bankIsModal.value = false
  if (suppressBankCloseFocus.value) {
    suppressBankCloseFocus.value = false
    return
  }
  if (mobile.value) void nextTick(() => (returnFocus.value ?? gapElements.get(activeId.value))?.focus())
}

function beforeChoices(changes: ClozeChoiceChange[]) {
  return Object.fromEntries(changes.map(change => [change.questionId, choiceMap.value[change.questionId] ?? null]))
}

function applyChanges(changes: ClozeChoiceChange[], recordUndo = true) {
  if (!canEdit.value || changes.length === 0) return false
  if (recordUndo) {
    undoStack.push({ before: beforeChoices(changes), changes: changes.map(change => ({ ...change })) })
    if (undoStack.length > 20) undoStack.shift()
  }
  emit('change', changes)
  return true
}

function selectWord(letter: string, inline = false) {
  if (!canEdit.value || !currentQuestion.value) return
  const plan = planClozeSelection(choiceMap.value, currentQuestion.value.id, letter)
  if (plan.type === 'none') return
  if (plan.type === 'apply') {
    if (!applyChanges(plan.changes)) return
    if (!inline) afterChoice(letter)
    return
  }

  pendingMove.value = {
    targetId: currentQuestion.value.id,
    sourceId: plan.sourceQuestionId,
    letter,
    inline,
  }
  moveDialog.value?.showModal()
  void nextTick(() => moveDialog.value?.querySelector<HTMLButtonElement>('[data-cancel-move]')?.focus())
}

function confirmMove() {
  const move = pendingMove.value
  if (!move || !canEdit.value) {
    cancelMove()
    return
  }
  const changes = confirmedClozeMove(choiceMap.value, move.targetId, move.letter, move.sourceId)
  pendingMove.value = null
  moveDialog.value?.close()
  const applied = applyChanges(changes)
  if (move.inline) void focusGap(move.targetId)
  else if (applied) afterChoice(move.letter)
  else void nextTick(() => wordElements.get(move.letter)?.focus())
}

function cancelMove() {
  const move = pendingMove.value
  pendingMove.value = null
  if (moveDialog.value?.open) moveDialog.value.close()
  if (move?.inline) void focusGap(move.targetId)
  else if (move) void nextTick(() => wordElements.get(move.letter)?.focus())
}

function afterChoice(letter: string) {
  if (onlyUnused.value) void nextTick(() => unusedFilter.value?.focus())
  if (mobile.value) closeBank(true)
  else if (!onlyUnused.value) void nextTick(() => wordElements.get(letter)?.focus())
}

function clearCurrent() {
  if (!canEdit.value || !currentQuestion.value || !currentAnswer.value) return
  applyChanges([{ questionId: currentQuestion.value.id, choice: null }])
}

function undo() {
  if (!canEdit.value) return
  const previous = undoStack.pop()
  if (!previous) return
  const inverse = invertClozeChanges(previous.before, previous.changes)
  applyChanges(inverse, false)
  if (mobile.value && onlyUnused.value) void nextTick(() => unusedFilter.value?.focus())
}

function submit() {
  if (!canEdit.value) return
  if (mobile.value && bankDialog.value?.open) {
    suppressBankCloseFocus.value = true
    closeBank(false)
  }
  emit('submit')
}

function onWordKeydown(event: KeyboardEvent, letter: string) {
  const keys = ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End']
  if (!keys.includes(event.key)) return
  const index = visibleWords.value.findIndex(word => word.letter === letter)
  if (index < 0 || visibleWords.value.length === 0) return
  event.preventDefault()
  const columns = 3
  let next = index
  if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = visibleWords.value.length - 1
  else if (event.key === 'ArrowLeft') next = Math.max(0, index - 1)
  else if (event.key === 'ArrowRight') next = Math.min(visibleWords.value.length - 1, index + 1)
  else if (event.key === 'ArrowUp') next = Math.max(0, index - columns)
  else next = Math.min(visibleWords.value.length - 1, index + columns)
  wordElements.get(visibleWords.value[next]?.letter ?? '')?.focus()
}

function jumpToResult(questionId: string) {
  navigateTo(questionId)
  void focusGap(questionId, true)
}

function moveDialogCancel(event: Event) {
  event.preventDefault()
  cancelMove()
}

watch(() => props.currentId, id => {
  if (id !== activeId.value) letterPicker.value?.close(false)
  if (id) activeId.value = id
})

watch(() => props.attempt.id, () => {
  letterPicker.value?.close(false)
  undoStack.splice(0)
  onlyWrong.value = false
  onlyUnused.value = false
  activeId.value = props.currentId || props.attempt.questions[0]?.id || ''
  pendingMove.value = null
  if (moveDialog.value?.open) moveDialog.value.close()
  if (mobile.value && bankDialog.value?.open) {
    suppressBankCloseFocus.value = true
    closeBank(false)
  }
})

watch(canEdit, editable => { if (!editable) letterPicker.value?.close(false) })

onMounted(() => {
  viewport = window.matchMedia('(max-width: 900px)')
  updateBankMode(viewport.matches)
  viewportHandler = event => updateBankMode(event.matches)
  viewport.addEventListener('change', viewportHandler)
})

onBeforeUnmount(() => {
  if (viewport && viewportHandler) viewport.removeEventListener('change', viewportHandler)
})
</script>

<template>
  <section class="practice-cloze-player" :class="{'practice-cloze-paper':paperLayout}" aria-label="选词填空作答区">
    <div class="practice-cloze-workspace">
      <article class="practice-cloze-article practice-panel" aria-labelledby="cloze-article-title"><PracticeSelectionArea :article="attempt.article"/>
        <header class="practice-cloze-article-heading">
          <div>
            <h2 id="cloze-article-title">{{ attempt.article.title }}</h2>
            <p>{{ isCompleted ? '本轮已交卷 · 空位显示判定结果' : '先阅读原文，再选择空位；填词后不会自动跳到下一空。' }}</p>
          </div>
        </header>

        <div class="practice-cloze-passage" lang="en">
          <p v-for="paragraph in paragraphs" :key="paragraph.key">
            <template v-for="sentence in paragraph.sentences" :key="sentence.key">
              <template v-for="(segment, index) in sentence.segments" :key="`${sentence.key}-${index}`">
                <button
                  v-if="segment.question"
                  :ref="element => registerGap(segment.question!.id, element as Element | null)"
                  type="button"
                  class="practice-cloze-gap"
                  :class="gapClass(segment.question)"
                  :data-question-id="segment.question.id"
                  :data-practice-gap="segment.question.stem.trim()"
                  :data-practice-sentence="sentence.key"
                  :aria-label="gapLabel(segment.question)"
                  :aria-current="segment.question.id === activeId ? 'step' : undefined"
                  :aria-haspopup="canEdit ? 'dialog' : undefined"
                  :disabled="disabled && !isCompleted"
                  @click="setCurrentFromGap(segment.question, $event.currentTarget as HTMLButtonElement)"
                >
                  <small>{{ segment.question.stem }}</small><span>{{ gapText(segment.question) }}</span>
                </button>
                <span v-else data-practice-source :data-practice-sentence="sentence.key">{{ segment.text }}</span>
              </template>
              {{ ' ' }}
            </template>
          </p>
        </div>
        <p class="practice-cloze-article-note">
          <template v-if="isCompleted">交卷后可在下方查看每空答案与解析。</template>
          <template v-else>词卡最多填入一个空位。已被使用的词需要确认后才会移动。</template>
        </p>
      </article>

      <dialog ref="bankDialog" class="practice-cloze-bank" aria-label="选词词库" @close="onBankClose">
        <header class="practice-cloze-bank-heading">
          <h2>{{paperLayout?'词库':'备选词'}}</h2>
          <button v-if="mobile" type="button" class="practice-cloze-close" aria-label="关闭词库" @click="closeBank()">关闭</button>
        </header>
        <p class="practice-cloze-bank-hint">{{ wordBank.length }} 个词卡 · 每词最多使用一次</p>
        <div v-if="!paperLayout" class="practice-cloze-current" aria-live="polite">
          <strong>第 {{ currentNumber }} 空</strong>
          <p>{{ currentAnswer ? `已选 ${currentAnswer} · ${letterWord(currentAnswer)}` : '请结合原文上下文选择一个词。' }}</p>
          <p v-if="currentContext" class="practice-cloze-context" lang="en">{{ currentContext }}</p>
        </div>

        <nav v-if="!paperLayout" class="practice-cloze-step-nav" aria-label="切换空位">
          <button type="button" :disabled="(!isCompleted && disabled) || !currentQuestion || orderedQuestions.indexOf(currentQuestion) <= 0" @click="navigateTo(orderedQuestions[Math.max(0, orderedQuestions.indexOf(currentQuestion!) - 1)]?.id ?? '')">上一空</button>
          <button type="button" :disabled="(!isCompleted && disabled) || !currentQuestion || orderedQuestions.indexOf(currentQuestion) >= orderedQuestions.length - 1" @click="navigateTo(orderedQuestions[Math.min(orderedQuestions.length - 1, orderedQuestions.indexOf(currentQuestion!) + 1)]?.id ?? '')">下一空</button>
        </nav>

        <nav v-if="!paperLayout" class="practice-cloze-numbers" aria-label="空位导航">
          <button
            v-for="question in orderedQuestions"
            :key="question.id"
            type="button"
            :aria-current="question.id === activeId ? 'step' : undefined"
            :aria-pressed="question.id === activeId"
            :class="{ active: question.id === activeId, answered: !!selectedChoice(question.id) }"
            :disabled="disabled && !isCompleted"
            @click="navigateTo(question.id, !mobile)"
          >
            {{ question.stem }}
          </button>
        </nav>

        <div class="practice-cloze-word-grid" role="group" aria-label="备选词卡">
          <button
            v-for="word in visibleWords"
            :key="word.letter"
            :ref="element => registerWord(word.letter, element as Element | null)"
            type="button"
            class="practice-cloze-word"
            :class="{ used: !!word.usedBy, current: word.current }"
            :aria-label="`${word.letter}，${word.word}${word.usedBy ? `，已用于第${questions.find(q => q.id === word.usedBy)?.stem}空` : ''}`"
            :aria-pressed="word.current"
            :disabled="!canEdit"
            @click="selectWord(word.letter)"
            @keydown="onWordKeydown($event, word.letter)"
          >
            <b>{{ word.letter }}</b>
            <span>{{ word.word }}</span>
            <small v-if="word.usedBy">已用 · {{ questions.find(q => q.id === word.usedBy)?.stem }} 空</small>
            <small v-else-if="word.current">当前选择</small>
          </button>
        </div>

        <footer class="practice-cloze-bank-footer">
          <label class="practice-cloze-unused"><input ref="unusedFilter" v-model="onlyUnused" type="checkbox" :disabled="!canEdit">只看未使用词</label>
          <div class="practice-cloze-bank-actions">
            <button type="button" :disabled="!canEdit || !currentAnswer" @click="clearCurrent">清空当前空</button>
            <button type="button" :disabled="!canEdit || undoStack.length === 0" @click="undo">撤销上一步</button>
          </div>
          <button v-if="!paperLayout && (!mobile || bankIsModal)" type="button" class="practice-cloze-submit" :disabled="!canEdit" @click="submit">整组交卷</button>
          <p class="practice-cloze-bank-note">{{ isCompleted ? '本轮已经完成，词卡仅用于回看上下文。' : `${answeredCount} / ${questions.length} 空已选择` }}</p>
        </footer>
      </dialog>
    </div>

    <PracticeLetterPicker ref="letterPicker" :title="`第 ${currentNumber} 空 · 从词库里选一个词`"
      :options="letterOptions" :selected="currentAnswer" @choose="selectWord($event, true)" @clear="clearCurrent" />

    <section v-if="isCompleted" class="practice-cloze-results practice-panel" aria-labelledby="cloze-results-title">
      <header class="practice-cloze-results-heading">
        <div>
          <h2 id="cloze-results-title">逐空结果</h2>
          <p>{{ attempt.correctCount ?? completedAnswers.filter(row => row.answer?.correct).length }} / {{ questions.length }} 空正确</p>
        </div>
        <label><input v-model="onlyWrong" type="checkbox">只看错空</label>
      </header>
      <p v-if="visibleResults.length === 0" class="practice-cloze-empty">没有错空。</p>
      <article v-for="row in visibleResults" :key="row.question.id" class="practice-cloze-result-row">
        <button type="button" class="practice-cloze-result-jump" @click="jumpToResult(row.question.id)">定位第 {{ row.question.stem }} 空</button>
        <div>
          <p :class="{ correct: row.answer?.correct, incorrect: row.answer?.correct === false }">
            <strong>你的答案：</strong>{{ row.choice ? `${row.choice} · ${letterWord(row.choice)}` : '未答' }}
            <template v-if="row.answer?.correct === false && row.answer.correctOption">　<strong>正确答案：</strong>{{ row.answer.correctOption }} · {{ letterWord(row.answer.correctOption) }}</template>
          </p>
          <p v-if="row.answer?.explanation" class="practice-cloze-explanation">{{ row.answer.explanation }}</p>
        </div>
      </article>
    </section>

    <div v-if="mobile && !isCompleted" class="practice-cloze-mobile-bar">
      <span>{{ answeredCount }} / {{ questions.length }}</span>
      <button type="button" class="practice-cloze-open-bank" :disabled="!canEdit" @click="openBank">打开词库</button>
      <button type="button" class="practice-cloze-mobile-submit" :disabled="!canEdit" @click="submit">整组交卷</button>
    </div>

    <dialog ref="moveDialog" class="practice-cloze-confirm" aria-labelledby="cloze-move-title" @cancel="moveDialogCancel">
      <h2 id="cloze-move-title">确认移动词卡</h2>
      <p v-if="pendingMove">{{ pendingMoveCopy }}</p>
      <div class="practice-cloze-confirm-actions">
        <button type="button" data-cancel-move @click="cancelMove">取消</button>
        <button type="button" class="practice-cloze-confirm-accept" @click="confirmMove">确认移动</button>
      </div>
    </dialog>
  </section>
</template>

<style scoped>
.practice-cloze-player{min-width:0;width:100%;color:var(--ink,#18181b)}
.practice-cloze-workspace{display:grid;grid-template-columns:minmax(0,1fr) 352px;gap:24px;align-items:start}
.practice-cloze-article{min-width:0;margin:0;padding:34px 38px;background:var(--surface);border:1px solid var(--practice-line, var(--line));border-radius:14px}
.practice-cloze-article-heading{display:flex;align-items:center;justify-content:space-between;gap:18px;border-bottom:1px solid var(--practice-line, var(--line));padding-bottom:18px;margin-bottom:22px}
.practice-cloze-article-heading h2{font-size:18px;line-height:1.55;margin:0 0 5px;overflow-wrap:anywhere}
.practice-cloze-article-heading p,.practice-cloze-article-note{font-size:12px;line-height:1.8;color:var(--muted);margin:0}
.practice-cloze-passage{max-width:790px;margin:auto;font:var(--practice-reading-size,20px)/var(--practice-reading-line,2.1) Georgia,"Times New Roman",serif;overflow-wrap:anywhere}
.practice-cloze-passage>p{margin:0 0 24px}.practice-cloze-passage>p:last-child{margin-bottom:0}
.practice-cloze-gap{display:inline-flex;vertical-align:baseline;align-items:center;gap:5px;min-height:34px;max-width:100%;margin:0 3px;padding:4px 8px;border:0;border-bottom:2px solid #d3c5d0;border-radius:5px;background:var(--surface-3);color:inherit;font:600 14px/1.45 "Segoe UI","Microsoft YaHei",sans-serif;text-align:left;overflow-wrap:anywhere;scroll-margin-top:25px;scroll-margin-bottom:90px}
.practice-cloze-gap small{font-size:10px;line-height:1.4;flex:none;color:var(--muted)}.practice-cloze-gap span{overflow-wrap:anywhere}
.practice-cloze-gap.current{background:var(--accent-soft);border-color:var(--accent);color:var(--accent-dark)}.practice-cloze-gap.filled{background:var(--surface-3)}.practice-cloze-gap.right{border-color:#557566;background:#edf4ef;color:#315b43}.practice-cloze-gap.wrong{border-color:#b55470;background:#fceef2;color:#95374f}
.practice-cloze-gap:focus-visible,.practice-cloze-word:focus-visible,.practice-cloze-numbers button:focus-visible,.practice-cloze-results button:focus-visible{outline:3px solid var(--accent);outline-offset:3px}
.practice-cloze-article-note{border-top:1px solid var(--practice-line, var(--line));padding-top:17px;margin-top:24px}
.practice-cloze-bank{box-sizing:border-box;width:100%;max-width:none;max-height:calc(100dvh - 44px);overflow:auto;position:sticky;top:22px;margin:0;padding:22px;border:1px solid var(--practice-line, var(--line));border-radius:14px;background:var(--surface);color:inherit;box-shadow:0 12px 35px #28233012}
.practice-cloze-bank::backdrop,.practice-cloze-confirm::backdrop{background:#201a2d65}
.practice-cloze-bank-heading,.practice-cloze-results-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}
.practice-cloze-bank-heading h2,.practice-cloze-results-heading h2{font-size:19px;margin:0}
.practice-cloze-bank button,.practice-cloze-confirm button,.practice-cloze-results button{min-height:38px;padding:8px 11px;border:1px solid var(--practice-line, var(--line));border-radius:9px;background:var(--surface);color:inherit;font:inherit;cursor:pointer}
.practice-cloze-bank button:disabled,.practice-cloze-confirm button:disabled,.practice-cloze-results button:disabled{opacity:.5;cursor:default}
.practice-cloze-close{display:none}
.practice-cloze-bank-hint{font-size:12px;line-height:1.8;color:var(--muted);margin:9px 0 14px}
.practice-cloze-current{font-size:13px;line-height:1.7;background:var(--surface-3);border-radius:9px;padding:12px;margin:0 0 13px;overflow-wrap:anywhere}
.practice-cloze-current strong{color:var(--accent)}.practice-cloze-current p{margin:5px 0 0}.practice-cloze-current .practice-cloze-context{font:13px/1.8 Georgia,"Times New Roman",serif;color:var(--muted);max-height:6.8em;overflow:auto}
.practice-cloze-step-nav{display:flex;justify-content:space-between;gap:10px;margin-bottom:11px}.practice-cloze-step-nav button{font-size:12px;padding:6px 11px}
.practice-cloze-numbers{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:6px;margin:13px 0 16px}
.practice-cloze-numbers button{padding:7px 2px;font-size:12px}.practice-cloze-numbers button.active{background:var(--accent);color:#fff;border-color:var(--accent)}.practice-cloze-numbers button.answered:not(.active){background:var(--accent-soft);color:var(--accent)}
.practice-cloze-word-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:7px}
.practice-cloze-word{display:flex;min-width:0;min-height:70px!important;flex-direction:column;align-items:center;justify-content:center;gap:4px;padding:8px 5px!important;text-align:center;overflow-wrap:anywhere}
.practice-cloze-word b{font-size:10px;font-weight:500;color:var(--muted)}.practice-cloze-word span{font:14px/1.45 Georgia,"Times New Roman",serif;overflow-wrap:anywhere}.practice-cloze-word small{font-size:9px;line-height:1.3;color:var(--muted)}
.practice-cloze-word.used{background:var(--surface-3);color:#756f79}.practice-cloze-word.current{background:var(--accent-soft);color:var(--accent);border-color:var(--line-strong)}
.practice-cloze-bank-footer{border-top:1px solid var(--practice-line, var(--line));padding-top:13px;margin-top:16px}
.practice-cloze-unused{display:flex;align-items:center;gap:8px;margin:0 0 12px;font-size:12px;color:var(--muted)}.practice-cloze-unused input{width:16px;height:16px;margin:0;accent-color:var(--accent)}
.practice-cloze-bank-actions{display:grid;grid-template-columns:1fr 1fr;gap:7px}.practice-cloze-bank-actions button{font-size:12px;padding:6px 5px}
.practice-cloze-submit,.practice-cloze-mobile-submit{width:100%;margin-top:10px;background:var(--accent)!important;border-color:var(--accent)!important;color:#fff!important}
.practice-cloze-bank-note{min-height:20px;font-size:11px;line-height:1.7;color:var(--muted);margin:9px 0 0}
.practice-cloze-results{margin:24px 0 0;padding:25px 28px}.practice-cloze-results-heading{margin-bottom:15px}.practice-cloze-results-heading p{font-size:12px;color:var(--muted);margin:5px 0 0}.practice-cloze-results-heading label{display:flex;align-items:center;gap:7px;font-size:12px}.practice-cloze-results-heading input{accent-color:var(--accent)}
.practice-cloze-result-row{display:grid;grid-template-columns:150px minmax(0,1fr);gap:18px;border-top:1px solid var(--practice-line, var(--line));padding:16px 0;align-items:start}.practice-cloze-result-jump{justify-self:start;font-size:12px}.practice-cloze-result-row p{font-size:13px;line-height:1.8;margin:0}.practice-cloze-result-row p.correct{color:#315b43}.practice-cloze-result-row p.incorrect{color:#95374f}.practice-cloze-result-row .practice-cloze-explanation{margin-top:8px;padding:11px 13px;border-radius:9px;background:var(--surface-3);color:var(--ink-soft)}.practice-cloze-empty{padding:20px 0;color:var(--muted);font-size:13px}
.practice-cloze-confirm{box-sizing:border-box;width:min(440px,calc(100vw - 28px));max-height:85dvh;overflow:auto;padding:24px;border:1px solid #ddd4df;border-radius:16px;background:var(--surface);color:var(--ink)}.practice-cloze-confirm h2{font-size:19px;margin:0 0 12px}.practice-cloze-confirm p{font-size:14px;line-height:1.8;color:#514c55;overflow-wrap:anywhere}.practice-cloze-confirm-actions{display:flex;justify-content:flex-end;gap:9px}.practice-cloze-confirm-actions .practice-cloze-confirm-accept{background:var(--accent);color:#fff;border-color:var(--accent)}
.practice-cloze-mobile-bar{display:none}
@media(max-width:1100px){.practice-cloze-workspace{grid-template-columns:minmax(0,1fr) 320px;gap:19px}.practice-cloze-article{padding:29px 28px}.practice-cloze-bank{padding:19px}}
@media(min-width:921px){:global(.practice-study-attempt.practice-reader-focus .practice-cloze-workspace){grid-template-columns:minmax(0,920px);justify-content:center}:global(.practice-study-attempt.practice-reader-focus .practice-cloze-bank){display:none}}
@media(max-width:900px){.practice-cloze-workspace{display:block}.practice-cloze-bank{position:fixed;inset:auto 0 0;width:min(540px,100%);max-width:100%;max-height:84dvh;margin:0 auto;padding:20px 20px calc(18px + env(safe-area-inset-bottom));border-radius:20px 20px 0 0;box-shadow:none}.practice-cloze-bank:modal{display:block}.practice-cloze-close{display:block}.practice-cloze-mobile-bar{position:fixed;left:0;right:0;bottom:0;z-index:3;display:flex;align-items:center;gap:9px;background:var(--surface);border-top:1px solid var(--practice-line, var(--line));padding:10px max(14px,env(safe-area-inset-right)) calc(10px + env(safe-area-inset-bottom)) max(14px,env(safe-area-inset-left))}.practice-cloze-mobile-bar>span{font-size:12px;color:var(--muted);white-space:nowrap}.practice-cloze-mobile-bar button{flex:1;min-width:0;min-height:42px;padding:8px 5px;border:1px solid var(--practice-line, var(--line));border-radius:9px;background:var(--surface);font-family:inherit;font-size:12px;line-height:1.3;color:inherit}.practice-cloze-mobile-bar .practice-cloze-open-bank{background:var(--accent-soft);color:var(--accent)}.practice-cloze-mobile-bar .practice-cloze-mobile-submit{width:auto;margin:0;background:var(--accent);color:#fff}.practice-cloze-player{padding-bottom:76px}.practice-cloze-results{margin-top:18px;padding:21px 19px}}
@media(max-width:560px){.practice-cloze-article{padding:23px 18px}.practice-cloze-article-heading{align-items:flex-start;gap:10px}.practice-cloze-article-heading h2{font-size:16px}.practice-cloze-passage{font-size:var(--practice-reading-size,19px);line-height:var(--practice-reading-line,2.2)}.practice-cloze-gap{min-height:31px;gap:4px;padding:3px 6px;margin:0 2px;font-size:13px}.practice-cloze-gap small{font-size:9px}.practice-cloze-bank{padding:17px 16px calc(16px + env(safe-area-inset-bottom))}.practice-cloze-bank-hint{margin-bottom:10px}.practice-cloze-current{padding:10px;margin-bottom:10px}.practice-cloze-numbers{margin:10px 0 13px}.practice-cloze-word{min-height:64px!important}.practice-cloze-word span{font-size:13px}.practice-cloze-results-heading{align-items:flex-start}.practice-cloze-result-row{grid-template-columns:minmax(0,1fr);gap:9px}.practice-cloze-result-jump{min-height:34px}.practice-cloze-confirm{padding:21px 17px}}
</style>

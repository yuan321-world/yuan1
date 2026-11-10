import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import type { Attempt } from '@/api/practice'
// @ts-expect-error Vitest's Vue plugin compiles this SFC import at test runtime.
import PracticeMatchPlayer from '@/components/practice/PracticeMatchPlayer.vue'
import { canUndoMatchChange, invertMatchChange } from '@/components/practice/match-player-state'

describe('MATCH local undo state', () => {
  it('restores the previous choice and flagged value when undoing a flag toggle', () => {
    const change = {
      questionId: 'q36',
      beforeChoice: 'B',
      afterChoice: 'B',
      beforeFlag: false,
      afterFlag: true,
      includesFlag: true,
    }

    expect(canUndoMatchChange(change, 'B', true)).toBe(true)
    expect(invertMatchChange(change)).toEqual({ questionId: 'q36', choice: 'B', flagged: false })
  })

  it('rejects an undo after the parent has replaced the recorded choice', () => {
    const change = {
      questionId: 'q36',
      beforeChoice: null,
      afterChoice: 'A',
      beforeFlag: false,
      afterFlag: false,
      includesFlag: false,
    }

    expect(canUndoMatchChange(change, 'C', false)).toBe(false)
    expect(invertMatchChange(change)).toEqual({ questionId: 'q36', choice: null })
  })
})

const paragraphs = [
  { key: 'p1', sentences: [{ key: 's1', text: '[A] First evidence sentence.' }, { key: 's2', text: 'A second sentence.' }] },
  { key: 'p2', sentences: [{ key: 's3', text: '[B] Another paragraph.' }] },
]

function makeAttempt(overrides: Partial<Attempt> = {}): Attempt {
  const questions = Array.from({ length: 10 }, (_, index) => ({
    id: `q${index + 36}`,
    key: `key-${index + 36}`,
    type: 'MATCH',
    stem: `Statement ${index + 36}`,
    options: [],
  }))

  return {
    id: 'attempt-1',
    setId: 'set-1',
    setVersionId: 'set-version-1',
    quizVersionId: 'quiz-version-1',
    kind: 'FULL',
    sourceAttemptId: null,
    status: 'IN_PROGRESS',
    version: 1,
    currentQuestionId: 'q36',
    total: 10,
    article: {
      id: 'article-1',
      versionId: 'article-version-1',
      versionNo: 1,
      title: 'Frozen article',
      titleZh: '',
      content: { paragraphs },
    } as unknown as Attempt['article'],
    questions,
    answers: [],
    startedAt: '2026-10-21T00:00:00Z',
    completedAt: null,
    title: 'Frozen article',
    readingType: 'MATCH',
    ...overrides,
  }
}

function mountPlayer(options: {
  attempt?: Attempt
  choices?: Record<string, string | null>
  flags?: Record<string, boolean>
  currentId?: string
  disabled?: boolean
} = {}) {
  return mount(PracticeMatchPlayer, {
    global: { stubs: { PracticeSelectionArea: true } },
    props: {
      attempt: options.attempt ?? makeAttempt(),
      choices: options.choices ?? {},
      flags: options.flags ?? {},
      currentId: options.currentId ?? 'q36',
      disabled: options.disabled ?? false,
    },
  })
}

describe('PracticeMatchPlayer', () => {
  it('renders the actual statement text and frozen 36–45 numbers, without treating the statement as a number', () => {
    const wrapper = mountPlayer()

    expect(wrapper.get('[aria-label="组内题号导航"]').text()).toContain('36')
    expect(wrapper.get('[aria-label="组内题号导航"]').text()).toContain('45')
    expect(wrapper.get('[aria-label="当前题干"]').text()).toContain('Statement 36')
    expect(wrapper.text()).toContain('第 36 题')
  })

  it('strips the paragraph label only at the start and leaves following sentence text intact', () => {
    const wrapper = mountPlayer()

    expect(wrapper.get('[data-paragraph="A"]').text()).toContain('First evidence sentence.')
    expect(wrapper.get('[data-paragraph="A"]').text()).toContain('A second sentence.')
    expect(wrapper.get('[data-paragraph="A"]').text()).not.toContain('[A]')
  })

  it('allows two questions to choose the same paragraph and keeps the current question selected', async () => {
    const wrapper = mountPlayer()

    await wrapper.get('[aria-label="匹配A段"]').trigger('click')
    expect(wrapper.emitted('change')?.at(-1)).toEqual([[{ questionId: 'q36', choice: 'A' }]])
    await wrapper.setProps({ currentId: 'q37', choices: { q36: 'A' } })
    await wrapper.get('[aria-label="匹配A段"]').trigger('click')
    await wrapper.setProps({ choices: { q36: 'A', q37: 'A' } })

    expect(wrapper.emitted('change')?.at(-1)).toEqual([[{ questionId: 'q37', choice: 'A' }]])
    expect(wrapper.get('[aria-label="当前题干"]').text()).toContain('Statement 37')
    expect(wrapper.props('choices')).toEqual({ q36: 'A', q37: 'A' })
    expect(wrapper.get('.practice-match-usage').text()).toContain('36、37')
    expect(wrapper.emitted('navigate')).toBeUndefined()
  })

  it('uses paragraph navigation only to locate evidence, without changing an answer', async () => {
    const wrapper = mountPlayer({ choices: { q36: 'A' } })

    await wrapper.get('[data-locate-paragraph="B"]').trigger('click')

    expect(wrapper.emitted('change')).toBeUndefined()
    expect(wrapper.get('[data-paragraph="B"]').attributes('data-target')).toBe('true')
  })

  it('returns from the mobile question tab to the article before locating the selected paragraph', async () => {
    const previousMatchMedia = Object.getOwnPropertyDescriptor(window, 'matchMedia')
    const mediaQuery = {
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn(() => mediaQuery),
    })
    const wrapper = mountPlayer({ choices: { q36: 'A' } })
    const paragraph = wrapper.get('[data-paragraph="A"]').element as HTMLElement
    const scrollIntoView = vi.fn()
    paragraph.scrollIntoView = scrollIntoView

    try {
      await wrapper.get('.practice-match-mobile-tabs button:nth-child(2)').trigger('click')
      expect(wrapper.get('.practice-match-workspace').attributes('data-tab')).toBe('questions')

      await wrapper.get('.practice-match-locate-choice').trigger('click')
      await flushPromises()

      expect(wrapper.get('.practice-match-workspace').attributes('data-tab')).toBe('article')
      expect(wrapper.get('[data-paragraph="A"]').attributes('data-target')).toBe('true')
      expect(scrollIntoView).toHaveBeenCalledOnce()
      expect(wrapper.emitted('change')).toBeUndefined()
    } finally {
      wrapper.unmount()
      if (previousMatchMedia) Object.defineProperty(window, 'matchMedia', previousMatchMedia)
      else Reflect.deleteProperty(window, 'matchMedia')
    }
  })

  it('keeps tab, filter, and undo state when a save returns a new attempt object with the same identity', async () => {
    const previousMatchMedia = Object.getOwnPropertyDescriptor(window, 'matchMedia')
    const mediaQuery = {
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn(() => mediaQuery),
    })
    const wrapper = mountPlayer()

    try {
      await wrapper.get('.practice-match-mobile-tabs button:nth-child(2)').trigger('click')
      await wrapper.get('.practice-match-filter select').setValue('flagged')
      await wrapper.get('.practice-match-letters button:first-child').trigger('click')
      await wrapper.setProps({ attempt: makeAttempt(), choices: { q36: 'A' } })

      expect(wrapper.get('.practice-match-workspace').attributes('data-tab')).toBe('questions')
      expect((wrapper.get('.practice-match-filter select').element as HTMLSelectElement).value).toBe('flagged')
      expect(wrapper.get('[aria-label="撤销上一步"]').attributes('disabled')).toBeUndefined()

      await wrapper.setProps({ attempt: makeAttempt({ id: 'attempt-2' }) })

      expect(wrapper.get('.practice-match-workspace').attributes('data-tab')).toBe('article')
      expect((wrapper.get('.practice-match-filter select').element as HTMLSelectElement).value).toBe('all')
      expect(wrapper.get('[aria-label="撤销上一步"]').attributes('disabled')).toBeDefined()
    } finally {
      wrapper.unmount()
      if (previousMatchMedia) Object.defineProperty(window, 'matchMedia', previousMatchMedia)
      else Reflect.deleteProperty(window, 'matchMedia')
    }
  })

  it('clears only the current question while keeping other matched questions unchanged', async () => {
    const wrapper = mountPlayer({ choices: { q36: 'A', q37: 'B' } })

    await wrapper.get('.practice-match-edit-actions button:first-child').trigger('click')

    expect(wrapper.emitted('change')?.at(-1)).toEqual([[{ questionId: 'q36', choice: null }]])
  })

  it('emits the current choice with a flag change and supports undoing that local change', async () => {
    const wrapper = mountPlayer({ choices: { q36: 'B' }, flags: { q36: false } })

    await wrapper.get('[aria-label="标记待检查"]').setValue(true)
    expect(wrapper.emitted('change')?.at(-1)).toEqual([[{ questionId: 'q36', choice: 'B', flagged: true }]])

    await wrapper.setProps({ flags: { q36: true } })
    await wrapper.get('[aria-label="撤销上一步"]').trigger('click')
    expect(wrapper.emitted('change')?.at(-1)).toEqual([[{ questionId: 'q36', choice: 'B', flagged: false }]])
  })

  it('keeps correctness and explanations hidden before completion, then reads them from saved answers', () => {
    const inProgress = mountPlayer({
      attempt: makeAttempt({ answers: [{ questionId: 'q36', correct: false, correctOption: 'B', explanation: 'Evidence from B.' }] }),
    })
    expect(inProgress.text()).not.toContain('正确答案：B')
    expect(inProgress.text()).not.toContain('Evidence from B.')

    const completed = mountPlayer({
      attempt: makeAttempt({
        status: 'COMPLETED',
        answers: [{ questionId: 'q36', submittedChoice: 'A', correct: false, correctOption: 'B', explanation: 'Evidence from B.' }],
      }),
    })
    expect(completed.text()).toContain('正确段落 B')
    expect(completed.text()).toContain('Evidence from B.')
  })

  it('synchronizes result location to that question locally without emitting navigation', async () => {
    const completed = mountPlayer({
      attempt: makeAttempt({
        status: 'COMPLETED',
        answers: [
          { questionId: 'q36', submittedChoice: 'A', correct: true, correctOption: 'A' },
          { questionId: 'q37', submittedChoice: 'A', correct: false, correctOption: 'B', explanation: 'Evidence from B.' },
        ],
      }),
    })

    await completed.get('[data-result-question="q37"]').trigger('click')

    expect(completed.get('[aria-label="当前题干"]').text()).toContain('Statement 37')
    expect(completed.get('[data-paragraph="B"]').attributes('data-target')).toBe('true')
    expect(completed.text()).toContain('正确段落 A')
    expect(completed.emitted('navigate')).toBeUndefined()
  })

  it('does not edit or emit parent navigation when the attempt is completed or disabled', async () => {
    const wrapper = mountPlayer({ disabled: true })

    expect(wrapper.get('[aria-label="匹配A段"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-question-id="q37"]').trigger('click')
    expect(wrapper.emitted('change')).toBeUndefined()
    expect(wrapper.emitted('navigate')).toBeUndefined()
  })
})

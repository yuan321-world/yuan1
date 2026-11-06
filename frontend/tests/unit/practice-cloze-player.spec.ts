import { describe, expect, it } from 'vitest'
import {
  confirmedClozeMove,
  invertClozeChanges,
  planClozeSelection,
  type ClozeChoiceChange,
} from '@/components/practice/cloze-player-state'

describe('CLOZE choice batches', () => {
  it('keeps a used word unchanged until a move is confirmed', () => {
    const choices = { q26: 'A', q27: null }

    expect(planClozeSelection(choices, 'q27', 'A')).toEqual({
      type: 'confirm-move',
      sourceQuestionId: 'q26',
    })
    expect(choices).toEqual({ q26: 'A', q27: null })
  })

  it('creates one batch that clears the source and fills the target', () => {
    expect(confirmedClozeMove({ q26: 'A', q27: 'B' }, 'q27', 'A', 'q26')).toEqual([
      { questionId: 'q26', choice: null },
      { questionId: 'q27', choice: 'A' },
    ] satisfies ClozeChoiceChange[])
  })

  it('restores every affected blank in one undo batch', () => {
    const before = { q26: 'A', q27: 'B' }
    const move = [
      { questionId: 'q26', choice: null },
      { questionId: 'q27', choice: 'A' },
    ] satisfies ClozeChoiceChange[]

    expect(invertClozeChanges(before, move)).toEqual([
      { questionId: 'q26', choice: 'A' },
      { questionId: 'q27', choice: 'B' },
    ])
  })
})

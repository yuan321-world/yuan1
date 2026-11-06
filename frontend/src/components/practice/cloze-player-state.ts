export interface ClozeChoiceChange {
  questionId: string
  choice: string | null
}

export type ClozeSelectionPlan =
  | { type: 'none' }
  | { type: 'apply'; changes: ClozeChoiceChange[] }
  | { type: 'confirm-move'; sourceQuestionId: string }

export function planClozeSelection(
  choices: Record<string, string | null>,
  targetQuestionId: string,
  letter: string,
): ClozeSelectionPlan {
  if (!targetQuestionId || !/^[A-O]$/.test(letter) || choices[targetQuestionId] === letter) {
    return { type: 'none' }
  }

  const sourceQuestionId = Object.entries(choices)
    .find(([questionId, choice]) => questionId !== targetQuestionId && choice === letter)?.[0]

  if (sourceQuestionId) return { type: 'confirm-move', sourceQuestionId }
  return { type: 'apply', changes: [{ questionId: targetQuestionId, choice: letter }] }
}

export function confirmedClozeMove(
  choices: Record<string, string | null>,
  targetQuestionId: string,
  letter: string,
  sourceQuestionId: string,
): ClozeChoiceChange[] {
  const plan = planClozeSelection(choices, targetQuestionId, letter)
  if (plan.type !== 'confirm-move' || plan.sourceQuestionId !== sourceQuestionId) return []

  return [
    { questionId: sourceQuestionId, choice: null },
    { questionId: targetQuestionId, choice: letter },
  ]
}

export function invertClozeChanges(
  before: Record<string, string | null>,
  changes: ClozeChoiceChange[],
): ClozeChoiceChange[] {
  return changes.map(({ questionId }) => ({
    questionId,
    choice: before[questionId] ?? null,
  }))
}

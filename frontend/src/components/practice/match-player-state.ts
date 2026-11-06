export interface MatchChoiceChange {
  questionId: string
  choice: string | null
  flagged?: boolean
}

export interface MatchUndoEntry {
  questionId: string
  beforeChoice: string | null
  afterChoice: string | null
  beforeFlag: boolean
  afterFlag: boolean
  includesFlag: boolean
}

export function invertMatchChange(entry: MatchUndoEntry): MatchChoiceChange {
  return {
    questionId: entry.questionId,
    choice: entry.beforeChoice,
    ...(entry.includesFlag ? { flagged: entry.beforeFlag } : {}),
  }
}

export function canUndoMatchChange(
  entry: MatchUndoEntry,
  currentChoice: string | null,
  currentFlag: boolean,
): boolean {
  return currentChoice === entry.afterChoice && (!entry.includesFlag || currentFlag === entry.afterFlag)
}

import { businessGet, businessWrite } from './business'
import type { PageResult } from './admin-articles'
import type { DictionarySense, DictionaryCandidate } from './dictionary'
import type { WordSource } from './vocabulary'
const root = '/api/v1/vocabulary/learning'
export interface Settings { batchSize?: number; newGoal: number; reviewGoal: number; autoNext?: boolean; reviewScope?: 'ALL'|'CURRENT' }
export interface LearningSource { wordCount?: number | null; kind: 'PERSONAL' | 'BOOK'; bookId: string | null; title: string; available: boolean; reason: string }
export interface Wordbook { id: string; title: string; dataVersion: string; sourceUrl: string; notice: string; originalCount: number; availableCount: number; missingPhoneticCount: number; excluded: {word: string; reason: string}[]; status: string; learned: number }
export interface SavedRound { sourceKey?:string; id:string; kind:'NEW'|'REVIEW'; mode:'RECOGNITION'|'SPELLING'; status:'ACTIVE'|'PAUSED'; phase:'QUESTION'|'DEFINITION'; passed:number; total:number; sourceTitle:string }
export interface Overview { reservedNew?:number; savedSessions?: SavedRound[]; candidates: number; unlearned: number; due: number; nextDueAt: string | null; newToday: number; reviewToday: number; settings: Settings; sessionId: string | null; source?: LearningSource }
export interface Candidate { wordId: string; lemma: string; meaning: string; joined: boolean; paused: boolean; unavailableReason: string }
export interface LearningWord { id: string; entryId: string; lemma: string; meaning: string; active: boolean; proficient?: boolean; stage: number; dueAt: string | null }
export interface Outcome { correct: boolean; action: string; selectedText: string; stage: number | null; dueAt: string | null }
export interface Current { entryId?: string; learningWordId?: string; hintCount?: number; hintPrefix?: string; attemptId?: number; assisted?: boolean; proficient?: boolean; detail?: DictionaryCandidate | null; id: string; lemma: string | null; phonetic: string | null; partOfSpeech: string; meaning: string; extra: string; options: { id: string; text: string }[]; spellingTarget: string | null; definition: { lemma: string; senses: DictionarySense[]; sourceNote: string } | null; source: WordSource | null; outcome: Outcome | null }
export interface Session { flowVersion?: string; reviewScope?: 'ALL'|'CURRENT'; id: string; version: number; kind: 'NEW' | 'REVIEW'; mode: 'RECOGNITION' | 'SPELLING'; status: 'ACTIVE' | 'PAUSED' | 'COMPLETED'; phase: 'QUESTION' | 'DEFINITION'; current: Current | null; queue: { id: string; meaning: string; current: boolean; failures: number }[]; passed: number; total: number; results: { lemma: string; failures: number; outcome: Outcome | null; definition?: DictionaryCandidate }[] }
export interface Command { flowVersion?: 'v2'; attemptId?: number; assisted?: boolean; requestId: string; expectedVersion?: number; itemId?: string; action?: string; optionId?: string; spelling?: string; hadSpellingError?: boolean; kind?: string; mode?: string; status?: string }
export const learningOverview = (signal?: AbortSignal) => businessGet<Overview>(`${root}/overview`, signal)
export const learningCandidates = (page = 1, signal?: AbortSignal) => businessGet<PageResult<Candidate>>(`${root}/candidates?page=${page}&pageSize=20`, signal)
export const learningWords = (page = 1, signal?: AbortSignal) => businessGet<PageResult<LearningWord>>(`${root}/words?page=${page}&pageSize=20`, signal)
export const joinLearning = (wordIds: string[], signal?: AbortSignal) => businessWrite<void>(`${root}/words`, { wordIds }, 'POST', signal)
export const setLearningActive = (id: string, active: boolean) => businessWrite<void>(`${root}/words/${encodeURIComponent(id)}`, { active }, 'PATCH')
export const saveLearningSettings = (value: Settings, signal?:AbortSignal) => businessWrite<Settings>(`${root}/settings`, value, 'PUT',signal)
export const learningSession = (signal?: AbortSignal) => businessGet<Session | null>(`${root}/session`, signal)
export const learningCommand = (path: string, command: Command, signal?: AbortSignal) => businessWrite<Session>(`${root}${path}`, command, 'POST', signal)
export const wordbookCatalog = (signal?: AbortSignal) => businessGet<Wordbook[]>(`${root}/wordbooks`, signal)
export const selectLearningSource = (kind: 'PERSONAL' | 'BOOK', bookId?: string, signal?: AbortSignal) => businessWrite<LearningSource>(`${root}/source`, {kind,...(bookId?{bookId}:{})}, 'PUT', signal)

export const setLearningProficiency = (id: string, proficient: boolean) => businessWrite<void>(`${root}/words/${encodeURIComponent(id)}/proficiency`, { proficient }, 'PUT')

export const learningSavedSession=(id:string,signal?:AbortSignal)=>businessGet<Session>(`${root}/sessions/${encodeURIComponent(id)}`,signal)

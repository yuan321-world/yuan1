import { businessGet, businessWrite } from './business'
export interface PhraseSourceInput { articleVersionId: string; sentenceKey: string; startOffset: number; endOffset: number; articleId?: string; practiceAttemptId?: string; paperAttemptId?: string; slot?: string }
export interface PhraseSource extends PhraseSourceInput { id: string; articleId: string; title: string; sentence: string }
export interface Phrase { id: string; text: string; meaningZh: string; note: string; active: boolean; enrolled: boolean; exempt: boolean; independentPasses: number; stage?: number; dueAt?: string | null; ruleVersion?: string; sources: PhraseSource[] }
export interface PhraseCollectInput { text: string; meaningZh: string; note?: string; source?: PhraseSourceInput }
export interface PhraseItem { phraseId: string; text: string; meaningZh: string; passed: boolean; failures: number; assisted: boolean; revealed: boolean; draft: string; answered: boolean; ruleVersion?: string | null }
export interface PhraseRound { id: string; version: number; mode: 'RECOGNIZE' | 'WRITE'; status: 'ACTIVE' | 'PAUSED' | 'COMPLETED'; items: PhraseItem[]; queue: string[] }
export interface PhraseOverview { page: number; pageSize: number; total: number; items: Phrase[]; stats: { collected: number; enrolled: number; independentPasses: number; available?: number } }
const base = '/api/v1/vocabulary/phrases'
export const phraseList = (query = '', includeCancelled = false, page = 1, signal?: AbortSignal) => businessGet<PhraseOverview>(`${base}?query=${encodeURIComponent(query)}&includeCancelled=${includeCancelled}&page=${page}&pageSize=20`, signal)
export const createPhrase = (input: PhraseCollectInput, signal?: AbortSignal, current?: () => boolean) => businessWrite<Phrase>(base, input, 'POST', signal, current)
export const editPhrase = (id: string, input: {meaningZh: string; note: string}, signal?: AbortSignal, current?: () => boolean) => businessWrite<Phrase>(`${base}/${encodeURIComponent(id)}`, input, 'PATCH', signal, current)
export const cancelPhrase = (id: string, signal?: AbortSignal, current?: () => boolean) => businessWrite<Phrase>(`${base}/${encodeURIComponent(id)}`, undefined, 'DELETE', signal, current)
export const restorePhrase = (id: string, signal?: AbortSignal, current?: () => boolean) => businessWrite<Phrase>(`${base}/${encodeURIComponent(id)}/restore`, undefined, 'POST', signal, current)
export const setPhraseLearning = (id: string, input: {enrolled?: boolean; exempt?: boolean}, signal?: AbortSignal, current?: () => boolean) => businessWrite<Phrase>(`${base}/${encodeURIComponent(id)}/learning`, input, 'PUT', signal, current)
export const currentPhraseRound = (signal?: AbortSignal) => businessGet<PhraseRound | null>(`${base}/practice`, signal)
export const startPhraseRound = (mode: PhraseRound['mode'], requestId = crypto.randomUUID(), limit = 10, signal?: AbortSignal, current?: () => boolean) => businessWrite<PhraseRound>(`${base}/practice`, {mode, requestId, limit}, 'POST', signal, current)
export interface PhraseCommand {requestId: string; expectedVersion: number; action: 'REVEAL'|'HINT'|'ERROR'|'PASS'|'NEXT'|'SAVE'|'RESUME'|'DRAFT'; answer?: string; known?: boolean; draft?: string}
export const phraseCommand = (id: string, input: PhraseCommand, signal?: AbortSignal, current?: () => boolean) => businessWrite<PhraseRound>(`${base}/practice/${encodeURIComponent(id)}/commands`, input, 'POST', signal, current)

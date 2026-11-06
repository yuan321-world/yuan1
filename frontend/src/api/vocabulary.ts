import { businessGet, businessWrite } from './business'
import type { DictionarySense } from './dictionary'
import type { PageResult } from './admin-articles'
export interface CollectionLearningState { id: string; personalEnrolled: boolean; active: boolean; stage: number; dueAt: string | null; proficient: boolean }
export interface CollectedWord { id: string; entryId: string; lemma: string; phonetic: string | null; senses: DictionarySense[]; sourceNote: string; active: boolean; createdAt: string; updatedAt: string; learning?: CollectionLearningState | null }
export interface WordSource { id: string; articleId: string; articleVersionId: string; title: string; sentenceKey: string; startOffset: number; endOffset: number; surfaceForm: string; sentence: string; createdAt: string }
export interface CollectInput { articleVersionId: string; sentenceKey: string; startOffset: number; endOffset: number; entryId: string }
export interface CollectionOrigin { id: string; kind: 'BOOK'|'LEARNING'|'DICTIONARY'; title: string; sessionId: string | null; createdAt: string }
export interface DifficultyState { difficult:boolean; attempts:number; assistedAttempts:number; manualOverride:boolean|null }
export interface CollectedDetail { word: CollectedWord; sources: PageResult<WordSource>; origins?: CollectionOrigin[]; difficulty?:DifficultyState|null }
export const collectFromLearning = (sessionId: string, entryId: string, signal?: AbortSignal, current?: () => boolean) => businessWrite<{ word: CollectedWord; created: boolean }>('/api/v1/vocabulary/words/from-learning', { sessionId, entryId }, 'POST', signal, current)
export const collectWord = (input: CollectInput, signal?: AbortSignal, current?: () => boolean) => businessWrite<{ word: CollectedWord; created: boolean }>('/api/v1/vocabulary/words', input, 'POST', signal, current)
export const vocabularyWords = (page = 1, signal?: AbortSignal) => businessGet<PageResult<CollectedWord>>(`/api/v1/vocabulary/words?page=${page}&pageSize=20`, signal)
export const searchVocabularyWords = (page=1, query='', status='ALL', signal?:AbortSignal) => businessGet<PageResult<CollectedWord>>(`/api/v1/vocabulary/words?page=${page}&pageSize=20&q=${encodeURIComponent(query)}&status=${encodeURIComponent(status)}`, signal)
export const vocabularyDetail = (id: string, page = 1, signal?: AbortSignal) => businessGet<CollectedDetail>(`/api/v1/vocabulary/words/${encodeURIComponent(id)}?page=${page}&pageSize=10`, signal)
export const cancelWord = (id: string, signal?: AbortSignal, current?: () => boolean) => businessWrite<void>(`/api/v1/vocabulary/words/${encodeURIComponent(id)}`, undefined, 'DELETE', signal, current)

export const collectFromDictionary = (word: string, entryId: string, signal?: AbortSignal) => businessWrite<{word: CollectedWord; created: boolean}>('/api/v1/vocabulary/words/from-dictionary', {word, entryId}, 'POST', signal)

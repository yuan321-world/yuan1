import { businessGet, businessWrite, publicReadingGet } from './business'
import type { ArticleSummary } from './reading'
import type { DictionarySense } from './dictionary'
import type { CollectedWord, WordSource } from './vocabulary'
import type { PageResult } from './admin-articles'
export interface ReadingKeyword { entryId: string; lemma: string; phonetic: string | null; senses: DictionarySense[]; sourceNote: string; forms: string[] }
export interface ReadingTools { keywordSource?:string;keywordSourceKind?:'BOOK'|'PERSONAL'|'EDITORIAL'; related: ArticleSummary[]; previous: ArticleSummary | null; next: ArticleSummary | null; keywords: ReadingKeyword[]; translation: { available: boolean; source: string; paragraphs: { key: string; text: string }[]; sentences?: { key: string; sourceText: string; text: string }[] } }
export interface ArticleWord { word: CollectedWord; source: WordSource }
const base = (article: string, version: string) => `/api/v1/reading/articles/${encodeURIComponent(article)}/versions/${encodeURIComponent(version)}`
export const getReadingTools = (article: string, version: string, signal?: AbortSignal) => publicReadingGet<ReadingTools>(`${base(article, version)}/tools`, signal)
export const getArticleWords = (article: string, version: string, page: number, signal?: AbortSignal) => businessGet<PageResult<ArticleWord>>(`${base(article, version)}/words?page=${page}&pageSize=10`, signal)
export interface FullArticleTranslation {
  status: 'MISSING' | 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  completed: number
  total: number
  configured: boolean
  translation: ReadingTools['translation']
  error: string | null
}
export const getFullArticleTranslation = (article: string, version: string, signal?: AbortSignal) => publicReadingGet<FullArticleTranslation>(`${base(article, version)}/full-translation`, signal)
export const prepareFullArticleTranslation = (article: string, version: string, signal?: AbortSignal) => businessWrite<FullArticleTranslation>(`${base(article, version)}/full-translation`, undefined, 'POST', signal)

import { businessGet, businessWrite, publicReadingGet } from './business'
import type { ArticleVersion, Category, PageResult } from './admin-articles'
import type { ProgressUpdate } from '../reading/useReadingProgress'
export interface ReadingHistory { article: ArticleSummary; progress: ReadingProgress }
export interface Recommendation { date: string; article: ArticleSummary | null }
export const readingRecommendation = (signal?: AbortSignal) => publicReadingGet<Recommendation>('/api/v1/reading/recommendation', signal)
export const readingHistory = (query: URLSearchParams, signal?: AbortSignal) => businessGet<PageResult<ReadingHistory>>(`/api/v1/reading/history?${query}`, signal)
export const saveProgress = (version: string, input: ProgressUpdate, signal?: AbortSignal) => businessWrite<ReadingProgress>(`/api/v1/reading/progress/${encodeURIComponent(version)}`, input, 'PUT', signal)
export interface ReadingProgress { id: string; articleId: string; articleVersionId: string; sentenceKey: string; offset: number; status: 'READING' | 'COMPLETED'; lockVersion: number; updatedAt: string; completedAt: string | null }
export interface ReadingDetail { version: ArticleVersion; progress: ReadingProgress | null }
export interface StartedReading extends ReadingDetail { progress: ReadingProgress; created: boolean }
export interface ArticleSummary { id: string; versionId: string; title: string; titleZh: string; summary: string; category: Category; difficulty: string; wordCount: number; estimatedMinutes: number; author: string; sourceType: ArticleVersion['sourceType']; sourceUrl: string | null; usageNote: string; progress: ReadingProgress | null }
export const readingCategories = (signal?: AbortSignal) => publicReadingGet<Category[]>('/api/v1/reading/categories', signal)
export const readingArticles = (query: URLSearchParams, signal?: AbortSignal) => publicReadingGet<PageResult<ArticleSummary>>(`/api/v1/reading/articles?${query}`, signal)
export const startReading = (id: string, signal?: AbortSignal) => businessWrite<StartedReading>(`/api/v1/reading/articles/${encodeURIComponent(id)}/start`, undefined, 'POST', signal)
export const readingVersion = (id: string, version: string, signal?: AbortSignal) => publicReadingGet<ReadingDetail>(`/api/v1/reading/articles/${encodeURIComponent(id)}/versions/${encodeURIComponent(version)}`, signal)

export const currentArticle = (id: string, signal?: AbortSignal) => publicReadingGet<ReadingDetail>(`/api/v1/reading/articles/${encodeURIComponent(id)}`, signal)

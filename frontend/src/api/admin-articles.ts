import { businessGet, businessWrite } from './business'
export interface Category { id: string; code: string; name: string }
export interface Sentence { key: string; text: string }
export interface Paragraph { key: string; sentences: Sentence[] }
export interface ContentDocument { schemaVersion: number; paragraphs: Paragraph[] }
export interface DraftInput { categoryId: string; title: string; titleZh: string; summary: string; difficulty: 'B1' | 'B2'; body: string; author: string; sourceType: 'ORIGINAL' | 'THIRD_PARTY'; sourceUrl: string | null; usageNote: string }
export interface ArticleVersion extends Omit<DraftInput, 'categoryId' | 'body'> { id: string; articleId: string; versionNo: number; category: Category; content: ContentDocument; wordCount: number; estimatedMinutes: number }
export interface AdminArticle { id: string; status: 'DRAFT' | 'PUBLISHED' | 'UNLISTED'; draftVersionId: string; publishedVersionId: string | null; lockVersion: number; draft: ArticleVersion }
export interface PageResult<T> { items: T[]; page: number; pageSize: number; total: number }
export interface ImportValidation { valid: boolean; fieldErrors: Record<string, string>; itemCount: number }
export interface ImportResult { batchId: string; articleIds: string[] }
export const validateImport = (input: unknown, signal?: AbortSignal) => businessWrite<ImportValidation>('/api/v1/admin/articles/import/validate', input, 'POST', signal)
export const confirmImport = (id: string, input: unknown, signal?: AbortSignal) => businessWrite<ImportResult>(`/api/v1/admin/articles/import/confirm?clientImportId=${encodeURIComponent(id)}`, input, 'POST', signal)
export const categories = () => businessGet<Category[]>('/api/v1/admin/articles/categories')
export const listArticles = (query: URLSearchParams, signal?: AbortSignal) => businessGet<PageResult<AdminArticle>>(`/api/v1/admin/articles?${query}`, signal)
export const getArticle = (id: string) => businessGet<AdminArticle>(`/api/v1/admin/articles/${encodeURIComponent(id)}`)
export const createArticle = (input: DraftInput) => businessWrite<AdminArticle>('/api/v1/admin/articles', input)
export const saveDraft = (id: string, input: DraftInput, expectedLockVersion: number) => businessWrite<AdminArticle>(`/api/v1/admin/articles/${encodeURIComponent(id)}/draft`, { ...input, expectedLockVersion }, 'PUT')
export const publishArticle = (id: string, expectedLockVersion: number) => businessWrite<AdminArticle>(`/api/v1/admin/articles/${encodeURIComponent(id)}/publish`, { expectedLockVersion })
export const unpublishArticle = (id: string, expectedLockVersion: number) => businessWrite<AdminArticle>(`/api/v1/admin/articles/${encodeURIComponent(id)}/unpublish`, { expectedLockVersion })
export const previewArticle = (id: string, versionId: string) => businessGet<ArticleVersion>(`/api/v1/admin/articles/${encodeURIComponent(id)}/preview?versionId=${encodeURIComponent(versionId)}`)
export const sourceLabel = (source: ArticleVersion['sourceType']) => source === 'ORIGINAL' ? 'Lingua Loop原创练习' : '第三方授权内容'
export function bodyText(version: ArticleVersion) { return version.content.paragraphs.map(p => p.sentences.map(s => s.text).join(' ')).join('\n\n') }

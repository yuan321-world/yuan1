import { businessGet, businessWrite } from './business'
import type { ArticleSummary } from './reading'
import type { PageResult } from './admin-articles'
export const favoriteStates = (ids: string[]) => businessGet<string[]>(`/api/v1/reading/favorites/states?ids=${ids.map(encodeURIComponent).join(',')}`)
export const setArticleFavorite = (id: string, active: boolean) => businessWrite<{ articleId: string; favorite: boolean }>(`/api/v1/reading/articles/${encodeURIComponent(id)}/favorite`, undefined, active ? 'PUT' : 'DELETE')
export const favoriteArticles = (query: URLSearchParams, signal?: AbortSignal) => businessGet<PageResult<ArticleSummary>>(`/api/v1/reading/favorites?${query}`, signal)

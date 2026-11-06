import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { useReadingTools } from '../../src/reading/useReadingTools'
import { getReadingTools, getArticleWords, type ReadingTools } from '../../src/api/reading-tools'
import type { ArticleVersion } from '../../src/api/admin-articles'
import { clearAccess } from '../../src/auth/token-session'
vi.mock('../../src/api/reading-tools', () => ({ getReadingTools: vi.fn(), getArticleWords: vi.fn() }))
const version = (id: string) => ({ id, articleId: id } as ArticleVersion)
const tools = (label: string) => ({ related: [], previous: null, next: null, keywords: [], translation: { available: false, source: label, paragraphs: [] } } as ReadingTools)
function fixture() { let state!: ReturnType<typeof useReadingTools>; const wrapper = mount(defineComponent({ setup() { state = useReadingTools(); return () => null } })); return { state, wrapper } }
beforeEach(() => { vi.resetAllMocks(); clearAccess(); vi.mocked(getArticleWords).mockResolvedValue({ items: [], page: 1, pageSize: 10, total: 0 }) })
it('ignores a late tools response from the previous article', async () => {
  let first!: (value: ReadingTools) => void
  vi.mocked(getReadingTools).mockImplementationOnce(() => new Promise(resolve => { first = resolve })).mockResolvedValueOnce(tools('new'))
  const { state, wrapper } = fixture(); state.load(version('1'), false); state.load(version('2'), false); await flushPromises()
  first(tools('old')); await flushPromises(); expect(state.tools.value?.translation.source).toBe('new'); wrapper.unmount()
})
it('does not replace words with a response belonging to a previous identity', async () => {
  let finish!: (value: Awaited<ReturnType<typeof getArticleWords>>) => void
  vi.mocked(getReadingTools).mockResolvedValue(tools('source')); vi.mocked(getArticleWords).mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  const { state, wrapper } = fixture(); state.load(version('1'), true); clearAccess()
  finish({ items: [], page: 1, pageSize: 10, total: 99 }); await flushPromises(); expect(state.wordTotal.value).toBe(0); wrapper.unmount()
})
it('retains the last confirmed page and count when another page fails', async () => {
  vi.mocked(getReadingTools).mockResolvedValue(tools('source')); vi.mocked(getArticleWords).mockResolvedValueOnce({ items: [], page: 1, pageSize: 10, total: 23 }).mockRejectedValueOnce(new Error('offline'))
  const { state, wrapper } = fixture(); state.load(version('1'), true); await flushPromises(); await state.reloadWords(2)
  expect(state.wordPage.value).toBe(1); expect(state.wordTotal.value).toBe(23); expect(state.wordsError.value).not.toBe(''); wrapper.unmount()
})

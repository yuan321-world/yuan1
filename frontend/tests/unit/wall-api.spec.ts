import { expect, it, vi } from 'vitest'
import { listMessages, createMessage } from '../../src/api/wall'
import { publicReadingGet, businessWrite } from '../../src/api/business'
vi.mock('../../src/api/business', () => ({ publicReadingGet: vi.fn().mockResolvedValue({ items: [], hasMore: false, nextCursor: null }), businessWrite: vi.fn().mockResolvedValue({ id: '1' }), businessGet: vi.fn() }))
it('encodes keyword and filters without creating another query parameter', async () => {
  await listMessages({ keyword: 'x&mine=true', category: 'STUDY' })
  const path = vi.mocked(publicReadingGet).mock.calls[0]![0]
  const url = new URL(path, 'https://local.invalid')
  expect(url.searchParams.get('keyword')).toBe('x&mine=true')
  expect(url.searchParams.has('mine')).toBe(false)
})
it('keeps the request UUID and delegates identity and CSRF to the existing transport', async () => {
  const input = { requestId: crypto.randomUUID(), kind: 'MESSAGE' as const, category: 'STUDY', color: 'yellow', text: 'Hello' }
  await createMessage(input)
  expect(businessWrite).toHaveBeenCalledWith('/api/v1/wall/messages', input)
})

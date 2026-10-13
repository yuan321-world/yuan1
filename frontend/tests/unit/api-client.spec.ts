import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, requestJson } from '../../src/api/client'

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response
}

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('requestJson', () => {
  it('parses JSON from an allowed same-origin API path and forces same-origin credentials', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ready: true }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(requestJson<{ ready: boolean }>('/api/v1/system/status', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })).resolves.toEqual({ ready: true })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    })
  })

  it('throws an ApiError with the HTTP status for a non-success response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({}, 503)))

    await expect(requestJson('/api/v1/system/status')).rejects.toMatchObject({
      name: 'ApiError',
      status: 503,
    } satisfies Partial<ApiError>)
  })

  it('rejects external and out-of-prefix paths without calling fetch', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(requestJson('https://example.com/api/v1/system/status')).rejects.toThrow()
    await expect(requestJson('/api/v1/../admin')).rejects.toThrow()
    await expect(requestJson('//example.com/api/v1/system/status')).rejects.toThrow()

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rejects a successful response whose body is not valid JSON', async () => {
    const badJson = new SyntaxError('Unexpected token')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockRejectedValue(badJson),
    }))

    await expect(requestJson('/api/v1/system/status')).rejects.toBe(badJson)
  })

  it('aborts the fetch after the default five-second timeout', async () => {
    vi.useFakeTimers()
    let requestSignal: AbortSignal | undefined
    vi.stubGlobal('fetch', vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
      requestSignal = init?.signal ?? undefined
      return new Promise<Response>(() => {})
    }))

    const request = requestJson('/api/v1/system/status')
    const rejected = expect(request).rejects.toThrow(/timed out/i)
    await vi.advanceTimersByTimeAsync(4_999)
    expect(requestSignal?.aborted).toBe(false)
    await vi.advanceTimersByTimeAsync(1)
    await rejected

    expect(requestSignal?.aborted).toBe(true)
  })

  it('keeps the timeout active while the response body is being parsed', async () => {
    vi.useFakeTimers()
    let requestSignal: AbortSignal | undefined
    vi.stubGlobal('fetch', vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
      requestSignal = init?.signal ?? undefined
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => new Promise<unknown>(() => {}),
      } as Response)
    }))

    const request = requestJson('/api/v1/system/status')
    const rejected = expect(request).rejects.toThrow(/timed out/i)
    await vi.runAllTicks()
    await vi.advanceTimersByTimeAsync(5_000)
    await rejected

    expect(requestSignal?.aborted).toBe(true)
  })

  it('forwards caller cancellation to fetch and rejects with AbortError', async () => {
    let requestSignal: AbortSignal | undefined
    vi.stubGlobal('fetch', vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
      requestSignal = init?.signal ?? undefined
      return new Promise<Response>(() => {})
    }))
    const caller = new AbortController()

    const request = requestJson('/api/v1/system/status', { signal: caller.signal })
    const rejected = expect(request).rejects.toMatchObject({ name: 'AbortError' })
    caller.abort()
    await rejected

    expect(requestSignal?.aborted).toBe(true)
  })

  it('rejects a pre-aborted signal without fetching or emitting an unhandled rejection', async () => {
    const fetchMock = vi.fn()
    const unhandledRejections: unknown[] = []
    const onUnhandledRejection = (reason: unknown) => unhandledRejections.push(reason)
    const caller = new AbortController()
    caller.abort()
    vi.stubGlobal('fetch', fetchMock)
    process.on('unhandledRejection', onUnhandledRejection)

    try {
      await expect(requestJson('/api/v1/system/status', { signal: caller.signal }))
        .rejects.toMatchObject({ name: 'AbortError' })
      await new Promise((resolve) => setTimeout(resolve, 0))

      expect(fetchMock).not.toHaveBeenCalled()
      expect(unhandledRejections).toEqual([])
    } finally {
      process.off('unhandledRejection', onUnhandledRejection)
    }
  })
})

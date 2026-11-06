const REQUEST_TIMEOUT_MS = 5_000
const API_PREFIX = '/api/v1/'

export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly publicMessage?: string
  readonly fieldErrors?: Record<string, string>
  readonly retryAfterSeconds?: number

  constructor(status: number, payload?: { code?: string; message?: string; fieldErrors?: Record<string, string> }, retryAfterSeconds?: number) {
    super(`Request failed with HTTP status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = payload?.code
    this.publicMessage = payload?.message
    this.fieldErrors = payload?.fieldErrors
    this.retryAfterSeconds = retryAfterSeconds
  }
}

function validateApiPath(path: string): string {
  if (
    !path.startsWith(API_PREFIX)
    || path.startsWith('//')
    || path.includes('\\')
    || /[\u0000-\u001f\u007f]/.test(path)
  ) {
    throw new TypeError('API paths must be safe same-origin paths under /api/v1/')
  }

  const url = new URL(path, window.location.origin)
  const pathPart = path.split(/[?#]/, 1)[0] ?? ''
  const hasUnsafeSegment = pathPart.split('/').some((segment) => {
    try {
      const decoded = decodeURIComponent(segment)
      return decoded === '.' || decoded === '..' || decoded.includes('/') || decoded.includes('\\')
    } catch {
      return true
    }
  })

  if (
    url.origin !== window.location.origin
    || !url.pathname.startsWith(API_PREFIX)
    || Boolean(url.hash)
    || hasUnsafeSegment
  ) {
    throw new TypeError('API paths must be safe same-origin paths under /api/v1/')
  }

  return `${url.pathname}${url.search}`
}

function abortError(): DOMException {
  return new DOMException('The operation was aborted', 'AbortError')
}

export async function requestJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const requestPath = validateApiPath(path)
  const callerSignal = options.signal
  if (callerSignal?.aborted) throw callerSignal.reason ?? abortError()

  const controller = new AbortController()
  let timeoutError: Error | undefined
  let removeAbortListener = () => {}

  const forwardCallerAbort = () => {
    controller.abort(callerSignal?.reason ?? abortError())
  }

  callerSignal?.addEventListener('abort', forwardCallerAbort, { once: true })

  const rejectOnAbort = new Promise<never>((_resolve, reject) => {
    const onAbort = () => reject(controller.signal.reason ?? abortError())
    controller.signal.addEventListener('abort', onAbort, { once: true })
    removeAbortListener = () => controller.signal.removeEventListener('abort', onAbort)
    if (controller.signal.aborted) onAbort()
  })

  const timeoutId = setTimeout(() => {
    timeoutError = new Error(`Request timed out after ${REQUEST_TIMEOUT_MS}ms`)
    timeoutError.name = 'TimeoutError'
    controller.abort(timeoutError)
  }, REQUEST_TIMEOUT_MS)

  const { signal: _callerSignal, ...requestOptions } = options

  try {
    const response = await Promise.race([
      fetch(requestPath, {
        ...requestOptions,
        credentials: 'same-origin',
        signal: controller.signal,
      }),
      rejectOnAbort,
    ])

    if (!response.ok) {
      let payload: { code?: string; message?: string; fieldErrors?: Record<string, string> } | undefined
      try {
        const data: unknown = await Promise.race([response.json(), rejectOnAbort])
        if (data && typeof data === 'object') {
          const p = data as Record<string, unknown>
          payload = { code: typeof p.code === 'string' ? p.code : undefined, message: typeof p.message === 'string' ? p.message : undefined }
          if (p.fieldErrors && typeof p.fieldErrors === 'object' && !Array.isArray(p.fieldErrors)) {
            payload.fieldErrors = Object.fromEntries(Object.entries(p.fieldErrors).filter((entry): entry is [string, string] => typeof entry[1] === 'string'))
          }
        }
      } catch { if (controller.signal.aborted) throw controller.signal.reason }
      const retryAfter = response.headers?.get('Retry-After')
      const retryAfterSeconds = parseRetryAfter(retryAfter)
      throw new ApiError(response.status, payload, retryAfterSeconds)
    }
    if (response.status === 204) return undefined as T

    const result = await Promise.race([
      Promise.resolve().then(() => response.json()),
      rejectOnAbort,
    ])

    if (controller.signal.aborted) throw timeoutError ?? controller.signal.reason ?? abortError()
    return result as T
  } finally {
    clearTimeout(timeoutId)
    callerSignal?.removeEventListener('abort', forwardCallerAbort)
    removeAbortListener()
  }
}

function parseRetryAfter(value: string | null): number | undefined {
  if (!value) return undefined
  if (/^\d+$/.test(value)) return Number(value)
  const time = Date.parse(value)
  return Number.isNaN(time) ? undefined : Math.max(0, Math.ceil((time - Date.now()) / 1000))
}

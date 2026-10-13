import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import * as systemApi from '../../src/api/system'
// The node-side TypeScript project does not include Vite's Vue SFC declarations.
// @ts-expect-error Vitest's Vue plugin resolves this component at test runtime.
import SystemStatusBadge from '../../src/components/SystemStatusBadge.vue'

const up = { status: 'UP', service: 'english-hero', database: 'UP' } as const

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('getSystemStatus', () => {
  it('returns a valid system status response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(up)))

    await expect(systemApi.getSystemStatus()).resolves.toEqual(up)
  })

  it('rejects unexpected fields, values, service names, and inconsistent status', async () => {
    for (const body of [
      { ...up, extra: true },
      { ...up, service: 'another-service' },
      { ...up, status: 'DOWN' },
      { ...up, database: 'DOWN' },
      { ...up, status: 'READY' },
    ]) {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(body)))
      await expect(systemApi.getSystemStatus()).rejects.toThrow()
    }
  })
})

describe('SystemStatusBadge', () => {
  it('shows loading and then the ready label', async () => {
    const getStatus = vi.spyOn(systemApi, 'getSystemStatus').mockResolvedValue(up)

    const wrapper = mount(SystemStatusBadge)
    expect(wrapper.text()).toMatch(/检查|连接/)
    await flushPromises()

    expect(wrapper.text()).toContain('连接正常')
    expect(getStatus).toHaveBeenCalledTimes(1)
    expect(getStatus.mock.calls[0]?.[0]).toBeInstanceOf(AbortSignal)
    expect(wrapper.attributes('aria-live')).toBe('polite')
  })

  it('shows unavailable when a valid HTTP 200 response reports the service is down', async () => {
    vi.spyOn(systemApi, 'getSystemStatus').mockResolvedValue({
      status: 'DOWN',
      service: 'english-hero',
      database: 'DOWN',
    })

    const wrapper = mount(SystemStatusBadge)
    await flushPromises()

    expect(wrapper.text()).toContain('服务暂不可用，点击重试')
    expect(wrapper.text()).not.toContain('连接正常')
  })

  it('shows an accessible retry action after failure and recovers when retried', async () => {
    vi.spyOn(systemApi, 'getSystemStatus')
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(up)

    const wrapper = mount(SystemStatusBadge)
    await flushPromises()
    expect(wrapper.text()).toContain('服务暂不可用，点击重试')
    expect(wrapper.get('button').text()).toContain('重试')

    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('连接正常')
  })

  it('cancels an in-flight request when the component unmounts', async () => {
    let requestSignal: AbortSignal | undefined
    vi.spyOn(systemApi, 'getSystemStatus').mockImplementation((signal) => {
      requestSignal = signal
      return new Promise(() => {})
    })

    const wrapper = mount(SystemStatusBadge)
    await flushPromises()
    wrapper.unmount()

    expect(requestSignal?.aborted).toBe(true)
  })

  it('does not let an older result overwrite a newer manual check', async () => {
    const older = deferred<systemApi.SystemStatusResponse>()
    const newer = deferred<systemApi.SystemStatusResponse>()
    vi.spyOn(systemApi, 'getSystemStatus')
      .mockReturnValueOnce(older.promise)
      .mockReturnValueOnce(newer.promise)

    const wrapper = mount(SystemStatusBadge)
    await flushPromises()
    await wrapper.get('button').trigger('click')
    newer.resolve(up)
    await flushPromises()
    expect(wrapper.text()).toContain('连接正常')

    older.resolve({ status: 'DOWN', service: 'english-hero', database: 'DOWN' })
    await flushPromises()
    expect(wrapper.text()).toContain('连接正常')
  })
})

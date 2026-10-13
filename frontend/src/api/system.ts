import { requestJson } from './client'

export type SystemStatusValue = 'UP' | 'DOWN'

export interface SystemStatusResponse {
  status: SystemStatusValue
  service: 'english-hero'
  database: SystemStatusValue
}

function isSystemStatusResponse(value: unknown): value is SystemStatusResponse {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false

  const fields = value as Record<string, unknown>
  const keys = Object.keys(fields).sort()

  return keys.length === 3
    && keys[0] === 'database'
    && keys[1] === 'service'
    && keys[2] === 'status'
    && fields.service === 'english-hero'
    && (fields.status === 'UP' || fields.status === 'DOWN')
    && (fields.database === 'UP' || fields.database === 'DOWN')
    && (fields.status === 'UP') === (fields.database === 'UP')
}

export async function getSystemStatus(signal?: AbortSignal): Promise<SystemStatusResponse> {
  const response = await requestJson<unknown>('/api/v1/system/status', { signal })
  if (!isSystemStatusResponse(response)) {
    throw new TypeError('Invalid system status response')
  }
  return response
}

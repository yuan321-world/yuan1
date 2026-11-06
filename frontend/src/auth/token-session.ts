// Access credentials deliberately live only in this module's memory.
let accessToken: string | null = null
let generation = 0
const listeners = new Set<() => void>()
export function getAccessToken() { return accessToken }
export function getGeneration() { return generation }
export function acceptAccess(token: string, expiresIn: number, expectedGeneration: number) {
  if (generation !== expectedGeneration) return false
  if (!token || !Number.isInteger(expiresIn) || expiresIn <= 0 || expiresIn > 600) throw new TypeError('登录凭据格式不正确')
  accessToken = token
  return true
}
export function advanceGeneration() { generation++; return generation }
export function clearAccess() { generation++; accessToken = null }
export function onRemoteIdentityChange(callback: () => void) { listeners.add(callback); return () => { listeners.delete(callback) } }
const channel = typeof window !== 'undefined' && typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('lingua-loop-identity-v1') : null
if (channel) channel.onmessage = event => {
  if (event.data === 'logout' || event.data === 'account-change') {
    clearAccess()
    listeners.forEach(listener => listener())
  }
}
export function broadcastIdentityChange(kind: 'logout' | 'account-change') { channel?.postMessage(kind) }

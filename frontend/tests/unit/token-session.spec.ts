import { expect, it } from 'vitest'
import { acceptAccess, clearAccess, getAccessToken, getGeneration } from '../../src/auth/token-session'
it('keeps access only in memory and rejects late generations', () => {
  clearAccess(); const generation = getGeneration()
  expect(acceptAccess('mock-access', 600, generation)).toBe(true)
  clearAccess()
  expect(acceptAccess('old-response', 600, generation)).toBe(false)
  expect(getAccessToken()).toBeNull()
  expect(localStorage.length).toBe(0)
  expect(sessionStorage.length).toBe(0)
})

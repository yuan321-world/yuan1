import { beforeEach, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthDialogStore } from '../../src/stores/auth-dialog'
beforeEach(() => { setActivePinia(createPinia()); document.body.innerHTML = '' })
it('retains mode on cancel, blocks closing while busy, and drops pending navigation', () => {
  const store = useAuthDialogStore()
  store.request('/notebook'); store.switchMode('register')
  store.busy = true; store.close(); expect(store.open).toBe(true)
  store.busy = false; store.close(); expect(store.open).toBe(false)
  expect(store.target).toBe(''); expect(store.mode).toBe('register')
  store.request(); expect(store.target).toBe('')
})
it('successful authentication invalidates all cached form instances after close', () => {
  const store = useAuthDialogStore(); store.request('/reading/12'); store.switchMode('register')
  store.complete(); expect(store.target).toBe(''); expect(store.open).toBe(false)
  store.closed(); expect(store.session).toBe(1); expect(store.mode).toBe('login')
})
it('returns focus to the opener after a canceled modal', () => {
  const button = document.createElement('button'); document.body.append(button); button.focus()
  const store = useAuthDialogStore(); store.request(); button.blur(); store.close(); store.closed()
  expect(document.activeElement).toBe(button)
})
it('clears cached credentials immediately when identity changes while dialog is already closed', () => {
  const store = useAuthDialogStore()
  store.complete(); const cleared = store.session
  store.request(); store.switchMode('register'); store.close(); store.closed()
  expect(store.session).toBe(cleared)
  expect(store.mode).toBe('register')
})

import { defineStore } from 'pinia'
import { ref } from 'vue'

export type AuthMode = 'login' | 'register' | 'reset'
/** UI only. Credentials and identity continue to belong to forms and the auth store. */
export const useAuthDialogStore = defineStore('auth-dialog', () => {
  const open = ref(false), busy = ref(false), mode = ref<AuthMode>('login')
  const target = ref(''), message = ref(''), session = ref(0)
  let discard = false
  let trigger: HTMLElement | null = null
  const articleIntent = ref<{ articleId: string; route: string } | null>(null)
  function request(destination = '', intent: { articleId: string; route: string } | null = null) {
    if (busy.value) return
    trigger = document.activeElement instanceof HTMLElement && document.activeElement !== document.body ? document.activeElement : null
    target.value = destination
    articleIntent.value = intent
    open.value = true
  }
  function switchMode(value: AuthMode, notice = '') { mode.value = value; message.value = notice }
  function close() { if (busy.value) return; open.value = false; target.value = ''; articleIntent.value = null }
  function clearSession() { session.value++; mode.value = 'login'; message.value = ''; discard = false }
  function complete() {
    discard = open.value
    busy.value = false; open.value = false; target.value = ''
    articleIntent.value = null
    if (!discard) clearSession()
  }
  function closed() {
    if (discard) clearSession()
    const element = trigger?.isConnected && !trigger.closest('[inert]') ? trigger : document.querySelector<HTMLElement>('.user-toggle, .nav-login, .brand')
    element?.focus({ preventScroll: true })
    trigger = null
  }
  return { open, busy, mode, target, message, session, articleIntent, request, switchMode, close, complete, closed }
})

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as api from '../api/identity'
import { ApiError } from '../api/client'
import { clearAccess, onRemoteIdentityChange } from '../auth/token-session'
export const useAuthStore = defineStore('auth', () => {
  const user = ref<api.UserDto | null>(null)
  const ready = ref(false)
  const connectionError = ref(false)
  let generation = 0
  let restoring: Promise<void> | null = null
  onRemoteIdentityChange(() => {
    generation++; user.value = null; ready.value = false; connectionError.value = false
  })
  const isAdmin = computed(() => user.value?.roles.includes('ADMIN') ?? false)
  async function restore(force = false) {
    if (restoring) return restoring
    if (ready.value && !force) return
    const current = generation
    restoring = (async () => {
      try {
        const value = await api.me()
        if (current === generation) { user.value = value; ready.value = true; connectionError.value = false }
      } catch (error) {
        if (current !== generation) return
        if (error instanceof ApiError && error.status === 401) { user.value = null; ready.value = true; connectionError.value = false }
        else { connectionError.value = true; throw error }
      } finally { restoring = null }
    })()
    return restoring
  }
  async function signIn(username: string, password: string, remember: boolean) {
    const value = await api.login(username, password, remember)
    generation++; user.value = value; ready.value = true; connectionError.value = false
  }
  function clear() { generation++; clearAccess(); user.value = null; ready.value = true; connectionError.value = false }
  async function signOut() { generation++; await api.logout(); clear() }
  async function saveProfile(displayName: string) {
    const current = generation
    const value = await api.profile(displayName)
    if (current !== generation) return false
    user.value = value; return true
  }
  async function bindEmail(email: string, currentPassword: string, challengeId: string, code: string) {
    const current = generation
    const value = await api.bindEmail(email, currentPassword, challengeId, code)
    if (current !== generation) return false
    user.value = value; return true
  }
  async function changePassword(currentPassword: string, newPassword: string) {
    const current = generation
    await api.password(currentPassword, newPassword)
    if (current !== generation) return false
    clear(); return true
  }
  return { user, ready, isAdmin, connectionError, restore, signIn, signOut, saveProfile, bindEmail, changePassword, clear }
})

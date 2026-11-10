<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { learnerNavigation } from '@/router/navigation'
import { useAuthStore } from '@/stores/auth'
import { useAuthDialogStore } from '@/stores/auth-dialog'
import { errorMessage } from '@/api/identity'
const auth = useAuthStore(), router = useRouter(), route = useRoute()
const authDialog = useAuthDialogStore()
const logoutError = ref(''), signingOut = ref(false)
const navigation = computed(() => auth.isAdmin ? [{ to: '/admin', label: '数据管理' }]
  : learnerNavigation.filter(i => i.to !== '/login' && i.to !== '/admin'))
const initial = computed(() => Array.from(auth.user?.displayName || auth.user?.username || 'L')[0]!.toLocaleUpperCase())
const open = ref(false), userOpen = ref(false)
const toggle = ref<HTMLButtonElement>(), userToggle = ref<HTMLButtonElement>(), userControls = ref<HTMLElement>()
async function signOut() {
  if (signingOut.value) return
  signingOut.value = true; logoutError.value = ''
  try { await auth.signOut(); await router.replace('/') }
  catch (error) {
    logoutError.value = errorMessage(error)
    try { await auth.restore(true); if (!auth.user) await router.replace('/') } catch { /* Retain identity until server state is known. */ }
  } finally { signingOut.value = false }
}
watch(() => route.fullPath, () => { open.value = false; userOpen.value = false })
function toggleNavigation() { open.value = !open.value; userOpen.value = false }
function toggleUser() { userOpen.value = !userOpen.value; open.value = false }
async function openUserWithKeyboard() {
  userOpen.value = true; open.value = false
  await nextTick()
  userControls.value?.querySelector<HTMLAnchorElement>('.user-menu a')?.focus()
}
function leaveUser(event: FocusEvent) {
  if (event.relatedTarget instanceof Node && !userControls.value?.contains(event.relatedTarget)) userOpen.value = false
}
function outsideUser(event: PointerEvent) {
  if (event.target instanceof Node && !userControls.value?.contains(event.target)) userOpen.value = false
}
function handleEscape(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (userOpen.value) { userOpen.value = false; userToggle.value?.focus() }
  else if (open.value) { open.value = false; toggle.value?.focus() }
}
onMounted(() => { document.addEventListener('keydown', handleEscape); document.addEventListener('pointerdown', outsideUser) })
onUnmounted(() => { document.removeEventListener('keydown', handleEscape); document.removeEventListener('pointerdown', outsideUser) })
</script>
<template>
  <header class="topbar" :class="{ 'topbar-in-flow': route.path === '/vocabulary' }">
    <div class="nav-shell">
      <RouterLink class="brand" :to="auth.isAdmin ? '/admin' : '/'" :aria-label="auth.isAdmin ? 'Lingua Loop 管理端' : 'Lingua Loop 首页'"><span class="brand-mark" aria-hidden="true">L</span>Lingua Loop</RouterLink>
      <button ref="toggle" class="menu-toggle" type="button" :aria-expanded="open" aria-controls="learner-navigation" :aria-label="open ? '关闭导航' : '打开导航'" @click="toggleNavigation">{{ open ? '关闭' : '菜单' }}</button>
      <nav id="learner-navigation" class="learner-nav" :class="{ 'is-open': open }" aria-label="学习导航">
        <RouterLink v-for="item in navigation" :key="item.to" :to="item.to" class="nav-link" @click="open = false">{{ item.label }}</RouterLink>
      </nav>
      <button v-if="!auth.user" type="button" class="button-primary nav-login" @click="authDialog.request()">登录</button>
      <div v-if="auth.user" ref="userControls" class="user-controls" @focusout="leaveUser">
        <button ref="userToggle" class="user-toggle" type="button" aria-label="用户菜单" :aria-expanded="userOpen" aria-controls="user-menu" @click="toggleUser" @keydown.down.prevent="openUserWithKeyboard">
          <span class="user-initial" aria-hidden="true">{{ initial }}</span>
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true"><path d="m3 4.5 3 3 3-3"/></svg>
        </button>
        <div v-if="userOpen" id="user-menu" class="user-menu" role="region" aria-label="账户菜单">
          <p class="user-menu-name">{{ auth.user.displayName || auth.user.username }}<span>@{{ auth.user.username }}</span></p>
          <RouterLink :to="auth.isAdmin ? '/admin/account' : '/account'">账户设置</RouterLink>
          <button type="button" :disabled="signingOut" @click="signOut">{{ signingOut ? '正在退出…' : '退出登录' }}</button>
          <p v-if="logoutError" role="alert" class="user-menu-error">{{ logoutError }}</p>
        </div>
      </div>
    </div>
  </header>
</template>

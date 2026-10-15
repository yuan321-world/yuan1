<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { learnerNavigation } from '@/router/navigation'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/api/identity'
const auth = useAuthStore(), router = useRouter(), logoutError = ref(''), signingOut = ref(false)
const navigation = computed(() => auth.isAdmin ? [{ to: '/admin', label: '数据管理' }, { to: '/admin/account', label: '账户设置' }]
  : auth.user ? [...learnerNavigation.filter(i => i.to !== '/login' && i.to !== '/admin'), { to: '/account', label: '账户' }]
    : [{ to: '/login', label: '登录' }, { to: '/register', label: '注册' }])
async function signOut() {
  signingOut.value = true; logoutError.value = ''
  try { await auth.signOut(); await router.replace('/login') }
  catch (error) { logoutError.value = errorMessage(error); try { await auth.restore(true); if (!auth.user) await router.replace('/login') } catch { /* Retain real identity until server state is known. */ } }
  finally { signingOut.value = false }
}

const open = ref(false)
const toggle = ref<HTMLButtonElement>()
const route = useRoute()
watch(() => route.fullPath, () => { open.value = false })
function handleEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && open.value) {
    open.value = false
    toggle.value?.focus()
  }
}
onMounted(() => document.addEventListener('keydown', handleEscape))
onUnmounted(() => document.removeEventListener('keydown', handleEscape))
</script>

<template>
  <header class="topbar">
    <div class="nav-shell">
      <RouterLink class="brand" :to="auth.isAdmin ? '/admin' : '/'" :aria-label="auth.isAdmin ? 'Lingua Loop 管理端' : 'Lingua Loop 首页'"><span class="brand-mark" aria-hidden="true">L</span>Lingua Loop</RouterLink>
      <button ref="toggle" class="menu-toggle" type="button" :aria-expanded="open" aria-controls="learner-navigation" :aria-label="open ? '关闭导航' : '打开导航'" @click="open = !open">{{ open ? '关闭' : '菜单' }}</button>
      <nav id="learner-navigation" class="learner-nav" :class="{ 'is-open': open }" aria-label="学习导航">
        <RouterLink v-for="item in navigation" :key="item.to" :to="item.to" class="nav-link" :class="{ 'admin-link': item.to === '/admin' }" @click="open = false">{{ item.label }}</RouterLink>
        <button v-if="auth.user" class="nav-link text-button" :disabled="signingOut" @click="signOut">退出</button>
      </nav>
      <p v-if="logoutError" role="alert" class="form-error">{{ logoutError }}</p>
    </div>
  </header>
</template>

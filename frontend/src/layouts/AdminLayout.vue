<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/api/identity'
const auth = useAuthStore(), router = useRouter()
const busy = ref(false), error = ref('')
async function logout() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await auth.signOut(); await router.replace('/login') }
  catch (e) {
    error.value = errorMessage(e)
    try { await auth.restore(true); if (!auth.user) await router.replace('/login') } catch { /* Keep the error available for retry. */ }
  } finally { busy.value = false }
}
</script>
<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <RouterLink class="brand" to="/admin" aria-label="Lingua Loop 管理端"><span class="brand-mark" aria-hidden="true">L</span>Lingua Loop</RouterLink>
      <p class="admin-caption">数据管理后台</p>
      <nav class="admin-menu" aria-label="管理导航">
        <RouterLink class="admin-menu-link" to="/admin">数据管理</RouterLink>
        <RouterLink class="admin-menu-link" to="/admin/account">账户设置</RouterLink>
        <button class="admin-menu-link text-button" :disabled="busy" @click="logout">{{ busy ? '正在退出…' : '退出登录' }}</button>
      </nav>
      <p v-if="error" class="form-message form-error" role="alert">{{ error }}</p>
    </aside>
    <main id="main-content" class="admin-content" tabindex="-1">
      <div class="admin-topline">Lingua Loop / 管理端</div>
      <RouterView />
    </main>
  </div>
</template>

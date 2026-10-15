<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import * as api from '@/api/identity'
const auth = useAuthStore(), router = useRouter()
const displayName = ref(auth.user?.displayName ?? '')
const currentPassword = ref(''), newPassword = ref(''), confirmation = ref('')
const busy = ref(false), message = ref(''), failed = ref(false)
async function submit(kind: 'profile' | 'password') {
  if (busy.value) return
  message.value = ''; failed.value = false
  if (kind === 'password' && newPassword.value !== confirmation.value) { message.value = '两次新密码不一致'; failed.value = true; return }
  busy.value = true
  try {
    if (kind === 'profile') { if (await auth.saveProfile(displayName.value)) message.value = '昵称已保存' }
    else { await api.password(currentPassword.value, newPassword.value); auth.clear(); await router.replace({ path: '/login', query: { changed: '1' } }) }
  } catch (error) {
    message.value = api.errorMessage(error); failed.value = true
    try { await auth.restore(true); if (!auth.user) await router.replace('/login') } catch { /* Keep the form and retry message on network failure. */ }
  } finally { busy.value = false; currentPassword.value = ''; newPassword.value = ''; confirmation.value = '' }
}
</script>
<template>
  <section class="account-page" aria-labelledby="account-title">
    <p class="eyebrow">YOUR ACCOUNT</p><h1 id="account-title">账户设置</h1>
    <p class="account-caption">{{ auth.user?.username }} · {{ auth.isAdmin ? '管理员' : '学习者' }}</p>
    <p v-if="message" :role="failed ? 'alert' : 'status'" class="form-message" :class="{ 'form-error': failed }">{{ message }}</p>
    <div class="account-grid">
      <form class="account-card" @submit.prevent="submit('profile')"><h2>个人资料</h2>
        <label for="display-name">昵称</label><input id="display-name" v-model="displayName" autocomplete="nickname" maxlength="64" required>
        <p class="field-hint">1—32个字符，用户名不可修改。</p><button class="button-primary" :disabled="busy">保存昵称</button>
      </form>
      <form class="account-card" @submit.prevent="submit('password')"><h2>修改密码</h2>
        <label for="current-password">当前密码</label><input id="current-password" v-model="currentPassword" type="password" autocomplete="current-password" required>
        <label for="new-password">新密码</label><input id="new-password" v-model="newPassword" type="password" autocomplete="new-password" minlength="8" maxlength="128" required>
        <label for="confirm-password">确认新密码</label><input id="confirm-password" v-model="confirmation" type="password" autocomplete="new-password" required>
        <p class="field-hint">8—64个字符，UTF-8最多72字节。修改后所有设备需重新登录。</p><button class="button-primary" :disabled="busy">更新密码</button>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { errorMessage, register } from '@/api/identity'
import { ApiError } from '@/api/client'
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const registering = computed(() => route.path === '/register')
const username = ref(''), password = ref(''), confirmation = ref(''), displayName = ref(''), remember = ref(false)
const busy = ref(false), error = ref(''), notice = ref(route.query.changed ? '密码已更新，请重新登录' : '')
const retrying = ref(false)
const fieldErrors = ref<Record<string, string>>({})
const form = ref<HTMLFormElement>(), errorPanel = ref<HTMLParagraphElement>()
watch(registering, () => { error.value = ''; fieldErrors.value = {}; password.value = ''; confirmation.value = '' })
function describedBy(field: string, hint?: string) {
  return [hint, fieldErrors.value[field] ? `${field}-error` : undefined].filter(Boolean).join(' ') || undefined
}
function clearField(field: string) {
  delete fieldErrors.value[field]
  if (field === 'password') delete fieldErrors.value.confirmation
  error.value = ''
}
async function focusError() {
  await nextTick()
  const invalid = form.value?.querySelector<HTMLInputElement>('input[aria-invalid="true"]')
  if (invalid) invalid.focus()
  else errorPanel.value?.focus()
}
async function submit() {
  if (busy.value) return
  error.value = ''; notice.value = ''; fieldErrors.value = {}
  if (registering.value && password.value !== confirmation.value) {
    fieldErrors.value.confirmation = '两次密码不一致'
    await focusError(); return
  }
  busy.value = true
  try {
    if (registering.value) { await register(username.value, password.value, displayName.value); await router.replace('/login'); notice.value = '注册成功，请登录' }
    else {
      await auth.signIn(username.value, password.value, remember.value)
      const destination = auth.isAdmin ? (route.query.redirect === '/admin/account' ? '/admin/account' : '/admin')
        : typeof route.query.redirect === 'string' && ['/', '/account', '/reading', '/vocabulary', '/practice', '/stats', '/admin'].includes(route.query.redirect) ? route.query.redirect : '/'
      await router.replace(destination)
    }
  } catch (e) {
    if (e instanceof ApiError && e.fieldErrors) {
      fieldErrors.value = Object.fromEntries(Object.entries(e.fieldErrors).filter(([key]) => ['username', 'password', 'displayName'].includes(key)))
    }
    error.value = Object.keys(fieldErrors.value).length ? '' : errorMessage(e)
  } finally {
    busy.value = false; password.value = ''; confirmation.value = ''
    if (error.value || Object.keys(fieldErrors.value).length) await focusError()
  }
}
async function retry() {
  if (retrying.value) return
  retrying.value = true; error.value = ''
  try { await auth.restore(true); if (auth.user) await router.replace('/') }
  catch { error.value = '仍无法连接服务，请稍后重试'; await focusError() }
  finally { retrying.value = false }
}
</script>
<template>
  <section class="login-page" aria-labelledby="login-title">
    <div class="login-card" :class="{ 'is-register': registering }">
      <div class="auth-heading">
        <h1 id="login-title">{{ registering ? '创建账号' : '欢迎回来' }}</h1>
        <p class="login-subtitle">{{ registering ? '从今天开始，让每一次积累都有迹可循。' : '让每一次积累，都成为下一次进步的起点。' }}</p>
      </div>
      <div v-if="auth.connectionError" class="auth-message" role="alert">
        暂时无法恢复登录，请检查服务连接。
        <button type="button" class="text-button auth-retry" :disabled="retrying" @click="retry">{{ retrying ? '连接中…' : '重新连接' }}</button>
      </div>
      <p v-if="error" ref="errorPanel" class="auth-message auth-error" role="alert" tabindex="-1">{{ error }}</p>
      <p v-if="notice" class="auth-message" role="status">{{ notice }}</p>
      <form ref="form" class="auth-form" :aria-busy="busy" @submit.prevent="submit">
        <div class="auth-fields">
          <div class="auth-field">
            <label for="login-username">用户名</label>
            <input id="login-username" v-model="username" autocomplete="username" placeholder="你的用户名" minlength="3" maxlength="32" required :disabled="busy"
              :aria-invalid="fieldErrors.username ? 'true' : undefined" :aria-describedby="describedBy('username', registering ? 'username-hint' : undefined)" @input="clearField('username')">
            <p v-if="registering" id="username-hint" class="auth-hint">3—32位字母、数字或下划线；admin 开头不可注册（忽略大小写）。</p>
            <p v-if="fieldErrors.username" id="username-error" class="auth-field-error" role="alert">{{ fieldErrors.username }}</p>
          </div>
          <div v-if="registering" class="auth-field">
            <label for="register-name">昵称<span class="auth-optional">（选填）</span></label>
            <input id="register-name" v-model="displayName" autocomplete="nickname" placeholder="你希望被怎样称呼" maxlength="64" :disabled="busy"
              :aria-invalid="fieldErrors.displayName ? 'true' : undefined" :aria-describedby="describedBy('displayName')" @input="clearField('displayName')">
            <p v-if="fieldErrors.displayName" id="displayName-error" class="auth-field-error" role="alert">{{ fieldErrors.displayName }}</p>
          </div>
          <div class="auth-field">
            <label for="login-password">密码</label>
            <input id="login-password" v-model="password" type="password" :autocomplete="registering ? 'new-password' : 'current-password'" :placeholder="registering ? '设置你的密码' : '输入你的密码'" minlength="8" maxlength="128" required :disabled="busy"
              :aria-invalid="fieldErrors.password ? 'true' : undefined" :aria-describedby="describedBy('password', registering ? 'password-hint' : undefined)" @input="clearField('password')">
            <p v-if="registering" id="password-hint" class="auth-hint">8—64个字符，UTF-8最多72字节。</p>
            <p v-if="fieldErrors.password" id="password-error" class="auth-field-error" role="alert">{{ fieldErrors.password }}</p>
          </div>
          <div v-if="registering" class="auth-field">
            <label for="register-confirm">确认密码</label>
            <input id="register-confirm" v-model="confirmation" type="password" autocomplete="new-password" placeholder="再次输入密码" required :disabled="busy"
              :aria-invalid="fieldErrors.confirmation ? 'true' : undefined" :aria-describedby="describedBy('confirmation')" @input="clearField('confirmation')">
            <p v-if="fieldErrors.confirmation" id="confirmation-error" class="auth-field-error" role="alert">{{ fieldErrors.confirmation }}</p>
          </div>
        </div>
        <label v-if="!registering" class="remember-label"><input v-model="remember" type="checkbox" :disabled="busy">保持登录7天</label>
        <button class="button-primary auth-submit" type="submit" :disabled="busy || auth.connectionError">
          <span v-if="busy" class="auth-spinner" aria-hidden="true"></span>
          {{ busy ? (registering ? '正在创建账号…' : '正在登录…') : registering ? '注册' : '登录' }}
        </button>
      </form>
      <p class="login-notice">
        <span v-if="busy" class="auth-switch-disabled" aria-disabled="true">{{ registering ? '已有账号？去登录' : '还没有账号？创建账号' }}</span>
        <RouterLink v-else :to="registering ? '/login' : '/register'">{{ registering ? '已有账号？去登录' : '还没有账号？创建账号' }}</RouterLink>
      </p>
    </div>
  </section>
</template>

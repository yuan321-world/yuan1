<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { errorMessage, register } from '@/api/identity'
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const registering = computed(() => route.path === '/register')
const username = ref(''), password = ref(''), confirmation = ref(''), displayName = ref(''), remember = ref(false)
const busy = ref(false), error = ref(''), notice = ref(route.query.changed ? '密码已更新，请重新登录' : '')
watch(registering, () => { error.value = ''; password.value = ''; confirmation.value = '' })
async function submit() {
  if (busy.value) return
  error.value = ''; notice.value = ''
  if (registering.value && password.value !== confirmation.value) { error.value = '两次密码不一致'; return }
  busy.value = true
  try {
    if (registering.value) { await register(username.value, password.value, displayName.value); await router.replace('/login'); notice.value = '注册成功，请登录' }
    else {
      await auth.signIn(username.value, password.value, remember.value)
      const destination = typeof route.query.redirect === 'string' && ['/', '/account', '/reading', '/vocabulary', '/practice', '/stats', '/admin'].includes(route.query.redirect) ? route.query.redirect : '/'
      await router.replace(destination)
    }
  } catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false; password.value = ''; confirmation.value = '' }
}
async function retry() { try { await auth.restore(true); if (auth.user) await router.replace('/') } catch { error.value = '仍无法连接服务，请稍后重试' } }
</script>
<template>
<section class="login-page" aria-labelledby="login-title"><div class="login-card">
  <div class="login-logo" aria-hidden="true">L</div><p class="login-brand">Lingua Loop</p>
  <h1 id="login-title">{{ registering ? '创建账号' : '欢迎回来' }}</h1><p class="login-subtitle">让每一次积累，都成为下一次进步的起点。</p>
  <div v-if="auth.connectionError" class="form-message" role="alert">暂时无法恢复登录，请检查服务连接。<button class="text-button" @click="retry">重新连接</button></div>
  <p v-if="error" class="form-message form-error" role="alert">{{ error }}</p>
  <p v-if="notice" class="form-message" role="status">{{ notice }}</p>
  <form :aria-busy="busy" @submit.prevent="submit">
    <label for="login-username">用户名</label><input id="login-username" v-model="username" autocomplete="username" placeholder="你的用户名" minlength="3" maxlength="32" required :disabled="busy" aria-describedby="username-hint">
    <p v-if="registering" id="username-hint" class="field-hint">3—32位英文字母、数字或下划线。</p>
    <template v-if="registering"><label for="register-name">昵称（选填）</label><input id="register-name" v-model="displayName" autocomplete="nickname" maxlength="64" :disabled="busy"></template>
    <label for="login-password">密码</label><input id="login-password" v-model="password" type="password" :autocomplete="registering ? 'new-password' : 'current-password'" placeholder="你的密码" minlength="8" maxlength="128" required :disabled="busy">
    <template v-if="registering"><p class="field-hint">8—64个字符，UTF-8最多72字节。</p><label for="register-confirm">确认密码</label><input id="register-confirm" v-model="confirmation" type="password" autocomplete="new-password" required :disabled="busy"></template>
    <label v-else class="remember-label"><input v-model="remember" type="checkbox" :disabled="busy"> 保持登录7天</label>
    <button class="button-primary" type="submit" :disabled="busy || auth.connectionError">{{ busy ? '正在提交…' : registering ? '注册' : '登录' }}</button>
  </form><p class="login-notice"><RouterLink :to="registering ? '/login' : '/register'">{{ registering ? '已有账号？去登录' : '还没有账号？创建账号' }}</RouterLink></p>
</div></section>
</template>

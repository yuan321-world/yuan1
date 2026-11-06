<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ApiError } from '@/api/client'
import type { EmailCodeChallenge } from '@/api/identity'

const props = withDefaults(defineProps<{
  idPrefix: string
  email: string
  code: string
  requestCode: () => Promise<EmailCodeChallenge | null>
  disabled?: boolean
  emailError?: string
  codeError?: string
}>(), { disabled: false })
const emit = defineEmits<{
  'update:email': [value: string]
  'update:code': [value: string]
  challenge: [value: EmailCodeChallenge]
  error: [value: unknown]
}>()
const emailInput = ref<HTMLInputElement>()
const requesting = ref(false)
const now = ref(Date.now())
let cooldownUntil = 0
const remaining = computed(() => Math.max(0, Math.ceil((cooldownUntil - now.value) / 1000)))
const buttonLabel = computed(() => requesting.value ? '请求中…' : remaining.value ? `再次请求（${remaining.value}秒）` : '获取验证码')
const tick = window.setInterval(() => { now.value = Date.now() }, 250)
onBeforeUnmount(() => window.clearInterval(tick))

async function request() {
  if (props.disabled || requesting.value || remaining.value) return
  // Each owner validates the fields required by this action, including this email,
  // so the first invalid field follows form order rather than this component's position.
  requesting.value = true
  try {
    const challenge = await props.requestCode()
    if (challenge) {
      emit('challenge', challenge)
      cooldownUntil = Date.now() + Math.max(0, challenge.resendAfter) * 1000
      now.value = Date.now()
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 429 && error.retryAfterSeconds !== undefined) {
      cooldownUntil = Date.now() + Math.max(0, error.retryAfterSeconds) * 1000
      now.value = Date.now()
    }
    emit('error', error)
  } finally { requesting.value = false }
}
</script>

<template>
  <div class="email-code-fields">
    <div class="auth-field">
      <label :for="`${props.idPrefix}-email`">邮箱地址</label>
      <div class="email-code-row">
        <input :id="`${props.idPrefix}-email`" name="email" ref="emailInput" :value="props.email" type="email"
          autocomplete="email" inputmode="email" maxlength="254" placeholder="name@example.com" required
          :disabled="props.disabled" :aria-invalid="props.emailError ? 'true' : undefined"
          :aria-describedby="props.emailError ? `${props.idPrefix}-email-error` : undefined"
          @input="emit('update:email', ($event.target as HTMLInputElement).value)">
        <button type="button" class="button-secondary email-code-button" :disabled="props.disabled || requesting || remaining > 0" @click="request">{{ buttonLabel }}</button>
      </div>
      <p v-if="props.emailError" :id="`${props.idPrefix}-email-error`" class="auth-field-error" role="alert">{{ props.emailError }}</p>
    </div>
    <div class="auth-field">
      <label :for="`${props.idPrefix}-code`">邮箱验证码</label>
      <input :id="`${props.idPrefix}-code`" :value="props.code" autocomplete="one-time-code" inputmode="numeric"
        placeholder="输入邮箱中的验证码" required :disabled="props.disabled"
        :aria-invalid="props.codeError ? 'true' : undefined"
        :aria-describedby="props.codeError ? `${props.idPrefix}-code-error` : undefined"
        name="code" @input="emit('update:code', ($event.target as HTMLInputElement).value)">
      <p v-if="props.codeError" :id="`${props.idPrefix}-code-error`" class="auth-field-error" role="alert">{{ props.codeError }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount, type ComponentPublicInstance } from 'vue'
import { useRouter } from 'vue-router'
import { requestJson } from '@/api/client'
import { emailCode, passwordReset, type EmailCodeChallenge } from '@/api/identity'
import EmailCodeField from '@/components/EmailCodeField.vue'
import FormFeedback from '@/components/FormFeedback.vue'
import PasswordInput from '@/components/PasswordInput.vue'
import { useFormFeedback, type PageFeedback } from '@/feedback/useFormFeedback'
import { validatePassword } from '@/auth/password-policy'

const props = defineProps<{ embedded?: boolean }>()
const emit = defineEmits<{ switch: [mode: 'login', message?: string]; busy: [value: boolean] }>()
let alive = true
onBeforeUnmount(() => { alive = false })
const router = useRouter()
const username = ref(''), email = ref(''), code = ref(''), challengeId = ref<string | null>(null)
const newPassword = ref(''), confirmation = ref('')
const busy = ref(false), notice = ref(''), pageFeedback = ref<PageFeedback | null>(null)
const retrying = ref(false)
watch([busy, retrying], ([posting, reconnecting]) => emit('busy', posting || reconnecting), { flush: 'sync' })

const feedback = useFormFeedback({
  fields: ['username', 'email', 'code', 'newPassword', 'confirmation'], action: 'reset',
  validators: {
    code: () => challengeId.value ? null : '请先获取邮箱验证码',
    newPassword: () => validatePassword(newPassword.value),
    confirmation: () => newPassword.value === confirmation.value ? null : '两次密码不一致',
  },
  errorIds: { username: 'username-error', email: 'reset-email-error', code: 'reset-code-error', newPassword: 'newPassword-error', confirmation: 'confirmation-error' },
  onPageError: error => { pageFeedback.value = error },
})
const fieldErrors = computed(() => feedback.fieldErrors.value)
const formError = computed(() => feedback.formError.value)

function setFeedbackForm(target: Element | ComponentPublicInstance | null) {
  feedback.form.value = target instanceof HTMLFormElement ? target : undefined
}
function setFeedbackPanel(target: Element | ComponentPublicInstance | null) {
  feedback.panel.value = target as unknown as NonNullable<typeof feedback.panel.value> | undefined
}
function handleInvalid(event: Event) { feedback.invalid(event) }
function changed(field: string) {
  feedback.changed(field)
}
function newPasswordChanged(value: string) {
  newPassword.value = value
  changed('newPassword')
  if (feedback.fieldErrors.value.confirmation) feedback.changed('confirmation')
}
function confirmationChanged(value: string) {
  confirmation.value = value
  changed('confirmation')
}

watch([username, email], () => {
  if (!challengeId.value) return
  challengeId.value = null; code.value = ''
  feedback.setField('code', '账号或邮箱已更改，请重新获取验证码')
  notice.value = ''
})

async function requestResetCode(): Promise<EmailCodeChallenge | null> {
  pageFeedback.value = null; notice.value = ''; feedback.formError.value = ''
  if (!feedback.validate(['username', 'email'])) { await feedback.focusError(); return null }
  busy.value = true
  try { return await emailCode('RESET', email.value.trim(), username.value.trim()) }
  finally { busy.value = false }
}
function codeRequested(challenge: EmailCodeChallenge) {
  challengeId.value = challenge.challengeId
  feedback.clearField('code')
  notice.value = '请求已受理。仅当用户名与已验证邮箱匹配时才会发送验证码。未收到时，请核对注册或绑定时使用的邮箱，并检查垃圾邮件。'
}
async function codeRequestError(error: unknown) {
  await feedback.fail(error)
}
async function retryConnection() {
  if (retrying.value) return
  retrying.value = true; pageFeedback.value = null
  try { await requestJson<unknown>('/api/v1/system/status') }
  catch { pageFeedback.value = { kind: 'network', message: '仍无法连接服务，请稍后重试' } }
  finally { retrying.value = false }
}

async function submit() {
  if (busy.value) return
  pageFeedback.value = null; notice.value = ''; feedback.formError.value = ''
  if (!feedback.validate(['username', 'email', 'code', 'newPassword', 'confirmation'])) { await feedback.focusError(); return }
  busy.value = true
  try {
    await passwordReset(username.value.trim(), email.value.trim(), challengeId.value!, code.value.trim(), newPassword.value)
    newPassword.value = ''; confirmation.value = ''; code.value = ''; challengeId.value = null
    if (!alive) return
    if (props.embedded) emit('switch', 'login', '密码已更新，请重新登录')
    else await router.replace({ path: '/login', query: { changed: '1' } })
  } catch (error) {
    await feedback.fail(error)
  } finally {
    busy.value = false; newPassword.value = ''; confirmation.value = ''
    if (Object.keys(feedback.fieldErrors.value).length || feedback.formError.value) await feedback.focusError()
  }
}
</script>

<template>
  <section class="login-page reset-page" aria-labelledby="reset-title">
    <div class="login-card">
      <div class="auth-heading">
        <h1 id="reset-title">重置密码</h1>
        <p class="login-subtitle">使用账号已验证的邮箱找回密码。</p>
      </div>
      <div v-if="pageFeedback" class="auth-message auth-page-feedback" role="alert">
        <span>{{ pageFeedback.message }}</span>
        <button v-if="pageFeedback.kind === 'network'" type="button" class="text-button auth-retry" :disabled="retrying" @click="retryConnection">{{ retrying ? '连接中…' : '重新连接' }}</button>
      </div>
      <form :ref="setFeedbackForm" class="auth-form" :aria-busy="busy" @submit.prevent="submit" @invalid.capture="handleInvalid">
        <div class="auth-fields">
          <div class="auth-field">
            <label for="reset-username">用户名</label>
            <input id="reset-username" name="username" v-model="username" autocomplete="username" minlength="3" maxlength="32" required :disabled="busy"
              :aria-invalid="fieldErrors.username ? 'true' : undefined" :aria-describedby="feedback.describedBy('username')" @input="changed('username')">
            <p v-if="fieldErrors.username" id="username-error" class="auth-field-error" role="alert">{{ fieldErrors.username }}</p>
          </div>
          <EmailCodeField id-prefix="reset" v-model:email="email" v-model:code="code" :request-code="requestResetCode" :disabled="busy"
            :email-error="fieldErrors.email" :code-error="fieldErrors.code" @challenge="codeRequested"
            @error="codeRequestError" @update:email="changed('email')" @update:code="changed('code')" />
          <p v-if="notice" class="auth-message auth-code-notice" role="status">{{ notice }}</p>
          <div class="auth-field">
            <label for="reset-password">新密码</label>
            <PasswordInput id="reset-password" name="newPassword" v-model="newPassword" autocomplete="new-password" placeholder="设置新密码" required :disabled="busy"
              :invalid="Boolean(fieldErrors.newPassword)" :described-by="feedback.describedBy('newPassword', 'reset-password-hint')" @update:model-value="newPasswordChanged" />
            <p id="reset-password-hint" class="auth-hint">8—64个字符，UTF-8最多72字节。</p>
            <p v-if="fieldErrors.newPassword" id="newPassword-error" class="auth-field-error" role="alert">{{ fieldErrors.newPassword }}</p>
          </div>
          <div class="auth-field">
            <label for="reset-confirm">确认新密码</label>
            <PasswordInput id="reset-confirm" name="confirmation" v-model="confirmation" autocomplete="new-password" placeholder="再次输入新密码" required :disabled="busy"
              :invalid="Boolean(fieldErrors.confirmation)" :described-by="feedback.describedBy('confirmation')" @update:model-value="confirmationChanged" />
            <p v-if="fieldErrors.confirmation" id="confirmation-error" class="auth-field-error" role="alert">{{ fieldErrors.confirmation }}</p>
          </div>
        </div>
        <FormFeedback :ref="setFeedbackPanel" :message="formError" />
        <button class="button-primary auth-submit" type="submit" :disabled="busy">
          <span v-if="busy" class="auth-spinner" aria-hidden="true"></span>
          {{ busy ? '正在重置…' : '重置密码' }}
        </button>
      </form>
      <p class="login-notice"><button v-if="!busy && props.embedded" type="button" class="text-button auth-mode-switch" @click="emit('switch', 'login')">返回登录</button><RouterLink v-else-if="!busy" to="/login">返回登录</RouterLink><span v-else class="auth-switch-disabled" aria-disabled="true">返回登录</span></p>
    </div>
  </section>
</template>

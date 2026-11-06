<script setup lang="ts">
import { safeRedirect } from '@/router/safe-redirect'
import { computed, ref, watch, onBeforeUnmount, type ComponentPublicInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { emailCode, register, type EmailCodeChallenge } from '@/api/identity'
import EmailCodeField from '@/components/EmailCodeField.vue'
import FormFeedback from '@/components/FormFeedback.vue'
import PasswordInput from '@/components/PasswordInput.vue'
import { useFormFeedback, type PageFeedback } from '@/feedback/useFormFeedback'
import { validatePassword, validateRegistrationUsername } from '@/auth/password-policy'

const props = defineProps<{ embedded?: boolean; mode?: 'login' | 'register'; message?: string }>()
const emit = defineEmits<{ switch: [mode: 'login' | 'register' | 'reset', message?: string]; success: []; busy: [value: boolean] }>()
let alive = true
onBeforeUnmount(() => { alive = false })
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const registering = computed(() => props.mode ? props.mode === 'register' : route.path === '/register')
const username = ref(''), password = ref(''), confirmation = ref(''), displayName = ref('')
const registrationEmail = ref(''), verificationCode = ref(''), registrationChallenge = ref<string | null>(null)
const busy = ref(false), notice = ref(props.embedded ? '' : route.query.changed ? '密码已更新，请重新登录' : route.query.expired ? '登录已失效，请重新登录' : '')
watch(() => props.message, value => { if (value) notice.value = value }, { immediate: true })
const codeNotice = ref(''), retrying = ref(false), pageFeedback = ref<PageFeedback | null>(null)
watch([busy, retrying], ([posting, reconnecting]) => emit('busy', posting || reconnecting), { flush: 'sync' })

const loginFeedback = useFormFeedback({
  fields: ['username', 'password'], action: 'login',
  errorIds: { username: 'username-error', password: 'password-error' },
  onPageError: error => { if (!(auth.connectionError && error.kind === 'network')) pageFeedback.value = error },
})
const registerFeedback = useFormFeedback({
  fields: ['username', 'email', 'code', 'displayName', 'password', 'confirmation'], action: 'register',
  validators: {
    username: () => validateRegistrationUsername(username.value),
    password: () => validatePassword(password.value),
    confirmation: () => password.value === confirmation.value ? null : '两次密码不一致',
    code: () => registrationChallenge.value ? null : '请先获取邮箱验证码',
  },
  errorIds: { username: 'username-error', email: 'register-email-error', code: 'register-code-error', displayName: 'displayName-error', password: 'password-error', confirmation: 'confirmation-error' },
  onPageError: error => { if (!(auth.connectionError && error.kind === 'network')) pageFeedback.value = error },
})
const activeFeedback = computed(() => registering.value ? registerFeedback : loginFeedback)
const fieldErrors = computed(() => activeFeedback.value.fieldErrors.value)
const formError = computed(() => activeFeedback.value.formError.value)

function setFeedbackForm(target: Element | ComponentPublicInstance | null) {
  const element = target instanceof HTMLFormElement ? target : undefined
  loginFeedback.form.value = element
  registerFeedback.form.value = element
}
function setFeedbackPanel(target: Element | ComponentPublicInstance | null) {
  const panel = target as unknown as NonNullable<typeof loginFeedback.panel.value> | undefined
  loginFeedback.panel.value = panel
  registerFeedback.panel.value = panel
}
function handleInvalid(event: Event) { activeFeedback.value.invalid(event) }
function changed(field: string) {
  activeFeedback.value.changed(field)
}
function passwordChanged(value: string) {
  password.value = value
  changed('password')
  if (activeFeedback.value.fieldErrors.value.confirmation) activeFeedback.value.changed('confirmation')
}
function confirmationChanged(value: string) {
  confirmation.value = value
  changed('confirmation')
}

watch(registering, () => {
  loginFeedback.reset(); registerFeedback.reset()
  pageFeedback.value = null; codeNotice.value = ''
  password.value = ''; confirmation.value = ''
  registrationEmail.value = ''; verificationCode.value = ''; registrationChallenge.value = null
})
watch([username, registrationEmail], () => {
  if (!registering.value || !registrationChallenge.value) return
  registrationChallenge.value = null
  verificationCode.value = ''
  registerFeedback.setField('code', '用户名或邮箱已更改，请重新获取验证码')
  codeNotice.value = ''
})

async function submit() {
  if (busy.value) return
  pageFeedback.value = null; codeNotice.value = ''
  const feedback = activeFeedback.value
  feedback.formError.value = ''
  const fields = registering.value ? ['username', 'email', 'code', 'displayName', 'password', 'confirmation'] : ['username', 'password']
  if (!feedback.validate(fields)) { await feedback.focusError(); return }

  busy.value = true
  try {
    if (registering.value) {
      await register(username.value.trim(), password.value, displayName.value, registrationEmail.value.trim(), verificationCode.value.trim(), registrationChallenge.value!)
      if (!alive) return
      registrationChallenge.value = null; verificationCode.value = ''
      if (props.embedded) emit('switch', 'login', '注册成功，请登录')
      else { await router.replace('/login'); notice.value = '注册成功，请登录' }
    } else {
      await auth.signIn(username.value, password.value, true)
      if (!alive) return
      if (props.embedded) emit('success')
      else await router.replace(safeRedirect(route.query.redirect, auth.isAdmin))
    }
  } catch (error) {
    await feedback.fail(error)
  } finally {
    busy.value = false
    password.value = ''; confirmation.value = ''
    if (Object.keys(activeFeedback.value.fieldErrors.value).length || formError.value) await activeFeedback.value.focusError()
  }
}

async function requestRegistrationCode(): Promise<EmailCodeChallenge | null> {
  pageFeedback.value = null; codeNotice.value = ''; registerFeedback.formError.value = ''
  if (!registerFeedback.validate(['username', 'email'])) { await registerFeedback.focusError(); return null }
  busy.value = true
  try { return await emailCode('REGISTER', registrationEmail.value.trim(), username.value.trim()) }
  finally { busy.value = false }
}
function setRegistrationChallenge(challenge: EmailCodeChallenge) {
  registrationChallenge.value = challenge.challengeId
  registerFeedback.clearField('code')
  codeNotice.value = '验证码请求已提交，请留意邮箱'
}
async function codeRequestError(error: unknown) {
  await registerFeedback.fail(error)
}
async function retryConnection() {
  if (retrying.value) return
  retrying.value = true; pageFeedback.value = null
  try { await auth.restore(true); if (alive && auth.user) { if (props.embedded) emit('success'); else await router.replace('/') } }
  catch { if (!auth.connectionError) pageFeedback.value = { kind: 'network', message: '仍无法连接服务，请稍后重试' } }
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
        <button type="button" class="text-button auth-retry" :disabled="retrying" @click="retryConnection">{{ retrying ? '连接中…' : '重新连接' }}</button>
      </div>
      <div v-if="pageFeedback" class="auth-message auth-page-feedback" role="alert">
        <span>{{ pageFeedback.message }}</span>
        <button v-if="pageFeedback.kind === 'network'" type="button" class="text-button auth-retry" :disabled="retrying" @click="retryConnection">{{ retrying ? '连接中…' : '重新连接' }}</button>
      </div>
      <p v-if="notice" class="auth-message" role="status">{{ notice }}</p>
      <form :ref="setFeedbackForm" class="auth-form" :aria-busy="busy" @submit.prevent="submit" @invalid.capture="handleInvalid">
        <div class="auth-fields">
          <div class="auth-field">
            <label for="login-username">用户名</label>
            <input id="login-username" name="username" v-model="username" autocomplete="username" placeholder="你的用户名" minlength="3" maxlength="32" required :disabled="busy"
              :aria-invalid="fieldErrors.username ? 'true' : undefined" :aria-describedby="activeFeedback.describedBy('username', registering ? 'username-hint' : undefined)" @input="changed('username')">
            <p v-if="registering" id="username-hint" class="auth-hint">3—32位字母、数字或下划线；admin 开头不可注册（忽略大小写）。</p>
            <p v-if="fieldErrors.username" id="username-error" class="auth-field-error" role="alert">{{ fieldErrors.username }}</p>
          </div>
          <EmailCodeField v-if="registering" id-prefix="register" v-model:email="registrationEmail" v-model:code="verificationCode"
            :request-code="requestRegistrationCode" :disabled="busy" :email-error="fieldErrors.email" :code-error="fieldErrors.code"
            @challenge="setRegistrationChallenge" @error="codeRequestError" @update:email="changed('email')" @update:code="changed('code')" />
          <p v-if="registering && codeNotice" class="auth-message auth-code-notice" role="status">{{ codeNotice }}</p>
          <div v-if="registering" class="auth-field">
            <label for="register-name">昵称<span class="auth-optional">（选填）</span></label>
            <input id="register-name" name="displayName" v-model="displayName" autocomplete="nickname" placeholder="你希望被怎样称呼" maxlength="64" :disabled="busy"
              :aria-invalid="fieldErrors.displayName ? 'true' : undefined" :aria-describedby="activeFeedback.describedBy('displayName')" @input="changed('displayName')">
            <p v-if="fieldErrors.displayName" id="displayName-error" class="auth-field-error" role="alert">{{ fieldErrors.displayName }}</p>
          </div>
          <div class="auth-field">
            <label for="login-password">密码</label>
            <PasswordInput id="login-password" name="password" v-model="password" :autocomplete="registering ? 'new-password' : 'current-password'" :placeholder="registering ? '设置你的密码' : '输入你的密码'" required :disabled="busy"
              :invalid="Boolean(fieldErrors.password)" :described-by="activeFeedback.describedBy('password', registering ? 'password-hint' : undefined)" @update:model-value="passwordChanged" />
            <p v-if="registering" id="password-hint" class="auth-hint">8—64个字符，UTF-8最多72字节。</p>
            <p v-if="fieldErrors.password" id="password-error" class="auth-field-error" role="alert">{{ fieldErrors.password }}</p>
          </div>
          <div v-if="registering" class="auth-field">
            <label for="register-confirm">确认密码</label>
            <PasswordInput id="register-confirm" name="confirmation" v-model="confirmation" autocomplete="new-password" placeholder="再次输入密码" required :disabled="busy"
              :invalid="Boolean(fieldErrors.confirmation)" :described-by="activeFeedback.describedBy('confirmation')" @update:model-value="confirmationChanged" />
            <p v-if="fieldErrors.confirmation" id="confirmation-error" class="auth-field-error" role="alert">{{ fieldErrors.confirmation }}</p>
          </div>
        </div>
        <button v-if="props.embedded && !registering && !busy" type="button" class="text-button forgot-link" @click="emit('switch', 'reset')">忘记密码？</button>
        <RouterLink v-else-if="!registering && !busy" class="forgot-link" to="/forgot-password">忘记密码？</RouterLink>
        <span v-else-if="!registering" class="forgot-link-disabled" aria-disabled="true">忘记密码？</span>
        <FormFeedback :ref="setFeedbackPanel" :message="formError" />
        <button class="button-primary auth-submit" type="submit" :disabled="busy || auth.connectionError">
          <span v-if="busy" class="auth-spinner" aria-hidden="true"></span>
          {{ busy ? (registering ? '正在创建账号…' : '正在登录…') : registering ? '注册' : '登录' }}
        </button>
      </form>
      <p class="login-notice">
        <span v-if="busy" class="auth-switch-disabled" aria-disabled="true">{{ registering ? '已有账号？去登录' : '还没有账号？创建账号' }}</span>
        <button v-else-if="props.embedded" type="button" class="text-button auth-mode-switch" @click="emit('switch', registering ? 'login' : 'register')">{{ registering ? '已有账号？去登录' : '还没有账号？创建账号' }}</button>
        <RouterLink v-else :to="registering ? '/login' : '/register'">{{ registering ? '已有账号？去登录' : '还没有账号？创建账号' }}</RouterLink>
      </p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { accountEmailCode, accountEmailDelivery, type EmailCodeChallenge } from '@/api/identity'
import { ApiError } from '@/api/client'
import { useFormFeedback, type PageFeedback } from '@/feedback/useFormFeedback'
import { notifySuccess } from '@/feedback/notify'
import FieldError from '@/components/FieldError.vue'
import FormFeedback from '@/components/FormFeedback.vue'
import EmailCodeField from '@/components/EmailCodeField.vue'
import PasswordInput from '@/components/PasswordInput.vue'
import { validatePassword } from '@/auth/password-policy'

const auth = useAuthStore(), router = useRouter()
const displayName = ref(auth.user?.displayName ?? '')
const currentPassword = ref(''), newPassword = ref(''), confirmation = ref('')
const bindingEmail = ref(auth.user?.email ?? ''), bindingCode = ref(''), bindingCurrentPassword = ref(''), bindingChallenge = ref<string | null>(null)
const busy = ref(false), reconnecting = ref(false), pageError = ref<PageFeedback | null>(null)
const bindingNotice = ref(''), statusRetry = ref(false)
let statusTimer: ReturnType<typeof setTimeout> | undefined
let statusController: AbortController | undefined
let statusGeneration = 0
function stopDeliveryCheck() {
  statusGeneration++
  clearTimeout(statusTimer); statusTimer = undefined
  statusController?.abort(); statusController = undefined
  statusRetry.value = false
}
onBeforeUnmount(stopDeliveryCheck)
function onPageError(error: PageFeedback) {
  pageError.value = error
  if (error.kind === 'session') { auth.clear(); void router.replace({ path: '/login', query: { expired: '1' } }) }
}
const profileFeedback = useFormFeedback({ fields: ['displayName'], action: 'profile', onPageError })
const passwordFeedback = useFormFeedback({
  fields: ['currentPassword', 'newPassword', 'confirmation'], action: 'password', onPageError,
  validators: {
    currentPassword: () => validatePassword(currentPassword.value), newPassword: () => validatePassword(newPassword.value),
    confirmation: () => newPassword.value === confirmation.value ? null : '两次新密码不一致',
  },
})
const bindingFeedback = useFormFeedback({
  fields: ['currentPassword', 'email', 'code'], action: 'binding', onPageError,
  validators: {
    currentPassword: () => validatePassword(bindingCurrentPassword.value),
    code: () => bindingChallenge.value ? null : '请先获取邮箱验证码',
  },
})
const { form: profileForm, panel: profilePanel, fieldErrors: profileErrors, formError: profileError } = profileFeedback
const { form: passwordForm, panel: passwordPanel, fieldErrors: passwordErrors, formError: passwordError } = passwordFeedback
const { form: bindingForm, panel: bindingPanel, fieldErrors: bindingErrors, formError: bindingError } = bindingFeedback

watch(bindingEmail, () => {
  stopDeliveryCheck(); bindingNotice.value = ''
  if (!bindingChallenge.value) return
  bindingChallenge.value = null; bindingCode.value = ''
  bindingFeedback.setField('code', '邮箱已更改，请重新获取验证码')
})
function passwordChanged() {
  passwordFeedback.changed('newPassword')
  passwordFeedback.changed('confirmation')
}
async function reconnect() {
  if (reconnecting.value) return
  reconnecting.value = true
  try {
    await auth.restore(true)
    if (!auth.user) onPageError({ kind: 'session', message: '登录已失效，请重新登录' })
    else pageError.value = null
  } catch { pageError.value = { kind: 'network', message: '仍无法连接服务，请稍后重试' } }
  finally { reconnecting.value = false }
}
async function report(error: unknown, feedback: ReturnType<typeof useFormFeedback>) {
  await feedback.fail(error)
  if (error instanceof ApiError && ['EMAIL_ALREADY_BOUND', 'ACCOUNT_CHANGED'].includes(error.code ?? '')) {
    try {
      await auth.restore(true)
      if (!auth.user) onPageError({ kind: 'session', message: '登录已失效，请重新登录' })
    } catch { onPageError({ kind: 'network', message: '无法连接服务，请检查连接后重试' }) }
  }
}
async function submit(kind: 'profile' | 'password') {
  if (busy.value) return
  const feedback = kind === 'profile' ? profileFeedback : passwordFeedback
  feedback.reset()
  if (!feedback.validate()) { await feedback.focusError(); return }
  busy.value = true
  try {
    if (kind === 'profile') { if (await auth.saveProfile(displayName.value)) notifySuccess('昵称已保存') }
    else if (await auth.changePassword(currentPassword.value, newPassword.value)) { await router.replace({ path: '/login', query: { changed: '1' } }) }
  } catch (error) { await report(error, feedback) }
  finally { busy.value = false; currentPassword.value = ''; newPassword.value = ''; confirmation.value = ''; await feedback.focusError() }
}
async function requestBindingCode(): Promise<EmailCodeChallenge | null> {
  if (busy.value) return null
  bindingFeedback.formError.value = ''
  if (!bindingFeedback.validate(['currentPassword', 'email'])) { await bindingFeedback.focusError(); return null }
  busy.value = true
  try { return await accountEmailCode(bindingEmail.value.trim(), bindingCurrentPassword.value) }
  finally { busy.value = false }
}
function acceptBindingChallenge(challenge: EmailCodeChallenge) {
  stopDeliveryCheck()
  bindingChallenge.value = challenge.challengeId
  bindingFeedback.clearField('code')
  bindingNotice.value = '请求已受理，正在确认邮件发送状态…'
  void checkBindingDelivery()
}
async function checkBindingDelivery(attempt = 0, generation = statusGeneration) {
  const id = bindingChallenge.value
  if (!id || generation !== statusGeneration) return
  statusRetry.value = false
  const controller = new AbortController(); statusController = controller
  try {
    const state = await accountEmailDelivery(id, controller.signal)
    if (generation !== statusGeneration || bindingChallenge.value !== id) return
    if (pageError.value?.kind === 'network') pageError.value = null
    if (state === 'QUEUED') {
      if (attempt < 14) statusTimer = setTimeout(() => { void checkBindingDelivery(attempt + 1, generation) }, 2000)
      else { bindingNotice.value = '请求已受理，暂未确认发送结果，可稍后重新检查。'; statusRetry.value = true }
    } else if (state === 'SMTP_ACCEPTED') {
      bindingNotice.value = '邮件服务已接受发送，请查看收件箱或垃圾邮件；这不代表邮件已送达。'
    } else {
      bindingChallenge.value = null; bindingCode.value = ''; bindingNotice.value = ''
      bindingFeedback.formError.value = state === 'EXPIRED' ? '验证码请求已过期，请重新获取。' : '邮件发送失败，请在请求间隔结束后重新获取验证码。'
      await bindingFeedback.focusError()
    }
  } catch (error) {
    if (generation !== statusGeneration || controller.signal.aborted) return
    if (error instanceof ApiError && error.code === 'EMAIL_REQUEST_NOT_FOUND') {
      bindingChallenge.value = null; bindingCode.value = ''; bindingNotice.value = ''
      bindingFeedback.formError.value = '验证码请求不存在或已失效，请重新获取。'
      await bindingFeedback.focusError(); return
    }
    bindingNotice.value = '请求已受理，暂无法确认发送结果。'; statusRetry.value = true
    await report(error, bindingFeedback)
  }
}
async function submitEmailBinding() {
  if (busy.value) return
  bindingFeedback.reset()
  if (!bindingFeedback.validate()) { await bindingFeedback.focusError(); return }
  busy.value = true
  try {
    if (await auth.bindEmail(bindingEmail.value.trim(), bindingCurrentPassword.value, bindingChallenge.value!, bindingCode.value.trim())) {
      bindingChallenge.value = null; bindingCode.value = ''; bindingCurrentPassword.value = ''
      stopDeliveryCheck(); bindingNotice.value = ''
      bindingEmail.value = auth.user?.email ?? bindingEmail.value.trim()
      notifySuccess('邮箱已验证')
    }
  } catch (error) { await report(error, bindingFeedback) }
  finally { busy.value = false; await bindingFeedback.focusError() }
}
</script>
<template>
  <section class="account-page" aria-labelledby="account-title">
    <p class="eyebrow">YOUR ACCOUNT</p><h1 id="account-title">账户设置</h1>
    <p class="account-caption">{{ auth.user?.username }} · {{ auth.isAdmin ? '管理员' : '学习者' }}</p>
    <div v-if="pageError" class="auth-message auth-error page-feedback" role="alert">
      {{ pageError.message }}
      <button v-if="pageError.kind === 'network'" class="text-button auth-retry" type="button" :disabled="reconnecting" @click="reconnect">{{ reconnecting ? '连接中…' : '重新连接' }}</button>
    </div>
    <section class="account-card account-email-card" aria-labelledby="account-email-title">
      <h2 id="account-email-title">邮箱验证</h2>
      <p v-if="auth.user?.emailVerified === true" class="verified-email"><span class="verified-mark" aria-hidden="true">✓</span><span>{{ auth.user.email }}</span><strong>已验证</strong></p>
      <form v-else ref="bindingForm" class="account-email-form" :aria-busy="busy" @invalid.capture="bindingFeedback.invalid" @submit.prevent="submitEmailBinding">
        <p class="field-hint">绑定并验证邮箱后，可用于找回密码。</p>
        <label for="binding-current-password">当前密码（验证邮箱）</label>
        <PasswordInput id="binding-current-password" name="currentPassword" v-model="bindingCurrentPassword" autocomplete="current-password" placeholder="输入当前密码" required :disabled="busy"
          :invalid="Boolean(bindingErrors.currentPassword)" :described-by="bindingFeedback.describedBy('currentPassword')" @update:model-value="bindingFeedback.changed('currentPassword')" />
        <FieldError id="binding-current-password-error" :message="bindingErrors.currentPassword" />
        <EmailCodeField id-prefix="account-email" v-model:email="bindingEmail" v-model:code="bindingCode" :request-code="requestBindingCode" :disabled="busy"
          :email-error="bindingErrors.email" :code-error="bindingErrors.code" @challenge="acceptBindingChallenge"
          @error="report($event, bindingFeedback)" @update:email="bindingFeedback.changed('email')" @update:code="bindingFeedback.changed('code')" />
        <p v-if="bindingNotice" class="field-hint" role="status">{{ bindingNotice }}</p>
        <button v-if="statusRetry" type="button" class="text-button" :disabled="busy" @click="checkBindingDelivery()">重新检查发送状态</button>
        <p class="field-hint">获取验证码和确认绑定时都会验证当前密码。</p>
        <FormFeedback ref="bindingPanel" :message="bindingError" />
        <button class="button-primary" :disabled="busy"><span v-if="busy" class="auth-spinner" aria-hidden="true"></span>{{ busy ? '正在绑定…' : '验证并绑定邮箱' }}</button>
      </form>
    </section>
    <div class="account-grid">
      <form ref="profileForm" class="account-card" :aria-busy="busy" @invalid.capture="profileFeedback.invalid" @submit.prevent="submit('profile')"><h2>个人资料</h2>
        <label for="display-name">昵称</label><input id="display-name" name="displayName" v-model="displayName" autocomplete="nickname" maxlength="64" required :disabled="busy"
          :aria-invalid="profileErrors.displayName ? 'true' : undefined" :aria-describedby="profileFeedback.describedBy('displayName', 'display-name-hint')" @input="profileFeedback.changed('displayName')">
        <p id="display-name-hint" class="field-hint">1—32个字符，用户名不可修改。</p>
        <FieldError id="display-name-error" :message="profileErrors.displayName" />
        <FormFeedback ref="profilePanel" :message="profileError" />
        <button class="button-primary" :disabled="busy">保存昵称</button>
      </form>
      <form ref="passwordForm" class="account-card" :aria-busy="busy" @invalid.capture="passwordFeedback.invalid" @submit.prevent="submit('password')"><h2>修改密码</h2>
        <label for="current-password">当前密码</label><PasswordInput id="current-password" name="currentPassword" v-model="currentPassword" autocomplete="current-password" required :disabled="busy"
          :invalid="Boolean(passwordErrors.currentPassword)" :described-by="passwordFeedback.describedBy('currentPassword')" @update:model-value="passwordFeedback.changed('currentPassword')" />
        <FieldError id="current-password-error" :message="passwordErrors.currentPassword" />
        <label for="new-password">新密码</label><PasswordInput id="new-password" name="newPassword" v-model="newPassword" autocomplete="new-password" required :disabled="busy"
          :invalid="Boolean(passwordErrors.newPassword)" :described-by="passwordFeedback.describedBy('newPassword', 'account-password-hint')" @update:model-value="passwordChanged" />
        <FieldError id="new-password-error" :message="passwordErrors.newPassword" />
        <label for="confirm-password">确认新密码</label><PasswordInput id="confirm-password" name="confirmation" v-model="confirmation" autocomplete="new-password" required :disabled="busy"
          :invalid="Boolean(passwordErrors.confirmation)" :described-by="passwordFeedback.describedBy('confirmation')" @update:model-value="passwordFeedback.changed('confirmation')" />
        <FieldError id="confirm-password-error" :message="passwordErrors.confirmation" />
        <p id="account-password-hint" class="field-hint">8—64个字符，UTF-8最多72字节。修改后所有设备需重新登录。</p>
        <FormFeedback ref="passwordPanel" :message="passwordError" />
        <button class="button-primary" :disabled="busy">更新密码</button>
      </form>
    </div>
  </section>
</template>

import { nextTick, ref } from 'vue'
import { ApiError } from '../api/client'

export type FeedbackAction = 'login' | 'register' | 'reset' | 'profile' | 'password' | 'binding'
export interface PageFeedback { kind: 'network' | 'session' | 'permission'; message: string }
interface RoutedFeedback { fieldErrors: Record<string, string>; formError: string; page?: PageFeedback; aborted?: boolean }

/** Presentation routing only: no requests, navigation, auth-state mutation, or toast side effects. */
export function classifyFeedback(error: unknown, fields: readonly string[], action?: FeedbackAction): RoutedFeedback {
  const result: RoutedFeedback = { fieldErrors: {}, formError: '' }
  if (error instanceof Error && error.name === 'AbortError') return { ...result, aborted: true }
  if (error instanceof Error && (error.name === 'TimeoutError' || error instanceof TypeError))
    return { ...result, page: { kind: 'network', message: error.name === 'TimeoutError' ? '请求超时，请检查连接后重试' : '无法连接服务，请检查连接后重试' } }
  if (!(error instanceof ApiError)) return { ...result, formError: '操作暂未完成，请重试' }
  if (error.code === 'UNAUTHENTICATED') return { ...result, page: { kind: 'session', message: '登录已失效，请重新登录' } }
  if (error.code === 'AUTH_SERVICE_UNAVAILABLE') return { ...result, page: { kind: 'network', message: '认证服务暂不可用，请稍后重试' } }
  if (error.code === 'FORBIDDEN') return { ...result, page: { kind: 'permission', message: '没有访问权限，请返回可访问的页面' } }

  const unknownFields: string[] = []
  for (const [field, message] of Object.entries(error.fieldErrors ?? {})) {
    if (fields.includes(field)) result.fieldErrors[field] = message
    else unknownFields.push(message)
  }
  if (unknownFields.length) result.formError = unknownFields.join('；')
  if (Object.keys(result.fieldErrors).length || unknownFields.length) return result

  const target = error.code === 'EMAIL_TAKEN' ? 'email' : error.code === 'USERNAME_TAKEN' ? 'username'
    : error.code === 'BAD_CREDENTIALS' && (action === 'password' || action === 'binding') ? 'currentPassword' : undefined
  if (target && fields.includes(target)) {
    result.fieldErrors[target] = error.publicMessage || (target === 'currentPassword' ? '当前密码不正确' : target === 'email' ? '邮箱已被使用' : '用户名已被使用')
    return result
  }
  const fallback: Record<string, string> = {
    BAD_CREDENTIALS: '用户名或密码不正确', CSRF_INVALID: '页面凭据已失效，请重试',
    RATE_LIMITED: '请求过于频繁，请稍后重试', EMAIL_UNAVAILABLE: '邮件服务暂不可用，请稍后重试',
    EMAIL_ALREADY_BOUND: '账号已绑定邮箱，请刷新后查看验证状态', ACCOUNT_CHANGED: '账号已发生变化，请刷新后重试',
    VALIDATION_ERROR: '请检查填写内容',
  }
  result.formError = fallback[error.code ?? ''] || error.publicMessage || '服务暂不可用，请稍后重试'
  return result
}

interface Options {
  fields: readonly string[]
  action?: FeedbackAction
  validators?: Record<string, () => string | null>
  errorIds?: Record<string, string>
  onPageError?: (error: PageFeedback) => void
}

export function useFormFeedback(options: Options) {
  const fieldErrors = ref<Record<string, string>>({}), formError = ref('')
  const form = ref<HTMLFormElement>()
  const panel = ref<{ focus: (options?: FocusOptions) => void; scrollIntoView?: (options?: ScrollIntoViewOptions) => void }>()
  const origins: Record<string, 'local' | 'server'> = {}
  let clearFormOnChange = false
  type FormControl = HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
  function inputFor(field: string) {
    return Array.from(form.value?.querySelectorAll<FormControl>('input[name], textarea[name], select[name]') ?? []).find(input => input.name === field)
  }
  function nativeError(input?: FormControl): string | null {
    if (!input || input.disabled || input.validity.valid) return null
    const label = input.labels?.[0]?.textContent?.trim().replace(/（选填）/g, '') || '此项'
    if (input.validity.valueMissing) return `请填写${label}`
    if (input.validity.typeMismatch && input.type === 'email') return '请输入有效的邮箱地址'
    if ('minLength' in input && input.validity.tooShort) return `${label}至少${input.minLength}个字符`
    if ('maxLength' in input && input.validity.tooLong) return `${label}最多${input.maxLength}个字符`
    return `请按要求填写${label}`
  }
  function clearField(field: string) { delete fieldErrors.value[field]; delete origins[field] }
  function setField(field: string, message: string) {
    if (!options.fields.includes(field)) { formError.value = message; return }
    fieldErrors.value[field] = message; origins[field] = 'local'
  }
  function checkField(field: string) {
    const error = nativeError(inputFor(field)) || options.validators?.[field]?.() || null
    if (error) setField(field, error); else clearField(field)
    return !error
  }
  function changed(field: string) {
    if (origins[field] === 'local') checkField(field)
    else if (origins[field] === 'server') clearField(field)
    if (clearFormOnChange) { formError.value = ''; clearFormOnChange = false }
  }
  function reset() {
    fieldErrors.value = {}; formError.value = ''; clearFormOnChange = false
    for (const key of Object.keys(origins)) delete origins[key]
  }
  function validate(fields: readonly string[] = options.fields) {
    let valid = true
    for (const field of fields) if (!checkField(field)) valid = false
    return valid
  }
  async function focusError() {
    await nextTick()
    const invalid = form.value?.querySelector<FormControl>('input[aria-invalid="true"], textarea[aria-invalid="true"], select[aria-invalid="true"]')
    const element = invalid || (formError.value ? panel.value : undefined)
    element?.focus({ preventScroll: true })
    element?.scrollIntoView?.({ block: 'nearest', behavior: 'auto' })
  }
  async function fail(error: unknown) {
    const routed = classifyFeedback(error, options.fields, options.action)
    if (routed.aborted) return
    // A code request can fail while another field still has a local validation error.
    // Its response must not erase unrelated, unresolved feedback.
    const localErrors = Object.fromEntries(Object.entries(fieldErrors.value).filter(([field]) => origins[field] === 'local'))
    for (const field of Object.keys(origins)) if (origins[field] !== 'local') delete origins[field]
    fieldErrors.value = { ...localErrors, ...routed.fieldErrors }; formError.value = routed.formError
    for (const field of Object.keys(routed.fieldErrors)) origins[field] = 'server'
    clearFormOnChange = error instanceof ApiError && error.code === 'BAD_CREDENTIALS'
    if (routed.page) options.onPageError?.(routed.page)
    else if (!routed.aborted) await focusError()
  }
  function invalid(event: Event) {
    if (!(event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement || event.target instanceof HTMLSelectElement)) return
    const input = event.target
    if (!options.fields.includes(input.name)) return
    event.preventDefault(); setField(input.name, nativeError(input) || '请检查填写内容')
    void focusError()
  }
  function describedBy(field: string, hint?: string) {
    const input = inputFor(field)
    return [hint, fieldErrors.value[field] ? (options.errorIds?.[field] || `${input?.id || field}-error`) : undefined].filter(Boolean).join(' ') || undefined
  }
  return { fieldErrors, formError, form, panel, reset, setField, clearField, changed, validate, fail, focusError, invalid, describedBy }
}

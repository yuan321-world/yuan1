import { describe, expect, it } from 'vitest'
import { ApiError } from '../../src/api/client'
import { classifyFeedback, useFormFeedback } from '../../src/feedback/useFormFeedback'

describe('structured feedback routing', () => {
  it('validates and focuses article textareas and selects in DOM order', async () => {
    const feedback = useFormFeedback({ fields: ['body', 'categoryId'] })
    const element = document.createElement('form')
    element.innerHTML = '<textarea name="body" required aria-invalid="true"></textarea><select name="categoryId" required><option value="">请选择</option></select>'
    document.body.append(element); feedback.form.value = element
    try {
      expect(feedback.validate()).toBe(false)
      expect(feedback.fieldErrors.value.body).toBeTruthy()
      expect(feedback.fieldErrors.value.categoryId).toBeTruthy()
      await feedback.focusError()
      expect(document.activeElement).toBe(element.querySelector('textarea'))
    } finally { element.remove() }
  })
  it('keeps wrong current passwords local while real session expiry is page scoped', () => {
    const wrong = new ApiError(401, { code: 'BAD_CREDENTIALS', message: '当前密码不正确' })
    expect(classifyFeedback(wrong, ['currentPassword'], 'password')).toMatchObject({ fieldErrors: { currentPassword: '当前密码不正确' } })
    expect(classifyFeedback(wrong, ['username', 'password'], 'login')).toMatchObject({ formError: '用户名或密码不正确' })
    expect(classifyFeedback(new ApiError(401, { code: 'UNAUTHENTICATED' }), ['currentPassword'], 'binding')).toMatchObject({ page: { kind: 'session' } })
  })
  it('maps server fields and preserves unknown-field errors instead of dropping them', () => {
    expect(classifyFeedback(new ApiError(400, { code: 'VALIDATION_ERROR', fieldErrors: { email: '邮箱不正确' } }), ['email'], 'binding'))
      .toMatchObject({ fieldErrors: { email: '邮箱不正确' }, formError: '' })
    expect(classifyFeedback(new ApiError(400, { code: 'VALIDATION_ERROR', fieldErrors: { futureField: '请求不适用，请重试' } }), ['email'], 'binding').formError)
      .toContain('请求不适用')
  })
  it('keeps throttling and email service failures in the current form; network and permissions are page scoped', () => {
    expect(classifyFeedback(new ApiError(429, { code: 'RATE_LIMITED' }), ['email'], 'binding').formError).toBeTruthy()
    expect(classifyFeedback(new ApiError(503, { code: 'EMAIL_UNAVAILABLE' }), ['email'], 'binding').page).toBeUndefined()
    expect(classifyFeedback(new TypeError('Failed to fetch'), [], 'login').page?.kind).toBe('network')
    expect(classifyFeedback(new ApiError(403, { code: 'CSRF_INVALID' }), [], 'login').page).toBeUndefined()
    expect(classifyFeedback(new ApiError(403, { code: 'FORBIDDEN' }), [], 'binding').page?.kind).toBe('permission')
  })
  it('clears local errors only after correction and isolates different forms', () => {
    let value = 'a'
    const binding = useFormFeedback({ fields: ['currentPassword'], validators: { currentPassword: () => value.length < 8 ? '密码至少8个字符' : null } })
    const profile = useFormFeedback({ fields: ['displayName'] })
    expect(binding.validate()).toBe(false)
    value = 'ab'; binding.changed('currentPassword')
    expect(binding.fieldErrors.value.currentPassword).toBeTruthy()
    value = 'correct-password'; binding.changed('currentPassword')
    expect(binding.fieldErrors.value.currentPassword).toBeUndefined()
    expect(profile.fieldErrors.value).toEqual({})
  })
  it('drops a stale server error when its submitted value changes', async () => {
    const form = useFormFeedback({ fields: ['email'] })
    await form.fail(new ApiError(409, { code: 'EMAIL_TAKEN', fieldErrors: { email: '邮箱已被使用' } }))
    expect(form.fieldErrors.value.email).toBeTruthy()
    form.changed('email')
    expect(form.fieldErrors.value.email).toBeUndefined()
  })
  it('does not erase an unresolved local field error when another operation fails', async () => {
    const form = useFormFeedback({ fields: ['email', 'password'], validators: { password: () => '密码至少8个字符' } })
    form.validate(['password'])
    await form.fail(new ApiError(503, { code: 'EMAIL_UNAVAILABLE' }))
    expect(form.fieldErrors.value.password).toBe('密码至少8个字符')
    expect(form.formError.value).toContain('邮件服务')
  })
})

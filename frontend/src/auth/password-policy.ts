/** Mirrors the identity service's 8-64 Unicode character / 72 UTF-8 byte policy. */
export function validatePassword(value: string): string | null {
  if (!hasValidUnicode(value)) return '密码必须由有效字符组成'
  const characters = [...value].length
  if (characters < 8 || characters > 64) return '密码需为8—64个字符'
  if (/[\u0000-\u001f\u007f-\u009f]/u.test(value)) return '密码不能包含控制字符'
  if (new TextEncoder().encode(value).length > 72) return '密码的UTF-8编码不能超过72字节'
  return null
}

export function validateRegistrationUsername(value: string): string | null {
  const username = value.trim()
  if (username.length < 3 || username.length > 32 || !/^[A-Za-z0-9_]+$/.test(username)) {
    return '用户名需为3—32位字母、数字或下划线'
  }
  if (username.toLowerCase().startsWith('admin')) return '以 admin 开头的用户名不可注册'
  return null
}

function hasValidUnicode(value: string): boolean {
  for (let index = 0; index < value.length; index++) {
    const unit = value.charCodeAt(index)
    if (unit >= 0xd800 && unit <= 0xdbff) {
      const next = value.charCodeAt(index + 1)
      if (!(next >= 0xdc00 && next <= 0xdfff)) return false
      index++
    } else if (unit >= 0xdc00 && unit <= 0xdfff) {
      return false
    }
  }
  return true
}

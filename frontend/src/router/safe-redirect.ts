export function safeRedirect(value: unknown, admin: boolean): string {
  const fallback = admin ? '/admin' : '/'
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u0020\u007f]/.test(value)) return fallback
  let url: URL
  try { url = new URL(value, 'https://lingua-loop.invalid') } catch { return fallback }
  if (url.origin !== 'https://lingua-loop.invalid' || url.hash) return fallback
  const numeric = '[1-9][0-9]{0,18}'
  const reading = new RegExp(`^/reading/${numeric}$`).test(url.pathname)
  const preview = admin && new RegExp(`^/admin/articles/${numeric}/(?:preview|writing|quiz)$`).test(url.pathname)
  const allowed = admin ? ['/admin', '/admin/account', '/admin/articles', '/admin/articles/new', '/admin/wall', '/admin/users'].includes(url.pathname) || new RegExp(`^/admin/articles/${numeric}/(?:edit|preview|writing|quiz)$`).test(url.pathname)
    : ['/', '/account', '/reading', '/vocabulary', '/practice', '/stats', '/notebook', '/writing', '/wall'].includes(url.pathname) || reading
  if (!allowed) return fallback
  const seen = new Set<string>()
  for (const [key, part] of url.searchParams) {
    if (seen.has(key)) return fallback
    seen.add(key)
    if ((!reading && !preview) || (preview && key !== 'version')) return fallback
    if (key === 'version' && new RegExp(`^${numeric}$`).test(part)) continue
    if (key === 'sentence' && /^p[0-9]{4}-s[0-9]{4}$/.test(part)) continue
    if ((key === 'start' || key === 'end') && /^(0|[1-9][0-9]{0,3})$/.test(part) && Number(part) <= 2000) continue
    return fallback
  }
  return url.pathname + url.search
}

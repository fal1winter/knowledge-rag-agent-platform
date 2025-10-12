export function normalizeMediaUrl(url, options = {}) {
  const fallback = options.fallback || ''
  if (!url) return fallback
  if (/^https?:\/\//.test(url) || url.startsWith('/')) return url
  return `/${url.replace(/^\/+/, '')}`
}

export function formatSalary(value, fallback = '—') {
  const text = String(value ?? '').trim()
  if (!text) return fallback
  if (!/\d/.test(text) || /^[¥￥$]/.test(text)) return text
  return `¥${text}`
}

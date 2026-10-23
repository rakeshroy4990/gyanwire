/**
 * Allow only http(s) URLs for href binding. Blocks javascript:, data:, etc.
 * @param {unknown} value
 * @returns {string}
 */
export function safeHttpUrl(value) {
  const raw = typeof value === 'string' ? value.trim() : '';
  if (!raw) return '';
  try {
    const parsed = new URL(raw, typeof window !== 'undefined' ? window.location.origin : 'http://localhost');
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return '';
    }
    return parsed.href;
  } catch {
    return '';
  }
}

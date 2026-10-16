import { apiUrl } from './apiBase.js';

async function send(path, options = {}) {
  const res = await fetch(apiUrl(path), {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  });
  const payload = await res.json().catch(() => ({}));
  if (!res.ok || payload.success === false) {
    const error = new Error(payload.message || 'Request failed.');
    error.status = res.status;
    error.code = payload.errorCode;
    error.data = payload.data;
    throw error;
  }
  return payload.data;
}

export function fetchNewsIdeas(newsId) {
  return send(`/api/news/${encodeURIComponent(newsId)}/ideas`, { method: 'POST' });
}

export function rateIdea(ideaId, rating) {
  return send(`/api/ideas/${encodeURIComponent(ideaId)}/rating`, {
    method: 'POST',
    body: JSON.stringify({ rating }),
  });
}

import { apiUrl } from './apiBase.js';
import { readNdjson } from './newsStream.js';

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

export async function fetchNewsIdeas(newsId, onEvent) {
  const res = await fetch(apiUrl(`/api/news/${encodeURIComponent(newsId)}/ideas`), {
    method: 'POST',
    credentials: 'include',
    cache: 'no-store',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/x-ndjson, application/json',
    },
  });
  if (!res.ok) {
    const payload = await res.json().catch(() => ({}));
    const error = new Error(payload.message || 'Request failed.');
    error.status = res.status;
    error.code = payload.errorCode;
    error.data = payload.data;
    throw error;
  }
  const contentType = res.headers.get('content-type') || '';
  if (!contentType.includes('ndjson')) {
    const payload = await res.json().catch(() => ({}));
    if (payload.success === false) {
      const error = new Error(payload.message || 'Request failed.');
      error.status = res.status;
      error.code = payload.errorCode;
      error.data = payload.data;
      throw error;
    }
    return payload.data;
  }
  const frame = { ideas: [] };
  await readNdjson(res, async (event) => {
    if (event?.type === 'error') {
      const error = new Error(event.message || 'Could not build ideas.');
      error.status = Number(event.status) || 500;
      error.code = event.errorCode;
      error.data = event.data;
      throw error;
    }
    if (event?.type === 'status') {
      if (onEvent) await onEvent({ type: 'status', stage: event.stage || '' });
      return;
    }
    if (event?.type === 'meta') {
      const ideas = frame.ideas;
      Object.assign(frame, event);
      frame.ideas = ideas;
      if (onEvent) await onEvent({ type: 'meta', data: frame });
    } else if (event?.type === 'item' && event.item) {
      frame.ideas = [...frame.ideas, event.item];
      if (onEvent) await onEvent({ type: 'item', item: event.item, ideas: frame.ideas });
    } else if (event?.type === 'research') {
      if (onEvent) {
        await onEvent({
          type: 'research',
          status: event.status || '',
          summary: event.summary || null,
          data: event,
        });
      }
    }
  });
  return frame;
}

export function rateIdea(ideaId, rating) {
  return send(`/api/ideas/${encodeURIComponent(ideaId)}/rating`, {
    method: 'POST',
    body: JSON.stringify({ rating }),
  });
}

export function fetchVideoResearch(newsId) {
  return send(`/api/news/${encodeURIComponent(newsId)}/video-research`);
}

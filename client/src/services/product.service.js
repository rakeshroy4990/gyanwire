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
    throw error;
  }
  return payload.data;
}

export function requestSkillPlan(ideaId) {
  return send('/api/plans/skill', { method: 'POST', body: JSON.stringify({ ideaId: ideaId || null }) });
}

export function requestWeeklyPlan(ideaId) {
  return send('/api/plans/weekly', { method: 'POST', body: JSON.stringify({ ideaId: ideaId || null }) });
}

export function requestOutline(ideaId) {
  return send('/api/plans/business-outline', { method: 'POST', body: JSON.stringify({ ideaId: ideaId || null }) });
}

export function outlineDownloadUrl(ideaId) {
  return apiUrl(`/api/plans/business-outline/${ideaId}.md`);
}

export function listProjects() {
  return send('/api/projects');
}

export function createProject(name) {
  return send('/api/projects', { method: 'POST', body: JSON.stringify({ name }) });
}

export function projectExportUrl(id, kind) {
  return apiUrl(`/api/projects/${id}/export.${kind}`);
}

export async function fetchProjectMarkdown(id) {
  const res = await fetch(projectExportUrl(id, 'md'), { credentials: 'include' });
  if (!res.ok) {
    throw new Error('Could not copy that project.');
  }
  return res.text();
}

export function sendFindingFeedback(body) {
  return send('/api/findings/feedback', { method: 'POST', body: JSON.stringify(body) });
}

export function sendNewsFeedback(body) {
  return send('/api/news/feedback', { method: 'POST', body: JSON.stringify(body) });
}

export function dismissNews(body) {
  return send('/api/news/dismiss', { method: 'POST', body: JSON.stringify(body) });
}

export function hideNewsSource(body) {
  return send('/api/news/hide-source', { method: 'POST', body: JSON.stringify(body) });
}

export function requestClaimCheck(sentence) {
  return send('/api/briefs/claim-check', { method: 'POST', body: JSON.stringify({ sentence, passages: [] }) });
}

export function requestIndustryMode(body) {
  return send('/api/industry-mode', { method: 'POST', body: JSON.stringify(body) });
}

export async function requestIdeas(body, onEvent) {
  const res = await fetch(apiUrl('/api/ideas/from-news'), {
    method: 'POST',
    credentials: 'include',
    cache: 'no-store',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/x-ndjson, application/json',
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const payload = await res.json().catch(() => ({}));
    const error = new Error(payload.message || 'Request failed.');
    error.status = res.status;
    error.code = payload.errorCode;
    throw error;
  }
  const contentType = res.headers.get('content-type') || '';
  if (!contentType.includes('ndjson')) {
    const payload = await res.json().catch(() => ({}));
    if (payload.success === false) {
      const error = new Error(payload.message || 'Request failed.');
      error.status = res.status;
      error.code = payload.errorCode;
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
      throw error;
    }
    if (event?.type === 'meta') {
      const ideas = frame.ideas;
      Object.assign(frame, event);
      frame.ideas = ideas;
      if (onEvent) await onEvent({ type: 'meta', data: frame });
    } else if (event?.type === 'item' && event.item) {
      frame.ideas = [...frame.ideas, event.item];
      if (onEvent) await onEvent({ type: 'item', item: event.item, ideas: frame.ideas });
    }
  });
  return frame;
}

export function requestLaunchPlan(ideaId) {
  return send(`/api/ideas/${ideaId}/launch-plan`, { method: 'POST', body: JSON.stringify({}) });
}

export function saveIdea(ideaId) {
  return send(`/api/ideas/${ideaId}/feedback`, {
    method: 'POST',
    body: JSON.stringify({ feedback: 1 }),
  });
}

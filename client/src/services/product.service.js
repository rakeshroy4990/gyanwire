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

export function requestClaimCheck(sentence) {
  return send('/api/briefs/claim-check', { method: 'POST', body: JSON.stringify({ sentence, passages: [] }) });
}

export function requestIndustryMode(body) {
  return send('/api/industry-mode', { method: 'POST', body: JSON.stringify(body) });
}

export function requestIdeas(body) {
  return send('/api/ideas/from-news', { method: 'POST', body: JSON.stringify(body) });
}

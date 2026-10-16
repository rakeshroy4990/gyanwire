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

export function createActionPlan(body) {
  return send('/api/action-plans', { method: 'POST', body: JSON.stringify(body || {}) });
}

export function fetchActionPlan(id) {
  return send(`/api/action-plans/${id}`);
}

export function toggleActionPlanStep(id, stepKey, done) {
  return send(`/api/action-plans/${id}/steps/${encodeURIComponent(stepKey)}`, {
    method: 'PATCH',
    body: JSON.stringify({ done: !!done }),
  });
}

export function fetchStepDetail(planId, stepKey, path = 'open_source') {
  const q = new URLSearchParams();
  if (path) q.set('path', path === 'mix' ? 'open_source' : path);
  const qs = q.toString();
  return send(
    `/api/action-plans/${planId}/steps/${encodeURIComponent(stepKey)}${qs ? `?${qs}` : ''}`,
  );
}

export function postTiming(planId, stepKey, body) {
  return send(`/api/action-plans/${planId}/steps/${encodeURIComponent(stepKey)}/timing`, {
    method: 'POST',
    body: JSON.stringify(body || {}),
  });
}

export function listMyActionPlans() {
  return send('/api/action-plans?mine=true');
}

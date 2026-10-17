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

export async function createActionPlan(body, onEvent) {
  const res = await fetch(apiUrl('/api/action-plans'), {
    method: 'POST',
    credentials: 'include',
    cache: 'no-store',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/x-ndjson, application/json',
    },
    body: JSON.stringify(body || {}),
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

  let lastPlan = null;
  await readNdjson(res, async (event) => {
    if (event?.type === 'error') {
      const error = new Error(event.message || 'Could not build the plan.');
      error.status = Number(event.status) || 500;
      error.code = event.errorCode;
      error.data = event.data;
      throw error;
    }
    if (event?.type === 'status') {
      if (onEvent) await onEvent({ type: 'status', stage: event.stage || '' });
      return;
    }
    if (event?.type === 'plan' && event.plan) {
      lastPlan = event.plan;
      if (onEvent) await onEvent({ type: 'plan', plan: event.plan });
    }
  });
  return lastPlan;
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

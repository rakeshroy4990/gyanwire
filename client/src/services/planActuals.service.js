import { apiUrl } from './apiBase.js';

export async function submitPlanActual({ ideaId, weekNo, optionId, taskType, hoursActual, baselineHours }) {
  const res = await fetch(apiUrl('/api/plan/actuals'), {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      ideaId,
      weekNo,
      optionId,
      taskType,
      hoursActual,
      baselineHours,
    }),
  });
  const payload = await res.json().catch(() => ({}));
  if (!res.ok || !payload?.success) {
    const error = new Error(payload?.message || 'Could not save hours.');
    error.status = res.status;
    error.code = payload?.errorCode || 'ACTUALS_FAILED';
    throw error;
  }
  return payload.data;
}

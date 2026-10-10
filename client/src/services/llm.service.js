import { apiUrl } from './apiBase.js';

async function parseJson(res) {
  try {
    return await res.json();
  } catch {
    return null;
  }
}

export async function fetchLlmSummary() {
  const res = await fetch(apiUrl('/api/admin/llm/summary'), {
    method: 'GET',
    credentials: 'include',
  });
  const payload = await parseJson(res);
  if (!res.ok || !payload?.success) {
    const error = new Error(payload?.message || 'Could not load LLM spend.');
    error.status = res.status;
    throw error;
  }
  return payload.data;
}

export async function fetchWhatIfText(costDelta, hoursDelta) {
  const res = await fetch(apiUrl('/api/plan/whatif-text'), {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ costDelta, hoursDelta }),
  });
  const payload = await parseJson(res);
  if (!res.ok || !payload?.success) {
    return '';
  }
  return payload.data?.text || '';
}

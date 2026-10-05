import { apiUrl } from './apiBase.js';

async function parseJson(res) {
  try {
    return await res.json();
  } catch {
    return null;
  }
}

export async function fetchUsage() {
  const res = await fetch(apiUrl('/api/me/usage'), {
    method: 'GET',
    credentials: 'include',
  });
  const payload = await parseJson(res);
  if (!res.ok || !payload?.success) {
    throw new Error(payload?.message || 'Could not load usage.');
  }
  return payload.data;
}

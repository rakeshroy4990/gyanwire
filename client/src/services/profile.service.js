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

export function fetchProfile() {
  return send('/api/me/profile');
}

export function saveProfile(body) {
  return send('/api/me/profile', { method: 'PUT', body: JSON.stringify(body) });
}

export function deleteProfile() {
  return send('/api/me/profile/data', { method: 'DELETE' });
}

export function fetchReferral() {
  return send('/api/me/referral');
}

export function redeemReferral(code) {
  return send('/api/referrals/redeem', { method: 'POST', body: JSON.stringify({ code }) });
}

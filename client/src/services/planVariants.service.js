import { apiUrl } from './apiBase.js';
import { idbDelete, idbGet, idbPut, STORE_VARIANTS } from './indexedDb.js';

function keyFor(ideaId) {
  return `idea:${ideaId}`;
}

function normalizeVariant(v) {
  if (!v || typeof v !== 'object') return null;
  return {
    id: String(v.id),
    name: String(v.name || 'Variant'),
    createdAt: v.createdAt || new Date().toISOString(),
    ideaId: String(v.ideaId || ''),
    weekOptions: v.weekOptions && typeof v.weekOptions === 'object' ? { ...v.weekOptions } : {},
    hourlyValueInr: v.hourlyValueInr == null ? null : Number(v.hourlyValueInr),
  };
}

export async function loadVariants(ideaId) {
  if (!ideaId) return { variants: [], activeId: null };
  try {
    const row = await idbGet(STORE_VARIANTS, keyFor(ideaId));
    const variants = Array.isArray(row?.variants)
      ? row.variants.map(normalizeVariant).filter(Boolean)
      : [];
    return { variants, activeId: row?.activeId || variants[0]?.id || null };
  } catch {
    return { variants: [], activeId: null };
  }
}

export async function saveVariants(ideaId, variants, activeId) {
  if (!ideaId) return;
  await idbPut(STORE_VARIANTS, {
    key: keyFor(ideaId),
    variants: variants.map(normalizeVariant).filter(Boolean),
    activeId: activeId || null,
    savedAt: new Date().toISOString(),
  });
}

export async function clearVariants(ideaId) {
  if (!ideaId) return;
  await idbDelete(STORE_VARIANTS, keyFor(ideaId));
}

export async function meterVariantCount(count) {
  const res = await fetch(apiUrl('/api/plan/variants/meter'), {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ count }),
  });
  const payload = await res.json().catch(() => ({}));
  if (!res.ok || !payload?.success) {
    const error = new Error(payload?.message || 'Variant limit reached.');
    error.status = res.status;
    error.code = payload?.errorCode || 'LIMIT_REACHED';
    error.data = payload?.data || {};
    throw error;
  }
  return payload.data;
}

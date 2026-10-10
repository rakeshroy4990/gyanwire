import { apiUrl } from './apiBase.js';
import { idbGet, idbPut, STORE_OPTIONS } from './indexedDb.js';

const CACHE_KEY = 'catalog';

function normalizeOption(item) {
  if (!item || typeof item.id !== 'string') return null;
  return {
    id: item.id,
    name: item.name || item.id,
    kind: item.kind || 'tool',
    monthlyInr: Number(item.monthlyInr || 0),
    costInr: Number(item.costInr || 0),
    billing: item.billing || 'free',
    pricedAt: item.pricedAt || null,
    stale: Boolean(item.stale),
    taskFit: item.taskFit && typeof item.taskFit === 'object' ? item.taskFit : {},
    freeAlternativeId: item.freeAlternativeId || null,
    bucket: item.bucket || null,
    sampleCount: Number(item.sampleCount || 0),
    skills: Array.isArray(item.skills) ? item.skills : [],
    url: item.url ? String(item.url) : '',
    blurb: item.blurb ? String(item.blurb) : '',
  };
}

function normalizeCatalog(rows) {
  if (!Array.isArray(rows)) return [];
  return rows.map(normalizeOption).filter(Boolean);
}

export async function readOptionsFromIndexedDb() {
  try {
    const row = await idbGet(STORE_OPTIONS, CACHE_KEY);
    const catalog = normalizeCatalog(row?.data);
    return catalog.length ? catalog : null;
  } catch {
    return null;
  }
}

export async function writeOptionsToIndexedDb(catalog) {
  const data = normalizeCatalog(catalog);
  if (!data.length) return;
  await idbPut(STORE_OPTIONS, {
    key: CACHE_KEY,
    data,
    cachedAt: new Date().toISOString(),
  });
}

async function fetchOptionsFromServer(taskType) {
  const q = taskType ? `?taskType=${encodeURIComponent(taskType)}` : '';
  const res = await fetch(apiUrl(`/api/options${q}`), { credentials: 'include' });
  const payload = await res.json().catch(() => ({}));
  if (!res.ok || !payload?.success) {
    const error = new Error(payload?.message || 'Failed to load options.');
    error.status = res.status;
    error.code = payload?.errorCode || 'OPTIONS_FETCH_FAILED';
    throw error;
  }
  return normalizeCatalog(payload.data?.options || payload.data || []);
}

let inflightLoad = null;

export async function loadOptionCatalog(taskType) {
  if (!taskType && inflightLoad) {
    return inflightLoad;
  }

  const run = (async () => {
    if (!taskType) {
      const cached = await readOptionsFromIndexedDb();
      if (cached?.length) {
        return { catalog: cached, source: 'indexeddb' };
      }
    }
    const catalog = await fetchOptionsFromServer(taskType);
    if (!taskType && catalog.length) {
      try {
        await writeOptionsToIndexedDb(catalog);
      } catch {
        // proceed in-memory
      }
    }
    return { catalog, source: 'network' };
  })();

  if (!taskType) {
    inflightLoad = run;
  }
  try {
    return await run;
  } finally {
    if (!taskType) {
      inflightLoad = null;
    }
  }
}

export async function ensureOptionsCached() {
  try {
    await loadOptionCatalog();
  } catch {
    // non-fatal warm
  }
}

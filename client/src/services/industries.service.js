import { apiUrl } from './apiBase.js';
import { idbGet, idbPut, STORE_INDUSTRIES } from './indexedDb.js';

const CACHE_KEY = 'catalog';

function normalizeCatalog(rows) {
  if (!Array.isArray(rows) || !rows.length) return [];
  return rows
    .filter((item) => item && typeof item.name === 'string' && item.name.trim())
    .map((item) => ({
      externalId: item.externalId || item.external_id || null,
      name: item.name,
      label: item.label || item.name,
      subs: Array.isArray(item.subs) ? item.subs.filter((s) => typeof s === 'string') : [],
    }));
}

export async function readIndustriesFromIndexedDb() {
  try {
    const row = await idbGet(STORE_INDUSTRIES, CACHE_KEY);
    const catalog = normalizeCatalog(row?.data);
    return catalog.length ? catalog : null;
  } catch {
    return null;
  }
}

export async function writeIndustriesToIndexedDb(catalog) {
  const data = normalizeCatalog(catalog);
  if (!data.length) return;
  await idbPut(STORE_INDUSTRIES, {
    key: CACHE_KEY,
    data,
    cachedAt: new Date().toISOString(),
  });
}

async function fetchIndustriesFromServer() {
  const res = await fetch(apiUrl('/api/industries'), { credentials: 'include' });
  const payload = await res.json().catch(() => ({}));
  if (!res.ok || !payload?.success) {
    const error = new Error(payload?.message || 'Failed to load research industries.');
    error.status = res.status;
    error.code = payload?.errorCode || 'INDUSTRIES_FETCH_FAILED';
    throw error;
  }
  return normalizeCatalog(payload.data);
}

let inflightLoad = null;

/**
 * IndexedDB-first catalog loader.
 * - If IndexedDB has industries, return them and do not call the server.
 * - Otherwise fetch from gyanwire-server (via /api/industries), persist, return.
 */
export async function loadResearchIndustries() {
  if (inflightLoad) {
    return inflightLoad;
  }

  inflightLoad = (async () => {
    const cached = await readIndustriesFromIndexedDb();
    if (cached?.length) {
      return { catalog: cached, source: 'indexeddb' };
    }

    const catalog = await fetchIndustriesFromServer();
    if (catalog.length) {
      try {
        await writeIndustriesToIndexedDb(catalog);
      } catch {
        // UI can still proceed with in-memory catalog.
      }
    }
    return { catalog, source: 'network' };
  })();

  try {
    return await inflightLoad;
  } finally {
    inflightLoad = null;
  }
}

/**
 * App-startup warm cache: only hits the network when IndexedDB is empty.
 */
export async function ensureIndustriesCached() {
  try {
    return await loadResearchIndustries();
  } catch {
    return { catalog: [], source: 'error' };
  }
}

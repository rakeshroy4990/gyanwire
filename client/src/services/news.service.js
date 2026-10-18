import { apiUrl } from './apiBase.js';
import { readNdjson } from './newsStream.js';

const PAGE_SIZE = 10;
/** Server widens 1d → 7d → 30d in memory; client asks once for the month. */
const INDUSTRY_WINDOWS = [1, 7, 30];
const WARMING_RETRY_MS = 1500;

function buildQuery({ limit = PAGE_SIZE, page = 1, sub = null, country = null, windowDays = 30 } = {}) {
  const params = new URLSearchParams();
  params.set('limit', String(limit));
  params.set('page', String(page));
  params.set('intent', 'products');
  params.set('window', `${windowDays}d`);
  if (sub) params.set('sub', sub);
  if (country) params.set('country', country);
  return params.toString();
}

function sleep(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(Object.assign(new Error('Aborted'), { name: 'AbortError' }));
      return;
    }
    const timer = setTimeout(resolve, ms);
    const onAbort = () => {
      clearTimeout(timer);
      reject(Object.assign(new Error('Aborted'), { name: 'AbortError' }));
    };
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

function resultCount(data) {
  if (Array.isArray(data?.results)) return data.results.length;
  if (Array.isArray(data?.items)) return data.items.length;
  const returned = Number(data?.returned);
  return Number.isFinite(returned) ? returned : 0;
}

async function parseJson(res) {
  const payload = await res.json().catch(() => ({}));
  if (!res.ok || payload.success === false) {
    const error = new Error(payload.message || 'Could not load product news.');
    error.status = res.status;
    error.code = payload.errorCode;
    throw error;
  }
  return payload.data || {};
}

async function readNewsResponse(res, { wantStream, onItem } = {}) {
  if (!res.ok) {
    return parseJson(res);
  }
  const contentType = res.headers.get('content-type') || '';
  if (wantStream && contentType.includes('ndjson')) {
    let frame = {};
    await readNdjson(res, async (event) => {
      if (event?.type === 'meta') {
        frame = event;
      } else if (event?.type === 'item') {
        if (typeof onItem === 'function') {
          await onItem(event.item);
        }
      } else if (event?.type === 'error') {
        throw new Error(event.message || 'Could not load product news.');
      } else if (event?.type === 'done') {
        frame = { ...frame, returned: event.returned };
      }
    });
    return frame;
  }
  return parseJson(res);
}

/** Industry browse for one page (newest first). Page 1 streams NDJSON when onItem is set. */
export async function fetchIndustryNews({
  industry,
  sub = null,
  country = null,
  page = 1,
  limit = PAGE_SIZE,
  windowDays = 30,
  signal,
  onItem,
} = {}) {
  const qs = buildQuery({ limit, page, sub, country, windowDays });
  const wantStream = page === 1 && typeof onItem === 'function';
  const res = await fetch(apiUrl(`/api/news/${encodeURIComponent(industry)}?${qs}`), {
    credentials: 'include',
    cache: 'no-store',
    signal,
    headers: { Accept: wantStream ? 'application/x-ndjson, application/json' : 'application/json' },
  });
  return readNewsResponse(res, { wantStream, onItem });
}

/**
 * Page 1: one 30d request (server widens 1d → 7d → 30d in memory).
 * Retries once when the feed is still warming up with no rows.
 */
export async function fetchIndustryNewsChunks({
  industry,
  sub = null,
  country = null,
  page = 1,
  limit = PAGE_SIZE,
  signal,
  onItem,
  onChunk,
} = {}) {
  if (page > 1) {
    return fetchIndustryNews({
      industry,
      sub,
      country,
      page,
      limit,
      windowDays: 30,
      signal,
    });
  }

  const load = () => fetchIndustryNews({
    industry,
    sub,
    country,
    page: 1,
    limit,
    windowDays: 30,
    signal,
    onItem,
  });

  let data = await load();
  if (data?.freshness === 'warming_up' && resultCount(data) === 0 && !signal?.aborted) {
    // Let the UI show the fetching banner once, then retry after ingest has a moment.
    if (typeof onChunk === 'function') {
      await onChunk(data, Number(data.windowDays) || 30);
    }
    await sleep(WARMING_RETRY_MS, signal);
    data = await load();
  }

  // Still empty after retry: settle as a real empty month, not perpetual "fetching".
  if (data?.freshness === 'warming_up' && resultCount(data) === 0) {
    data = {
      ...data,
      freshness: 'last 30 days',
      windowDays: Number(data.windowDays) > 0 ? Number(data.windowDays) : 30,
      message: data.message || 'No product news yet.',
    };
  }

  if (typeof onChunk === 'function') {
    await onChunk(data, Number(data.windowDays) || 30);
  }
  return data;
}

/** Default pool browse (JSON). Falls back to NDJSON stream for page 1 when preferred. */
export async function fetchDefaultNews({
  country = null,
  page = 1,
  limit = PAGE_SIZE,
  signal,
  onItem,
} = {}) {
  const params = new URLSearchParams();
  params.set('limit', String(limit));
  params.set('page', String(page));
  params.set('intent', 'products');
  if (country) params.set('country', country);

  const wantStream = page === 1 && typeof onItem === 'function';
  const res = await fetch(apiUrl(`/api/news/default?${params}`), {
    credentials: 'include',
    cache: 'no-store',
    signal,
    headers: { Accept: wantStream ? 'application/x-ndjson, application/json' : 'application/json' },
  });
  return readNewsResponse(res, { wantStream, onItem });
}

export { PAGE_SIZE, INDUSTRY_WINDOWS, WARMING_RETRY_MS };

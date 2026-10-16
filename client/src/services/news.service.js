import { apiUrl } from './apiBase.js';
import { readNdjson } from './newsStream.js';

const PAGE_SIZE = 10;

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

/** Industry browse: last 30 days, newest first, paginated. */
export async function fetchIndustryNews({
  industry,
  sub = null,
  country = null,
  page = 1,
  limit = PAGE_SIZE,
  signal,
} = {}) {
  const qs = buildQuery({ limit, page, sub, country });
  const res = await fetch(apiUrl(`/api/news/${encodeURIComponent(industry)}?${qs}`), {
    credentials: 'include',
    cache: 'no-store',
    signal,
    headers: { Accept: 'application/json' },
  });
  return parseJson(res);
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
        await onItem(event.item);
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

export { PAGE_SIZE };

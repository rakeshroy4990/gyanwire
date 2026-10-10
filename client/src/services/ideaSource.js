const STORAGE_KEY = 'gyanwire.ideaSources.v1';

function readAll() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function writeAll(map) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(map));
  } catch {
    // Quota or private mode — in-memory navigation still works for this page load.
  }
}

async function shortKey(url) {
  const text = String(url || '');
  if (typeof crypto !== 'undefined' && crypto.subtle) {
    const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
    return [...new Uint8Array(buf)]
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('')
      .slice(0, 16);
  }
  let h = 2166136261;
  for (let i = 0; i < text.length; i += 1) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return (h >>> 0).toString(16).padStart(8, '0');
}

function normalize(source = {}) {
  const ideaCount = Number(source.ideaCount);
  return {
    url: String(source.url || '').trim(),
    title: String(source.title || '').trim(),
    description: String(source.description || '').trim().slice(0, 1500),
    industry: String(source.industry || '').trim(),
    ideaCount: Number.isFinite(ideaCount) && ideaCount > 0 ? Math.min(8, Math.round(ideaCount)) : 0,
    signalType: String(source.signalType || '').trim(),
    whyIdea: String(source.whyIdea || '').trim(),
  };
}

/** Persist finding fields and return a short route key. */
export async function rememberIdeaSource(source) {
  const payload = normalize(source);
  if (!payload.url) return '';
  const src = await shortKey(payload.url);
  const all = readAll();
  all[src] = payload;
  writeAll(all);
  return src;
}

export function loadIdeaSource(src) {
  if (!src) return null;
  const row = readAll()[src];
  if (!row || typeof row !== 'object') return null;
  const payload = normalize(row);
  return payload.url ? payload : null;
}

/**
 * Resolve from ?src=… (preferred) or legacy ?url=&title=&… query params.
 */
export function resolveIdeaSource(query = {}) {
  if (typeof query.src === 'string' && query.src) {
    const stored = loadIdeaSource(query.src);
    if (stored) return { src: query.src, ...stored };
  }
  if (typeof query.url === 'string' && query.url.trim()) {
    return {
      src: '',
      ...normalize({
        url: query.url,
        title: query.title,
        description: query.description,
        industry: query.industry,
        ideaCount: query.ideaCount,
        signalType: query.signalType,
        whyIdea: query.whyIdea,
      }),
    };
  }
  return null;
}

/** Query object for idea/plan navigation — short key only when available. */
export function ideaSourceRouteQuery(source) {
  if (source?.src) return { src: source.src };
  if (source?.url) {
    return {
      url: source.url,
      title: source.title || '',
      description: source.description || '',
      industry: source.industry || '',
    };
  }
  return {};
}

import { withoutFyiNews } from './newsFyi.js';

const WORKSPACE_KEY = 'gyanwire.researchWorkspace.v1';
const RETURN_KEY = 'gyanwire.researchReturn.v1';
const HOME_KEY = 'gyanwire.researchHome.v1';

function readJson(key) {
  try {
    const raw = sessionStorage.getItem(key);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch {
    return null;
  }
}

function writeJson(key, value) {
  try {
    sessionStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Quota or private mode — this tab can still keep the in-memory workspace.
  }
}

function cssAttr(value) {
  const text = String(value);
  if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') return CSS.escape(text);
  return text.replace(/["\\]/g, '\\$&');
}

function settledThread(thread) {
  if (!Array.isArray(thread)) return [];
  return thread.map((turn) => {
    if (!turn || typeof turn !== 'object') return turn;
    if (turn.status !== 'streaming' && turn.status !== 'pending') return turn;
    const results = Array.isArray(turn.results) ? turn.results : [];
    return {
      ...turn,
      status: results.length ? 'done' : 'error',
      error: results.length ? '' : (turn.error || 'That search was interrupted.'),
    };
  });
}

/** Last research selection for this tab, so Back can restore it after the page unmounts. */
export function loadResearchWorkspace() {
  const saved = readJson(WORKSPACE_KEY);
  if (!saved || typeof saved.industry !== 'string' || !saved.industry) return null;
  const browseResults = withoutFyiNews(Array.isArray(saved.browseResults) ? saved.browseResults : []);
  const thread = settledThread(saved.thread);
  return {
    industry: saved.industry,
    subcategory: typeof saved.subcategory === 'string' && saved.subcategory ? saved.subcategory : null,
    thoughts: typeof saved.thoughts === 'string' ? saved.thoughts : '',
    thread,
    browseResults,
    browseSub: typeof saved.browseSub === 'string' ? saved.browseSub : '',
    browseFreshness: typeof saved.browseFreshness === 'string' ? saved.browseFreshness : '',
    browseNote: typeof saved.browseNote === 'string' ? saved.browseNote : '',
    emptyMessage: typeof saved.emptyMessage === 'string' ? saved.emptyMessage : '',
    showEmpty: typeof saved.showEmpty === 'boolean' ? saved.showEmpty : browseResults.length === 0 && thread.length === 0,
    followProfile: saved.followProfile !== false,
    country: typeof saved.country === 'string' && /^[A-Za-z]{2}$/.test(saved.country)
      ? saved.country.toUpperCase()
      : null,
    browseCountries: Array.isArray(saved.browseCountries) ? saved.browseCountries : [],
  };
}

export function saveResearchWorkspace(state = {}) {
  writeJson(WORKSPACE_KEY, {
    industry: state.industry || 'All',
    subcategory: state.subcategory || null,
    thoughts: state.thoughts || '',
    thread: state.thread || [],
    browseResults: state.browseResults || [],
    browseSub: state.browseSub || '',
    browseFreshness: state.browseFreshness || '',
    browseNote: state.browseNote || '',
    emptyMessage: state.emptyMessage || '',
    showEmpty: Boolean(state.showEmpty),
    followProfile: state.followProfile !== false,
    country: typeof state.country === 'string' ? state.country : null,
    browseCountries: state.browseCountries || [],
  });
}

/** Logo home asked for a fresh feed. Consumed on the next research mount. */
export function markResearchHome() {
  writeJson(HOME_KEY, { at: Date.now() });
}

export function consumeResearchHome() {
  const marked = Boolean(readJson(HOME_KEY));
  if (!marked) return false;
  try {
    sessionStorage.removeItem(HOME_KEY);
  } catch {
    // ignore
  }
  return true;
}

/** Finding the user opened with Get an idea, plus the scroll position they left. */
export function rememberResearchReturn(item, scrollY = 0) {
  const url = String(item?.url || '').trim();
  const id = item?.id == null || item.id === '' ? '' : String(item.id);
  if (!url && !id) return;
  const y = Number(scrollY);
  writeJson(RETURN_KEY, {
    url,
    id,
    scrollY: Number.isFinite(y) && y > 0 ? y : 0,
  });
}

export function loadResearchReturn() {
  const saved = readJson(RETURN_KEY);
  if (!saved) return null;
  const url = typeof saved.url === 'string' ? saved.url : '';
  const id = typeof saved.id === 'string' ? saved.id : '';
  const scrollY = Number(saved.scrollY);
  if (!url && !id) return null;
  return {
    url,
    id,
    scrollY: Number.isFinite(scrollY) && scrollY > 0 ? scrollY : 0,
  };
}

export function hasResearchReturn() {
  return Boolean(loadResearchReturn());
}

export function clearResearchReturn() {
  try {
    sessionStorage.removeItem(RETURN_KEY);
  } catch {
    // ignore
  }
}

export function findingSelector(target) {
  if (!target) return '';
  if (target.id) return `[data-finding-id="${cssAttr(target.id)}"]`;
  if (target.url) return `[data-finding-url="${cssAttr(target.url)}"]`;
  return '';
}

export function findingElement(target, root = typeof document !== 'undefined' ? document : null) {
  const selector = findingSelector(target);
  if (!selector || !root || typeof root.querySelector !== 'function') return null;
  return root.querySelector(selector);
}

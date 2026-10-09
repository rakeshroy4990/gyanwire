import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useUiStore } from '../stores/ui.store.js';
import { apiUrl } from '../services/apiBase.js';
import { loadResearchIndustries } from '../services/industries.service.js';
import { useUsage } from './useUsage.js';

const FALLBACK_CATALOG = [
  {
    name: 'Share Market',
    subs: ['IT', 'EV', 'Space', 'Pharma', 'Banking', 'Energy'],
  },
  {
    name: 'IT',
    subs: ['AI', 'Semiconductors', 'Cybersecurity', 'Cloud'],
  },
  {
    name: 'Medical',
    subs: ['Paediatrics', 'Cardiology', 'Gynecology', 'Oncology', 'Mental Health', 'Dermatology', 'Autism'],
  },
  {
    name: 'Space',
    subs: ['Satellites', 'Launch', 'Commercial Space', 'Lunar'],
  },
  {
    name: 'Social Media',
    subs: ['Short Video', 'Social Commerce', 'Messaging', 'Creators'],
  },
  {
    name: 'Gaming',
    subs: ['Mobile', 'Esports', 'Console', 'PC Gaming', 'Game Dev'],
  },
  {
    name: 'Astrology',
    subs: ['Horoscope', 'Vedic', 'Tarot', 'Numerology', 'Vastu'],
  },
];

function hostname(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return url;
  }
}

function mapResults(payload) {
  const list = Array.isArray(payload?.results) ? payload.results : [];
  return list.map((item) => ({
    ...item,
    host: hostname(item.url),
  }));
}

async function readSearchStream(response, turnId, scope, usage, thread) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const chunks = buffer.split('\n\n');
    buffer = chunks.pop() || '';
    for (const chunk of chunks) {
      const name = chunk.match(/^event:\s*(.*)$/m)?.[1]?.trim() || 'message';
      const dataText = chunk.split('\n').filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trim()).join('\n');
      if (!dataText) continue;
      const data = JSON.parse(dataText);
      const turn = thread.value.find((item) => item.id === turnId);
      if (!turn) continue;
      if (name === 'status') {
        turn.status = 'streaming';
        turn.resultsSub = `${data.stage || 'working'} · ${scope}`;
      } else if (name === 'finding') {
        turn.status = 'streaming';
        const row = { ...data, host: hostname(data.url) };
        if (!turn.results.some((item) => item.url === row.url)) {
          turn.results = [...turn.results, row];
        }
      } else if (name === 'done') {
        turn.status = 'done';
        turn.results = mapResults(data);
        turn.brief = data.brief || null;
        turn.disclaimer = data.disclaimer || '';
        turn.resultsSub = `${turn.results.length} R&D findings · ${scope}`;
        usage.applySearchUsage(data.usage);
      } else if (name === 'error') {
        throw new Error(data.message || 'Research failed.');
      }
    }
  }
}

function makeId() {
  return `turn_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

export function useResearch() {
  const ui = useUiStore();
  const { homeNonce } = storeToRefs(ui);
  const usage = useUsage();
  const catalog = ref(FALLBACK_CATALOG);
  const industry = ref('Share Market');
  const subcategory = ref(null);
  const thoughts = ref('');
  const formError = ref('');
  const isLoading = ref(false);
  /** Chat turns: each research question keeps its findings underneath. */
  const thread = ref([]);
  /** Browse-mode product news (shown only when the chat thread is empty). */
  const browseResults = ref([]);
  const browseSub = ref('');
  const emptyMessage = ref('Pick an industry for product news, or start a research chat.');
  const showEmpty = ref(true);
  let newsRequestId = 0;

  const subs = computed(() => {
    return catalog.value.find((item) => item.name === industry.value)?.subs || [];
  });

  const hasThread = computed(() => thread.value.length > 0);

  const showBrowseResults = computed(() => !hasThread.value && browseResults.value.length > 0);

  const queryPreview = computed(() => {
    const text = thoughts.value.trim();
    if (!industry.value || text.length < 8) return '';
    const cleaned = text
      .replace(/[^\p{L}\p{N}\s'-]/gu, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    const scope = subcategory.value
      ? `${industry.value} · ${subcategory.value}`
      : industry.value;
    return `R&D · ${scope} · ${cleaned.split(' ').slice(0, 8).join(' ')}`
      .trim()
      .slice(0, 120);
  });

  function scrollComposerIntoView() {
    nextTick(() => {
      document.getElementById('research-thoughts')?.focus({ preventScroll: true });
      document.getElementById('research-chat')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  }

  function scrollTurnIntoView(turnId) {
    nextTick(() => {
      document.getElementById(`turn-${turnId}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });
  }

  async function loadNewsFeed({ industryName = null, sub = null } = {}) {
    // News browsing does not clear an active research chat.
    if (hasThread.value) return;

    const requestId = ++newsRequestId;
    isLoading.value = true;
    browseResults.value = [];
    browseSub.value = sub || industryName || 'Loading…';
    showEmpty.value = false;

    try {
      let path = '/api/news/default';
      if (industryName) {
        path = `/api/news/${encodeURIComponent(industryName)}`;
        if (sub) path += `?sub=${encodeURIComponent(sub)}`;
      }
      const res = await fetch(apiUrl(path), { credentials: 'include' });
      const payload = await res.json();
      if (requestId !== newsRequestId) return;
      if (!res.ok || !payload.success) {
        throw new Error(payload.message || 'Could not load product news.');
      }
      const label = payload.data?.label || industryName || 'Product news';
      const scope = sub ? `${industryName} · ${sub}` : industryName;
      browseResults.value = mapResults(payload.data);
      browseSub.value = industryName
        ? `5 latest ${label}${sub ? ` · ${scope}` : ''}`
        : 'Top searched product news';
      showEmpty.value = browseResults.value.length === 0;
      emptyMessage.value = browseResults.value.length
        ? 'Pick an industry for product news, or start a research chat.'
        : 'No product news yet.';
    } catch (error) {
      if (requestId !== newsRequestId) return;
      browseResults.value = [];
      browseSub.value = '';
      showEmpty.value = true;
      emptyMessage.value = error.message || 'Product news will appear here.';
    } finally {
      if (requestId === newsRequestId) {
        isLoading.value = false;
      }
    }
  }

  function selectIndustry(name) {
    if (industry.value === name) {
      industry.value = null;
      subcategory.value = null;
      formError.value = '';
      if (!hasThread.value) loadNewsFeed();
      return;
    }
    industry.value = name;
    subcategory.value = null;
    formError.value = '';
    if (!hasThread.value) loadNewsFeed({ industryName: name });
  }

  function selectSub(name) {
    if (!industry.value) return;
    if (subcategory.value === name) {
      subcategory.value = null;
      if (!hasThread.value) loadNewsFeed({ industryName: industry.value });
      return;
    }
    subcategory.value = name;
    formError.value = '';
    if (!hasThread.value) loadNewsFeed({ industryName: industry.value, sub: name });
  }

  async function runSearch() {
    formError.value = '';
    const text = thoughts.value.trim();
    if (!industry.value) {
      formError.value = 'Pick a research industry.';
      return;
    }
    if (text.length < 8) {
      formError.value = 'Add a clearer research question.';
      return;
    }

    const turnId = makeId();
    const scope = subcategory.value
      ? `${industry.value} · ${subcategory.value}`
      : industry.value;

    thread.value.push({
      id: turnId,
      userText: text,
      industry: industry.value,
      subcategory: subcategory.value,
      status: 'pending',
      results: [],
      resultsSub: `Researching · ${scope}`,
      error: '',
      createdAt: Date.now(),
    });

    thoughts.value = '';
    browseResults.value = [];
    showEmpty.value = false;
    isLoading.value = true;
    usage.clearLimitPrompt();
    scrollTurnIntoView(turnId);

    const searchBody = {
      categories: [industry.value],
      subcategory: subcategory.value,
      thoughts: text,
      limit: 6,
    };

    try {
      let streamed = false;
      try {
        const stream = await fetch(apiUrl('/api/search/stream'), {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
          body: JSON.stringify(searchBody),
        });
        if (stream.status === 402) {
          const payload = await stream.json();
          usage.handleLimitError(payload);
          throw new Error(payload?.message || 'Daily search limit reached.');
        }
        if (stream.ok && stream.body) {
          await readSearchStream(stream, turnId, scope, usage, thread);
          streamed = true;
        }
      } catch (streamError) {
        if (streamError?.message?.includes('limit')) throw streamError;
        streamed = false;
      }

      if (!streamed) {
        const res = await fetch(apiUrl('/api/search'), {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(searchBody),
        });
        const payload = await res.json();
        if (res.status === 402 || payload?.errorCode === 'LIMIT_REACHED') {
          usage.handleLimitError(payload);
          throw new Error(payload?.message || 'Daily search limit reached.');
        }
        if (!res.ok || !payload.success) {
          throw new Error(payload.message || 'Research failed.');
        }
        usage.applySearchUsage(payload.data?.usage);
        const turn = thread.value.find((item) => item.id === turnId);
        if (turn) {
          turn.status = 'done';
          turn.results = mapResults(payload.data);
          turn.brief = payload.data?.brief || null;
          turn.disclaimer = payload.data?.disclaimer || '';
          turn.resultsSub = `${turn.results.length} R&D findings · ${scope}`;
          turn.error = '';
        }
      }
      scrollTurnIntoView(turnId);
      scrollComposerIntoView();
    } catch (error) {
      const turn = thread.value.find((item) => item.id === turnId);
      if (turn) {
        turn.status = 'error';
        turn.results = [];
        turn.resultsSub = '';
        turn.error = error.message || 'Something went wrong.';
      }
      formError.value = error.message || 'Something went wrong.';
    } finally {
      isLoading.value = false;
    }
  }

  function clearForm() {
    industry.value = 'Share Market';
    subcategory.value = null;
    thoughts.value = '';
    formError.value = '';
    thread.value = [];
    browseResults.value = [];
    browseSub.value = '';
    showEmpty.value = true;
    usage.clearLimitPrompt();
    loadNewsFeed({ industryName: 'Share Market' });
  }

  function goHomeWorkspace() {
    clearForm();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  watch(homeNonce, (value, previous) => {
    if (value !== previous) goHomeWorkspace();
  });

  onMounted(async () => {
    try {
      const { catalog: rows } = await loadResearchIndustries();
      if (rows.length) {
        const share = rows.find((item) => item.name === 'Share Market');
        const rest = rows.filter((item) => item.name !== 'Share Market');
        catalog.value = share ? [share, ...rest] : rows;
      }
    } catch {
      // keep FALLBACK_CATALOG
    }
    const defaultIndustry = catalog.value[0]?.name || 'Share Market';
    industry.value = defaultIndustry;
    subcategory.value = null;
    loadNewsFeed({ industryName: defaultIndustry });
  });

  return {
    catalog,
    industry,
    subcategory,
    thoughts,
    formError,
    isLoading,
    thread,
    hasThread,
    browseResults,
    browseSub,
    showBrowseResults,
    emptyMessage,
    showEmpty,
    subs,
    queryPreview,
    selectIndustry,
    selectSub,
    runSearch,
    clearForm,
    searchesLeftLabel: usage.searchesLeftLabel,
    limitReached: usage.limitReached,
    limitMessage: usage.limitMessage,
    upgradeUrl: usage.upgradeUrl,
  };
}

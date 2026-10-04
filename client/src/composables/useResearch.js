import { computed, onMounted, ref } from 'vue';
import { useUiStore } from '../stores/ui.store.js';
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
];

function hostname(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return url;
  }
}

export function useResearch() {
  const ui = useUiStore();
  const usage = useUsage();
  const catalog = ref(FALLBACK_CATALOG);
  const industry = ref('Share Market');
  const subcategory = ref(null);
  const thoughts = ref('');
  const formError = ref('');
  const isLoading = ref(false);
  const results = ref([]);
  const resultsSub = ref('');
  const emptyMessage = ref('Pick an industry for product news, or research a question.');
  const showEmpty = ref(true);
  const showResults = ref(false);
  let newsRequestId = 0;

  const subs = computed(() => {
    return catalog.value.find((item) => item.name === industry.value)?.subs || [];
  });

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

  function setLoading(loading, status = 'Researching…') {
    isLoading.value = loading;
    ui.setStatus(loading ? status : '');
    if (loading) {
      showEmpty.value = false;
      showResults.value = false;
      resultsSub.value = '';
    }
  }

  function applyResults(payload, { scroll = true, subtitle } = {}) {
    const list = Array.isArray(payload?.results) ? payload.results : [];
    results.value = list.map((item) => ({
      ...item,
      host: hostname(item.url),
    }));
    resultsSub.value = subtitle
      || (list.length ? `${list.length} findings` : 'No strong findings');
    showEmpty.value = list.length === 0;
    showResults.value = list.length > 0;
    emptyMessage.value = list.length
      ? 'Pick an industry for product news, or research a question.'
      : 'No strong findings yet. Try a clearer question or another industry.';
    ui.setStatus('');
    if (scroll && list.length) {
      document.getElementById('resultsTitle')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  async function loadNewsFeed({ industryName = null, sub = null } = {}) {
    const requestId = ++newsRequestId;
    const status = sub
      ? `Loading ${industryName} · ${sub}…`
      : industryName
        ? `Loading ${industryName} products…`
        : 'Loading product news…';
    setLoading(true, status);
    resultsSub.value = sub || industryName || 'Products';

    try {
      let path = '/api/news/default';
      if (industryName) {
        path = `/api/news/${encodeURIComponent(industryName)}`;
        if (sub) path += `?sub=${encodeURIComponent(sub)}`;
      }
      const res = await fetch(path);
      const payload = await res.json();
      if (requestId !== newsRequestId) return;
      if (!res.ok || !payload.success) {
        throw new Error(payload.message || 'Could not load product news.');
      }
      const label = payload.data?.label || industryName || 'Product news';
      const scope = sub ? `${industryName} · ${sub}` : industryName;
      applyResults(payload.data, {
        scroll: Boolean(industryName || sub),
        subtitle: industryName
          ? `5 latest ${label}${sub ? ` · ${scope}` : ''}`
          : 'Top searched product news',
      });
    } catch (error) {
      if (requestId !== newsRequestId) return;
      results.value = [];
      showResults.value = false;
      showEmpty.value = true;
      resultsSub.value = '';
      emptyMessage.value = error.message || 'Product news will appear here.';
      ui.setStatus('');
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
      loadNewsFeed();
      return;
    }
    industry.value = name;
    subcategory.value = null;
    formError.value = '';
    loadNewsFeed({ industryName: name });
  }

  function selectSub(name) {
    if (!industry.value) return;
    if (subcategory.value === name) {
      subcategory.value = null;
      loadNewsFeed({ industryName: industry.value });
      return;
    }
    subcategory.value = name;
    formError.value = '';
    loadNewsFeed({ industryName: industry.value, sub: name });
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

    setLoading(true, 'Researching…');
    usage.clearLimitPrompt();
    try {
      const res = await fetch('/api/search', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          categories: [industry.value],
          subcategory: subcategory.value,
          thoughts: text,
          limit: 6,
        }),
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
      const scope = subcategory.value
        ? `${industry.value} · ${subcategory.value}`
        : industry.value;
      applyResults(payload.data, {
        subtitle: `${payload.data.results?.length || 0} R&D findings · ${scope}`,
      });
    } catch (error) {
      showEmpty.value = true;
      showResults.value = false;
      results.value = [];
      ui.setStatus('');
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
    loadNewsFeed({ industryName: 'Share Market' });
  }

  onMounted(async () => {
    try {
      const res = await fetch('/api/industries');
      const payload = await res.json();
      if (res.ok && payload.success && Array.isArray(payload.data) && payload.data.length) {
        const share = payload.data.find((item) => item.name === 'Share Market');
        const rest = payload.data.filter((item) => item.name !== 'Share Market');
        catalog.value = share ? [share, ...rest] : payload.data;
      }
    } catch {
      // fallback catalog
    }
    industry.value = 'Share Market';
    subcategory.value = null;
    loadNewsFeed({ industryName: 'Share Market' });
  });

  return {
    catalog,
    industry,
    subcategory,
    thoughts,
    formError,
    isLoading,
    results,
    resultsSub,
    emptyMessage,
    showEmpty,
    showResults,
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

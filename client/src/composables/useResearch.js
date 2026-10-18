import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useRoute } from 'vue-router';
import { useUiStore } from '../stores/ui.store.js';
import { useProfileStore } from '../stores/profile.store.js';
import { apiUrl } from '../services/apiBase.js';
import { loadResearchIndustries } from '../services/industries.service.js';
import { withoutFyiNews } from '../services/newsFyi.js';
import { fetchDefaultNews, fetchIndustryNewsChunks, PAGE_SIZE } from '../services/news.service.js';
import { dismissNews, hideNewsSource } from '../services/product.service.js';
import { fetchProfile } from '../services/profile.service.js';
import {
  clearResearchReturn,
  consumeResearchHome,
  loadResearchWorkspace,
  saveResearchWorkspace,
} from '../services/researchWorkspace.js';
import { useAuth } from './useAuth.js';
import {
  homeFeedIndustry,
  needsProfileForHomeFeed,
} from './profileIndustry.js';
import { useUsage } from './useUsage.js';

const ALL_INDUSTRY = 'All';
const HOME_INDUSTRY = 'AI';

const FALLBACK_CATALOG = [
  {
    name: 'AI',
    subs: ['Anthropic', 'OpenAI', 'Grok', 'Google', 'Meta', 'Microsoft'],
  },
  {
    name: 'Share Market',
    subs: ['IT', 'EV', 'Space', 'Pharma', 'Banking', 'Energy'],
  },
  {
    name: 'Real Estate',
    subs: ['Residential', 'Commercial', 'PropTech', 'Construction', 'RERA'],
  },
  {
    name: 'IT',
    subs: ['Semiconductors', 'Cybersecurity', 'Cloud'],
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
  {
    name: 'GitHub',
    subs: ['Stars', 'Trending', 'AI', 'DevTools', 'Web', 'Data', 'Mobile'],
  },
];

function withAiFirst(rows) {
  const cleaned = rows.map((item) => {
    if (item.name !== 'IT' || !Array.isArray(item.subs)) return item;
    return { ...item, subs: item.subs.filter((name) => name !== 'AI') };
  });
  const lead = cleaned.find((item) => item.name === HOME_INDUSTRY) || FALLBACK_CATALOG[0];
  const rest = cleaned.filter((item) => item.name !== HOME_INDUSTRY);
  return [lead, ...rest];
}

function hostname(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return url;
  }
}

function findingScore(item) {
  const value = item?.score;
  if (value == null || value === '') return 0;
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

/** Research chat: keep scored findings, drop FYI, highest score first. */
function mapResults(payload) {
  const list = Array.isArray(payload?.results)
    ? payload.results
    : Array.isArray(payload?.items)
      ? payload.items
      : Array.isArray(payload)
        ? payload
        : [];
  const mapped = withoutFyiNews(list).map((item) => ({
    ...item,
    host: item.source || hostname(item.url),
    score: item.opportunityScore ?? item.score,
    why: item.whyIdea || item.why || item.description,
  }));
  if (payload && typeof payload === 'object' && payload.freshness) {
    return mapped;
  }
  return mapped
    .filter((item) => findingScore(item) > 0)
    .sort((a, b) => findingScore(b) - findingScore(a));
}

function windowScopeLabel(data) {
  const freshness = data?.freshness;
  if (freshness === 'fresh') return 'today';
  if (freshness === 'last 7 days') return 'last 7 days';
  if (freshness === 'last 30 days') return 'last 30 days';
  if (freshness === 'warming_up') return 'fetching';
  const days = Number(data?.windowDays);
  if (days === 1) return 'today';
  if (days > 0) return `last ${days} days`;
  return 'last 30 days';
}

/** Browse feed: keep server order (newest first within the active window chunk). */
function mapBrowseResults(payload) {
  const list = Array.isArray(payload?.results)
    ? payload.results
    : Array.isArray(payload?.items)
      ? payload.items
      : Array.isArray(payload)
        ? payload
        : [];
  return list.map((item) => ({
    ...item,
    host: item.source || hostname(item.url),
    score: item.relevanceScore ?? item.opportunityScore ?? item.score,
    why: item.relevanceReason || item.whyIdea || item.why || item.description,
  }));
}

function mapStreamItem(item) {
  if (!item || typeof item !== 'object') return null;
  const [row] = mapBrowseResults({ results: [item] });
  return row || null;
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
        if (findingScore(row) <= 0) continue;
        if (!turn.results.some((item) => item.url === row.url)) {
          turn.results = mapResults([...turn.results, row]);
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

function countryName(code, countries) {
  if (!code) return '';
  const match = (countries || []).find((item) => item?.code === code);
  return match?.name || code;
}

function makeId() {
  return `turn_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

export function useResearch() {
  const ui = useUiStore();
  const route = useRoute();
  const { homeNonce } = storeToRefs(ui);
  const { isAuthenticated } = useAuth();
  const profileStore = useProfileStore();
  const usage = useUsage();
  const savedWorkspace = consumeResearchHome() ? null : loadResearchWorkspace();
  /** False after the user picks an industry chip. Home follows the profile industry (AI fallback) while this stays true. */
  const followProfile = ref(savedWorkspace ? savedWorkspace.followProfile : true);
  const catalog = ref(FALLBACK_CATALOG);
  const industry = ref(
    savedWorkspace && !savedWorkspace.followProfile
      ? (savedWorkspace.industry || HOME_INDUSTRY)
      : HOME_INDUSTRY,
  );
  const subcategory = ref(savedWorkspace?.subcategory ?? null);
  const thoughts = ref(savedWorkspace?.thoughts || '');
  const formError = ref('');
  const isLoading = ref(false);
  /** Chat turns: each research question keeps its findings underneath. */
  const thread = ref(savedWorkspace?.thread || []);
  /** Browse-mode product news (shown only when the chat thread is empty). */
  const browseResults = ref(savedWorkspace?.browseResults || []);
  const browseSub = ref(savedWorkspace?.browseSub || '');
  const browseFreshness = ref(savedWorkspace?.browseFreshness || '');
  const browseNote = ref(savedWorkspace?.browseNote || '');
  const browseCountries = ref(savedWorkspace?.browseCountries || []);
  const browsePage = ref(1);
  const browseTotal = ref(0);
  const browseTotalPages = ref(0);
  const browseHasMore = ref(false);
  /** Null means worldwide, with India still ranked first. */
  const country = ref(savedWorkspace?.country || null);
  const emptyMessage = ref(
    savedWorkspace?.emptyMessage || 'Pick an industry for product news, or start a research chat.',
  );
  const showEmpty = ref(savedWorkspace ? savedWorkspace.showEmpty : true);
  let newsRequestId = 0;
  let newsAbort = null;
  let persistReady = false;

  const isAllIndustry = computed(() => !industry.value || industry.value === ALL_INDUSTRY);

  const subs = computed(() => {
    if (isAllIndustry.value) return [];
    return catalog.value.find((item) => item.name === industry.value)?.subs || [];
  });

  const hasThread = computed(() => thread.value.length > 0);

  const showBrowseResults = computed(() => !hasThread.value && browseResults.value.length > 0);

  const queryPreview = computed(() => {
    const text = thoughts.value.trim();
    if (text.length < 8) return '';
    const cleaned = text
      .replace(/[^\p{L}\p{N}\s'-]/gu, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    const scope = isAllIndustry.value
      ? ALL_INDUSTRY
      : subcategory.value
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

  watch(
    () => (typeof route.query.q === 'string' ? route.query.q : ''),
    (q) => {
      const text = q.trim().slice(0, 2000);
      if (!text) return;
      thoughts.value = text;
      scrollComposerIntoView();
    },
    { immediate: true },
  );

  function scrollTurnIntoView(turnId) {
    nextTick(() => {
      document.getElementById(`turn-${turnId}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });
  }

  async function resolveHomeIndustry() {
    let profile = profileStore.profile;
    if (needsProfileForHomeFeed(isAuthenticated.value, profile)) {
      try {
        profile = await fetchProfile();
        profileStore.setProfile(profile);
      } catch {
        profile = null;
      }
    }
    const catalogNames = catalog.value.map((item) => item.name);
    return homeFeedIndustry(profile?.industries, catalogNames, HOME_INDUSTRY);
  }

  async function applyHomeFeed() {
    if (!followProfile.value || hasThread.value) return;
    const homeIndustry = await resolveHomeIndustry();
    if (!followProfile.value || hasThread.value) return;
    industry.value = homeIndustry;
    subcategory.value = null;
    await loadNewsFeed({ industryName: homeIndustry, page: 1 });
  }

  function applyBrowsePaging(data) {
    browsePage.value = Number(data?.page) > 0 ? Number(data.page) : 1;
    browseTotal.value = Number(data?.total) >= 0 ? Number(data.total) : browseResults.value.length;
    browseTotalPages.value = Number(data?.totalPages) >= 0
      ? Number(data.totalPages)
      : (browseTotal.value ? Math.ceil(browseTotal.value / PAGE_SIZE) : 0);
    browseHasMore.value = Boolean(data?.hasMore) || browsePage.value < browseTotalPages.value;
  }

  function applyBrowseMeta(data, scoped, sub) {
    const label = data?.label || scoped || 'Product news';
    const scope = sub ? `${scoped} · ${sub}` : scoped;
    browseCountries.value = Array.isArray(data?.countries) ? data.countries : [];
    browseFreshness.value = data?.freshness || '';
    browseNote.value = data?.message || '';
    applyBrowsePaging(data);
    const place = countryName(country.value, browseCountries.value);
    const total = browseTotal.value;
    const page = browsePage.value;
    const pageSize = Number(data?.pageSize) > 0 ? Number(data.pageSize) : PAGE_SIZE;
    const from = total === 0 ? 0 : (page - 1) * pageSize + 1;
    const to = Math.min(page * pageSize, total);
    const range = total ? `${from}–${to} of ${total}` : '0';
    const windowLabel = windowScopeLabel(data);
    const scopeLine = scoped
      ? `${range} · ${windowLabel} · ${label}${sub ? ` · ${scope}` : ''}`
      : `${range} · ${windowLabel} · Top searched product news`;
    browseSub.value = place
      ? `${place} · ${scopeLine}`
      : `Worldwide, India first · ${scopeLine}`;
    return place;
  }

  function settleBrowse(data, scoped, place, sub = null) {
    const warming = data?.freshness === 'warming_up' && browseResults.value.length === 0;
    // warming_up: banner + skeleton cover status — do not flash the empty state.
    showEmpty.value = browseResults.value.length === 0 && !warming;
    const chip = sub || scoped;
    emptyMessage.value = browseResults.value.length
      ? 'Pick an industry for product news, or start a research chat.'
      : place
        ? `No findings for ${place} yet.`
        : chip
          ? `No product news for ${chip} yet.`
          : 'No product news yet.';
  }

  async function loadNewsFeed({ industryName = null, sub = null, page = 1 } = {}) {
    // News browsing does not clear an active research chat.
    if (hasThread.value) return;

    const scoped = industryName && industryName !== ALL_INDUSTRY ? industryName : null;
    const requestId = ++newsRequestId;
    newsAbort?.abort();
    const controller = new AbortController();
    newsAbort = controller;
    isLoading.value = true;
    browseResults.value = [];
    browsePage.value = Math.max(1, page);
    browseSub.value = sub || scoped || 'Loading…';
    browseFreshness.value = '';
    browseNote.value = '';
    showEmpty.value = false;

    try {
      let data;
      if (scoped) {
        data = await fetchIndustryNewsChunks({
          industry: scoped,
          sub,
          country: country.value,
          page: browsePage.value,
          limit: PAGE_SIZE,
          signal: controller.signal,
          onItem: async (item) => {
            if (requestId !== newsRequestId) return;
            const row = mapStreamItem(item);
            if (!row) return;
            const key = row.url || row.id;
            if (key && browseResults.value.some((entry) => (entry.url || entry.id) === key)) return;
            browseResults.value = [...browseResults.value, row];
            isLoading.value = false;
            await nextTick();
          },
          onChunk: async (chunk) => {
            if (requestId !== newsRequestId) return;
            const rows = mapBrowseResults(chunk);
            if (rows.length) {
              browseResults.value = rows;
            }
            applyBrowseMeta(chunk, scoped, sub);
            const warming = chunk?.freshness === 'warming_up' && browseResults.value.length === 0;
            if (warming) {
              // Keep the loading skeleton while news.service retries once.
              showEmpty.value = false;
              return;
            }
            settleBrowse(chunk, scoped, countryName(country.value, browseCountries.value), sub);
            isLoading.value = false;
            await nextTick();
          },
        });
        if (requestId !== newsRequestId) return;
        if (!browseResults.value.length) {
          browseResults.value = mapBrowseResults(data);
        }
      } else {
        browseResults.value = [];
        data = await fetchDefaultNews({
          country: country.value,
          page: browsePage.value,
          limit: PAGE_SIZE,
          signal: controller.signal,
          onItem: async (item) => {
            if (requestId !== newsRequestId) return;
            const row = mapStreamItem(item);
            if (!row) return;
            const key = row.url || row.id;
            if (key && browseResults.value.some((entry) => (entry.url || entry.id) === key)) return;
            browseResults.value = [...browseResults.value, row];
            await nextTick();
          },
        });
        if (requestId !== newsRequestId) return;
        if (!browseResults.value.length) {
          browseResults.value = mapBrowseResults(data);
        }
      }
      const place = applyBrowseMeta(data, scoped, sub);
      settleBrowse(data, scoped, place, sub);
    } catch (error) {
      if (error?.name === 'AbortError' || requestId !== newsRequestId) return;
      browseResults.value = [];
      browseSub.value = '';
      browseFreshness.value = '';
      browseNote.value = '';
      browseTotal.value = 0;
      browseTotalPages.value = 0;
      browseHasMore.value = false;
      showEmpty.value = true;
      emptyMessage.value = error.message || 'Product news will appear here.';
    } finally {
      if (requestId === newsRequestId) {
        isLoading.value = false;
      }
    }
  }

  function goBrowsePage(nextPage) {
    const page = Math.max(1, Number(nextPage) || 1);
    if (browseTotalPages.value && page > browseTotalPages.value) return;
    if (page === browsePage.value && browseResults.value.length) return;
    const scoped = industry.value && industry.value !== ALL_INDUSTRY ? industry.value : null;
    loadNewsFeed({
      industryName: scoped || ALL_INDUSTRY,
      sub: scoped ? subcategory.value : null,
      page,
    });
  }

  async function dismissBrowseItem(item) {
    if (!item || hasThread.value) return;
    const key = item.url || item.id;
    browseResults.value = browseResults.value.filter((row) => (row.url || row.id) !== key);
    try {
      await dismissNews({
        itemId: item.id,
        url: item.url,
        industry: item.industry || industry.value || HOME_INDUSTRY,
      });
    } catch {
      // Keep the optimistic remove; feed reload still fills the slot when possible.
    }
    const scoped = industry.value && industry.value !== ALL_INDUSTRY ? industry.value : HOME_INDUSTRY;
    await loadNewsFeed({ industryName: scoped, sub: subcategory.value, page: browsePage.value || 1 });
  }

  async function hideBrowseSource(item) {
    if (!item || hasThread.value) return;
    const sourceKey = item.sourceId;
    if (sourceKey) {
      browseResults.value = browseResults.value.filter((row) => row.sourceId !== sourceKey);
    }
    try {
      await hideNewsSource({
        sourceId: item.sourceId,
        itemId: item.id,
      });
    } catch {
      // Optimistic hide; reload refreshes.
    }
    const scoped = industry.value && industry.value !== ALL_INDUSTRY ? industry.value : HOME_INDUSTRY;
    await loadNewsFeed({ industryName: scoped, sub: subcategory.value, page: browsePage.value || 1 });
  }

  function selectIndustry(name) {
    followProfile.value = false;
    if (industry.value === name) {
      if (name === ALL_INDUSTRY) return;
      industry.value = ALL_INDUSTRY;
      subcategory.value = null;
      formError.value = '';
      if (!hasThread.value) loadNewsFeed({ industryName: ALL_INDUSTRY, page: 1 });
      return;
    }
    industry.value = name;
    subcategory.value = null;
    formError.value = '';
    if (!hasThread.value) loadNewsFeed({ industryName: name, page: 1 });
  }

  function selectCountry(code) {
    if (!code && !country.value) return;
    const next = code && country.value !== code ? code : null;
    country.value = next;
    if (!hasThread.value) {
      loadNewsFeed({
        industryName: industry.value,
        sub: isAllIndustry.value ? null : subcategory.value,
        page: 1,
      });
    }
  }

  function selectSub(name) {
    followProfile.value = false;
    if (isAllIndustry.value) return;
    if (subcategory.value === name) {
      subcategory.value = null;
      if (!hasThread.value) loadNewsFeed({ industryName: industry.value, page: 1 });
      return;
    }
    subcategory.value = name;
    formError.value = '';
    if (!hasThread.value) loadNewsFeed({ industryName: industry.value, sub: name, page: 1 });
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
    const scope = isAllIndustry.value
      ? ALL_INDUSTRY
      : subcategory.value
        ? `${industry.value} · ${subcategory.value}`
        : industry.value;

    thread.value.push({
      id: turnId,
      userText: text,
      industry: isAllIndustry.value ? ALL_INDUSTRY : industry.value,
      subcategory: isAllIndustry.value ? null : subcategory.value,
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
      categories: isAllIndustry.value ? [ALL_INDUSTRY] : [industry.value],
      subcategory: isAllIndustry.value ? null : subcategory.value,
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

  function persistWorkspace() {
    if (!persistReady) return;
    saveResearchWorkspace({
      industry: industry.value,
      subcategory: subcategory.value,
      thoughts: thoughts.value,
      thread: thread.value,
      browseResults: browseResults.value,
      browseSub: browseSub.value,
      browseFreshness: browseFreshness.value,
      browseNote: browseNote.value,
      browseCountries: browseCountries.value,
      country: country.value,
      emptyMessage: emptyMessage.value,
      showEmpty: showEmpty.value,
      followProfile: followProfile.value,
    });
  }

  function clearForm() {
    consumeResearchHome();
    clearResearchReturn();
    followProfile.value = true;
    subcategory.value = null;
    thoughts.value = '';
    formError.value = '';
    thread.value = [];
    browseResults.value = [];
    browseSub.value = '';
    browseFreshness.value = '';
    browseNote.value = '';
    browseCountries.value = [];
    browsePage.value = 1;
    browseTotal.value = 0;
    browseTotalPages.value = 0;
    browseHasMore.value = false;
    country.value = null;
    showEmpty.value = true;
    usage.clearLimitPrompt();
    applyHomeFeed();
  }

  function goHomeWorkspace() {
    clearForm();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  watch(homeNonce, (value, previous) => {
    if (value !== previous) goHomeWorkspace();
  });

  watch(
    [industry, subcategory, thoughts, thread, browseResults, browseSub, browseFreshness, browseNote, browseCountries, country, emptyMessage, showEmpty, followProfile],
    persistWorkspace,
    { deep: true },
  );

  persistReady = true;

  onMounted(async () => {
    try {
      const { catalog: rows } = await loadResearchIndustries();
      if (rows.length) {
        catalog.value = withAiFirst(rows);
      }
    } catch {
      // keep FALLBACK_CATALOG
    }
    // Manual industry picks restore; still refresh so FYI cannot stick from sessionStorage.
    if (savedWorkspace && !followProfile.value) {
      persistWorkspace();
      if (!hasThread.value) {
        await loadNewsFeed({ industryName: industry.value, sub: subcategory.value, page: 1 });
      }
      return;
    }
    await applyHomeFeed();
  });

  watch(isAuthenticated, async (signedIn, wasSignedIn) => {
    if (!signedIn) {
      followProfile.value = true;
      profileStore.setProfile(null);
    } else if (!wasSignedIn) {
      followProfile.value = true;
    }
    if (followProfile.value && !hasThread.value) await applyHomeFeed();
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
    browseFreshness,
    browseNote,
    browseCountries,
    browsePage,
    browseTotal,
    browseTotalPages,
    browseHasMore,
    country,
    showBrowseResults,
    emptyMessage,
    showEmpty,
    subs,
    queryPreview,
    selectIndustry,
    selectSub,
    selectCountry,
    goBrowsePage,
    dismissBrowseItem,
    hideBrowseSource,
    runSearch,
    clearForm,
    searchesLeftLabel: usage.searchesLeftLabel,
    limitReached: usage.limitReached,
    limitMessage: usage.limitMessage,
    upgradeUrl: usage.upgradeUrl,
  };
}

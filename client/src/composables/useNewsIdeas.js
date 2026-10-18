import { nextTick, ref } from 'vue';
import { fetchNewsIdeas, fetchVideoResearch, rateIdea } from '../services/newsIdeas.service.js';
import { startClickTiming } from '../utils/clickTiming.js';

const STAGE_LABELS = {
  brief: 'Reading the article',
  angles: 'Picking ideas',
};

const POLL_MS = 2000;
const POLL_MAX = 45;

/** Merge a streamed idea item; real cards replace any preview. */
export function applyIdeaItem(list, next) {
  if (!next) return Array.isArray(list) ? [...list] : [];
  const rows = Array.isArray(list) ? list : [];
  const id = next.id || next.title;
  let out = rows;
  if (!next.preview) {
    out = out.filter((row) => !row.preview);
  }
  const idx = out.findIndex((row) => (row.id || row.title) === id);
  if (idx >= 0) {
    out = [...out];
    out[idx] = next;
    return out;
  }
  return [...out, next];
}

export function useNewsIdeas() {
  const open = ref(false);
  const loading = ref(false);
  const error = ref('');
  const item = ref(null);
  const ideas = ref([]);
  const brief = ref(null);
  const headline = ref('');
  const selected = ref(null);
  const ratingId = ref('');
  const stageLabel = ref('Loading ideas');
  const researchStatus = ref('');
  const researchSummary = ref(null);
  let pollTimer = null;
  let pollCount = 0;
  let pollNewsId = '';

  function stopPoll() {
    if (pollTimer) {
      clearTimeout(pollTimer);
      pollTimer = null;
    }
    pollCount = 0;
    pollNewsId = '';
  }

  async function pollResearch(newsId) {
    if (!newsId || newsId !== pollNewsId) return;
    try {
      const data = await fetchVideoResearch(newsId);
      if (newsId !== pollNewsId) return;
      const status = data?.status || '';
      researchStatus.value = status;
      if (status === 'ready' && data?.summary) {
        researchSummary.value = data.summary;
        stopPoll();
        return;
      }
      if (status === 'failed') {
        researchSummary.value = null;
        stopPoll();
        return;
      }
    } catch {
      // Keep ideas usable if the poll fails.
      if (pollCount >= 3) {
        if (researchStatus.value === 'running' || researchStatus.value === 'started') {
          researchStatus.value = 'failed';
        }
        stopPoll();
        return;
      }
    }
    pollCount += 1;
    if (pollCount >= POLL_MAX) {
      if (researchStatus.value === 'running' || researchStatus.value === 'started') {
        researchStatus.value = 'failed';
      }
      stopPoll();
      return;
    }
    pollTimer = setTimeout(() => pollResearch(newsId), POLL_MS);
  }

  function startPoll(newsId) {
    stopPoll();
    if (!newsId) return;
    pollNewsId = newsId;
    researchStatus.value = researchStatus.value || 'running';
    // First fetch immediately — stream may omit summary when research is still running.
    pollTimer = setTimeout(() => pollResearch(newsId), 0);
  }

  function applyResearchEvent(event, newsId) {
    const status = event?.status || 'running';
    researchStatus.value = status;
    const summary = event?.summary || event?.data?.summary;
    if (summary && typeof summary === 'object' && !Array.isArray(summary)) {
      researchSummary.value = summary;
    }
    if (!researchSummary.value && (status === 'started' || status === 'running' || status === 'ready')) {
      startPoll(newsId || item.value?.id || '');
    }
  }

  async function openForItem(newsItem) {
    const timing = startClickTiming('ideas');
    if (!newsItem?.id) {
      error.value = 'This news item has no id.';
      timing.fail(new Error('missing news id'));
      return;
    }
    stopPoll();
    open.value = true;
    loading.value = true;
    error.value = '';
    item.value = newsItem;
    ideas.value = [];
    brief.value = null;
    headline.value = newsItem.title || '';
    selected.value = null;
    researchStatus.value = '';
    researchSummary.value = null;
    stageLabel.value = 'Loading ideas';
    timing.mark('panelOpen');
    timing.debug('panelOpen');
    try {
      const data = await fetchNewsIdeas(newsItem.id, async (event) => {
        if (event?.type === 'status') {
          stageLabel.value = STAGE_LABELS[event.stage] || 'Loading ideas';
          timing.debug(`status:${event.stage || ''}`);
          return;
        }
        if (event?.type === 'meta' && event.data) {
          headline.value = event.data.headline || newsItem.title || '';
          brief.value = event.data.brief || null;
          timing.debug('meta');
          return;
        }
        if (event?.type === 'item' && event.item) {
          ideas.value = applyIdeaItem(ideas.value, event.item);
          const kind = event.item.preview ? 'preview' : 'idea';
          timing.debug(`item:${kind}`);
          await nextTick();
          timing.firstUi(kind);
          return;
        }
        if (event?.type === 'research') {
          applyResearchEvent(event, newsItem.id);
          timing.debug(`research:${event.status || ''}`);
        }
      });
      if (Array.isArray(data?.ideas) && data.ideas.length && !ideas.value.length) {
        ideas.value = data.ideas;
        await nextTick();
        timing.firstUi('json');
      }
      if (data?.headline) headline.value = data.headline;
      if (data?.brief) brief.value = data.brief;
      if (!ideas.value.length) {
        error.value = 'No idea fit this news.';
        timing.done('empty');
      } else {
        timing.done(`${ideas.value.length} ideas`);
      }
      // Poll until ready when the stream did not already include a summary.
      if (!researchSummary.value && researchStatus.value !== 'failed') {
        startPoll(newsItem.id);
      }
    } catch (err) {
      error.value = err.message || 'Could not build ideas.';
      timing.fail(err);
    } finally {
      loading.value = false;
    }
  }

  function close() {
    stopPoll();
    open.value = false;
    loading.value = false;
    error.value = '';
    item.value = null;
    ideas.value = [];
    brief.value = null;
    headline.value = '';
    selected.value = null;
    ratingId.value = '';
    researchStatus.value = '';
    researchSummary.value = null;
    stageLabel.value = 'Loading ideas';
  }

  async function rate(ideaId, rating) {
    if (!ideaId || !rating) return;
    ratingId.value = ideaId;
    try {
      await rateIdea(ideaId, rating);
      if (rating === 'not_useful') {
        ideas.value = ideas.value.filter((row) => row.id !== ideaId);
        if (selected.value?.id === ideaId) selected.value = null;
      }
    } catch (err) {
      error.value = err.message || 'Could not save that rating.';
    } finally {
      ratingId.value = '';
    }
  }

  function selectIdea(idea) {
    selected.value = idea || null;
  }

  return {
    open,
    loading,
    error,
    item,
    ideas,
    brief,
    headline,
    selected,
    ratingId,
    stageLabel,
    researchStatus,
    researchSummary,
    openForItem,
    close,
    rate,
    selectIdea,
  };
}

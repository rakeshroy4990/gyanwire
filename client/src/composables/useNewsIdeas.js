import { ref } from 'vue';
import { fetchNewsIdeas, rateIdea } from '../services/newsIdeas.service.js';

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

  async function openForItem(newsItem) {
    if (!newsItem?.id) {
      error.value = 'This news item has no id.';
      return;
    }
    open.value = true;
    loading.value = true;
    error.value = '';
    item.value = newsItem;
    ideas.value = [];
    brief.value = null;
    headline.value = newsItem.title || '';
    selected.value = null;
    try {
      const data = await fetchNewsIdeas(newsItem.id);
      ideas.value = Array.isArray(data?.ideas) ? data.ideas : [];
      brief.value = data?.brief || null;
      headline.value = data?.headline || newsItem.title || '';
      if (!ideas.value.length) {
        error.value = 'No idea fit this news.';
      }
    } catch (err) {
      error.value = err.message || 'Could not build ideas.';
    } finally {
      loading.value = false;
    }
  }

  function close() {
    open.value = false;
    loading.value = false;
    error.value = '';
    item.value = null;
    ideas.value = [];
    brief.value = null;
    headline.value = '';
    selected.value = null;
    ratingId.value = '';
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
    openForItem,
    close,
    rate,
    selectIdea,
  };
}

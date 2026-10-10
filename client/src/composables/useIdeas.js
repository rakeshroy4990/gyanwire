import { ref } from 'vue';
import { requestIdeas } from '../services/product.service.js';

function sortByScore(rows) {
  return [...(rows || [])].sort((a, b) => Number(b.score || 0) - Number(a.score || 0));
}

export function useIdeas() {
  const ideas = ref([]);
  const idea = ref(null);
  const loading = ref(false);
  const error = ref('');
  const limitReached = ref(false);

  async function runFromNews(body) {
    loading.value = true;
    error.value = '';
    limitReached.value = false;
    ideas.value = [];
    idea.value = null;
    try {
      const data = await requestIdeas(body);
      const rows = sortByScore(data?.ideas);
      ideas.value = rows;
      idea.value = rows[0] || null;
      if (!rows.length) error.value = 'No idea fit this profile.';
    } catch (err) {
      if (err.status === 402) limitReached.value = true;
      error.value = err.message || 'Could not build ideas.';
    } finally {
      loading.value = false;
    }
  }

  return { idea, ideas, loading, error, limitReached, runFromNews };
}

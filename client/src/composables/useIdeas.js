import { ref } from 'vue';
import { requestIdeas } from '../services/product.service.js';

export function useIdeas() {
  const idea = ref(null);
  const loading = ref(false);
  const error = ref('');
  const limitReached = ref(false);

  async function runFromNews(body) {
    loading.value = true;
    error.value = '';
    limitReached.value = false;
    idea.value = null;
    try {
      const data = await requestIdeas(body);
      const rows = data?.ideas || [];
      idea.value = rows[0] || null;
      if (!idea.value) error.value = 'No idea fit this profile.';
    } catch (err) {
      if (err.status === 402) limitReached.value = true;
      error.value = err.message || 'Could not build ideas.';
    } finally {
      loading.value = false;
    }
  }

  return { idea, loading, error, limitReached, runFromNews };
}

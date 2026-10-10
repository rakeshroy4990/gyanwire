import { ref } from 'vue';
import { fetchLlmSummary } from '../services/llm.service.js';

export function useLlmSummary() {
  const summary = ref(null);
  const error = ref('');
  const loading = ref(false);

  async function refresh() {
    loading.value = true;
    error.value = '';
    try {
      summary.value = await fetchLlmSummary();
    } catch (err) {
      summary.value = null;
      error.value = err?.message || 'Could not load LLM spend.';
    } finally {
      loading.value = false;
    }
  }

  return { summary, error, loading, refresh };
}

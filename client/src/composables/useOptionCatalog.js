import { onMounted, ref } from 'vue';
import { loadOptionCatalog } from '../services/optionCatalog.service.js';

export function useOptionCatalog() {
  const catalog = ref([]);
  const loading = ref(false);
  const error = ref('');

  async function refresh(taskType) {
    loading.value = true;
    error.value = '';
    try {
      const { catalog: rows } = await loadOptionCatalog(taskType);
      catalog.value = rows;
      return rows;
    } catch (err) {
      error.value = err?.message || 'Could not load options.';
      return [];
    } finally {
      loading.value = false;
    }
  }

  function optionsForTask(taskType) {
    if (!taskType) return catalog.value;
    return catalog.value.filter((o) => o.taskFit && o.taskFit[taskType]);
  }

  onMounted(() => {
    refresh();
  });

  return { catalog, loading, error, refresh, optionsForTask };
}

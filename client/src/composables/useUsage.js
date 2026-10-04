import { computed, onMounted, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useAuthSessionStore } from '../stores/authSession.store.js';
import { fetchUsage } from '../services/usage.service.js';

export function useUsage() {
  const authSession = useAuthSessionStore();
  const { userId } = storeToRefs(authSession);

  const planName = ref('Free');
  const searchesToday = ref(0);
  const searchLimit = ref(5);
  const searchesRemaining = ref(5);
  const upgradeUrl = ref('/pricing');
  const limitReached = ref(false);
  const limitMessage = ref('');
  const isLoadingUsage = ref(false);

  const searchesLeftLabel = computed(() => (
    `${searchesRemaining.value} of ${searchLimit.value} searches left today`
  ));

  function applyUsage(data) {
    if (!data) return;
    planName.value = data.plan?.name || 'Free';
    searchesToday.value = Number(data.searchesToday || 0);
    searchLimit.value = Number(data.searchLimit || data.plan?.dailySearchLimit || 5);
    searchesRemaining.value = Number(
      data.searchesRemaining ?? Math.max(0, searchLimit.value - searchesToday.value),
    );
    if (searchesRemaining.value <= 0) {
      limitReached.value = true;
    }
  }

  async function refreshUsage() {
    isLoadingUsage.value = true;
    try {
      const data = await fetchUsage();
      applyUsage(data);
      if (searchesRemaining.value > 0) {
        limitReached.value = false;
        limitMessage.value = '';
      }
    } catch {
      // keep last known values
    } finally {
      isLoadingUsage.value = false;
    }
  }

  function applySearchUsage(usage) {
    if (!usage) return;
    applyUsage({
      plan: usage.plan,
      searchesToday: usage.searchesToday,
      searchLimit: usage.searchLimit,
      searchesRemaining: usage.searchesRemaining,
    });
  }

  function handleLimitError(payload) {
    const data = payload?.data || {};
    limitReached.value = true;
    limitMessage.value = payload?.message
      || 'Daily search limit reached. Upgrade to continue.';
    upgradeUrl.value = data.upgradeUrl || '/pricing';
    if (typeof data.searchesToday === 'number') searchesToday.value = data.searchesToday;
    if (typeof data.searchLimit === 'number') searchLimit.value = data.searchLimit;
    searchesRemaining.value = 0;
    if (data.plan?.name) planName.value = data.plan.name;
  }

  function clearLimitPrompt() {
    limitReached.value = false;
    limitMessage.value = '';
  }

  watch(userId, () => {
    refreshUsage();
  });

  onMounted(() => {
    refreshUsage();
  });

  return {
    planName,
    searchesToday,
    searchLimit,
    searchesRemaining,
    searchesLeftLabel,
    upgradeUrl,
    limitReached,
    limitMessage,
    isLoadingUsage,
    refreshUsage,
    applySearchUsage,
    handleLimitError,
    clearLimitPrompt,
  };
}

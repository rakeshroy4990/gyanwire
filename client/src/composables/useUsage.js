import { computed, onMounted, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useAuthSessionStore } from '../stores/authSession.store.js';
import { fetchUsage } from '../services/usage.service.js';

/** Server sends Integer.MAX_VALUE when an account has no daily idea cap. */
export const UNLIMITED_IDEA_CAP = 2147483647;

export function isUnlimitedIdeaCap(value) {
  const n = Number(value);
  return Number.isFinite(n) && n >= UNLIMITED_IDEA_CAP;
}

export function formatIdeaButtonLabel({ ready, loading, limitReached, remaining, limit }) {
  if (!ready || loading || limitReached) {
    return limitReached && ready && !loading ? 'Daily idea limit reached' : 'Get an idea';
  }
  if (remaining == null || limit == null) return 'Get an idea';
  const left = Number(remaining);
  if (!Number.isFinite(left) || isUnlimitedIdeaCap(left) || isUnlimitedIdeaCap(limit)) {
    return 'Get an idea';
  }
  return `Get an idea (${left} left)`;
}

export function useUsage() {
  const authSession = useAuthSessionStore();
  const { userId } = storeToRefs(authSession);

  const planName = ref('Free');
  const searchesToday = ref(0);
  const searchLimit = ref(5);
  const searchesRemaining = ref(5);
  const ideasToday = ref(0);
  const ideaLimit = ref(3);
  const ideasRemaining = ref(3);
  const upgradeUrl = ref('/pricing');
  const limitReached = ref(false);
  const limitMessage = ref('');
  const isLoadingUsage = ref(false);

  const usageReady = ref(false);
  const searchesLeftLabel = computed(() => {
    if (!usageReady.value || isLoadingUsage.value) return '';
    return `${searchesRemaining.value} of ${searchLimit.value} searches left today`;
  });
  const ideaLimitReached = computed(() => {
    if (!usageReady.value) return false;
    if (ideaLimit.value == null || ideasRemaining.value == null) return false;
    const lim = Number(ideaLimit.value);
    if (!Number.isFinite(lim) || lim <= 0) return false;
    return Number(ideasRemaining.value) <= 0;
  });
  const ideaButtonLabel = computed(() => formatIdeaButtonLabel({
    ready: usageReady.value,
    loading: isLoadingUsage.value,
    limitReached: ideaLimitReached.value,
    remaining: ideasRemaining.value,
    limit: ideaLimit.value,
  }));

  function applyUsage(data) {
    if (!data) return;
    planName.value = data.plan?.name || 'Free';
    searchesToday.value = Number(data.searchesToday || 0);
    searchLimit.value = Number(data.searchLimit || data.plan?.dailySearchLimit || 5);
    searchesRemaining.value = Number(
      data.searchesRemaining ?? Math.max(0, searchLimit.value - searchesToday.value),
    );
    ideasToday.value = Number(data.ideasToday || 0);
    const rawIdeaLimit = data.ideaLimit ?? data.plan?.dailyIdeaLimit;
    if (rawIdeaLimit == null || isUnlimitedIdeaCap(rawIdeaLimit)) {
      ideaLimit.value = null;
      ideasRemaining.value = null;
    } else {
      ideaLimit.value = Number(rawIdeaLimit);
      ideasRemaining.value = Number(
        data.ideasRemaining ?? Math.max(0, ideaLimit.value - ideasToday.value),
      );
    }
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
      usageReady.value = true;
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
    ideasToday,
    ideaLimit,
    ideasRemaining,
    ideaLimitReached,
    ideaButtonLabel,
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

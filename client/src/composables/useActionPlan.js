import { computed, ref } from 'vue';
import {
  createActionPlan,
  fetchActionPlan,
  fetchStepDetail,
  postTiming,
  toggleActionPlanStep,
} from '../services/actionPlan.service.js';

export function useActionPlan() {
  const plan = ref(null);
  const loading = ref(false);
  const error = ref('');
  const pasteRequired = ref(false);
  const limitReached = ref(false);

  const path = ref('open_source');
  const openStepKey = ref(null);
  const stepDetail = ref(null);
  const stepDetailLoading = ref(false);
  const stepDetailError = ref('');
  const timingPrompt = ref(null);
  const timingMinutes = ref('');
  const timingSaving = ref(false);

  const steps = computed(() => (Array.isArray(plan.value?.steps) ? plan.value.steps : []));
  const doneCount = computed(() => steps.value.filter((s) => s.done).length);
  const pathTotals = computed(() => plan.value?.pathTotals || null);

  const progress = computed(() => {
    const total = steps.value.length;
    const done = doneCount.value;
    const usePaid = path.value === 'paid';
    const remainingMin = steps.value
      .filter((s) => !s.done)
      .reduce((sum, s) => {
        const os = Number(s.durationMin) || 0;
        const paid = Number(s.paidDurationMin);
        const min = usePaid && Number.isFinite(paid) ? paid : os;
        return sum + min;
      }, 0);
    return {
      done,
      total,
      remainingMin,
      label: total ? `${done} of ${total} steps · ${formatMinutes(remainingMin)} left` : '',
    };
  });

  const drawerOpen = computed(() => !!openStepKey.value);

  async function generate(payload) {
    loading.value = true;
    error.value = '';
    pasteRequired.value = false;
    limitReached.value = false;
    try {
      plan.value = await createActionPlan(payload);
      return plan.value;
    } catch (err) {
      if (err.status === 402 || err.code === 'LIMIT_REACHED') {
        limitReached.value = true;
      }
      if (err.status === 422 || err.code === 'ARTICLE_UNREADABLE') {
        pasteRequired.value = true;
      }
      error.value = err.message || 'Could not build the plan.';
      throw err;
    } finally {
      loading.value = false;
    }
  }

  async function load(id) {
    loading.value = true;
    error.value = '';
    try {
      plan.value = await fetchActionPlan(id);
      return plan.value;
    } catch (err) {
      error.value = err.message || 'Plan not found.';
      throw err;
    } finally {
      loading.value = false;
    }
  }

  async function openStep(stepKey) {
    if (!plan.value?.planId || !stepKey) return;
    openStepKey.value = stepKey;
    stepDetail.value = null;
    stepDetailError.value = '';
    stepDetailLoading.value = true;
    try {
      const pathParam = path.value === 'mix' ? 'open_source' : path.value;
      stepDetail.value = await fetchStepDetail(plan.value.planId, stepKey, pathParam);
    } catch (err) {
      stepDetailError.value = err.message || 'Could not load step.';
    } finally {
      stepDetailLoading.value = false;
    }
  }

  function closeStep() {
    openStepKey.value = null;
    stepDetail.value = null;
    stepDetailError.value = '';
    stepDetailLoading.value = false;
  }

  async function toggleStep(stepKey, done, options = {}) {
    if (!plan.value?.planId) {
      if (plan.value?.steps) {
        plan.value = {
          ...plan.value,
          steps: plan.value.steps.map((s) => (s.id === stepKey ? { ...s, done: !!done } : s)),
        };
      }
      return;
    }
    const updated = await toggleActionPlanStep(plan.value.planId, stepKey, done);
    if (updated?.steps) {
      plan.value = updated;
    } else if (plan.value?.steps) {
      plan.value = {
        ...plan.value,
        steps: plan.value.steps.map((s) => (s.id === stepKey ? { ...s, done: !!done } : s)),
      };
    }
    if (done && options.promptTiming !== false) {
      timingMinutes.value = '';
      timingPrompt.value = { stepKey };
    } else if (!done) {
      dismissTiming();
    }
  }

  async function submitTiming(toolId) {
    if (!plan.value?.planId || !timingPrompt.value?.stepKey) return;
    const minutes = Number(timingMinutes.value);
    if (!Number.isFinite(minutes) || minutes < 0 || minutes > 1440) {
      return;
    }
    timingSaving.value = true;
    try {
      await postTiming(plan.value.planId, timingPrompt.value.stepKey, {
        minutes: Math.round(minutes),
        ...(toolId ? { toolId } : {}),
      });
      dismissTiming();
    } finally {
      timingSaving.value = false;
    }
  }

  function dismissTiming() {
    timingPrompt.value = null;
    timingMinutes.value = '';
  }

  function setPlan(value) {
    plan.value = value;
  }

  function setPath(value) {
    const next = String(value || 'open_source');
    path.value = ['open_source', 'paid', 'mix'].includes(next) ? next : 'open_source';
    if (openStepKey.value && plan.value?.planId) {
      openStep(openStepKey.value);
    }
  }

  return {
    plan,
    loading,
    error,
    pasteRequired,
    limitReached,
    path,
    setPath,
    openStepKey,
    stepDetail,
    stepDetailLoading,
    stepDetailError,
    drawerOpen,
    timingPrompt,
    timingMinutes,
    timingSaving,
    steps,
    pathTotals,
    progress,
    generate,
    load,
    openStep,
    closeStep,
    toggleStep,
    submitTiming,
    dismissTiming,
    setPlan,
  };
}

function formatMinutes(min) {
  const n = Math.max(0, Number(min) || 0);
  const h = Math.floor(n / 60);
  const m = n % 60;
  if (h <= 0) return `${m}m`;
  if (m === 0) return `${h}h`;
  return `${h}h ${m}m`;
}

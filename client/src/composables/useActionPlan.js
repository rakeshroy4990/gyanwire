import { computed, ref, watch } from 'vue';
import {
  fetchActionPlan,
  postTiming,
  toggleActionPlanStep,
} from '../services/actionPlan.service.js';
import {
  clearSession,
  matchesPlanId,
  sessionActive,
  sessionError,
  sessionPlan,
  sessionStage,
  stageLabel,
  startCreate,
} from './actionPlanSession.js';

export function useActionPlan() {
  const plan = ref(null);
  const loading = ref(false);
  const error = ref('');
  const pasteRequired = ref(false);
  const limitReached = ref(false);
  const buildStopped = ref(false);
  const stage = ref('');

  const path = ref('open_source');
  const timingPrompt = ref(null);
  const timingMinutes = ref('');
  const timingSaving = ref(false);

  const steps = computed(() => (Array.isArray(plan.value?.steps) ? plan.value.steps : []));
  const doneCount = computed(() => steps.value.filter((s) => s.done).length);
  const pathTotals = computed(() => plan.value?.pathTotals || null);
  const buildStatus = computed(() => String(plan.value?.buildStatus || 'ready'));
  const isBuilding = computed(() => buildStatus.value === 'building');
  const isReady = computed(() => buildStatus.value === 'ready' || !plan.value?.buildStatus);
  const stageText = computed(() => stageLabel(stage.value) || stageLabel(sessionStage.value));

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

  watch(sessionPlan, (value) => {
    if (value && matchesPlanId(value.planId) && plan.value?.planId === value.planId) {
      plan.value = value;
      if (value.buildStatus === 'ready') {
        pasteRequired.value = false;
        buildStopped.value = false;
      }
      if (value.buildStatus === 'failed' || value.buildStatus === 'paste') {
        pasteRequired.value = false;
        buildStopped.value = true;
      }
    }
  });

  watch(sessionStage, (value) => {
    if (sessionActive.value && matchesPlanId(plan.value?.planId)) {
      stage.value = value || '';
    }
  });

  watch(sessionError, (value) => {
    if (!value || !matchesPlanId(plan.value?.planId)) return;
    pasteRequired.value = false;
    if (plan.value?.buildStatus === 'failed' || plan.value?.buildStatus === 'paste') {
      buildStopped.value = true;
    }
    error.value = value;
  });

  async function generate(payload) {
    loading.value = true;
    error.value = '';
    pasteRequired.value = false;
    limitReached.value = false;
    buildStopped.value = false;
    stage.value = '';
    try {
      const first = await startCreate(payload);
      plan.value = first;
      loading.value = false;
      return first;
    } catch (err) {
      if (err.status === 402 || err.code === 'LIMIT_REACHED') {
        limitReached.value = true;
      }
      if (err.status === 422 || err.code === 'ARTICLE_UNREADABLE' || err.code === 'BRIEF_UNUSABLE') {
        if (sessionPlan.value) plan.value = sessionPlan.value;
        buildStopped.value = true;
      }
      error.value = err.message || 'Could not build the plan.';
      throw err;
    } finally {
      loading.value = false;
    }
  }

  async function load(id) {
    if (matchesPlanId(id) && sessionPlan.value) {
      plan.value = sessionPlan.value;
      loading.value = false;
      error.value = '';
      pasteRequired.value = false;
      buildStopped.value =
        sessionPlan.value.buildStatus === 'failed'
        || sessionPlan.value.buildStatus === 'paste'
        || (sessionPlan.value.buildStatus === 'building' && !sessionActive.value);
      stage.value = sessionStage.value || '';
      return plan.value;
    }

    loading.value = true;
    error.value = '';
    pasteRequired.value = false;
    buildStopped.value = false;
    stage.value = '';
    try {
      const loaded = await fetchActionPlan(id);
      plan.value = loaded;
      if (loaded?.buildStatus === 'failed' || loaded?.buildStatus === 'paste') {
        buildStopped.value = true;
      } else if (loaded?.buildStatus === 'building' && !sessionActive.value) {
        buildStopped.value = true;
      }
      return loaded;
    } catch (err) {
      error.value = err.message || 'Plan not found.';
      throw err;
    } finally {
      loading.value = false;
    }
  }

  async function toggleStep(stepKey, done, options = {}) {
    if (!isReady.value) return;
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
  }

  return {
    plan,
    loading,
    error,
    pasteRequired,
    limitReached,
    buildStopped,
    stage,
    stageText,
    isBuilding,
    isReady,
    buildStatus,
    path,
    setPath,
    timingPrompt,
    timingMinutes,
    timingSaving,
    steps,
    pathTotals,
    progress,
    generate,
    load,
    toggleStep,
    submitTiming,
    dismissTiming,
    setPlan,
    clearSession,
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

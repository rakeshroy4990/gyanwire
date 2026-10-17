import { ref } from 'vue';
import { createActionPlan } from '../services/actionPlan.service.js';

const STAGE_LABELS = {
  brief: 'Reading the article',
  angles: 'Choosing the angle',
  slots: 'Writing the steps',
};

/** Module-level so navigation does not abort the in-flight stream. */
export const sessionPlan = ref(null);
export const sessionStage = ref('');
export const sessionError = ref('');
export const sessionActive = ref(false);
let streamPromise = null;

export function useActionPlanSession() {
  return {
    sessionPlan,
    sessionStage,
    sessionError,
    sessionActive,
    stageLabel,
    startCreate,
    clearSession,
    matchesPlanId,
  };
}

export function stageLabel(stage) {
  return STAGE_LABELS[stage] || '';
}

export function matchesPlanId(id) {
  if (!id || !sessionPlan.value?.planId) return false;
  return String(sessionPlan.value.planId) === String(id);
}

export function clearSession() {
  sessionPlan.value = null;
  sessionStage.value = '';
  sessionError.value = '';
  sessionActive.value = false;
  streamPromise = null;
}

/**
 * Starts the NDJSON create stream. Resolves when the first plan line
 * arrives (or rejects on an early error). The rest of the stream keeps
 * updating sessionPlan / sessionStage in the background.
 */
export function startCreate(body) {
  sessionError.value = '';
  sessionStage.value = '';
  sessionActive.value = true;

  let firstPlanResolve;
  let firstPlanReject;
  let firstPlanSettled = false;
  const firstPlan = new Promise((resolve, reject) => {
    firstPlanResolve = resolve;
    firstPlanReject = reject;
  });

  streamPromise = createActionPlan(body, async (event) => {
    if (event.type === 'status') {
      sessionStage.value = event.stage || '';
      return;
    }
    if (event.type === 'plan' && event.plan) {
      sessionPlan.value = event.plan;
      if (!firstPlanSettled) {
        firstPlanSettled = true;
        firstPlanResolve(event.plan);
      }
    }
  })
    .then((plan) => {
      if (plan) sessionPlan.value = plan;
      sessionStage.value = '';
      sessionActive.value = false;
      if (!firstPlanSettled) {
        firstPlanSettled = true;
        if (plan) firstPlanResolve(plan);
        else firstPlanReject(new Error('Could not build the plan.'));
      }
      return plan;
    })
    .catch((err) => {
      sessionError.value = err.message || 'Could not build the plan.';
      sessionStage.value = '';
      sessionActive.value = false;
      if (err.code === 'ARTICLE_UNREADABLE' && sessionPlan.value) {
        sessionPlan.value = {
          ...sessionPlan.value,
          buildStatus: 'paste',
        };
      }
      if (!firstPlanSettled) {
        firstPlanSettled = true;
        firstPlanReject(err);
      }
      // Do not rethrow: the sheet already left on the first plan line, and
      // ActionPlanPage watches session state for paste / error updates.
    });

  return firstPlan;
}

export function waitForStream() {
  return streamPromise || Promise.resolve(sessionPlan.value);
}

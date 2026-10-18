<script setup>
import { onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useActionPlan } from '../composables/useActionPlan.js';
import PlanStepList from '../components/actionplan/PlanStepList.vue';
import PlanProgress from '../components/actionplan/PlanProgress.vue';
import PlanCard from '../components/actionplan/PlanCard.vue';
import PathSwitch from '../components/actionplan/PathSwitch.vue';
import PageSkeleton from '../components/ui/PageSkeleton.vue';

const route = useRoute();
const router = useRouter();
const {
  plan,
  loading,
  error,
  buildStopped,
  stageText,
  isBuilding,
  isReady,
  progress,
  path,
  setPath,
  pathTotals,
  timingPrompt,
  timingMinutes,
  timingSaving,
  load,
  toggleStep,
  submitTiming,
  dismissTiming,
} = useActionPlan();
onMounted(() => {
  if (route.params.id) {
    load(route.params.id).catch(() => {});
  }
});

watch(
  () => route.params.id,
  (id) => {
    if (id) load(id).catch(() => {});
  },
);

function onPath(value) {
  setPath(value);
}

function onToggle(stepKey, done) {
  if (!isReady.value) return;
  toggleStep(stepKey, done);
}

function onOpen(stepKey) {
  if (!isReady.value || !plan.value?.planId || !stepKey) return;
  const pathParam = path.value === 'mix' ? 'open_source' : path.value;
  router.push({
    name: 'action-plan-step',
    params: { id: plan.value.planId, stepKey },
    query: pathParam && pathParam !== 'open_source' ? { path: pathParam } : {},
  });
}

function goBack() {
  if (window.history.length > 1) {
    router.back();
    return;
  }
  router.push({ name: 'research' });
}
</script>

<template>
  <main class="account-page action-plan-page">
    <button type="button" class="page-back" @click="goBack">← Back</button>

    <PageSkeleton v-if="loading && !plan" variant="list" label="Loading plan" />
    <p v-else-if="error" class="error">{{ error }}</p>

    <template v-else-if="plan">
      <PlanCard :plan="plan" />
      <p v-if="isBuilding && stageText" class="action-plan-page__stage" aria-live="polite">
        {{ stageText }}…
      </p>
      <p v-else-if="buildStopped || plan.buildStatus === 'failed'" class="action-plan-page__stage">
        This build stopped. Go back to ideas and try Make plan again — we search related articles when the original page is blocked.
      </p>
      <PathSwitch :model-value="path" @update:model-value="onPath" />
      <PlanProgress :progress="progress" :path-totals="pathTotals" />
      <PlanStepList
        :steps="plan.steps || []"
        :path="path"
        @toggle="onToggle"
        @open="onOpen"
      />

      <div v-if="timingPrompt" class="timing-prompt" role="dialog" aria-modal="true" aria-label="How long did this take">
        <p>How many minutes did this step take?</p>
        <div class="timing-prompt__row">
          <input
            v-model="timingMinutes"
            type="number"
            min="0"
            max="1440"
            inputmode="numeric"
            class="timing-prompt__input"
            aria-label="Actual minutes"
          />
          <button
            type="button"
            class="btn btn--primary"
            :disabled="timingSaving"
            @click="submitTiming()"
          >
            Save
          </button>
          <button type="button" class="btn" @click="dismissTiming">Skip</button>
        </div>
      </div>
    </template>
  </main>
</template>

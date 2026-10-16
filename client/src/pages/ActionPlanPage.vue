<script setup>
import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useActionPlan } from '../composables/useActionPlan.js';
import PlanStepList from '../components/actionplan/PlanStepList.vue';
import PlanProgress from '../components/actionplan/PlanProgress.vue';
import PlanCard from '../components/actionplan/PlanCard.vue';
import PathSwitch from '../components/actionplan/PathSwitch.vue';
import StepDrawer from '../components/actionplan/StepDrawer.vue';
import PageSkeleton from '../components/ui/PageSkeleton.vue';

const route = useRoute();
const {
  plan,
  loading,
  error,
  pasteRequired,
  progress,
  path,
  setPath,
  pathTotals,
  drawerOpen,
  stepDetail,
  stepDetailLoading,
  stepDetailError,
  timingPrompt,
  timingMinutes,
  timingSaving,
  load,
  generate,
  toggleStep,
  openStep,
  closeStep,
  submitTiming,
  dismissTiming,
} = useActionPlan();
const pasted = ref('');

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

async function submitPaste() {
  await generate({
    newsUrl: plan.value?.newsBrief?.publisherUrl || route.query.url || 'https://example.com/news',
    title: plan.value?.newsBrief?.headline || route.query.title || 'News',
    pastedText: pasted.value,
    budgetMinutes: 240,
    save: true,
  });
}

function onPath(value) {
  setPath(value);
}
</script>

<template>
  <main class="account-page action-plan-page">
    <PageSkeleton v-if="loading && !plan" variant="list" label="Loading plan" />
    <p v-else-if="error && !pasteRequired" class="error">{{ error }}</p>

    <template v-else-if="pasteRequired">
      <h1>Could not read enough of this article</h1>
      <p>Paste the article text below (at least a few paragraphs).</p>
      <textarea v-model="pasted" rows="12" class="action-plan-page__paste" />
      <button type="button" class="btn btn--primary" @click="submitPaste">Build plan</button>
    </template>

    <template v-else-if="plan">
      <PlanCard :plan="plan" />
      <p v-if="plan.lint && plan.lint.passed === false" class="action-plan-card__warn">Check this plan</p>
      <PathSwitch :model-value="path" @update:model-value="onPath" />
      <PlanProgress :progress="progress" :path-totals="pathTotals" />
      <PlanStepList
        :steps="plan.steps || []"
        :path="path"
        @toggle="toggleStep"
        @open="openStep"
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

    <StepDrawer
      :open="drawerOpen"
      :detail="stepDetail"
      :loading="stepDetailLoading"
      :error="stepDetailError"
      :path="path"
      @close="closeStep"
    />
  </main>
</template>

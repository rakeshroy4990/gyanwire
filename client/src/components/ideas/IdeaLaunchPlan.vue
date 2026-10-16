<script setup>
import { computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useActionPlan } from '../../composables/useActionPlan.js';
import PlanStepList from '../actionplan/PlanStepList.vue';
import PlanProgress from '../actionplan/PlanProgress.vue';
import PlanCard from '../actionplan/PlanCard.vue';
import PathSwitch from '../actionplan/PathSwitch.vue';
import StepDrawer from '../actionplan/StepDrawer.vue';

const props = defineProps({
  plan: { type: Object, required: true },
});

const router = useRouter();
const {
  plan: localPlan,
  setPlan,
  path,
  setPath,
  pathTotals,
  progress,
  drawerOpen,
  stepDetail,
  stepDetailLoading,
  stepDetailError,
  timingPrompt,
  timingMinutes,
  timingSaving,
  toggleStep,
  openStep,
  closeStep,
  submitTiming,
  dismissTiming,
} = useActionPlan();

const actionPlan = computed(() => props.plan?.kind === 'action_plan' && Array.isArray(props.plan?.steps));

watch(
  () => props.plan,
  (plan) => {
    setPlan(plan ? { ...plan } : null);
  },
  { immediate: true, deep: true },
);

function openFull() {
  if (props.plan?.planId) {
    router.push({ name: 'action-plan', params: { id: props.plan.planId } });
  }
}

async function onOpen(stepKey) {
  if (localPlan.value?.planId) {
    await openStep(stepKey);
  }
}
</script>

<template>
  <article v-if="actionPlan" class="launch-plan launch-plan--action">
    <PlanCard :plan="plan" />
    <p v-if="plan.lint && plan.lint.passed === false" class="action-plan-card__warn">Check this plan</p>
    <p v-if="plan.money?.claim" class="launch-plan__money">{{ plan.money.claim }}</p>
    <PathSwitch :model-value="path" @update:model-value="setPath" />
    <PlanProgress :progress="progress" :path-totals="pathTotals || plan.pathTotals" />
    <button v-if="plan.planId" type="button" class="btn" @click="openFull">Open full plan</button>
    <PlanStepList
      :steps="localPlan?.steps || plan.steps || []"
      :path="path"
      @toggle="toggleStep"
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
        <button type="button" class="btn btn--primary" :disabled="timingSaving" @click="submitTiming()">
          Save
        </button>
        <button type="button" class="btn" @click="dismissTiming">Skip</button>
      </div>
    </div>

    <StepDrawer
      :open="drawerOpen"
      :detail="stepDetail"
      :loading="stepDetailLoading"
      :error="stepDetailError"
      :path="path"
      @close="closeStep"
    />
  </article>

  <article v-else class="launch-plan">
    <p class="account-page__kicker">Launch plan</p>
    <h2>{{ plan.landingCopy?.headline || 'This week' }}</h2>
    <p v-if="plan.customer"><span class="idea-card__label">Customer:</span> {{ plan.customer }}</p>
    <p v-if="plan.offer"><span class="idea-card__label">Offer:</span> {{ plan.offer }}</p>
    <p v-if="plan.landingCopy?.subhead">{{ plan.landingCopy.subhead }}</p>
    <p v-if="plan.landingCopy?.cta" class="launch-plan__cta">{{ plan.landingCopy.cta }}</p>
    <h3>7-day schedule</h3>
    <ol class="launch-plan__days">
      <li v-for="step in plan.schedule || []" :key="step.day">
        <strong>{{ step.day }}.</strong> {{ step.task }}
      </li>
    </ol>
    <h3>Outreach</h3>
    <ul class="launch-plan__notes">
      <li v-for="(message, index) in plan.outreach || []" :key="index">{{ message }}</li>
    </ul>
    <p v-if="plan.validationMetric"><span class="idea-card__label">Validation metric:</span> {{ plan.validationMetric }}</p>
  </article>
</template>

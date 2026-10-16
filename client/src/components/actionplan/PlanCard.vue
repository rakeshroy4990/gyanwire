<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';

const props = defineProps({
  plan: { type: Object, required: true },
  saveable: { type: Boolean, default: false },
});

const emit = defineEmits(['save']);
const router = useRouter();

const angle = computed(() => props.plan?.angle || {});
const platforms = computed(() => props.plan?.platforms || []);
const budget = computed(() => Number(props.plan?.budgetMinutes) || 240);

function openPlan() {
  if (props.plan?.planId) {
    router.push({ name: 'action-plan', params: { id: props.plan.planId } });
  }
}
</script>

<template>
  <article class="action-plan-card">
    <p class="account-page__kicker">Social media · {{ Math.round(budget / 60) }} hours</p>
    <h2>{{ plan.label || 'Quick short' }}</h2>
    <p v-if="angle.oneLine">{{ angle.oneLine }}</p>
    <p v-if="angle.whyNow"><strong>Why now:</strong> {{ angle.whyNow }}</p>
    <p v-if="angle.test"><strong>Test:</strong> {{ angle.test }}</p>
    <p class="action-plan-card__tags">
      <span v-for="p in platforms" :key="p">{{ p }}</span>
    </p>
    <p v-if="plan.lint && plan.lint.passed === false" class="action-plan-card__warn">Check this plan</p>
    <div class="action-plan-card__actions">
      <button type="button" class="btn btn--primary" @click="openPlan">Open plan</button>
      <button v-if="saveable" type="button" class="btn" @click="emit('save')">Save</button>
    </div>
  </article>
</template>

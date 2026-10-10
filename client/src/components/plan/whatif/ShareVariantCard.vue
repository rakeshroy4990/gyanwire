<script setup>
import { computed } from 'vue';
import { formatInr, simulatePlan } from '../../../composables/usePlanSimulator.js';

const props = defineProps({
  title: { type: String, default: 'Plan to finish' },
  variants: { type: Array, default: () => [] },
  weeks: { type: Array, default: () => [] },
  catalog: { type: Array, default: () => [] },
  monthsToGoal: { type: Number, default: 1 },
  hoursPerWeek: { type: Number, default: 5 },
});

const lines = computed(() => props.variants.map((v) => {
  const sim = simulatePlan({
    weeks: props.weeks,
    weekOptions: v.weekOptions,
    catalog: props.catalog,
    monthsToGoal: props.monthsToGoal,
    weeklyCapacityHours: props.hoursPerWeek,
  });
  return `${v.name}: ${formatInr(sim.costInr)}, ~${sim.savedHours[0].toFixed(0)}–${sim.savedHours[1].toFixed(0)} hrs saved (estimate)`;
}));
</script>

<template>
  <aside class="plan-share-card" aria-label="Shareable comparison">
    <h3>{{ title }}</h3>
    <p class="plan-efficiency__hint">Variant comparison</p>
    <p v-for="line in lines" :key="line">{{ line }}</p>
  </aside>
</template>

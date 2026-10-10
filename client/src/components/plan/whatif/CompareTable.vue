<script setup>
import { computed } from 'vue';
import { formatInr, simulatePlan } from '../../../composables/usePlanSimulator.js';

const props = defineProps({
  variants: { type: Array, default: () => [] },
  weeks: { type: Array, default: () => [] },
  catalog: { type: Array, default: () => [] },
  monthsToGoal: { type: Number, default: 1 },
  hoursPerWeek: { type: Number, default: 5 },
});

const rows = computed(() => props.variants.map((v) => {
  const sim = simulatePlan({
    weeks: props.weeks,
    weekOptions: v.weekOptions,
    catalog: props.catalog,
    monthsToGoal: props.monthsToGoal,
    hourlyValueInr: v.hourlyValueInr,
    weeklyCapacityHours: props.hoursPerWeek,
  });
  const confidences = sim.assumptions.map((a) => a.confidence);
  const confidence = confidences.includes('placeholder')
    ? 'mixed / placeholder'
    : confidences.includes('measured')
      ? 'measured'
      : 'baseline';
  return {
    id: v.id,
    name: v.name,
    cost: formatInr(sim.costInr),
    saved: `${Math.max(0, sim.savedHours[0]).toFixed(0)}–${Math.max(0, sim.savedHours[1]).toFixed(0)} hrs`,
    shortened: `${sim.weeksShortened[0].toFixed(1)}–${sim.weeksShortened[1].toFixed(1)} wks`,
    confidence,
  };
}));
</script>

<template>
  <table class="plan-compare">
    <thead>
      <tr>
        <th>Variant</th>
        <th>Cost</th>
        <th>Hours saved (estimate)</th>
        <th>Weeks shortened (estimate)</th>
        <th>Confidence</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="row in rows" :key="row.id">
        <td data-label="Variant">{{ row.name }}</td>
        <td data-label="Cost">{{ row.cost }}</td>
        <td data-label="Hours saved (estimate)">{{ row.saved }}</td>
        <td data-label="Weeks shortened (estimate)">{{ row.shortened }}</td>
        <td data-label="Confidence">{{ row.confidence }}</td>
      </tr>
    </tbody>
  </table>
</template>

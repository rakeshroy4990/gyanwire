<script setup>
import { computed } from 'vue';
import { formatInr } from '../../../composables/usePlanSimulator.js';
import AssumptionsPopover from './AssumptionsPopover.vue';

const props = defineProps({
  simulation: { type: Object, default: null },
  hoursPerSitting: { type: Number, default: 5 },
});

const savedLabel = computed(() => {
  const s = props.simulation?.savedHours;
  if (!s) return '—';
  const lo = Math.max(0, s[0]);
  const hi = Math.max(0, s[1]);
  if (hi < 0.05) return 'About 0 hrs saved';
  return `About ${lo.toFixed(0)}–${hi.toFixed(0)} hrs saved`;
});

const bandStyle = computed(() => {
  const s = props.simulation?.savedHours || [0, 0];
  const hi = Math.max(s[1], 0.01);
  const loPct = Math.max(0, (s[0] / hi) * 100);
  const width = Math.min(100, Math.max(12, ((s[1] - s[0]) / Math.max(hi, 1)) * 100 + 24));
  return { left: `${Math.min(70, loPct * 0.3)}%`, width: `${width}%` };
});

const costLabel = computed(() => formatInr(props.simulation?.costInr || 0));
const perHour = computed(() => {
  const r = props.simulation?.costPerHourSaved;
  if (!r) return '—';
  return `${formatInr(r[0])}–${formatInr(r[1])} / hr saved`;
});
</script>

<template>
  <section class="plan-efficiency" aria-label="Efficiency meter">
    <p class="plan-efficiency__title">Efficiency <span class="plan-efficiency__est">estimate</span></p>
    <p class="plan-efficiency__range">{{ savedLabel }}</p>
    <div class="plan-efficiency__band" aria-hidden="true">
      <div class="plan-efficiency__band-fill" :style="bandStyle" />
    </div>
    <p class="plan-efficiency__hint">
      Variant cost {{ costLabel }}
      <template v-if="simulation?.freeUpgrade"> · free upgrade (estimate)</template>
      <template v-else-if="simulation?.unclearWorth"> · not clearly worth it</template>
      <template v-else> · {{ perHour }}</template>
    </p>
    <AssumptionsPopover
      :assumptions="simulation?.assumptions || []"
      :hours-per-sitting="hoursPerSitting"
    />
  </section>
</template>

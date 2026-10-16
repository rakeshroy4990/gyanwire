<script setup>
import { computed } from 'vue';
import GainBadge from './GainBadge.vue';

const props = defineProps({
  progress: { type: Object, default: () => ({ label: '' }) },
  pathTotals: { type: Object, default: null },
});

const totals = computed(() => props.pathTotals || null);

function formatMinutes(min) {
  const n = Math.max(0, Number(min) || 0);
  const h = Math.floor(n / 60);
  const m = n % 60;
  if (h <= 0) return `${m}m`;
  if (m === 0) return `${h}h`;
  return `${h}h ${m}m`;
}
</script>

<template>
  <div class="plan-progress">
    <p v-if="progress.label" class="plan-progress__label">{{ progress.label }}</p>
    <div v-if="totals" class="plan-progress__paths">
      <span>Open Source {{ formatMinutes(totals.openSourceMinutes) }}</span>
      <span aria-hidden="true">·</span>
      <span>Paid {{ formatMinutes(totals.paidMinutes) }}</span>
      <GainBadge
        v-if="totals.gainPct != null"
        :pct="totals.gainPct"
        :basis="totals.basis || 'estimate'"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import GainBadge from './GainBadge.vue';

const props = defineProps({
  costInrMonth: { type: [Number, String], default: null },
  openSourceMinutes: { type: [Number, String], default: 0 },
  paidMinutes: { type: [Number, String], default: 0 },
  gainPct: { type: [Number, String], default: 0 },
  gainBasis: { type: String, default: 'estimate' },
  whatChanges: { type: String, default: '' },
  giveUp: { type: String, default: '' },
});

const costLabel = computed(() => {
  const n = Number(props.costInrMonth);
  if (!Number.isFinite(n) || n <= 0) return 'Cost varies';
  return `₹${Math.round(n)}/month`;
});

function formatMin(min) {
  const n = Math.max(0, Number(min) || 0);
  return `${n} min`;
}
</script>

<template>
  <section class="paid-benefit">
    <p class="step-card__label">Paid path</p>
    <dl class="paid-benefit__grid">
      <div>
        <dt>Cost</dt>
        <dd>{{ costLabel }}</dd>
      </div>
      <div>
        <dt>Time</dt>
        <dd>{{ formatMin(openSourceMinutes) }} → {{ formatMin(paidMinutes) }}</dd>
      </div>
      <div>
        <dt>Gain</dt>
        <dd><GainBadge :pct="gainPct" :basis="gainBasis" /></dd>
      </div>
    </dl>
    <p v-if="whatChanges"><strong>What changes:</strong> {{ whatChanges }}</p>
    <p v-if="giveUp"><strong>What you give up:</strong> {{ giveUp }}</p>
  </section>
</template>

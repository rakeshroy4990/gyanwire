<script setup>
import { computed } from 'vue';

const props = defineProps({
  pct: { type: [Number, String], default: 0 },
  basis: { type: String, default: 'estimate' },
  label: { type: String, default: '' },
});

const displayPct = computed(() => {
  const n = Number(props.pct);
  if (!Number.isFinite(n)) return '0%';
  const rounded = Math.round(n * 10) / 10;
  return `${rounded % 1 === 0 ? Math.round(rounded) : rounded}%`;
});

const basisLabel = computed(() => {
  const raw = String(props.label || props.basis || 'estimate').toLowerCase();
  return raw === 'measured' ? 'measured' : 'estimate';
});
</script>

<template>
  <span class="gain-badge" :class="`gain-badge--${basisLabel}`">
    <span class="gain-badge__pct">{{ displayPct }}</span>
    <span class="gain-badge__basis">{{ basisLabel }}</span>
  </span>
</template>

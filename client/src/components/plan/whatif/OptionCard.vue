<script setup>
import { computed } from 'vue';
import { formatInr, speedupFor } from '../../../composables/usePlanSimulator.js';

const props = defineProps({
  option: { type: Object, required: true },
  taskType: { type: String, required: true },
  selected: { type: Boolean, default: false },
});

defineEmits(['select']);

const fit = computed(() => speedupFor(props.option, props.taskType));
const bandWidth = computed(() => {
  const hi = fit.value.hi;
  return `${Math.max(8, Math.min(100, ((hi - 1) / 1) * 100))}%`;
});
const priceLabel = computed(() => {
  const o = props.option;
  if (o.stale) return `${formatInr(o.costInr)} · check current price`;
  if (o.billing === 'free' || !o.costInr) return 'Free';
  return `${formatInr(o.costInr)} · ${o.billing}`;
});
const badge = computed(() => {
  const c = fit.value.confidence;
  if (c === 'measured') return `measured (n=${fit.value.sampleCount || props.option.sampleCount || 0})`;
  return c || 'placeholder';
});
</script>

<template>
  <button
    type="button"
    class="plan-option"
    :class="{ 'is-selected': selected }"
    @click="$emit('select', option)"
  >
    <div class="plan-option__top">
      <strong>{{ option.name }}</strong>
      <span
        class="plan-badge"
        :class="{
          'plan-badge--placeholder': badge === 'placeholder',
          'plan-badge--measured': String(badge).startsWith('measured'),
        }"
      >{{ badge }}</span>
    </div>
    <div>{{ priceLabel }}</div>
    <div class="plan-option__band" :title="`Estimate speedup ${fit.lo}–${fit.hi}×`">
      <div class="plan-option__band-fill" :style="{ width: bandWidth }" />
    </div>
    <div class="plan-efficiency__hint">Efficiency band (estimate) {{ fit.lo.toFixed(2) }}–{{ fit.hi.toFixed(2) }}×</div>
  </button>
</template>

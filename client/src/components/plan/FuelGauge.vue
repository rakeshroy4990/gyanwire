<script setup>
import { computed } from 'vue';
import { formatInr } from '../../composables/usePlanSimulator.js';

const props = defineProps({
  budget: { type: Number, default: 0 },
  spent: { type: Number, default: 0 },
  label: { type: String, default: 'Fuel' },
});

const CIRCUMFERENCE = 2 * Math.PI * 54;

const ratio = computed(() => {
  const budget = Math.max(0, Number(props.budget) || 0);
  const spent = Math.max(0, Number(props.spent) || 0);
  if (budget <= 0) return spent > 0 ? 1 : 0;
  return Math.min(1, spent / budget);
});

const ringOffset = computed(() => CIRCUMFERENCE * (1 - ratio.value));
const leftover = computed(() => Math.max(0, (Number(props.budget) || 0) - (Number(props.spent) || 0)));
</script>

<template>
  <section class="plan-fuel" aria-label="Fuel gauge — skill budget">
    <p class="plan-fuel__label">{{ label }}</p>
    <div class="plan-fuel__row">
      <div class="plan-fuel__ring-wrap">
        <svg class="plan-fuel__ring" viewBox="0 0 120 120" aria-hidden="true">
          <circle class="plan-fuel__track" cx="60" cy="60" r="54" />
          <circle
            class="plan-fuel__fill"
            cx="60"
            cy="60"
            r="54"
            :stroke-dasharray="CIRCUMFERENCE"
            :stroke-dashoffset="ringOffset"
          />
        </svg>
        <div class="plan-fuel__center">{{ formatInr(spent) }}</div>
      </div>
      <p class="plan-fuel__meta">
        {{ formatInr(spent) }} of {{ formatInr(budget) }} skill budget.
        {{ formatInr(leftover) }} left this month.
      </p>
    </div>
  </section>
</template>

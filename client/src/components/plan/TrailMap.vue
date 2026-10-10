<script setup>
import { savedChipLabel } from '../../composables/usePlanSimulator.js';

defineProps({
  weeks: { type: Array, default: () => [] },
  activeWeek: { type: Number, default: null },
  weekResults: { type: Array, default: () => [] },
  showGhost: { type: Boolean, default: false },
});

const emit = defineEmits(['select']);

function resultFor(weekNo, weekResults) {
  return (weekResults || []).find((r) => r.weekNo === weekNo);
}
</script>

<template>
  <div class="plan-trail">
    <div class="plan-trail__scroll">
      <div class="plan-trail__track">
        <div class="plan-trail__line" aria-hidden="true" />
        <div v-if="showGhost" class="plan-trail__ghost" aria-hidden="true" />
        <button
          v-for="week in weeks"
          :key="week.weekNo"
          type="button"
          class="plan-trail__node"
          :class="{
            'is-active': activeWeek === week.weekNo,
            'is-faster': (resultFor(week.weekNo, weekResults)?.savedRange?.[1] || 0) > 0.05,
          }"
          :aria-pressed="activeWeek === week.weekNo"
          :aria-label="`Week ${week.weekNo}: ${week.goal}`"
          @click="emit('select', week.weekNo)"
        >
          <span class="plan-trail__dot">{{ week.weekNo }}</span>
          <span
            v-if="savedChipLabel(resultFor(week.weekNo, weekResults)?.savedRange)"
            class="plan-trail__chip"
          >
            {{ savedChipLabel(resultFor(week.weekNo, weekResults)?.savedRange) }}
          </span>
          <span class="plan-trail__goal">{{ week.goal }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

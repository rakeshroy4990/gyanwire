<script setup>
import { computed } from 'vue';
import { formatInr } from '../../composables/usePlanSimulator.js';

const props = defineProps({
  open: { type: Boolean, default: false },
  week: { type: Object, default: null },
  optionName: { type: String, default: '' },
  optionCost: { type: Number, default: 0 },
  billing: { type: String, default: '' },
});

const emit = defineEmits(['close', 'swap']);

const title = computed(() => (props.week ? `Week ${props.week.weekNo}` : 'Week'));

function onKey(e) {
  if (e.key === 'Escape') emit('close');
}
</script>

<template>
  <div
    class="plan-sheet"
    :class="{ 'is-open': open }"
    role="dialog"
    aria-modal="true"
    :aria-hidden="!open"
    @keydown="onKey"
  >
    <button type="button" class="plan-sheet__backdrop" aria-label="Close week" @click="emit('close')" />
    <div v-if="week" class="plan-sheet__panel">
      <div class="plan-sheet__head">
        <div>
          <p class="plan-page__eyebrow">{{ title }} · {{ week.taskType || 'task' }}</p>
          <h2>{{ week.goal }}</h2>
        </div>
        <button type="button" class="plan-sheet__close" aria-label="Close" @click="emit('close')">×</button>
      </div>

      <div class="plan-sheet__tool">
        <div>
          <strong>{{ optionName || week.toolName }}</strong>
          <div>{{ formatInr(optionCost ?? week.costInr) }} · {{ billing || week.billing }}</div>
        </div>
        <button type="button" class="btn btn--ghost" @click="emit('swap')">Swap</button>
      </div>

      <ul class="plan-sheet__tasks">
        <li v-for="task in week.tasks" :key="task">{{ task }}</li>
      </ul>
      <p class="plan-timeline__metric">Done when: {{ week.metric }}.</p>
    </div>
  </div>
</template>

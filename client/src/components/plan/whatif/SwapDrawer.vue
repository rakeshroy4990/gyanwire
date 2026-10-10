<script setup>
import { computed } from 'vue';
import OptionCard from './OptionCard.vue';

const props = defineProps({
  open: { type: Boolean, default: false },
  taskType: { type: String, default: 'learning' },
  options: { type: Array, default: () => [] },
  selectedId: { type: String, default: '' },
});

const emit = defineEmits(['close', 'select']);

const ordered = computed(() => {
  const rows = [...(props.options || [])];
  rows.sort((a, b) => {
    const af = (a.billing === 'free' || !a.costInr) ? 0 : 1;
    const bf = (b.billing === 'free' || !b.costInr) ? 0 : 1;
    if (af !== bf) return af - bf;
    return String(a.name).localeCompare(String(b.name));
  });
  return rows;
});
</script>

<template>
  <div v-if="open" class="plan-drawer" role="dialog" aria-modal="true" aria-label="Swap gear">
    <button type="button" class="plan-drawer__backdrop" aria-label="Close" @click="emit('close')" />
    <div class="plan-drawer__panel">
      <div class="plan-sheet__head">
        <div>
          <p class="plan-page__eyebrow">Swap the gear</p>
          <h2>Options for {{ taskType }}</h2>
        </div>
        <button type="button" class="plan-sheet__close" @click="emit('close')">×</button>
      </div>
      <p class="plan-page__section-lede">Estimates are ranges. Free options first. No sponsored order.</p>
      <OptionCard
        v-for="option in ordered"
        :key="option.id"
        :option="option"
        :task-type="taskType"
        :selected="option.id === selectedId"
        @select="emit('select', $event)"
      />
      <p v-if="!ordered.length" class="plan-efficiency__hint">No catalog options for this task type yet.</p>
    </div>
  </div>
</template>

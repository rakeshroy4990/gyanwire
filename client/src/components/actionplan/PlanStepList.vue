<script setup>
import StepCard from './StepCard.vue';

defineProps({
  steps: { type: Array, default: () => [] },
  path: { type: String, default: 'open_source' },
});

defineEmits(['toggle', 'open']);
</script>

<template>
  <div class="plan-step-list">
    <StepCard
      v-for="step in steps"
      :key="step.id"
      :step="step"
      :path="path"
      @toggle="(id, done) => $emit('toggle', id, done)"
      @open="(id) => $emit('open', id)"
    />
  </div>
</template>

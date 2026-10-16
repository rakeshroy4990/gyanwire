<script setup>
defineProps({
  steps: { type: Array, default: () => [] },
});
</script>

<template>
  <ol v-if="steps.length" class="micro-steps">
    <li v-for="(step, index) in steps" :key="index">{{ step }}</li>
  </ol>
  <p v-else class="micro-steps micro-steps--empty">No micro-steps for this path yet.</p>
</template>

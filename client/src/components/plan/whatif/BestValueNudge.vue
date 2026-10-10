<script setup>
defineProps({
  message: { type: String, default: '' },
  suggestion: { type: Object, default: null },
});

defineEmits(['apply']);
</script>

<template>
  <div class="plan-nudge">
    <p class="plan-efficiency__est">Best value (estimate)</p>
    <p>{{ message }}</p>
    <button
      v-if="suggestion"
      type="button"
      class="btn btn--ghost"
      @click="$emit('apply', suggestion)"
    >
      Try {{ suggestion.optionName }} on week {{ suggestion.weekNo }}
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  fact: { type: Object, required: true },
  open: { type: Boolean, default: false },
});

const emit = defineEmits(['toggle']);

const explanation = computed(() => {
  const text = String(props.fact?.text || '').trim();
  const span = String(props.fact?.sourceSpan || '').trim();
  return text || span;
});
const label = computed(() => {
  const text = explanation.value;
  if (!text) return props.fact?.id || 'Fact';
  const short = text.length > 28 ? `${text.slice(0, 28).trim()}…` : text;
  return short;
});
const quote = computed(() => {
  const text = String(props.fact?.text || '').trim();
  const span = String(props.fact?.sourceSpan || '').trim();
  if (!span || !text || span === text) return '';
  return span;
});
const tip = computed(() => explanation.value || String(props.fact?.id || 'Fact'));
</script>

<template>
  <span class="evidence-chip" :class="{ 'is-open': open }" @click.stop>
    <button
      type="button"
      class="evidence-chip__btn"
      :title="tip"
      :aria-expanded="open"
      @click="emit('toggle')"
    >
      {{ label }}
    </button>
    <span v-if="open && explanation" class="evidence-chip__pop" role="tooltip">
      <span class="evidence-chip__text">{{ explanation }}</span>
      <span v-if="quote" class="evidence-chip__quote">{{ quote }}</span>
    </span>
  </span>
</template>

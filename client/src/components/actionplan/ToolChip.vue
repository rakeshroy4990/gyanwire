<script setup>
import { computed } from 'vue';

const props = defineProps({
  tool: { type: Object, default: null },
  alt: { type: Boolean, default: false },
});

const tier = computed(() => String(props.tool?.tier || 'free').toLowerCase());
const label = computed(() => {
  if (tier.value === 'open_source') return 'Open source';
  if (tier.value === 'freemium') return 'Free tier';
  if (tier.value === 'paid') return 'Paid';
  return 'Free';
});
</script>

<template>
  <span v-if="tool" class="tool-chip" :class="[`tool-chip--${tier}`, { 'tool-chip--alt': alt }]">
    <a v-if="tool.url" :href="tool.url" target="_blank" rel="noopener noreferrer">{{ tool.name || tool.id }}</a>
    <span v-else>{{ tool.name || tool.id }}</span>
    <small>{{ label }}</small>
  </span>
</template>

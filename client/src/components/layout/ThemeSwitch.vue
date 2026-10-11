<script setup>
import { computed } from 'vue';
import { storeToRefs } from 'pinia';
import { useUiStore } from '../../stores/ui.store.js';

const ui = useUiStore();
const { theme } = storeToRefs(ui);

const label = computed(() => (
  theme.value === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'
));

function toggleTheme() {
  ui.setTheme(theme.value === 'light' ? 'dark' : 'light');
}
</script>

<template>
  <button
    type="button"
    class="theme-switch"
    :aria-label="label"
    :title="label"
    @click="toggleTheme"
  >
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="8" />
      <circle class="theme-switch__half" cx="12" cy="12" r="6.9" />
    </svg>
  </button>
</template>

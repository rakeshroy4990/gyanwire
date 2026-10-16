<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

const props = defineProps({
  fact: { type: Object, required: true },
});

const root = ref(null);
const open = ref(false);
const label = computed(() => props.fact?.id || 'fact');
const span = computed(() => props.fact?.sourceSpan || props.fact?.text || '');

function toggle() {
  open.value = !open.value;
}

function onDocClick(e) {
  if (!root.value?.contains(e.target)) open.value = false;
}

function onKey(e) {
  if (e.key === 'Escape') open.value = false;
}

onMounted(() => {
  document.addEventListener('click', onDocClick);
  document.addEventListener('keydown', onKey);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick);
  document.removeEventListener('keydown', onKey);
});
</script>

<template>
  <span ref="root" class="evidence-chip" :class="{ 'is-open': open }">
    <button
      type="button"
      class="evidence-chip__btn"
      :title="span"
      :aria-expanded="open"
      @click.stop="toggle"
    >
      {{ label }}
    </button>
    <span v-if="open && span" class="evidence-chip__pop" role="tooltip">{{ span }}</span>
  </span>
</template>

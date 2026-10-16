<script setup>
defineProps({
  modelValue: { type: String, default: 'open_source' },
});

const emit = defineEmits(['update:modelValue']);

const options = [
  { id: 'open_source', label: 'Open Source' },
  { id: 'paid', label: 'Paid' },
  { id: 'mix', label: 'Mix' },
];

function select(id) {
  emit('update:modelValue', id);
}
</script>

<template>
  <div class="path-switch" role="radiogroup" aria-label="Plan path">
    <button
      v-for="opt in options"
      :key="opt.id"
      type="button"
      class="path-switch__btn"
      role="radio"
      :aria-checked="modelValue === opt.id"
      :class="{ 'is-active': modelValue === opt.id }"
      @click="select(opt.id)"
    >
      {{ opt.label }}
    </button>
  </div>
</template>

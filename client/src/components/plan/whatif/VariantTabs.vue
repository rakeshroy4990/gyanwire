<script setup>
defineProps({
  variants: { type: Array, default: () => [] },
  activeId: { type: String, default: '' },
});

defineEmits(['select', 'duplicate', 'remove']);
</script>

<template>
  <div class="plan-variants">
    <button
      v-for="v in variants"
      :key="v.id"
      type="button"
      class="plan-variants__tab"
      :class="{ 'is-active': v.id === activeId }"
      @click="$emit('select', v.id)"
    >
      {{ v.name }}
    </button>
    <button type="button" class="btn btn--ghost" @click="$emit('duplicate')">Duplicate</button>
    <button
      v-if="variants.length > 1"
      type="button"
      class="btn btn--ghost"
      @click="$emit('remove', activeId)"
    >
      Delete
    </button>
  </div>
</template>

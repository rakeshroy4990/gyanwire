<script setup>
defineProps({
  rows: { type: Number, default: 4 },
  embedded: { type: Boolean, default: false },
});
</script>

<template>
  <div
    class="skeleton-findings"
    :class="{ 'skeleton-findings--embedded': embedded }"
    aria-busy="true"
    aria-live="polite"
    aria-label="Loading findings"
  >
    <div class="skeleton-findings__head">
      <span class="skeleton-line skeleton-line--title" />
      <span class="skeleton-line skeleton-line--meta" />
    </div>
    <div
      v-for="n in rows"
      :key="n"
      class="skeleton-row"
    >
      <span class="skeleton-line skeleton-line--rank" />
      <div class="skeleton-row__body">
        <span class="skeleton-line skeleton-line--heading" />
        <span class="skeleton-line skeleton-line--host" />
        <span class="skeleton-line skeleton-line--why" />
      </div>
      <span class="skeleton-line skeleton-line--score" />
    </div>
  </div>
</template>

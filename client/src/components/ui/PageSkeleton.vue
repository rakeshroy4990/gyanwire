<script setup>
defineProps({
  /** cards | facts | list | pricing | compact */
  variant: { type: String, default: 'cards' },
  rows: { type: Number, default: 4 },
  label: { type: String, default: 'Loading' },
});
</script>

<template>
  <div
    class="page-skeleton"
    :class="`page-skeleton--${variant}`"
    aria-busy="true"
    aria-live="polite"
    :aria-label="label"
  >
    <template v-if="variant === 'pricing'">
      <div v-for="n in 4" :key="n" class="page-skeleton__card">
        <span class="skeleton-line skeleton-line--title" />
        <span class="skeleton-line skeleton-line--heading" />
        <span class="skeleton-line skeleton-line--why" />
        <span class="skeleton-line skeleton-line--host" />
        <span class="skeleton-line skeleton-line--why" />
        <span class="skeleton-line page-skeleton__button" />
      </div>
    </template>

    <template v-else-if="variant === 'facts'">
      <div class="page-skeleton__card">
        <span class="skeleton-line skeleton-line--title" />
        <div v-for="n in rows" :key="n" class="page-skeleton__fact">
          <span class="skeleton-line skeleton-line--host" />
          <span class="skeleton-line skeleton-line--heading" />
        </div>
        <span class="skeleton-line page-skeleton__button" />
      </div>
    </template>

    <template v-else-if="variant === 'list'">
      <div class="page-skeleton__card">
        <span class="skeleton-line skeleton-line--title" />
        <span class="skeleton-line skeleton-line--why" />
        <span class="skeleton-line page-skeleton__button" />
      </div>
      <div v-for="n in rows" :key="n" class="page-skeleton__card">
        <span class="skeleton-line skeleton-line--heading" />
        <span class="skeleton-line skeleton-line--why" />
        <div class="page-skeleton__actions">
          <span class="skeleton-line page-skeleton__chip" />
          <span class="skeleton-line page-skeleton__chip" />
          <span class="skeleton-line page-skeleton__chip" />
        </div>
      </div>
    </template>

    <template v-else-if="variant === 'compact'">
      <span class="skeleton-line skeleton-line--title" />
      <span class="skeleton-line skeleton-line--heading" />
      <span class="skeleton-line skeleton-line--why" />
      <span class="skeleton-line skeleton-line--host" />
      <span class="skeleton-line page-skeleton__button" />
    </template>

    <template v-else>
      <div v-for="n in rows" :key="n" class="page-skeleton__card">
        <span class="skeleton-line skeleton-line--title" />
        <span class="skeleton-line skeleton-line--heading" />
        <span class="skeleton-line skeleton-line--why" />
        <span class="skeleton-line skeleton-line--host" />
      </div>
    </template>
  </div>
</template>

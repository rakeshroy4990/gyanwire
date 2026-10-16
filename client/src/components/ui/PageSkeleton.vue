<script setup>
defineProps({
  /** cards | facts | list | pricing | compact | options | ideas */
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

    <template v-else-if="variant === 'options'">
      <div v-for="n in rows" :key="n" class="page-skeleton__option">
        <div class="page-skeleton__option-top">
          <span class="skeleton-line skeleton-line--meta" />
          <span class="skeleton-line skeleton-line--score" />
        </div>
        <span class="skeleton-line skeleton-line--heading" />
        <span class="skeleton-line skeleton-line--heading page-skeleton__line--short" />
        <span class="skeleton-line skeleton-line--why" />
        <span class="skeleton-line skeleton-line--why page-skeleton__line--mid" />
        <span class="skeleton-line skeleton-line--host" />
        <span class="skeleton-line page-skeleton__button" />
      </div>
    </template>

    <template v-else-if="variant === 'ideas'">
      <div v-for="n in rows" :key="n" class="page-skeleton__idea">
        <div class="page-skeleton__idea-top">
          <span class="skeleton-line skeleton-line--meta" />
          <div class="page-skeleton__idea-tags">
            <span class="skeleton-line page-skeleton__pill" />
            <span class="skeleton-line page-skeleton__pill" />
          </div>
        </div>
        <span class="skeleton-line skeleton-line--heading" />
        <span class="skeleton-line skeleton-line--why" />
        <span class="skeleton-line skeleton-line--host page-skeleton__line--mid" />
        <span class="skeleton-line skeleton-line--meta page-skeleton__idea-details" />
        <div class="page-skeleton__idea-actions">
          <span class="skeleton-line page-skeleton__button page-skeleton__button--grow" />
          <span class="skeleton-line page-skeleton__button page-skeleton__button--save" />
        </div>
      </div>
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

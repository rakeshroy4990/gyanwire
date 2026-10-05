<script setup>
import FindingsSkeleton from './FindingsSkeleton.vue';

defineProps({
  results: { type: Array, default: () => [] },
  resultsSub: { type: String, default: '' },
  emptyMessage: { type: String, default: 'Findings will settle here after you research.' },
  showEmpty: { type: Boolean, default: true },
  showResults: { type: Boolean, default: false },
  isLoading: { type: Boolean, default: false },
  /** Compact findings block used inside a chat turn. */
  embedded: { type: Boolean, default: false },
  heading: { type: String, default: 'Findings' },
});
</script>

<template>
  <section
    class="results-section"
    :class="{ 'results-section--embedded': embedded }"
    :aria-labelledby="embedded ? undefined : 'resultsTitle'"
  >
    <div v-if="!isLoading" class="results-section__head">
      <h2 :id="embedded ? undefined : 'resultsTitle'" class="results-section__h">
        {{ heading }}
      </h2>
      <p class="results-sub">{{ resultsSub || (embedded ? '' : 'Waiting for a search') }}</p>
    </div>

    <FindingsSkeleton
      v-if="isLoading"
      :embedded="embedded"
      :rows="embedded ? 3 : 5"
    />

    <div v-else-if="showEmpty" class="empty" :class="{ 'empty--embedded': embedded }">
      <p v-if="!embedded" class="empty__title">Nothing here yet</p>
      <p>{{ emptyMessage }}</p>
    </div>

    <ol v-else-if="showResults" class="results">
      <li
        v-for="(item, index) in results"
        :key="item.url + index"
        class="result"
        :style="{ animationDelay: `${index * 45}ms` }"
      >
        <div class="result__rank" aria-hidden="true">{{ index + 1 }}</div>
        <div class="result__body">
          <h3 class="result__title">
            <a :href="item.url" target="_blank" rel="noopener noreferrer">{{ item.title }}</a>
          </h3>
          <p class="result__url">{{ item.host }}</p>
          <p class="result__why">{{ item.why || item.description }}</p>
        </div>
        <span v-if="item.score != null && item.score !== ''" class="result__score">
          {{ item.score }}
        </span>
      </li>
    </ol>
  </section>
</template>

<script setup>
defineProps({
  results: { type: Array, default: () => [] },
  resultsSub: { type: String, default: '' },
  emptyMessage: { type: String, default: 'Findings will settle here after you research.' },
  showEmpty: { type: Boolean, default: true },
  showResults: { type: Boolean, default: false },
  isLoading: { type: Boolean, default: false },
});
</script>

<template>
  <section class="results-section" aria-labelledby="resultsTitle">
    <div class="results-section__head">
      <h2 id="resultsTitle">Findings</h2>
      <p class="results-sub">{{ resultsSub || 'Waiting for a search' }}</p>
    </div>

    <div v-if="showEmpty && !isLoading" class="empty">
      <p class="empty__title">Nothing here yet</p>
      <p>{{ emptyMessage }}</p>
    </div>

    <div v-if="isLoading" class="loading" aria-live="polite">
      <div class="loading__bar" aria-hidden="true" />
      <p>Researching…</p>
    </div>

    <ol v-if="showResults && !isLoading" class="results">
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

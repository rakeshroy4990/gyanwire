<script setup>
defineProps({
  results: { type: Array, default: () => [] },
  resultsSub: { type: String, default: '' },
  emptyMessage: { type: String, default: 'Product news will appear here.' },
  showEmpty: { type: Boolean, default: true },
  showResults: { type: Boolean, default: false },
  isLoading: { type: Boolean, default: false },
});
</script>

<template>
  <section class="results-section" aria-labelledby="resultsTitle">
    <div class="results-section__head">
      <h2 id="resultsTitle">Findings</h2>
      <p class="results-sub">{{ resultsSub }}</p>
    </div>

    <div v-if="showEmpty && !isLoading" class="empty">
      <p>{{ emptyMessage }}</p>
    </div>

    <div v-if="isLoading" class="loading">
      <div class="loading__bar" aria-hidden="true" />
      <p>Researching…</p>
    </div>

    <ol v-if="showResults && !isLoading" class="results">
      <li
        v-for="(item, index) in results"
        :key="item.url + index"
        class="result"
        :style="{ animationDelay: `${index * 40}ms` }"
      >
        <div class="result__rank">{{ index + 1 }}</div>
        <div class="result__body">
          <h3 class="result__title">
            <a :href="item.url" target="_blank" rel="noopener noreferrer">{{ item.title }}</a>
          </h3>
          <p class="result__url">{{ item.host }}</p>
          <p class="result__why">{{ item.why || item.description }}</p>
        </div>
        <span class="result__score">{{ item.score ?? '' }}</span>
      </li>
    </ol>
  </section>
</template>

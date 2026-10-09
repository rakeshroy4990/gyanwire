<script setup>
import { reactive } from 'vue';
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
  showIdeas: { type: Boolean, default: false },
});

const emit = defineEmits(['idea', 'feedback']);
const votes = reactive({});

function vote(item, value) {
  votes[item.url] = value;
  emit('feedback', { url: item.url, vote: value });
}

function articleDate(item) {
  if (item?.publishedLabel) return item.publishedLabel;
  if (!item?.publishedAt) return '';
  const parsed = new Date(item.publishedAt);
  if (Number.isNaN(parsed.getTime())) return '';
  return parsed.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}
</script>

<template>
  <section
    class="results-section"
    :class="{ 'results-section--embedded': embedded }"
    :aria-labelledby="embedded ? undefined : 'resultsTitle'"
  >
    <div v-if="!isLoading || (embedded && results.length)" class="results-section__head">
      <h2 :id="embedded ? undefined : 'resultsTitle'" class="results-section__h">
        {{ heading }}
      </h2>
      <p class="results-sub">{{ resultsSub || (embedded ? '' : 'Waiting for a search') }}</p>
    </div>

    <FindingsSkeleton
      v-if="isLoading && (!embedded || results.length === 0)"
      :embedded="embedded"
      :rows="embedded ? 3 : 5"
    />

    <div v-else-if="showEmpty" class="empty" :class="{ 'empty--embedded': embedded }">
      <p v-if="!embedded" class="empty__title">Nothing here yet</p>
      <p>{{ emptyMessage }}</p>
    </div>

    <ol v-else-if="!isLoading || embedded" v-show="showResults || results.length" class="results">
      <li
        v-for="(item, index) in results"
        :id="item.id ? `finding-${item.id}` : undefined"
        :key="item.url + index"
        class="result"
        :style="{ animationDelay: `${index * 45}ms` }"
      >
        <div class="result__rank" aria-hidden="true">{{ index + 1 }}</div>
        <div class="result__body">
          <h3 class="result__title">
            <time v-if="articleDate(item)" class="result__date" :datetime="item.publishedAt || undefined">{{ articleDate(item) }}</time>
            <a :href="item.url" target="_blank" rel="noopener noreferrer">{{ item.title }}</a>
          </h3>
          <p class="result__url">{{ item.host }}</p>
          <p class="result__why">{{ item.why || item.description }}</p>
          <p v-if="item.trust" class="trust-badge">
            {{ item.trust.domainTier }} trust
            <span v-if="item.trust.https"> · HTTPS</span>
            <span v-if="item.trust.primarySource"> · primary</span>
          </p>
          <div class="result__actions">
            <button v-if="showIdeas" type="button" class="btn btn--ghost" @click="emit('idea', item)">Get an idea</button>
            <div class="result__votes" role="group" aria-label="Was this finding useful?">
              <span class="result__votes-label">Was this useful?</span>
              <button
                type="button"
                class="vote"
                :class="{ 'is-selected': votes[item.url] === 1 }"
                :aria-pressed="votes[item.url] === 1"
                @click="vote(item, 1)"
              >Yes</button>
              <button
                type="button"
                class="vote"
                :class="{ 'is-selected': votes[item.url] === -1 }"
                :aria-pressed="votes[item.url] === -1"
                @click="vote(item, -1)"
              >No</button>
            </div>
          </div>
        </div>
        <span v-if="item.score != null && item.score !== ''" class="result__score">
          {{ item.score }}
        </span>
      </li>
    </ol>
    <FindingsSkeleton
      v-if="isLoading && embedded && results.length"
      :embedded="embedded"
      :rows="1"
    />
  </section>
</template>

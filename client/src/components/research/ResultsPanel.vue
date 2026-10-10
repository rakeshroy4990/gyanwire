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
  freshness: { type: String, default: '' },
  note: { type: String, default: '' },
});

const emit = defineEmits(['idea', 'feedback']);
const votes = reactive({});

function vote(item, value, reason) {
  const key = item.id || item.url;
  votes[key] = reason || value;
  emit('feedback', {
    url: item.url,
    vote: value,
    reason: reason || undefined,
    itemId: item.id,
    signalType: item.signalType,
    industry: item.industry,
  });
}

function signalLabel(signal) {
  const labels = {
    launch: 'Launch',
    funding: 'Funding',
    approval: 'Approval',
    regulation: 'Regulation',
    pricing: 'Price',
    shortage: 'Shortage',
    research: 'Research',
    opinion: 'Opinion',
  };
  return labels[signal] || '';
}

function potential(item) {
  const value = Number(item?.opportunityScore ?? item?.score);
  if (!Number.isFinite(value)) return 0;
  return Math.max(0, Math.min(100, Math.round(value)));
}

function freshnessText(value) {
  if (value === 'warming_up') return 'Fetching the latest news...';
  if (value === 'last 30 days') return 'Few recent items — showing the last 30 days.';
  if (value === 'older — few recent items') return 'Few recent items — showing older coverage.';
  return '';
}

function articleDate(item) {
  if (item?.publishedLabel) return item.publishedLabel;
  if (!item?.publishedAt) return '';
  const parsed = new Date(item.publishedAt);
  if (Number.isNaN(parsed.getTime())) return '';
  return parsed.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

/** Distinct idea formats for this finding. The server picks the count from the news. */
function ideaCount(item) {
  const n = Number(item?.ideaCount);
  return Number.isFinite(n) && n > 0 ? Math.round(n) : 0;
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
      <p v-if="freshnessText(freshness)" class="results-banner">{{ freshnessText(freshness) }}</p>
      <p v-else-if="note" class="results-banner">{{ note }}</p>
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
            <time v-if="item.ageLabel || articleDate(item)" class="result__date" :datetime="item.publishedAt || undefined">{{ item.ageLabel || articleDate(item) }}</time>
            <span v-if="signalLabel(item.signalType)" class="signal-chip">{{ signalLabel(item.signalType) }}</span>
            <a :href="item.url" target="_blank" rel="noopener noreferrer">{{ item.title }}</a>
          </h3>
          <p class="result__url">{{ item.host }}</p>
          <p class="result__why">{{ item.whyIdea || item.why || item.description }}</p>
          <p v-if="item.sample" class="result__flag">Sample data</p>
          <p v-if="item.weakSignal" class="result__flag">Weak signal</p>
          <p v-if="item.trust" class="trust-badge">
            {{ item.trust.domainTier }} trust
            <span v-if="item.trust.https"> · HTTPS</span>
            <span v-if="item.trust.primarySource"> · primary</span>
          </p>
          <div class="result__actions">
            <button v-if="showIdeas" type="button" class="btn btn--ghost" @click="emit('idea', item)">Get an idea</button>
            <span v-if="showIdeas && ideaCount(item)" class="result__idea-count">{{ ideaCount(item) }} {{ ideaCount(item) === 1 ? 'idea' : 'ideas' }}</span>
            <div class="result__votes" role="group" aria-label="Was this finding useful?">
              <span class="result__votes-label">Was this useful?</span>
              <button
                type="button"
                class="vote"
                :class="{ 'is-selected': votes[item.id || item.url] === 1 }"
                :aria-pressed="votes[item.id || item.url] === 1"
                @click="vote(item, 1)"
              >Yes</button>
              <button
                type="button"
                class="vote"
                :class="{ 'is-selected': votes[item.id || item.url] === -1 }"
                :aria-pressed="votes[item.id || item.url] === -1"
                @click="vote(item, -1)"
              >No</button>
              <button
                v-if="item.signalType"
                type="button"
                class="vote"
                :class="{ 'is-selected': votes[item.id || item.url] === 'not_relevant' }"
                :aria-pressed="votes[item.id || item.url] === 'not_relevant'"
                @click="vote(item, -1, 'not_relevant')"
              >Not my industry</button>
            </div>
          </div>
        </div>
        <div v-if="item.signalType" class="result__potential">
          <span class="result__potential-label">Idea potential</span>
          <span class="result__meter" :style="{ '--fill': `${potential(item)}%` }" />
          <span class="result__potential-value">{{ potential(item) }}</span>
        </div>
        <span v-else-if="item.score != null && item.score !== ''" class="result__score">
          {{ item.score }}%
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

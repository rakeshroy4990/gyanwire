<script setup>
import { reactive } from 'vue';
import FindingsSkeleton from './FindingsSkeleton.vue';

const props = defineProps({
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
  allowDismiss: { type: Boolean, default: false },
  freshness: { type: String, default: '' },
  note: { type: String, default: '' },
  returnKey: { type: String, default: '' },
  countries: { type: Array, default: () => [] },
  country: { type: String, default: null },
  page: { type: Number, default: 1 },
  totalPages: { type: Number, default: 0 },
  total: { type: Number, default: 0 },
  hasMore: { type: Boolean, default: false },
});

const emit = defineEmits(['idea', 'feedback', 'dismiss', 'hide-source', 'select-country', 'page']);
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
    fyi: 'FYI',
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
  if (value === 'fresh') return "Showing today's news first, then widening the window.";
  if (value === 'last 30 days') return 'Showing news from the last 30 days, newest first.';
  if (value === 'last 7 days') return 'Showing the last 7 days; older coverage loads next.';
  if (value === 'older — few recent items') return 'Few recent items — showing older coverage.';
  return '';
}

function itemRank(item, index) {
  const ranked = Number(item?.rank);
  if (Number.isFinite(ranked) && ranked > 0) return ranked;
  return index + 1;
}

function goPage(next) {
  if (next < 1) return;
  if (props.totalPages && next > props.totalPages) return;
  emit('page', next);
}

function dismiss(item) {
  emit('dismiss', item);
}

function hideSource(item) {
  emit('hide-source', item);
}

function whyLine(item) {
  return item.relevanceReason || item.whyIdea || item.why || item.description || '';
}

function signalChips(item) {
  if (Array.isArray(item.signals) && item.signals.length) {
    return item.signals.map((s) => signalLabel(s) || s).filter(Boolean);
  }
  const one = signalLabel(item.signalType);
  return one ? [one] : [];
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

function isReturned(item) {
  const key = props.returnKey;
  if (!key) return false;
  return String(item?.id || '') === key || item?.url === key;
}
</script>

<template>
  <section
    class="results-section"
    :class="{ 'results-section--embedded': embedded }"
    :aria-labelledby="embedded ? undefined : 'resultsTitle'"
  >
    <div v-if="!embedded || !isLoading || results.length" class="results-section__head">
      <h2 :id="embedded ? undefined : 'resultsTitle'" class="results-section__h">
        {{ heading }}
      </h2>
      <p class="results-sub">{{ resultsSub || (embedded ? '' : 'Waiting for a search') }}</p>
      <div
        v-if="!embedded"
        class="topics topics--subs findings-countries"
        role="group"
        aria-label="Country"
      >
        <button
          type="button"
          class="category"
          :class="{ 'is-active': !country }"
          :aria-pressed="!country"
          @click="emit('select-country', null)"
        >
          All
        </button>
        <button
          v-for="item in countries"
          :key="item.code"
          type="button"
          class="category"
          :class="{ 'is-active': country === item.code }"
          :aria-pressed="country === item.code"
          @click="emit('select-country', item.code)"
        >
          {{ item.name }}
        </button>
      </div>
      <p v-if="freshnessText(freshness)" class="results-banner">{{ freshnessText(freshness) }}</p>
      <p v-else-if="note" class="results-banner">{{ note }}</p>
    </div>

    <FindingsSkeleton
      v-if="isLoading && results.length === 0"
      :embedded="embedded"
      :rows="embedded ? 3 : 5"
      :show-head="embedded"
    />

    <!-- warming_up status is the banner only — avoid "Nothing here yet" + duplicate fetch copy -->
    <div
      v-else-if="showEmpty && freshness !== 'warming_up'"
      class="empty"
      :class="{ 'empty--embedded': embedded }"
    >
      <p v-if="!embedded" class="empty__title">Nothing here yet</p>
      <p>{{ emptyMessage }}</p>
    </div>

    <ol v-else v-show="showResults || results.length" class="results">
      <li
        v-for="(item, index) in results"
        :id="item.id ? `finding-${item.id}` : undefined"
        :key="item.url + index"
        class="result"
        :class="{ 'result--return': isReturned(item) }"
        :data-finding-id="item.id != null && item.id !== '' ? String(item.id) : undefined"
        :data-finding-url="item.url || undefined"
        :style="{ animationDelay: `${index * 45}ms` }"
      >
        <div class="result__rank" aria-hidden="true">{{ itemRank(item, index) }}</div>
        <div class="result__body">
          <div class="result__headline">
            <h3 class="result__title">
              <time v-if="item.ageLabel || articleDate(item)" class="result__date" :datetime="item.publishedAt || undefined">{{ item.ageLabel || articleDate(item) }}</time>
              <span v-if="signalLabel(item.signalType)" class="signal-chip">{{ signalLabel(item.signalType) }}</span>
              <span v-if="item.countryName" class="country-chip">{{ item.countryName }}</span>
              <a :href="item.url" target="_blank" rel="noopener noreferrer">{{ item.title }}</a>
            </h3>
            <button
              v-if="allowDismiss"
              type="button"
              class="result__dismiss"
              aria-label="Remove from list"
              title="Remove from list"
              @click="dismiss(item)"
            >
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true" focusable="false">
                <path
                  d="M3.5 3.5l9 9m0-9l-9 9"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.6"
                  stroke-linecap="round"
                />
              </svg>
            </button>
          </div>
          <p class="result__url">{{ item.host }}</p>
          <p v-if="signalChips(item).length" class="result__signals">
            Signals: {{ signalChips(item).join(' · ') }}
          </p>
          <p class="result__why">{{ whyLine(item) }}</p>
          <p v-if="item.sample" class="result__flag">Sample data</p>
          <p v-if="item.weakSignal" class="result__flag">Weak signal</p>
          <p v-if="item.trust" class="trust-badge">
            {{ item.trust.domainTier }} trust
            <span v-if="item.trust.https"> · HTTPS</span>
            <span v-if="item.trust.primarySource"> · primary</span>
          </p>
          <div class="result__actions">
            <button v-if="showIdeas" type="button" class="btn btn--ghost" @click="emit('idea', item)">Get an idea</button>
            <a v-if="item.url" class="btn btn--ghost" :href="item.url" target="_blank" rel="noopener noreferrer">Open</a>
            <button
              v-if="allowDismiss && (item.sourceId || item.id)"
              type="button"
              class="btn btn--ghost"
              @click="hideSource(item)"
            >Hide this source</button>
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
      v-if="isLoading && results.length"
      :embedded="embedded"
      :rows="1"
      :show-head="false"
    />

    <nav
      v-if="!embedded && !isLoading && results.length && totalPages > 1"
      class="results-pager"
      aria-label="News pages"
    >
      <button
        type="button"
        class="btn btn--ghost"
        :disabled="page <= 1"
        @click="goPage(page - 1)"
      >
        Previous
      </button>
      <p class="results-pager__status">Page {{ page }} of {{ totalPages }}</p>
      <button
        type="button"
        class="btn btn--ghost"
        :disabled="page >= totalPages && !hasMore"
        @click="goPage(page + 1)"
      >
        Next
      </button>
    </nav>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import PageSkeleton from '../ui/PageSkeleton.vue';
import IdeaCard from './IdeaCard.vue';

const props = defineProps({
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  ideas: { type: Array, default: () => [] },
  ratingId: { type: String, default: '' },
  stageLabel: { type: String, default: 'Loading ideas' },
  researchStatus: { type: String, default: '' },
  researchSummary: { type: Object, default: null },
});

const emit = defineEmits(['make-plan', 'not-useful']);

const openFactKey = ref('');

const researchFacts = computed(() => {
  const facts = props.researchSummary?.facts;
  return Array.isArray(facts) ? facts.slice(0, 5) : [];
});

function toggleFact(key) {
  openFactKey.value = openFactKey.value === key ? '' : key;
}

function closeFact() {
  openFactKey.value = '';
}

function onDocClick() {
  closeFact();
}

function onKey(e) {
  if (e.key === 'Escape' && openFactKey.value) closeFact();
}

if (typeof document !== 'undefined') {
  document.addEventListener('keydown', onKey);
  document.addEventListener('click', onDocClick);
}

watch(() => props.ideas, closeFact);

onBeforeUnmount(() => {
  if (typeof document === 'undefined') return;
  document.removeEventListener('keydown', onKey);
  document.removeEventListener('click', onDocClick);
});
</script>

<template>
  <div class="news-ideas-view">
    <section
      v-if="researchSummary || researchStatus === 'running' || researchStatus === 'started' || researchStatus === 'ready'"
      class="news-ideas-view__research"
      aria-live="polite"
    >
      <details v-if="researchSummary" class="news-ideas-view__research-details" open>
        <summary class="news-ideas-view__research-kicker">Ready for the video</summary>
        <p v-if="researchSummary.whatHappened" class="news-ideas-view__research-body">
          {{ researchSummary.whatHappened }}
        </p>
        <p v-if="researchSummary.whyItMatters" class="news-ideas-view__research-why">
          {{ researchSummary.whyItMatters }}
        </p>
        <ul v-if="researchFacts.length" class="news-ideas-view__research-facts">
          <li v-for="fact in researchFacts" :key="fact.id || fact.text">
            <span>{{ fact.text }}</span>
            <a
              v-if="fact.sourceUrl"
              :href="fact.sourceUrl"
              target="_blank"
              rel="noopener noreferrer"
            >Source</a>
          </li>
        </ul>
      </details>
      <p v-else-if="researchStatus === 'ready'" class="news-ideas-view__research-status">
        Ready for the video
      </p>
      <p v-else class="news-ideas-view__research-status">
        Gathering sources for the video…
      </p>
    </section>

    <PageSkeleton
      v-if="loading && !ideas.length"
      variant="ideas"
      :rows="3"
      :label="stageLabel"
    />
    <p v-else-if="error && !ideas.length" class="error">{{ error }}</p>
    <div v-else class="news-ideas-view__list">
      <IdeaCard
        v-for="idea in ideas"
        :key="idea.id || idea.title"
        :idea="idea"
        :rating="ratingId === idea.id"
        :open-fact-key="openFactKey"
        @toggle-fact="toggleFact"
        @make-plan="emit('make-plan', $event)"
        @not-useful="emit('not-useful', $event)"
      />
      <PageSkeleton
        v-if="loading"
        variant="ideas"
        :rows="1"
        :label="stageLabel"
      />
      <p v-else-if="error" class="error">{{ error }}</p>
      <p v-else-if="!ideas.length" class="news-ideas-view__empty">No ideas left for this news.</p>
    </div>
  </div>
</template>

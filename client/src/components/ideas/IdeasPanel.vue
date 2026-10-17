<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import PageSkeleton from '../ui/PageSkeleton.vue';
import IdeaCard from './IdeaCard.vue';

const props = defineProps({
  open: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  headline: { type: String, default: '' },
  ideas: { type: Array, default: () => [] },
  ratingId: { type: String, default: '' },
  stageLabel: { type: String, default: 'Loading ideas' },
  researchStatus: { type: String, default: '' },
  researchSummary: { type: Object, default: null },
});

const emit = defineEmits(['close', 'make-plan', 'not-useful', 'open-full']);

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

function onKey(e) {
  if (e.key === 'Escape') {
    if (openFactKey.value) {
      closeFact();
      return;
    }
    if (props.open) emit('close');
  }
}

function onDocClick() {
  closeFact();
}

function setOpen(isOpen) {
  if (typeof document === 'undefined') return;
  document.body.classList.toggle('ideas-panel-open', isOpen);
  if (isOpen) {
    document.addEventListener('keydown', onKey);
    document.addEventListener('click', onDocClick);
  } else {
    document.removeEventListener('keydown', onKey);
    document.removeEventListener('click', onDocClick);
    closeFact();
  }
}

watch(() => props.open, setOpen, { immediate: true });
watch(() => props.ideas, closeFact);

onBeforeUnmount(() => setOpen(false));
</script>

<template>
  <div
    class="ideas-panel"
    :class="{ 'is-open': open }"
    role="dialog"
    aria-modal="true"
    aria-label="Ideas from this news"
    :aria-hidden="!open"
  >
    <button type="button" class="ideas-panel__backdrop" aria-label="Close ideas" @click="emit('close')" />
    <aside class="ideas-panel__drawer">
      <header class="ideas-panel__head">
        <div>
          <p class="ideas-panel__kicker">Ideas from this news</p>
          <h2>{{ headline || 'Ideas' }}</h2>
        </div>
        <button type="button" class="ideas-panel__close" aria-label="Close" @click="emit('close')">×</button>
      </header>

      <p class="ideas-panel__full">
        <button type="button" class="page-back" @click="emit('open-full')">Open full page</button>
      </p>

      <section
        v-if="researchStatus === 'running' || researchStatus === 'started' || researchSummary"
        class="ideas-panel__research"
        aria-live="polite"
      >
        <p v-if="researchStatus === 'running' || researchStatus === 'started'" class="ideas-panel__research-status">
          Gathering sources for the video…
        </p>
        <template v-else-if="researchSummary">
          <p class="ideas-panel__research-kicker">Ready for the video</p>
          <p v-if="researchSummary.whatHappened" class="ideas-panel__research-body">
            {{ researchSummary.whatHappened }}
          </p>
          <p v-if="researchSummary.whyItMatters" class="ideas-panel__research-why">
            {{ researchSummary.whyItMatters }}
          </p>
          <ul v-if="researchFacts.length" class="ideas-panel__research-facts">
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
        </template>
      </section>

      <PageSkeleton
        v-if="loading && !ideas.length"
        variant="ideas"
        :rows="3"
        :label="stageLabel"
      />
      <p v-else-if="error && !ideas.length" class="error">{{ error }}</p>
      <div v-else class="ideas-panel__list">
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
        <p v-else-if="!ideas.length" class="ideas-panel__empty">No ideas left for this news.</p>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue';
import PageSkeleton from '../ui/PageSkeleton.vue';
import IdeaCard from './IdeaCard.vue';

const props = defineProps({
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  ideas: { type: Array, default: () => [] },
  ratingId: { type: String, default: '' },
  stageLabel: { type: String, default: 'Loading ideas' },
});

const emit = defineEmits(['make-plan', 'not-useful']);

const openFactKey = ref('');

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

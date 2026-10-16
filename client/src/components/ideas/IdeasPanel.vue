<script setup>
import { onBeforeUnmount, watch } from 'vue';
import PageSkeleton from '../ui/PageSkeleton.vue';
import IdeaCard from './IdeaCard.vue';

const props = defineProps({
  open: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  headline: { type: String, default: '' },
  ideas: { type: Array, default: () => [] },
  ratingId: { type: String, default: '' },
});

const emit = defineEmits(['close', 'make-plan', 'not-useful', 'open-full']);

function onKey(e) {
  if (e.key === 'Escape' && props.open) emit('close');
}

function setOpen(isOpen) {
  if (typeof document === 'undefined') return;
  document.body.classList.toggle('ideas-panel-open', isOpen);
  if (isOpen) document.addEventListener('keydown', onKey);
  else document.removeEventListener('keydown', onKey);
}

watch(() => props.open, setOpen, { immediate: true });

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

      <PageSkeleton v-if="loading" variant="ideas" :rows="3" label="Loading ideas" />
      <p v-else-if="error" class="error">{{ error }}</p>
      <div v-else class="ideas-panel__list">
        <IdeaCard
          v-for="idea in ideas"
          :key="idea.id || idea.title"
          :idea="idea"
          :rating="ratingId === idea.id"
          @make-plan="emit('make-plan', $event)"
          @not-useful="emit('not-useful', $event)"
        />
        <p v-if="!ideas.length" class="ideas-panel__empty">No ideas left for this news.</p>
      </div>
    </aside>
  </div>
</template>

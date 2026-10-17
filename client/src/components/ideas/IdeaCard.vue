<script setup>
import { computed } from 'vue';
import EvidenceChip from './EvidenceChip.vue';

const props = defineProps({
  idea: { type: Object, required: true },
  rating: { type: Boolean, default: false },
  openFactKey: { type: String, default: '' },
});

const emit = defineEmits(['make-plan', 'not-useful', 'toggle-fact']);

const typeLabel = computed(() => {
  const t = String(props.idea?.type || '').toLowerCase();
  if (t === 'social_media' || t.includes('social')) return 'Social media';
  return props.idea?.type || 'Idea';
});

const meters = computed(() => {
  const row = props.idea || {};
  const list = [
    { key: 'demand', label: 'Demand', meter: row.demand },
    { key: 'effort', label: 'Effort', meter: row.effortMeter || row.effort },
    { key: 'competition', label: 'Competition', meter: row.competition },
  ];
  return list
    .filter((m) => m.meter && (m.meter.value != null || m.meter.label))
    .map((m) => ({
      ...m,
      value: Number(m.meter.value) || 0,
      tip: m.meter.label || 'estimate',
    }));
});

const facts = computed(() => (Array.isArray(props.idea?.facts) ? props.idea.facts : []));

function factKey(fact) {
  const ideaId = props.idea?.id || props.idea?.title || '';
  const factId = fact?.id || fact?.text || '';
  return `${ideaId}::${factId}`;
}
</script>

<template>
  <article
    class="idea-card panel-idea-card"
    :class="{ 'idea-card--best': idea.bestBet, 'idea-card--social': typeLabel === 'Social media' }"
  >
    <div class="idea-card__top">
      <p class="idea-card__angle">{{ typeLabel }}</p>
      <div class="idea-card__tags">
        <p v-if="idea.bestBet" class="idea-card__tag idea-card__tag--best">Best bet</p>
        <p v-if="idea.quickWin" class="idea-card__tag">Quick win</p>
        <p v-if="idea.effortLabel" class="idea-card__tag">{{ idea.effortLabel }}</p>
      </div>
    </div>

    <h2>{{ idea.title }}</h2>
    <p v-if="idea.audience" class="idea-card__meta">For: {{ idea.audience }}</p>
    <p v-if="idea.whyNow" class="idea-card__why"><span class="idea-card__label">Why now:</span> {{ idea.whyNow }}</p>
    <p v-if="idea.firstTest"><span class="idea-card__label">First test:</span> {{ idea.firstTest }}</p>

    <div v-if="meters.length" class="idea-score" aria-label="Estimated scores">
      <div class="idea-score__bars">
        <div
          v-for="bar in meters"
          :key="bar.key"
          class="idea-score__bar"
          :title="bar.tip"
        >
          <span>{{ bar.label }}</span>
          <span class="idea-score__track">
            <span class="idea-score__fill" :style="{ width: `${Math.min(100, bar.value)}%` }" />
          </span>
          <span class="idea-score__caption">{{ bar.tip }}</span>
        </div>
      </div>
    </div>

    <div v-if="facts.length" class="panel-idea-card__facts" aria-label="Evidence">
      <EvidenceChip
        v-for="fact in facts"
        :key="fact.id || fact.text"
        :fact="fact"
        :open="openFactKey === factKey(fact)"
        @toggle="emit('toggle-fact', factKey(fact))"
      />
    </div>

    <div class="idea-card__actions">
      <button type="button" class="btn btn--primary" @click="$emit('make-plan', idea)">
        Make plan
      </button>
      <button
        type="button"
        class="btn btn--ghost"
        :disabled="rating"
        @click="$emit('not-useful', idea)"
      >
        {{ rating ? 'Saving…' : 'Not useful' }}
      </button>
    </div>
  </article>
</template>

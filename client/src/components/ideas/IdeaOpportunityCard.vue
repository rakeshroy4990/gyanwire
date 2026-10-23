<script setup>
import { computed } from 'vue';
import {
  angleLabel,
  customerLine,
  effortTag,
  moneyLine,
  moneyShort,
  scoreBars,
  testLine,
  urgencyChip,
} from '../../services/ideaCards.js';

const props = defineProps({
  idea: { type: Object, required: true },
  best: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  saved: { type: Boolean, default: false },
  building: { type: Boolean, default: false },
});

defineEmits(['build', 'save']);

const social = computed(() => props.idea?.angleType === 'comparison_content');

const hasDetails = computed(() => Boolean(
  customerLine(props.idea)
  || props.idea.whyNow
  || moneyLine(props.idea)
  || testLine(props.idea)
  || props.idea.dependsOnUnconfirmed
  || (props.idea.firstWeek && props.idea.firstWeek.length)
  || (props.idea.risks && props.idea.risks.length)
  || props.idea.disclaimer
  || scoreBars(props.idea).length,
));
</script>

<template>
  <article
    class="idea-card"
    :class="{ 'idea-card--best': best, 'idea-card--social': social }"
  >
    <div class="idea-card__top">
      <p class="idea-card__angle">{{ angleLabel(idea) }}</p>
      <div class="idea-card__tags">
        <p v-if="best" class="idea-card__tag idea-card__tag--best">Best bet</p>
        <p v-if="effortTag(idea.buildEffort)" class="idea-card__tag">{{ effortTag(idea.buildEffort) }}</p>
        <p v-if="!social && urgencyChip(idea.timeWindow?.urgency)" class="idea-card__tag">
          {{ urgencyChip(idea.timeWindow.urgency) }}
        </p>
      </div>
    </div>

    <h2>{{ idea.title }}</h2>
    <p class="idea-card__liner">{{ idea.oneLiner }}</p>
    <p v-if="social" class="idea-card__meta">YouTube · Instagram · LinkedIn · 4-hour plan</p>
    <p v-else-if="moneyShort(idea)" class="idea-card__meta">{{ moneyShort(idea) }}</p>

    <details v-if="hasDetails" class="idea-card__details">
      <summary>{{ social ? 'Why this post' : 'More detail' }}</summary>
      <p v-if="idea.dependsOnUnconfirmed" class="idea-card__banner">Pre-launch: based on expected specs</p>
      <p v-if="customerLine(idea)"><span class="idea-card__label">For:</span> {{ customerLine(idea) }}</p>
      <p v-if="idea.whyNow"><span class="idea-card__label">Why now:</span> {{ idea.whyNow }}</p>
      <p v-if="moneyLine(idea)"><span class="idea-card__label">Money:</span> {{ moneyLine(idea) }}</p>
      <p v-if="testLine(idea)"><span class="idea-card__label">Test:</span> {{ testLine(idea) }}</p>
      <div v-if="scoreBars(idea).length" class="idea-score" aria-label="Estimated scores">
        <div class="idea-score__bars">
          <div v-for="bar in scoreBars(idea)" :key="bar.key" class="idea-score__bar" :title="bar.tip">
            <span>{{ bar.label }}</span>
            <span class="idea-score__track">
              <span class="idea-score__fill" :style="{ width: `${bar.score * 20}%` }" />
            </span>
          </div>
        </div>
      </div>
      <ol v-if="idea.firstWeek?.length">
        <li v-for="step in idea.firstWeek" :key="step">{{ step }}</li>
      </ol>
      <ul v-if="idea.risks?.length">
        <li v-for="risk in idea.risks" :key="risk">{{ risk }}</li>
      </ul>
      <p v-if="idea.disclaimer" class="disclaimer">{{ idea.disclaimer }}</p>
    </details>

    <div class="idea-card__actions">
      <button type="button" class="btn btn--ghost" :disabled="building" @click="$emit('build')">
        {{ building ? 'Building…' : 'Plan later' }}
      </button>
      <button type="button" class="btn btn--ghost" :disabled="saving || saved" @click="$emit('save')">
        {{ saved ? 'Saved' : saving ? 'Saving…' : 'Save' }}
      </button>
    </div>
  </article>
</template>

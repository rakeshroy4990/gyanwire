<script setup>
import { computed, ref, watch } from 'vue';

const props = defineProps({
  plan: { type: Object, required: true },
});

const selected = ref('youtube');
const openHour = ref('Hour 1');

const social = computed(() => props.plan?.kind === 'social' && Array.isArray(props.plan?.platforms));

const active = computed(() => {
  const platforms = props.plan?.platforms || [];
  return platforms.find((item) => item.id === selected.value) || platforms[0] || null;
});

watch(
  () => props.plan,
  (plan) => {
    const first = plan?.platforms?.[0]?.id;
    if (first) selected.value = first;
    openHour.value = 'Hour 1';
  },
  { immediate: true },
);

watch(selected, () => {
  openHour.value = 'Hour 1';
});

function stars(value) {
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0) return '';
  if (n >= 1000) {
    const digits = n >= 10000 ? 0 : 1;
    return `${(n / 1000).toFixed(digits)}k★`;
  }
  return `${n}★`;
}

function toolSummary(tool) {
  return tool?.summary || tool?.why || tool?.note || '';
}

function toggleHour(hour) {
  openHour.value = openHour.value === hour ? '' : hour;
}

function selectPlatform(id) {
  selected.value = id;
}
</script>

<template>
  <article v-if="social" class="launch-plan launch-plan--social">
    <p class="account-page__kicker">Social media plan</p>
    <h2>{{ plan.landingCopy?.headline || 'Post this today' }}</h2>
    <p class="social-plan__span">{{ plan.span || '4 hours · finish today' }}</p>
    <p v-if="plan.landingCopy?.subhead" class="social-plan__hook">{{ plan.landingCopy.subhead }}</p>

    <div class="social-plan">
      <div class="social-plan__platforms" role="tablist" aria-label="Social platforms">
        <button
          v-for="platform in plan.platforms"
          :key="platform.id"
          type="button"
          class="social-plan__platform"
          role="tab"
          :class="{ 'is-active': active && active.id === platform.id }"
          :aria-selected="active && active.id === platform.id"
          @click="selectPlatform(platform.id)"
        >
          {{ platform.label }}
        </button>
      </div>

      <ol v-if="active" class="social-plan__hours">
        <li
          v-for="step in active.hours"
          :key="step.hour"
          class="social-plan__hour"
          :class="{ 'is-open': openHour === step.hour }"
        >
          <button
            type="button"
            class="social-plan__hour-btn"
            :aria-expanded="openHour === step.hour"
            @click="toggleHour(step.hour)"
          >
            <span class="social-plan__hour-label">
              <strong>{{ step.hour }}</strong>
              <span>{{ step.roleLabel }}</span>
            </span>
            <span class="social-plan__hour-task">{{ step.task }}</span>
            <span class="social-plan__chevron" aria-hidden="true">{{ openHour === step.hour ? '−' : '+' }}</span>
          </button>

          <div v-if="openHour === step.hour" class="social-plan__hour-body">
            <p class="social-plan__section-label">Open source · GitHub</p>
            <ul class="social-plan__tool-grid">
              <li v-for="tool in step.openSource || []" :key="tool.repo" class="social-plan__tool">
                <span class="social-plan__tool-name">{{ tool.name }}</span>
                <span class="social-plan__meta">{{ tool.licence }} · {{ stars(tool.stars) }}</span>
                <p v-if="toolSummary(tool)" class="social-plan__tool-summary">{{ toolSummary(tool) }}</p>
                <a
                  class="social-plan__link"
                  :href="tool.url"
                  target="_blank"
                  rel="noopener noreferrer"
                >{{ tool.repo }} ↗</a>
              </li>
            </ul>

            <p class="social-plan__section-label">Paid tools</p>
            <ul class="social-plan__tool-grid">
              <li v-for="tool in step.paid || []" :key="tool.name" class="social-plan__tool social-plan__tool--paid">
                <span class="social-plan__tool-name">{{ tool.name }}</span>
                <span class="social-plan__meta">Paid</span>
                <p v-if="toolSummary(tool)" class="social-plan__tool-summary">{{ toolSummary(tool) }}</p>
                <p v-if="tool.whyPaid" class="social-plan__tool-why-paid">
                  <span class="social-plan__why-label">Why paid</span>
                  {{ tool.whyPaid }}
                </p>
                <a
                  v-if="tool.url"
                  class="social-plan__link"
                  :href="tool.url"
                  target="_blank"
                  rel="noopener noreferrer"
                >Open {{ tool.name }} ↗</a>
              </li>
            </ul>
          </div>
        </li>
      </ol>
    </div>
  </article>

  <article v-else class="launch-plan">
    <p class="account-page__kicker">Launch plan</p>
    <h2>{{ plan.landingCopy?.headline || 'This week' }}</h2>
    <p v-if="plan.customer"><span class="idea-card__label">Customer:</span> {{ plan.customer }}</p>
    <p v-if="plan.offer"><span class="idea-card__label">Offer:</span> {{ plan.offer }}</p>
    <p v-if="plan.landingCopy?.subhead">{{ plan.landingCopy.subhead }}</p>
    <p v-if="plan.landingCopy?.cta" class="launch-plan__cta">{{ plan.landingCopy.cta }}</p>
    <h3>7-day schedule</h3>
    <ol class="launch-plan__days">
      <li v-for="step in plan.schedule || []" :key="step.day">
        <strong>{{ step.day }}.</strong> {{ step.task }}
      </li>
    </ol>
    <h3>Outreach</h3>
    <ul class="launch-plan__notes">
      <li v-for="(message, index) in plan.outreach || []" :key="index">{{ message }}</li>
    </ul>
    <p v-if="plan.validationMetric"><span class="idea-card__label">Validation metric:</span> {{ plan.validationMetric }}</p>
  </article>
</template>

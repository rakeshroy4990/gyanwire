<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import MicroSteps from './MicroSteps.vue';
import ToolTabs from './ToolTabs.vue';
import PaidBenefit from './PaidBenefit.vue';

const props = defineProps({
  open: { type: Boolean, default: false },
  detail: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  path: { type: String, default: 'open_source' },
});

const emit = defineEmits(['close']);

const toolTab = ref(normalizePath(props.path));
const closeBtn = ref(null);

const microSteps = computed(() => {
  if (!props.detail) return [];
  if (toolTab.value === 'paid') {
    return props.detail.paid?.microSteps || props.detail.activeMicroSteps || [];
  }
  return props.detail.openSource?.microSteps || props.detail.activeMicroSteps || [];
});

const paidCost = computed(() => {
  const tools = props.detail?.paid?.tools || [];
  const priced = tools.find((t) => Number(t?.priceInrMonth) > 0);
  return priced?.priceInrMonth ?? null;
});

const gain = computed(() => props.detail?.gain || {});

watch(
  () => props.path,
  (path) => {
    toolTab.value = normalizePath(path);
  },
);

watch(
  () => props.open,
  (isOpen) => {
    document.body.classList.toggle('app-dialog-open', isOpen);
    if (isOpen) {
      toolTab.value = normalizePath(props.path);
      requestAnimationFrame(() => closeBtn.value?.focus());
    }
  },
);

onMounted(() => {
  window.addEventListener('keydown', onKey);
});

onUnmounted(() => {
  window.removeEventListener('keydown', onKey);
  document.body.classList.remove('app-dialog-open');
});

function onKey(e) {
  if (e.key === 'Escape' && props.open) {
    e.preventDefault();
    emit('close');
  }
}

function normalizePath(path) {
  const p = String(path || 'open_source').toLowerCase();
  return p === 'paid' ? 'paid' : 'open_source';
}
</script>

<template>
  <div
    class="step-drawer"
    :class="{ 'is-open': open }"
    role="dialog"
    aria-modal="true"
    aria-labelledby="step-drawer-title"
    :aria-hidden="open ? 'false' : 'true'"
  >
    <button type="button" class="step-drawer__backdrop" aria-label="Close step" @click="emit('close')" />
    <aside class="step-drawer__panel">
      <header class="step-drawer__head">
        <div>
          <p v-if="detail?.order" class="account-page__kicker">Step {{ detail.order }}</p>
          <h2 id="step-drawer-title">{{ detail?.title || 'Step' }}</h2>
        </div>
        <button
          ref="closeBtn"
          type="button"
          class="step-drawer__close"
          aria-label="Close"
          @click="emit('close')"
        >
          ×
        </button>
      </header>

      <p v-if="loading" class="step-drawer__status">Loading step…</p>
      <p v-else-if="error" class="error">{{ error }}</p>

      <template v-else-if="detail">
        <p class="step-card__label">Goal</p>
        <p>{{ detail.goal }}</p>

        <p class="step-card__label">Input → Output</p>
        <p>{{ detail.input }} → {{ detail.output }}</p>

        <p class="step-card__label">Done when</p>
        <p>{{ detail.doneWhen }}</p>

        <p class="step-card__label">Micro-steps</p>
        <MicroSteps :steps="microSteps" />

        <template v-if="detail.commonMistakes">
          <p class="step-card__label">Common mistakes</p>
          <p>{{ detail.commonMistakes }}</p>
        </template>

        <template v-if="detail.ifStuck">
          <p class="step-card__label">If stuck</p>
          <p>{{ detail.ifStuck }}</p>
        </template>

        <p class="step-card__label">Tools</p>
        <ToolTabs
          :open-source-tools="detail.openSource?.tools || []"
          :paid-tools="detail.paid?.tools || []"
          :free-tools="detail.freeNotOpenSource || []"
          :initial-tab="toolTab"
          @update:tab="toolTab = $event"
        />

        <PaidBenefit
          v-if="toolTab === 'paid'"
          :cost-inr-month="paidCost"
          :open-source-minutes="detail.openSourceMinutes"
          :paid-minutes="detail.paidMinutes"
          :gain-pct="gain.pct"
          :gain-basis="gain.basis || gain.label || 'estimate'"
          :what-changes="detail.paid?.whatChanges || ''"
          :give-up="detail.paid?.giveUp || ''"
        />
      </template>
    </aside>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue';

const props = defineProps({
  open: { type: Boolean, default: false },
  idea: { type: Object, default: null },
  submitting: { type: Boolean, default: false },
  error: { type: String, default: '' },
});

const emit = defineEmits(['close', 'confirm']);

const form = reactive({
  format: 'short_vertical',
  budgetMinutes: 240,
  demo: 'no',
});

const formats = [
  { id: 'short_vertical', label: 'Short vertical' },
  { id: 'linkedin_post', label: 'LinkedIn post' },
  { id: 'youtube_long', label: 'YouTube long' },
  { id: 'carousel', label: 'Carousel' },
];

const budgets = [
  { value: 240, label: '4 h' },
  { value: 480, label: '1 day' },
  { value: 960, label: '2 days' },
];

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      form.format = 'short_vertical';
      form.budgetMinutes = 240;
      form.demo = 'no';
    }
  },
);

function onKey(e) {
  if (e.key === 'Escape') emit('close');
}

function confirm() {
  emit('confirm', {
    format: form.format,
    budgetMinutes: form.budgetMinutes,
    demo: form.demo === 'yes',
  });
}
</script>

<template>
  <div
    v-if="open"
    class="plan-choice-sheet"
    role="dialog"
    aria-modal="true"
    aria-label="Choose plan options"
    @keydown="onKey"
  >
    <button type="button" class="plan-choice-sheet__backdrop" aria-label="Close" @click="emit('close')" />
    <div class="plan-choice-sheet__panel">
      <div class="plan-choice-sheet__head">
        <div>
          <p class="ideas-panel__kicker">Make a plan</p>
          <h2>{{ idea?.title || 'Plan options' }}</h2>
        </div>
        <button type="button" class="ideas-panel__close" aria-label="Close" @click="emit('close')">×</button>
      </div>

      <fieldset class="plan-choice-sheet__group">
        <legend>Format</legend>
        <div class="plan-choice-sheet__options">
          <label v-for="opt in formats" :key="opt.id" class="plan-choice-sheet__option">
            <input v-model="form.format" type="radio" name="format" :value="opt.id" />
            {{ opt.label }}
          </label>
        </div>
      </fieldset>

      <fieldset class="plan-choice-sheet__group">
        <legend>Budget</legend>
        <p class="plan-choice-sheet__hint">How much time you can spend on this plan</p>
        <div class="plan-choice-sheet__options">
          <label v-for="opt in budgets" :key="opt.value" class="plan-choice-sheet__option">
            <input v-model.number="form.budgetMinutes" type="radio" name="budget" :value="opt.value" />
            {{ opt.label }}
          </label>
        </div>
      </fieldset>

      <fieldset class="plan-choice-sheet__group">
        <legend>Demo live product</legend>
        <div class="plan-choice-sheet__options">
          <label class="plan-choice-sheet__option">
            <input v-model="form.demo" type="radio" name="demo" value="yes" />
            Yes
          </label>
          <label class="plan-choice-sheet__option">
            <input v-model="form.demo" type="radio" name="demo" value="no" />
            No
          </label>
        </div>
      </fieldset>

      <p v-if="error" class="error">{{ error }}</p>

      <div class="plan-choice-sheet__actions">
        <button type="button" class="btn btn--ghost" @click="emit('close')">Cancel</button>
        <button type="button" class="btn btn--primary" :disabled="submitting" @click="confirm">
          {{ submitting ? 'Building…' : 'Build plan' }}
        </button>
      </div>
    </div>
  </div>
</template>

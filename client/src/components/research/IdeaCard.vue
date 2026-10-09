<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { useIdeas } from '../../composables/useIdeas.js';
import { useProfile } from '../../composables/useProfile.js';
import PageSkeleton from '../ui/PageSkeleton.vue';

const props = defineProps({
  item: { type: Object, default: null },
  industry: { type: String, default: '' },
});
const emit = defineEmits(['close']);
const { runFromNews, idea, loading, error, limitReached } = useIdeas();
const { load, save, saving } = useProfile();
const refineOpen = ref(false);
const refineError = ref('');
const form = reactive({
  consent: true,
  persona: 'working',
  goal90d: 'learn',
  incomeBand: '0',
  capitalBand: '0',
  investPct: 10,
  hoursPerWeek: 10,
  industries: [props.industry || 'IT'],
  languages: ['en'],
});

async function loadIdea(force = false) {
  if (!props.item?.url) return;
  await runFromNews({
    url: props.item.url,
    title: props.item.title,
    description: props.item.description || props.item.why || '',
    industry: props.industry,
    force,
  });
}

function applyProfile(data) {
  if (!data) return;
  form.persona = data.persona || form.persona;
  form.goal90d = data.goal90d || form.goal90d;
  form.incomeBand = data.incomeBand || form.incomeBand;
  form.capitalBand = data.capitalBand || form.capitalBand;
  form.investPct = Number(data.investPct) || form.investPct;
  form.hoursPerWeek = Number(data.hoursPerWeek) || form.hoursPerWeek;
  if (Array.isArray(data.industries) && data.industries.length) {
    form.industries = [...data.industries];
  }
}

async function refine() {
  refineError.value = '';
  try {
    const data = await save({
      ...form,
      consent: true,
      industries: form.industries?.length ? form.industries : [props.industry || 'IT'],
    });
    applyProfile(data);
    refineOpen.value = false;
    await loadIdea(true);
  } catch (err) {
    refineError.value = err.message || 'Could not refine this idea.';
  }
}

onMounted(async () => {
  try {
    applyProfile(await load());
  } catch {
    // Starter profile is created by the idea call.
  }
  await loadIdea(false);
});

watch(() => props.item?.url, () => loadIdea(false));
</script>

<template>
  <div class="auth-modal" role="presentation">
    <div class="auth-modal__backdrop" @click="emit('close')" />
    <div
      class="auth-modal__panel auth-modal__panel--scroll"
      role="dialog"
      aria-modal="true"
      aria-labelledby="ideaTitle"
    >
      <button type="button" class="auth-modal__close" aria-label="Close" @click="emit('close')">
        <span aria-hidden="true">×</span>
      </button>
      <div class="auth-modal__brand" aria-hidden="true">
        <span class="auth-modal__mark" />
      </div>
      <header class="auth-modal__intro">
        <h2 id="ideaTitle">Idea</h2>
        <p class="auth-modal__lede">
          {{ loading ? 'Building a first idea from this finding.' : 'A scored way to act on this finding.' }}
        </p>
      </header>
      <PageSkeleton v-if="loading" variant="compact" label="Scoring ideas" />
      <p v-else-if="error" class="form-error">{{ error }}</p>
      <p v-if="limitReached" class="auth-modal__footer">
        <RouterLink to="/pricing">See plans</RouterLink>
      </p>
      <div v-else-if="idea" class="idea-card">
        <p class="idea-card__score">{{ idea.score }}</p>
        <h3>{{ idea.title }}</h3>
        <p>{{ idea.whyNow || idea.why }}</p>
        <p v-if="idea.disclaimer" class="disclaimer">{{ idea.disclaimer }}</p>
        <p>Who pays: {{ idea.segment || 'The people named in the news.' }}</p>
        <p>How to start: {{ idea.offer || idea.first_customer_path }}</p>
        <p>Cost: ₹{{ idea.capitalNeededInr ?? 0 }} estimate</p>
        <p v-if="idea.drivers">Drivers: {{ idea.drivers.join(', ') }}</p>
        <RouterLink class="btn btn--primary btn--block" :to="`/plan?ideaId=${idea.id || ''}`">Plan to finish</RouterLink>
        <button type="button" class="btn btn--ghost btn--block" @click="refineOpen = !refineOpen">
          {{ refineOpen ? 'Hide refine more' : 'Refine more' }}
        </button>
        <form v-if="refineOpen" class="auth-form" @submit.prevent="refine">
          <p class="auth-field__hint">Answer only what you want. We rescore the idea with your answers.</p>
          <label class="auth-field">
            <span class="auth-field__label">Persona</span>
            <select v-model="form.persona">
              <option value="student">Student</option>
              <option value="fresher">Fresher</option>
              <option value="working">Working</option>
              <option value="self_employed">Self-employed</option>
              <option value="founder">Founder</option>
            </select>
          </label>
          <label class="auth-field">
            <span class="auth-field__label">90-day goal</span>
            <select v-model="form.goal90d">
              <option value="first_income">First income</option>
              <option value="side_income">Side income</option>
              <option value="start_business">Start a business</option>
              <option value="switch_job">Switch job</option>
              <option value="learn">Learn</option>
            </select>
          </label>
          <label class="auth-field">
            <span class="auth-field__label">Income band</span>
            <select v-model="form.incomeBand">
              <option value="0">₹0</option>
              <option value="under_15k">Under ₹15k</option>
              <option value="15_30">₹15–30k</option>
              <option value="30_50">₹30–50k</option>
              <option value="50_100">₹50k–1L</option>
              <option value="over_100">Over ₹1L</option>
            </select>
          </label>
          <label class="auth-field">
            <span class="auth-field__label">Hours per week</span>
            <select v-model.number="form.hoursPerWeek">
              <option :value="5">5</option>
              <option :value="10">10</option>
              <option :value="20">20</option>
              <option :value="40">40+</option>
            </select>
          </label>
          <p v-if="refineError" class="form-error">{{ refineError }}</p>
          <button class="btn btn--primary btn--block" type="submit" :disabled="saving || loading">
            {{ saving || loading ? 'Updating…' : 'Update idea' }}
          </button>
        </form>
      </div>
    </div>
  </div>
</template>

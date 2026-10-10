<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useIdeas } from '../composables/useIdeas.js';
import { useProfile } from '../composables/useProfile.js';
import {
  ideaSourceRouteQuery,
  rememberIdeaSource,
  resolveIdeaSource,
} from '../services/ideaSource.js';
import PageSkeleton from '../components/ui/PageSkeleton.vue';

const route = useRoute();
const router = useRouter();
const { runFromNews, ideas, loading, error, limitReached } = useIdeas();
const { load, save, saving } = useProfile();
const refineOpen = ref(false);
const refineError = ref('');
const source = ref(null);
const sourceMissing = ref(false);
const form = reactive({
  consent: true,
  persona: 'working',
  goal90d: 'learn',
  incomeBand: '0',
  capitalBand: '0',
  investPct: 10,
  hoursPerWeek: 10,
  industries: [],
  languages: ['en'],
});

const newsUrl = computed(() => source.value?.url || '');
const newsTitle = computed(() => source.value?.title || '');
const newsDescription = computed(() => source.value?.description || '');
const industry = computed(() => source.value?.industry || '');
const expectedCount = computed(() => {
  const fromSource = Number(source.value?.ideaCount);
  if (Number.isFinite(fromSource) && fromSource > 0) return Math.min(8, Math.round(fromSource));
  const n = Number(route.query.ideaCount);
  return Number.isFinite(n) && n > 0 ? Math.min(8, Math.round(n)) : 4;
});

function moneyLine(row) {
  return row?.business_model || row?.businessModel || '';
}

function templateHint(row) {
  const format = String(row?.format || '').toLowerCase();
  return format.includes('template')
    ? 'Ready-to-fill files. You make the pack once from this news, then sell each copy.'
    : '';
}

/** @returns {'ok' | 'missing' | 'replaced'} */
async function hydrateSource() {
  sourceMissing.value = false;
  let resolved = resolveIdeaSource(route.query);
  if (!resolved && typeof route.query.src === 'string' && route.query.src) {
    source.value = null;
    sourceMissing.value = true;
    return 'missing';
  }
  if (resolved?.url && !resolved.src) {
    const legacy = {
      ...resolved,
      ideaCount: route.query.ideaCount,
    };
    const src = await rememberIdeaSource(legacy);
    if (src) {
      resolved = { ...legacy, src };
      source.value = resolved;
      await router.replace({ name: 'idea', query: { src } });
      return 'replaced';
    }
  }
  source.value = resolved;
  return 'ok';
}

function goBack() {
  if (window.history.length > 1) {
    router.back();
    return;
  }
  router.push({ name: 'research' });
}

function planLink(ideaId) {
  return {
    name: 'plan',
    query: {
      ideaId: ideaId || '',
      ...ideaSourceRouteQuery(source.value),
    },
  };
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

async function loadIdeas(force = false) {
  if (!newsUrl.value) return;
  if (!form.industries.length) {
    form.industries = [industry.value || 'IT'];
  }
  await runFromNews({
    url: newsUrl.value,
    title: newsTitle.value,
    description: newsDescription.value,
    industry: industry.value,
    signalType: source.value?.signalType || '',
    whyIdea: source.value?.whyIdea || '',
    force,
  });
}

async function refine() {
  refineError.value = '';
  try {
    const data = await save({
      ...form,
      consent: true,
      industries: form.industries?.length ? form.industries : [industry.value || 'IT'],
    });
    applyProfile(data);
    refineOpen.value = false;
    await loadIdeas(true);
  } catch (err) {
    refineError.value = err.message || 'Could not refine these ideas.';
  }
}

function scoreLabel(value) {
  const n = Number(value);
  return Number.isFinite(n) ? `${Math.round(n)}%` : '—';
}

async function bootFromRoute() {
  const status = await hydrateSource();
  if (status === 'replaced') return;
  if (!form.industries.length) {
    form.industries = [industry.value || 'IT'];
  }
  await loadIdeas(false);
}

onMounted(async () => {
  try {
    applyProfile(await load());
  } catch {
    // Starter profile is created by the idea call.
  }
  await bootFromRoute();
});

watch(
  () => String(route.query.src || '') + '|' + String(route.query.url || ''),
  () => bootFromRoute(),
);
</script>

<template>
  <main class="account-page idea-page">
    <button type="button" class="page-back" @click="goBack">← Back</button>

    <header class="account-page__hero">
      <p class="account-page__kicker">Idea</p>
      <h1>Choose one way to act on this finding</h1>
      <p v-if="newsTitle">
        From:
        <a v-if="newsUrl" :href="newsUrl" target="_blank" rel="noopener noreferrer">{{ newsTitle }}</a>
        <template v-else>{{ newsTitle }}</template>
      </p>
      <p v-else>Open a finding in research, then choose Get an idea.</p>
    </header>

    <template v-if="sourceMissing">
      <p class="account-empty">This idea link expired in this tab. Open the finding again from research.</p>
      <RouterLink class="btn btn--primary" to="/">Back to research</RouterLink>
    </template>

    <template v-else-if="!newsUrl">
      <p class="account-empty">Pick a finding first so we can score ideas from that story.</p>
      <RouterLink class="btn btn--primary" to="/">Back to research</RouterLink>
    </template>

    <template v-else>
      <PageSkeleton v-if="loading" variant="options" :rows="expectedCount" label="Scoring ideas" />

      <p v-else-if="error" class="form-error" role="alert">{{ error }}</p>

      <p v-if="limitReached" class="account-notice">
        Daily idea limit reached.
        <RouterLink to="/pricing">See plans</RouterLink>
      </p>

      <template v-else-if="!loading && ideas.length">
        <p class="idea-page__sort-note">Each card is a different offer. Higher scores are a closer fit.</p>
        <ul class="idea-options">
          <li v-for="row in ideas" :key="row.id || row.title">
            <article class="idea-option">
              <div class="idea-option__top">
                <p class="idea-option__format">{{ row.format || 'Idea' }}</p>
                <p class="idea-option__score">{{ scoreLabel(row.score) }}</p>
              </div>
              <h2>{{ row.title }}</h2>
              <p>{{ row.offer || row.first_customer_path || row.whyNow || row.why }}</p>
              <p v-if="templateHint(row)" class="auth-field__hint">{{ templateHint(row) }}</p>
              <p v-if="row.disclaimer" class="disclaimer">{{ row.disclaimer }}</p>
              <p class="idea-option__meta">
                <template v-if="moneyLine(row)">{{ moneyLine(row) }} · </template>
                ₹{{ row.capitalNeededInr ?? 0 }} to start
              </p>
              <RouterLink class="btn btn--primary" :to="planLink(row.id)">Choose this</RouterLink>
            </article>
          </li>
        </ul>
      </template>

      <section v-if="newsUrl && !limitReached" class="account-card">
        <h2>Refine more</h2>
        <p class="auth-field__hint">Answer only what you want. We rescore ideas with your answers.</p>
        <button type="button" class="btn btn--ghost" @click="refineOpen = !refineOpen">
          {{ refineOpen ? 'Hide refine' : 'Refine profile answers' }}
        </button>
        <form v-if="refineOpen" class="account-form" @submit.prevent="refine">
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
          <button class="btn btn--primary" type="submit" :disabled="saving || loading">
            {{ saving || loading ? 'Updating…' : 'Update ideas' }}
          </button>
        </form>
      </section>
    </template>
  </main>
</template>

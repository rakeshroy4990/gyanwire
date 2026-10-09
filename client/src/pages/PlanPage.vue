<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useProduct } from '../composables/useProduct.js';
import PageSkeleton from '../components/ui/PageSkeleton.vue';

const route = useRoute();
const plan = ref(null);
const loading = ref(true);
const { skillPlan, error } = useProduct();

const ideaId = computed(() => (typeof route.query.ideaId === 'string' ? route.query.ideaId : ''));

const buckets = [
  { id: 'learning', label: 'Learn with' },
  { id: 'tools', label: 'Make it with' },
  { id: 'proof', label: 'Prove it with' },
  { id: 'community', label: 'Talk it through with' },
];

function billingLabel(value) {
  if (value === 'free') return 'free';
  if (value === 'once') return 'one-time';
  if (value === 'monthly') return 'monthly';
  return value || '';
}

function toolsIn(bucket) {
  return (plan.value?.lines || []).filter((line) => line.bucket === bucket);
}

async function load() {
  plan.value = null;
  if (!ideaId.value) {
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    plan.value = await skillPlan(ideaId.value);
  } catch {
    plan.value = null;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
watch(ideaId, load);
</script>

<template>
  <main class="account-page">
    <PageSkeleton v-if="loading" variant="cards" :rows="4" label="Loading the plan" />
    <template v-else-if="!ideaId">
      <header class="account-page__hero">
        <p class="account-page__kicker">Plan</p>
        <h1>Start from a news finding</h1>
        <p>The plan is how you finish one idea from a story you just read. Open a finding, choose an idea, then come back for the weekly goals and the tools.</p>
      </header>
      <RouterLink class="btn btn--primary" to="/">Back to research</RouterLink>
    </template>
    <template v-else-if="plan">
      <header class="account-page__hero">
        <p class="account-page__kicker">From the news</p>
        <h1>{{ plan.ideaTitle }}</h1>
        <p>{{ plan.goal }}</p>
        <p v-if="plan.newsTitle">
          News:
          <a v-if="plan.newsUrl" :href="plan.newsUrl" target="_blank" rel="noopener noreferrer">{{ plan.newsTitle }}</a>
          <template v-else>{{ plan.newsTitle }}</template>
        </p>
        <p v-if="plan.offer">How it starts: {{ plan.offer }}</p>
      </header>

      <section class="account-card">
        <h2>This month</h2>
        <dl class="account-facts">
          <dt>You can spend</dt>
          <dd>₹{{ plan.skillBudgetMonth }}</dd>
          <dt>This plan uses</dt>
          <dd>₹{{ plan.total }}</dd>
          <dt>Weeks to finish</dt>
          <dd>{{ plan.weeks?.length || 0 }}</dd>
        </dl>
        <p>{{ plan.explanation }}</p>
      </section>

      <section class="account-card">
        <h2>Tools in this plan</h2>
        <p>Each tool is counted once. A later week that uses it again does not add the price.</p>
        <div v-for="bucket in buckets" :key="bucket.id" class="plan-tools">
          <template v-if="toolsIn(bucket.id).length">
            <h3>{{ bucket.label }}</h3>
            <ul>
              <li v-for="line in toolsIn(bucket.id)" :key="line.id">
                <span>{{ line.name }}</span>
                <span>₹{{ line.costInr }} · {{ billingLabel(line.billing) }}<template v-if="line.cancelBy"> · cancel by {{ line.cancelBy }}</template></span>
              </li>
            </ul>
          </template>
        </div>
      </section>

      <ol class="plan-weeks">
        <li v-for="week in plan.weeks" :key="week.weekNo" class="account-card">
          <p class="account-page__kicker">Week {{ week.weekNo }}</p>
          <h2>{{ week.goal }}</h2>
          <p class="plan-tool">
            Tool · {{ week.toolName }} · ₹{{ week.costInr }} · {{ billingLabel(week.billing) }}
            <span v-if="week.cancelBy"> · cancel by {{ week.cancelBy }}</span>
          </p>
          <ul>
            <li v-for="task in week.tasks" :key="task">{{ task }}</li>
          </ul>
          <p>Done when: {{ week.metric }}.</p>
        </li>
      </ol>

      <RouterLink class="btn btn--primary" :to="`/roadmap?ideaId=${ideaId}`">Check in each week</RouterLink>
    </template>
    <template v-else>
      <header class="account-page__hero">
        <p class="account-page__kicker">Plan</p>
        <h1>This idea needs a news finding</h1>
        <p v-if="error" class="account-notice form-error">{{ error }}</p>
        <p>Open a story in research and choose an idea. The weekly path is built from that idea, not from a general tool list.</p>
      </header>
      <RouterLink class="btn btn--primary" to="/">Back to research</RouterLink>
    </template>
  </main>
</template>

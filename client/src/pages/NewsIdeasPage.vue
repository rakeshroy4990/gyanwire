<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useNewsIdeas } from '../composables/useNewsIdeas.js';
import { startCreate } from '../composables/actionPlanSession.js';
import NewsIdeasView from '../components/ideas/NewsIdeasView.vue';
import PlanSheet from '../components/ideas/PlanSheet.vue';

const route = useRoute();
const router = useRouter();
const planSheetOpen = ref(false);
const planSubmitting = ref(false);
const planError = ref('');

const {
  loading,
  error,
  item,
  ideas,
  headline,
  selected,
  ratingId,
  stageLabel,
  researchStatus,
  researchSummary,
  openForItem,
  close,
  rate,
  selectIdea,
} = useNewsIdeas();

const routeNews = computed(() => ({
  id: String(route.params.id || ''),
  title: typeof route.query.title === 'string' ? route.query.title : '',
  url: typeof route.query.url === 'string' ? route.query.url : '',
}));

const pageTitle = computed(() => headline.value || routeNews.value.title || 'Ideas');

async function loadFromRoute() {
  const newsItem = routeNews.value;
  if (!newsItem.id) {
    error.value = 'This news item has no id.';
    return;
  }
  planSheetOpen.value = false;
  planError.value = '';
  await openForItem(newsItem);
}

function goBack() {
  router.push({ name: 'research' });
}

function onMakePlan(idea) {
  selectIdea(idea);
  planError.value = '';
  planSheetOpen.value = true;
}

async function onNotUseful(idea) {
  if (!idea?.id) return;
  await rate(idea.id, 'not_useful');
}

async function onPlanConfirm({ format, budgetMinutes, demo }) {
  const newsItem = item.value || routeNews.value;
  if (!newsItem?.url) {
    planError.value = 'Missing news URL for this plan.';
    return;
  }
  planSubmitting.value = true;
  planError.value = '';
  try {
    const plan = await startCreate({
      newsUrl: newsItem.url,
      title: newsItem.title || headline.value || '',
      budgetMinutes,
      demo: !!demo,
      format,
      save: true,
      ideaTitle: selected.value?.title || '',
      whyNow: selected.value?.whyNow || '',
    });
    planSheetOpen.value = false;
    if (plan?.planId) {
      router.push({ name: 'action-plan', params: { id: plan.planId } });
    }
  } catch (err) {
    planError.value = err.message || 'Could not build that plan.';
  } finally {
    planSubmitting.value = false;
  }
}

function closePlanSheet() {
  planSheetOpen.value = false;
  selectIdea(null);
}

onMounted(loadFromRoute);
watch(() => String(route.params.id || ''), loadFromRoute);
onBeforeUnmount(close);
</script>

<template>
  <main class="account-page news-ideas-page">
    <button type="button" class="page-back" @click="goBack">← Back to research</button>

    <header class="account-page__hero">
      <p class="account-page__kicker">Ideas from this news</p>
      <h1>{{ pageTitle }}</h1>
      <p v-if="routeNews.url">
        <a
          :href="routeNews.url"
          target="_blank"
          rel="noopener noreferrer"
        >Open original article</a>
      </p>
    </header>

    <NewsIdeasView
      :loading="loading"
      :error="error"
      :ideas="ideas"
      :rating-id="ratingId"
      :stage-label="stageLabel"
      :research-status="researchStatus"
      :research-summary="researchSummary"
      @make-plan="onMakePlan"
      @not-useful="onNotUseful"
    />

    <PlanSheet
      :open="planSheetOpen"
      :idea="selected"
      :submitting="planSubmitting"
      :error="planError"
      @close="closePlanSheet"
      @confirm="onPlanConfirm"
    />
  </main>
</template>

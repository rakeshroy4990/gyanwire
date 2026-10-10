<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuth } from '../../composables/useAuth.js';
import { useResearch } from '../../composables/useResearch.js';
import { useProduct } from '../../composables/useProduct.js';
import { rememberIdeaSource } from '../../services/ideaSource.js';
import ResearchPanel from './ResearchPanel.vue';
import ResultsPanel from './ResultsPanel.vue';

const router = useRouter();
const { isAuthenticated, greetingName, openLoginPopup } = useAuth();
const product = useProduct();
const claim = ref('');

const {
  catalog,
  industry,
  subcategory,
  thoughts,
  formError,
  isLoading,
  thread,
  hasThread,
  browseResults,
  browseSub,
  browseFreshness,
  browseNote,
  showBrowseResults,
  emptyMessage,
  showEmpty,
  subs,
  queryPreview,
  selectIndustry,
  selectSub,
  runSearch,
  clearForm,
  searchesLeftLabel,
  limitReached,
  limitMessage,
  upgradeUrl,
} = useResearch();

async function onIdea(item) {
  if (!isAuthenticated.value) {
    openLoginPopup();
    return;
  }
  const scopedIndustry = industry.value && industry.value !== 'All'
    ? industry.value
    : (item.industry || '');
  const src = await rememberIdeaSource({
    url: item.url || '',
    title: item.title || '',
    description: item.whyIdea || item.description || item.why || '',
    industry: scopedIndustry,
    ideaCount: item.ideaCount,
    signalType: item.signalType || '',
    whyIdea: item.whyIdea || '',
  });
  router.push({
    name: 'idea',
    query: src ? { src } : {
      url: item.url || '',
      title: item.title || '',
      description: (item.description || item.why || '').slice(0, 1500),
      industry: scopedIndustry,
      ideaCount: item.ideaCount ? String(item.ideaCount) : '',
    },
  });
}

async function onFeedback(body) {
  if (body?.signalType) {
    await product.newsFeedback(body);
    return;
  }
  await product.feedback(body);
}

async function onClaim(sentence) {
  try {
    const data = await product.claim(sentence);
    claim.value = data?.stance || '';
  } catch (err) {
    claim.value = err.message;
  }
}
</script>

<template>
  <main class="page">
    <ResearchPanel
      :greeting-name="isAuthenticated ? greetingName : ''"
      :catalog="catalog"
      :industry="industry"
      :subcategory="subcategory"
      :subs="subs"
      :thoughts="thoughts"
      :query-preview="queryPreview"
      :form-error="formError"
      :is-loading="isLoading"
      :thread="thread"
      :has-thread="hasThread"
      :searches-left-label="searchesLeftLabel"
      :limit-reached="limitReached"
      :limit-message="limitMessage"
      :upgrade-url="upgradeUrl"
      @update:thoughts="thoughts = $event"
      @select-industry="selectIndustry"
      @select-sub="selectSub"
      @search="runSearch"
      @clear="clearForm"
      @idea="onIdea"
      @feedback="onFeedback"
      @claim="onClaim"
    />
    <ResultsPanel
      v-if="!hasThread"
      :results="browseResults"
      :results-sub="browseSub"
      :freshness="browseFreshness"
      :note="browseNote"
      :empty-message="emptyMessage"
      :show-empty="showEmpty && !isLoading"
      :show-results="showBrowseResults"
      :is-loading="isLoading && !hasThread"
      show-ideas
      @idea="onIdea"
      @feedback="onFeedback"
    />
    <p v-if="claim" class="disclaimer">Claim check: {{ claim }}</p>
  </main>
</template>

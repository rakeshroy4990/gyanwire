<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { useAuth } from '../../composables/useAuth.js';
import { useResearch } from '../../composables/useResearch.js';
import { useProduct } from '../../composables/useProduct.js';
import ResearchPanel from './ResearchPanel.vue';
import ResultsPanel from './ResultsPanel.vue';
import IdeaCard from './IdeaCard.vue';
import IndustryStrip from './IndustryStrip.vue';

const { isAuthenticated, greetingName, openLoginPopup } = useAuth();
const product = useProduct();
const ideaItem = ref(null);
const claim = ref('');

onMounted(() => {
  window.addEventListener('keydown', onDialogKey);
});

onUnmounted(() => {
  window.removeEventListener('keydown', onDialogKey);
  document.body.classList.remove('app-dialog-open');
});

function onIdea(item) {
  if (!isAuthenticated.value) {
    openLoginPopup();
    return;
  }
  ideaItem.value = { ...item, industry: industry.value };
}

watch(ideaItem, (value) => {
  document.body.classList.toggle('app-dialog-open', Boolean(value));
});

function onDialogKey(event) {
  if (event.key === 'Escape' && ideaItem.value) {
    ideaItem.value = null;
  }
}

async function onFeedback(body) {
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
      :empty-message="emptyMessage"
      :show-empty="showEmpty && !isLoading"
      :show-results="showBrowseResults"
      :is-loading="isLoading && !hasThread"
      show-ideas
      @idea="onIdea"
      @feedback="onFeedback"
    />
    <IndustryStrip
      v-if="!isLoading || hasThread"
      :industry="industry"
      :results="hasThread ? [] : browseResults"
    />
    <p v-if="claim" class="disclaimer">Claim check: {{ claim }}</p>
    <IdeaCard
      v-if="ideaItem"
      :item="ideaItem"
      :industry="ideaItem.industry || industry"
      @close="ideaItem = null"
    />
  </main>
</template>

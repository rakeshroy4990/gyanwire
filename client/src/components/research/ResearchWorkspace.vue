<script setup>
import { nextTick, onActivated, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useAuth } from '../../composables/useAuth.js';
import { useResearch } from '../../composables/useResearch.js';
import { useProduct } from '../../composables/useProduct.js';
import { rememberIdeaSource } from '../../services/ideaSource.js';
import {
  clearResearchReturn,
  findingElement,
  loadResearchReturn,
  rememberResearchReturn,
} from '../../services/researchWorkspace.js';
import ResearchPanel from './ResearchPanel.vue';
import ResultsPanel from './ResultsPanel.vue';

const router = useRouter();
const { isAuthenticated, greetingName, openLoginPopup } = useAuth();
const product = useProduct();
const claim = ref('');
const returnKey = ref('');

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
  browseCountries,
  browsePage,
  browseTotal,
  browseTotalPages,
  browseHasMore,
  country,
  showBrowseResults,
  emptyMessage,
  showEmpty,
  subs,
  queryPreview,
  selectIndustry,
  selectSub,
  selectCountry,
  goBrowsePage,
  dismissBrowseItem,
  hideBrowseSource,
  runSearch,
  clearForm,
  searchesLeftLabel,
  limitReached,
  limitMessage,
  upgradeUrl,
} = useResearch();

function restoreFindingPosition() {
  const target = loadResearchReturn();
  if (!target) return;
  returnKey.value = target.id || target.url;
  nextTick(() => {
    requestAnimationFrame(() => {
      const el = findingElement(target);
      if (el) el.scrollIntoView({ block: 'center' });
      else if (target.scrollY) window.scrollTo({ top: target.scrollY, left: 0 });
      clearResearchReturn();
    });
  });
}

onMounted(restoreFindingPosition);
onActivated(restoreFindingPosition);

watch([industry, subcategory], () => {
  returnKey.value = '';
});

function scopedIndustryFor(item) {
  return industry.value && industry.value !== 'All'
    ? industry.value
    : (item?.industry || '');
}

async function openFullIdeaPage(item) {
  if (!item) return;
  rememberResearchReturn(item, window.scrollY);
  const scopedIndustry = scopedIndustryFor(item);
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

async function onIdea(item) {
  if (!isAuthenticated.value) {
    openLoginPopup();
    return;
  }
  rememberResearchReturn(item, window.scrollY);
  if (!item?.id) {
    await openFullIdeaPage(item);
    return;
  }
  router.push({
    name: 'news-ideas',
    params: { id: item.id },
    query: {
      ...(item.title ? { title: item.title } : {}),
      ...(item.url ? { url: item.url } : {}),
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
      :return-key="returnKey"
      @update:thoughts="thoughts = $event"
      @select-industry="selectIndustry"
      @select-sub="selectSub"
      @select-country="selectCountry"
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
      :countries="browseCountries"
      :country="country"
      :page="browsePage"
      :total="browseTotal"
      :total-pages="browseTotalPages"
      :has-more="browseHasMore"
      :empty-message="emptyMessage"
      :show-empty="showEmpty && !isLoading"
      :show-results="showBrowseResults"
      :is-loading="isLoading && !hasThread"
      :return-key="returnKey"
      show-ideas
      allow-dismiss
      @idea="onIdea"
      @feedback="onFeedback"
      @dismiss="dismissBrowseItem"
      @hide-source="hideBrowseSource"
      @select-country="selectCountry"
      @page="goBrowsePage"
    />
    <p v-if="claim" class="disclaimer">Claim check: {{ claim }}</p>
  </main>
</template>

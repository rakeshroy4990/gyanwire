<script setup>
import { nextTick, onActivated, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useAuth } from '../../composables/useAuth.js';
import { useResearch } from '../../composables/useResearch.js';
import { useProduct } from '../../composables/useProduct.js';
import { useNewsIdeas } from '../../composables/useNewsIdeas.js';
import { rememberIdeaSource } from '../../services/ideaSource.js';
import { startCreate } from '../../composables/actionPlanSession.js';
import {
  clearResearchReturn,
  findingElement,
  loadResearchReturn,
  rememberResearchReturn,
} from '../../services/researchWorkspace.js';
import ResearchPanel from './ResearchPanel.vue';
import ResultsPanel from './ResultsPanel.vue';
import IdeasPanel from '../ideas/IdeasPanel.vue';
import PlanSheet from '../ideas/PlanSheet.vue';

const router = useRouter();
const { isAuthenticated, greetingName, openLoginPopup } = useAuth();
const product = useProduct();
const claim = ref('');
const returnKey = ref('');
const planSheetOpen = ref(false);
const planSubmitting = ref(false);
const planError = ref('');

const {
  open: ideasOpen,
  loading: ideasLoading,
  error: ideasError,
  item: ideasItem,
  ideas: ideaRows,
  headline: ideasHeadline,
  selected: selectedIdea,
  ratingId: ideasRatingId,
  stageLabel: ideasStageLabel,
  researchStatus: ideasResearchStatus,
  researchSummary: ideasResearchSummary,
  openForItem,
  close: closeIdeas,
  rate: rateIdea,
  selectIdea,
} = useNewsIdeas();

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
  const target = item || ideasItem.value;
  if (!target) return;
  rememberResearchReturn(target, window.scrollY);
  const scopedIndustry = scopedIndustryFor(target);
  const src = await rememberIdeaSource({
    url: target.url || '',
    title: target.title || '',
    description: target.whyIdea || target.description || target.why || '',
    industry: scopedIndustry,
    ideaCount: target.ideaCount,
    signalType: target.signalType || '',
    whyIdea: target.whyIdea || '',
  });
  closeIdeas();
  planSheetOpen.value = false;
  router.push({
    name: 'idea',
    query: src ? { src } : {
      url: target.url || '',
      title: target.title || '',
      description: (target.description || target.why || '').slice(0, 1500),
      industry: scopedIndustry,
      ideaCount: target.ideaCount ? String(target.ideaCount) : '',
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
  planSheetOpen.value = false;
  planError.value = '';
  await openForItem(item);
}

function onMakePlan(idea) {
  selectIdea(idea);
  planError.value = '';
  planSheetOpen.value = true;
}

async function onNotUseful(idea) {
  if (!idea?.id) return;
  await rateIdea(idea.id, 'not_useful');
}

async function onPlanConfirm({ format, budgetMinutes, demo }) {
  const newsItem = ideasItem.value;
  if (!newsItem?.url) {
    planError.value = 'Missing news URL for this plan.';
    return;
  }
  planSubmitting.value = true;
  planError.value = '';
  try {
    const plan = await startCreate({
      newsUrl: newsItem.url,
      title: newsItem.title || ideasHeadline.value || '',
      budgetMinutes,
      demo: !!demo,
      format,
      save: true,
      ideaTitle: selectedIdea.value?.title || '',
      whyNow: selectedIdea.value?.whyNow || '',
    });
    planSheetOpen.value = false;
    closeIdeas();
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

    <IdeasPanel
      :open="ideasOpen"
      :loading="ideasLoading"
      :error="ideasError"
      :headline="ideasHeadline"
      :ideas="ideaRows"
      :rating-id="ideasRatingId"
      :stage-label="ideasStageLabel"
      :research-status="ideasResearchStatus"
      :research-summary="ideasResearchSummary"
      @close="closeIdeas"
      @make-plan="onMakePlan"
      @not-useful="onNotUseful"
      @open-full="openFullIdeaPage()"
    />

    <PlanSheet
      :open="planSheetOpen"
      :idea="selectedIdea"
      :submitting="planSubmitting"
      :error="planError"
      @close="closePlanSheet"
      @confirm="onPlanConfirm"
    />
  </main>
</template>

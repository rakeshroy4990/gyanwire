<script setup>
import { useAuth } from '../../composables/useAuth.js';
import { useResearch } from '../../composables/useResearch.js';
import ResearchPanel from './ResearchPanel.vue';
import ResultsPanel from './ResultsPanel.vue';

const { isAuthenticated, greetingName } = useAuth();

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
    />
    <ResultsPanel
      v-if="!hasThread"
      :results="browseResults"
      :results-sub="browseSub"
      :empty-message="emptyMessage"
      :show-empty="showEmpty && !isLoading"
      :show-results="showBrowseResults"
      :is-loading="isLoading && !hasThread"
    />
  </main>
</template>

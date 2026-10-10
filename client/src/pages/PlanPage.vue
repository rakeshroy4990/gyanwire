<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useProduct } from '../composables/useProduct.js';
import { useOptionCatalog } from '../composables/useOptionCatalog.js';
import { usePlanVariants } from '../composables/usePlanVariants.js';
import {
  bestValueSwap,
  formatInr,
  simulatePlan,
} from '../composables/usePlanSimulator.js';
import FuelGauge from '../components/plan/FuelGauge.vue';
import PlanStory from '../components/plan/PlanStory.vue';
import GearPack from '../components/plan/GearPack.vue';
import TrailMap from '../components/plan/TrailMap.vue';
import WeekSheet from '../components/plan/WeekSheet.vue';
import EfficiencyMeter from '../components/plan/whatif/EfficiencyMeter.vue';
import SwapDrawer from '../components/plan/whatif/SwapDrawer.vue';
import VariantTabs from '../components/plan/whatif/VariantTabs.vue';
import CompareTable from '../components/plan/whatif/CompareTable.vue';
import BestValueNudge from '../components/plan/whatif/BestValueNudge.vue';
import ValueOfTimeInput from '../components/plan/whatif/ValueOfTimeInput.vue';
import ShareVariantCard from '../components/plan/whatif/ShareVariantCard.vue';
import PageSkeleton from '../components/ui/PageSkeleton.vue';
import { fetchWhatIfText } from '../services/llm.service.js';

const route = useRoute();
const router = useRouter();
const plan = ref(null);
const loading = ref(true);
const activeWeekNo = ref(null);
const swapOpen = ref(false);
const { skillPlan, error } = useProduct();
const { catalog, optionsForTask, refresh: refreshCatalog } = useOptionCatalog();

const ideaId = computed(() => (typeof route.query.ideaId === 'string' ? route.query.ideaId : ''));
const ideaReturnQuery = computed(() => {
  if (typeof route.query.src === 'string' && route.query.src) {
    return { src: route.query.src };
  }
  const q = {};
  for (const key of ['url', 'title', 'description', 'industry']) {
    if (typeof route.query[key] === 'string' && route.query[key]) q[key] = route.query[key];
  }
  return q;
});
const canReturnToIdeas = computed(() => Boolean(ideaReturnQuery.value.src || ideaReturnQuery.value.url));

const {
  variants,
  activeId,
  active,
  limitError,
  upgradeUrl,
  select,
  duplicate,
  remove,
  setWeekOption,
  setHourlyValue,
} = usePlanVariants(ideaId, plan);

const activeWeek = computed(() => (plan.value?.weeks || []).find((w) => w.weekNo === activeWeekNo.value) || null);

const simulation = computed(() => {
  if (!plan.value || !active.value) return null;
  return simulatePlan({
    weeks: plan.value.weeks,
    weekOptions: active.value.weekOptions,
    catalog: catalog.value,
    monthsToGoal: plan.value.monthsToGoal || 1,
    hourlyValueInr: active.value.hourlyValueInr,
    weeklyCapacityHours: plan.value.hoursPerWeek || 5,
  });
});

const baselineOptions = computed(() => {
  const map = {};
  for (const week of plan.value?.weeks || []) {
    map[week.weekNo] = week.toolId;
  }
  return map;
});

const showGhost = computed(() => {
  if (!active.value) return false;
  return JSON.stringify(active.value.weekOptions) !== JSON.stringify(baselineOptions.value);
});

const nudge = computed(() => {
  if (!plan.value || !active.value) return { suggestion: null, message: '' };
  return bestValueSwap({
    weeks: plan.value.weeks,
    weekOptions: active.value.weekOptions,
    catalog: catalog.value,
    monthsToGoal: plan.value.monthsToGoal || 1,
  });
});

const sheetOption = computed(() => {
  if (!activeWeek.value || !active.value) return null;
  const id = active.value.weekOptions[activeWeek.value.weekNo] || activeWeek.value.toolId;
  return catalog.value.find((o) => o.id === id) || null;
});

const swapOptions = computed(() => {
  if (!activeWeek.value) return [];
  return optionsForTask(activeWeek.value.taskType || 'learning');
});

const whatIfText = ref('');

watch(simulation, async (sim) => {
  if (!sim || !plan.value || !showGhost.value) {
    whatIfText.value = '';
    return;
  }
  const costDelta = Math.round((sim.costInr || 0) - (plan.value.total || 0));
  const hoursDelta = Math.round(sim.savedHours?.[0] || 0);
  whatIfText.value = await fetchWhatIfText(costDelta, hoursDelta);
});

const newsLabel = computed(() => {
  const title = String(plan.value?.newsTitle || '').trim();
  if (!title) return '';
  if (title.length <= 72) return title;
  return `${title.slice(0, 69).trimEnd()}…`;
});

const planMeta = computed(() => {
  if (!plan.value) return '';
  const weeks = plan.value.weeks?.length || 0;
  const budget = formatInr(plan.value.skillBudgetMonth || 0);
  return `${weeks} weeks · ${budget} skill budget this month`;
});

function goBack() {
  if (canReturnToIdeas.value) {
    router.push({ name: 'idea', query: ideaReturnQuery.value });
    return;
  }
  if (window.history.length > 1) {
    router.back();
    return;
  }
  router.push({ name: 'research' });
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
    await refreshCatalog();
  } catch {
    plan.value = null;
  } finally {
    loading.value = false;
  }
}

function openWeek(weekNo) {
  activeWeekNo.value = weekNo;
}

function closeSheet() {
  activeWeekNo.value = null;
  swapOpen.value = false;
}

async function onSwapSelect(option) {
  if (!activeWeek.value || !option) return;
  await setWeekOption(activeWeek.value.weekNo, option.id);
  swapOpen.value = false;
}

async function onDuplicate() {
  try {
    await duplicate();
  } catch {
    // limitError set in composable
  }
}

async function applyNudge(suggestion) {
  if (!suggestion) return;
  await setWeekOption(suggestion.weekNo, suggestion.optionId);
}

onMounted(load);
watch(ideaId, load);
</script>

<template>
  <main class="plan-page">
    <button type="button" class="page-back" @click="goBack">
      ← {{ canReturnToIdeas ? 'Back to ideas' : 'Back' }}
    </button>

    <PageSkeleton v-if="loading" variant="cards" :rows="4" label="Loading the plan" />

    <template v-else-if="!ideaId">
      <header class="plan-page__hero">
        <p class="plan-page__eyebrow">Plan to finish</p>
        <h1>Start from a scored idea</h1>
        <p>Open a finding, pick an idea, then come here for the week-by-week path and tools.</p>
      </header>
      <RouterLink class="btn btn--primary" to="/">Back to research</RouterLink>
    </template>

    <template v-else-if="plan">
      <header class="plan-page__hero">
        <p class="plan-page__eyebrow">Plan to finish</p>
        <h1>{{ plan.ideaTitle }}</h1>
        <p v-if="plan.offer" class="plan-page__offer">{{ plan.offer }}</p>
        <p class="plan-page__meta">{{ planMeta }}</p>
        <p v-if="newsLabel" class="plan-page__source">
          Inspired by
          <a
            v-if="plan.newsUrl"
            :href="plan.newsUrl"
            target="_blank"
            rel="noopener noreferrer"
            :title="plan.newsTitle"
          >{{ newsLabel }}</a>
          <template v-else>{{ newsLabel }}</template>
        </p>
      </header>

      <PlanStory :plan="plan" />

      <div class="plan-gauges">
        <FuelGauge :budget="plan.skillBudgetMonth" :spent="simulation?.costInr ?? plan.total" />
        <EfficiencyMeter
          :simulation="simulation"
          :hours-per-sitting="plan.hoursPerSitting || 5"
        />
      </div>
      <p v-if="whatIfText" class="plan-page__meta">{{ whatIfText }}</p>

      <section class="plan-page__section">
        <h2>Variants</h2>
        <VariantTabs
          :variants="variants"
          :active-id="activeId"
          @select="select"
          @duplicate="onDuplicate"
          @remove="remove"
        />
        <p v-if="limitError" class="account-notice form-error">
          {{ limitError }}
          <RouterLink :to="upgradeUrl">Upgrade</RouterLink>
        </p>
        <ValueOfTimeInput
          :model-value="active?.hourlyValueInr"
          :payback="simulation?.payback"
          @update:model-value="setHourlyValue"
        />
      </section>

      <section class="plan-page__section">
        <h2>Gear pack</h2>
        <p class="plan-page__section-lede">Each tool is counted once. A later week that uses it again does not add the price.</p>
        <GearPack
          :lines="plan.lines"
          :news-url="plan.newsUrl || ''"
          :news-title="plan.newsTitle || ''"
        />
      </section>

      <section class="plan-page__section">
        <h2>Trail map</h2>
        <p class="plan-page__section-lede">Tap a week to open its sheet. Swap gear to see cost and efficiency estimates.</p>
        <TrailMap
          :weeks="plan.weeks"
          :active-week="activeWeekNo"
          :week-results="simulation?.weekResults || []"
          :show-ghost="showGhost"
          @select="openWeek"
        />
      </section>

      <BestValueNudge
        :message="nudge.message"
        :suggestion="nudge.suggestion"
        @apply="applyNudge"
      />

      <section v-if="variants.length > 1" class="plan-page__section">
        <h2>Compare</h2>
        <CompareTable
          :variants="variants"
          :weeks="plan.weeks"
          :catalog="catalog"
          :months-to-goal="plan.monthsToGoal || 1"
          :hours-per-week="plan.hoursPerWeek || 5"
        />
        <ShareVariantCard
          :title="plan.ideaTitle || 'Plan to finish'"
          :variants="variants"
          :weeks="plan.weeks"
          :catalog="catalog"
          :months-to-goal="plan.monthsToGoal || 1"
          :hours-per-week="plan.hoursPerWeek || 5"
        />
      </section>

      <div class="plan-page__actions">
        <RouterLink class="btn btn--primary" :to="`/roadmap?ideaId=${ideaId}`">Check in each week</RouterLink>
        <button type="button" class="btn btn--ghost" @click="goBack">
          {{ canReturnToIdeas ? 'Back to ideas' : 'Back' }}
        </button>
      </div>

      <WeekSheet
        :open="Boolean(activeWeek)"
        :week="activeWeek"
        :option-name="sheetOption?.name || activeWeek?.toolName"
        :option-cost="sheetOption?.costInr ?? activeWeek?.costInr"
        :billing="sheetOption?.billing || activeWeek?.billing"
        @close="closeSheet"
        @swap="swapOpen = true"
      />

      <SwapDrawer
        :open="swapOpen"
        :task-type="activeWeek?.taskType || 'learning'"
        :options="swapOptions"
        :selected-id="sheetOption?.id || activeWeek?.toolId"
        @close="swapOpen = false"
        @select="onSwapSelect"
      />
    </template>

    <template v-else>
      <header class="plan-page__hero">
        <p class="plan-page__eyebrow">Plan to finish</p>
        <h1>This idea needs a news finding</h1>
        <p v-if="error" class="account-notice form-error">{{ error }}</p>
        <p>Open a story in research and choose an idea. The weekly path is built from that idea.</p>
      </header>
      <RouterLink class="btn btn--primary" to="/">Back to research</RouterLink>
    </template>
  </main>
</template>

<script setup>
import { onMounted } from 'vue';
import FuelGauge from '../components/plan/FuelGauge.vue';
import { useLlmSummary } from '../composables/useLlmSummary.js';

const { summary, error, loading, refresh } = useLlmSummary();
onMounted(refresh);

function rows(bucket) {
  const source = bucket || {};
  return Object.entries(source).map(([name, cost]) => ({ name, cost }));
}
</script>

<template>
  <main class="pricing-page">
    <header class="pricing-page__hero">
      <p class="pricing-page__brand">Gyanwire</p>
      <h1>Model spend</h1>
      <p>Today against the daily cap. Stage flags stay off until you turn them on.</p>
    </header>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-else-if="loading">Loading spend</p>
    <template v-else-if="summary">
      <FuelGauge
        label="Today"
        :budget="Number(summary.capInr) || 0"
        :spent="Number(summary.today?.costInr) || 0"
      />
      <section>
        <h2>Today by stage</h2>
        <ul>
          <li v-for="row in rows(summary.today?.byStage)" :key="row.name">
            {{ row.name }} · ₹{{ Number(row.cost).toFixed(2) }}
          </li>
          <li v-if="!rows(summary.today?.byStage).length">No model calls yet.</li>
        </ul>
        <p>7 days ₹{{ Number(summary.days7?.costInr || 0).toFixed(2) }} · 30 days ₹{{ Number(summary.days30?.costInr || 0).toFixed(2) }}</p>
        <p>Cache hits {{ summary.cacheHits }} across {{ summary.cacheRows }} shared plans. Escalations today {{ summary.today?.escalations || 0 }}.</p>
      </section>
      <section v-if="summary.news">
        <h2>News</h2>
        <p v-if="summary.news.error">{{ summary.news.error }}</p>
        <p v-else>Classification cost, 30 days: ₹{{ Number(summary.news.classifyCostInr30d || 0).toFixed(2) }}</p>
        <ul>
          <li v-for="row in summary.news.industries || []" :key="row.industry">
            {{ row.industry }} · median age {{ Number(row.medianAgeDays || 0).toFixed(1) }} days · fresh {{ row.freshPct }}%
          </li>
        </ul>
        <ul>
          <li v-for="row in summary.news.sources || []" :key="row.industry + row.name">
            {{ row.industry }} · {{ row.name }} · {{ row.itemsDay }} today
            <template v-if="row.consecutiveFailures"> · {{ row.consecutiveFailures }} failures</template>
            <template v-if="!row.enabled"> · off</template>
          </li>
        </ul>
        <ul>
          <li v-for="row in summary.news.usefulness || []" :key="(row.industry || '') + (row.signalType || '')">
            {{ row.industry }} · {{ row.signalType || 'unknown' }} · yes {{ row.yes }} · no {{ row.no }} · not my industry {{ row.notRelevant }}
          </li>
        </ul>
      </section>
    </template>
  </main>
</template>

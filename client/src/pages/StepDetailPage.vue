<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { fetchStepDetail } from '../services/actionPlan.service.js';
import StepDetailView from '../components/actionplan/StepDetailView.vue';

const route = useRoute();
const router = useRouter();

const detail = ref(null);
const loading = ref(false);
const error = ref('');

const planId = computed(() => String(route.params.id || ''));
const stepKey = computed(() => String(route.params.stepKey || ''));
const path = computed(() => {
  const raw = typeof route.query.path === 'string' ? route.query.path : 'open_source';
  return raw === 'paid' ? 'paid' : 'open_source';
});

const pageTitle = computed(() => detail.value?.title || 'Step');
const pageKicker = computed(() => (
  detail.value?.order ? `Step ${detail.value.order}` : 'Plan step'
));

async function loadDetail() {
  if (!planId.value || !stepKey.value) {
    error.value = 'Missing plan or step.';
    detail.value = null;
    return;
  }
  loading.value = true;
  error.value = '';
  detail.value = null;
  try {
    detail.value = await fetchStepDetail(planId.value, stepKey.value, path.value);
  } catch (err) {
    error.value = err.message || 'Could not load step.';
  } finally {
    loading.value = false;
  }
}

function goBack() {
  if (planId.value) {
    router.push({ name: 'action-plan', params: { id: planId.value } });
    return;
  }
  router.push({ name: 'research' });
}

onMounted(loadDetail);
watch(
  () => `${planId.value}|${stepKey.value}|${path.value}`,
  loadDetail,
);
</script>

<template>
  <main class="account-page step-detail-page">
    <button type="button" class="page-back" @click="goBack">← Back to plan</button>

    <header class="account-page__hero">
      <p class="account-page__kicker">{{ pageKicker }}</p>
      <h1>{{ pageTitle }}</h1>
    </header>

    <StepDetailView
      :detail="detail"
      :loading="loading"
      :error="error"
      :path="path"
    />
  </main>
</template>

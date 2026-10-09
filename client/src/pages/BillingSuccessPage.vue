<script setup>
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { useUsage } from '../composables/useUsage.js';
import { fetchBillingStatus } from '../services/billing.service.js';
import PageSkeleton from '../components/ui/PageSkeleton.vue';

const { refreshUsage, planName, searchesLeftLabel } = useUsage();
const statusLabel = ref('');
const error = ref('');
const loading = ref(true);

onMounted(async () => {
  loading.value = true;
  try {
    await refreshUsage();
    const status = await fetchBillingStatus();
    statusLabel.value = status.status === 'active' || status.planId !== 'free'
      ? `You are on ${status.planName}.`
      : 'Payment received. Your plan will activate once Razorpay confirms the webhook.';
  } catch (err) {
    error.value = err.message || 'Could not refresh plan status.';
    statusLabel.value = 'We could not confirm the plan yet.';
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="billing-success">
    <p class="pricing-page__brand">Gyanwire</p>
    <h1>You are all set</h1>
    <PageSkeleton v-if="loading" variant="compact" label="Refreshing plan" />
    <template v-else>
      <p>{{ statusLabel }}</p>
      <p v-if="planName" class="billing-success__meta">
        Plan: {{ planName }} · {{ searchesLeftLabel }}
      </p>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <RouterLink class="btn btn--primary" to="/">Back to research</RouterLink>
    </template>
  </main>
</template>

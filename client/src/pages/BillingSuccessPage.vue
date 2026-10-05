<script setup>
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { useUsage } from '../composables/useUsage.js';
import { fetchBillingStatus } from '../services/billing.service.js';

const { refreshUsage, planName, searchesLeftLabel } = useUsage();
const statusLabel = ref('Refreshing your plan…');
const error = ref('');

onMounted(async () => {
  try {
    await refreshUsage();
    const status = await fetchBillingStatus();
    statusLabel.value = status.status === 'active' || status.planId !== 'free'
      ? `You are on ${status.planName}.`
      : 'Payment received. Your plan will activate once Razorpay confirms the webhook.';
  } catch (err) {
    error.value = err.message || 'Could not refresh plan status.';
  }
});
</script>

<template>
  <main class="billing-success">
    <p class="pricing-page__brand">Gyanwire</p>
    <h1>You are all set</h1>
    <p>{{ statusLabel }}</p>
    <p v-if="planName" class="billing-success__meta">
      Plan: {{ planName }} · {{ searchesLeftLabel }}
    </p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <RouterLink class="btn btn--primary" to="/">Back to research</RouterLink>
  </main>
</template>

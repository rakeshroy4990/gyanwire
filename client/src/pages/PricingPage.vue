<script setup>
import { ref } from 'vue';
import { useBilling } from '../composables/useBilling.js';
import { useAuth } from '../composables/useAuth.js';

const interval = ref('monthly');
const { checkout, isBusy, error } = useBilling();
const { isAuthenticated, openLoginPopup } = useAuth();

const plans = [
  {
    id: 'free',
    name: 'Free',
    monthly: 0,
    annual: 0,
    blurb: 'Try Gyanwire on a daily search allowance.',
    features: ['5 searches / day', 'Markdown export later', 'Community support'],
  },
  {
    id: 'pro',
    name: 'Pro',
    monthly: 999,
    annual: 9990,
    blurb: 'For solo researchers who need depth every day.',
    features: ['100 searches / day', 'Save projects', 'Export DOCX/PDF', 'Alerts'],
  },
  {
    id: 'team',
    name: 'Team',
    monthly: 2999,
    annual: 29990,
    blurb: 'Shared seats for small R&D and product teams.',
    features: ['500 searches / day', '5 seats', 'Everything in Pro', 'Priority support'],
  },
];

function priceLabel(plan) {
  const amount = interval.value === 'annual' ? plan.annual : plan.monthly;
  if (!amount) return '₹0';
  return interval.value === 'annual' ? `₹${amount.toLocaleString('en-IN')}/yr` : `₹${amount.toLocaleString('en-IN')}/mo`;
}

function onSelect(planId) {
  if (planId === 'free') return;
  if (!isAuthenticated.value) {
    openLoginPopup();
    return;
  }
  checkout(planId, interval.value);
}
</script>

<template>
  <main class="pricing-page">
    <header class="pricing-page__hero">
      <h1>Pricing</h1>
      <p>Start free. Upgrade when your research needs more room.</p>
      <div class="pricing-toggle" role="group" aria-label="Billing interval">
        <button
          type="button"
          class="pricing-toggle__btn"
          :class="{ 'is-active': interval === 'monthly' }"
          @click="interval = 'monthly'"
        >
          Monthly
        </button>
        <button
          type="button"
          class="pricing-toggle__btn"
          :class="{ 'is-active': interval === 'annual' }"
          @click="interval = 'annual'"
        >
          Annual
        </button>
      </div>
    </header>

    <p v-if="error" class="form-error" role="alert">{{ error }}</p>

    <div class="pricing-grid">
      <article
        v-for="plan in plans"
        :key="plan.id"
        class="pricing-card"
        :class="{ 'pricing-card--featured': plan.id === 'pro' }"
      >
        <h2>{{ plan.name }}</h2>
        <p class="pricing-card__price">{{ priceLabel(plan) }}</p>
        <p class="pricing-card__blurb">{{ plan.blurb }}</p>
        <ul>
          <li v-for="feature in plan.features" :key="feature">{{ feature }}</li>
        </ul>
        <button
          type="button"
          class="btn"
          :class="plan.id === 'free' ? 'btn--ghost' : 'btn--primary'"
          :disabled="isBusy || plan.id === 'free'"
          @click="onSelect(plan.id)"
        >
          <template v-if="plan.id === 'free'">Current free tier</template>
          <template v-else>Choose {{ plan.name }}</template>
        </button>
      </article>
    </div>
  </main>
</template>

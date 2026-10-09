<script setup>
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { useBilling } from '../composables/useBilling.js';
import { useAuth } from '../composables/useAuth.js';
import { fetchBillingOffers } from '../services/billing.service.js';
import PageSkeleton from '../components/ui/PageSkeleton.vue';

const interval = ref('monthly');
const offers = ref({ annual: false, student: false });
const loading = ref(true);
onMounted(async () => {
  loading.value = true;
  try {
    offers.value = await fetchBillingOffers();
  } finally {
    loading.value = false;
  }
});
const { checkout, isBusy, error } = useBilling();
const { isAuthenticated, openLoginPopup } = useAuth();

const plans = [
  {
    id: 'free',
    name: 'Free',
    monthly: 0,
    annual: 0,
    blurb: 'Try the workbench with a daily search allowance.',
    features: ['5 searches per day', 'Live query preview', 'India-first ranking'],
  },
  {
    id: 'pro',
    name: 'Pro',
    monthly: 999,
    annual: 9990,
    blurb: 'For solo researchers who need depth every day.',
    features: ['100 searches per day', 'Saved projects', 'Export DOCX and PDF', 'Watch alerts'],
  },
  {
    id: 'team',
    name: 'Team',
    monthly: 2999,
    annual: 29990,
    blurb: 'Shared seats for small R&D and product teams.',
    features: ['500 searches per day', '5 seats', 'Everything in Pro', 'Priority support'],
  },
  {
    id: 'student',
    name: 'Student',
    monthly: 0,
    annual: 0,
    blurb: 'For .ac.in and .edu email addresses. Checkout stays off until a plan id is configured.',
    features: ['Student allowance', 'Idea engine in student mode', 'No charge until billing is configured'],
  },
];

function priceLabel(plan) {
  if (plan.id === 'student') return 'Set in billing';
  const amount = interval.value === 'annual' ? plan.annual : plan.monthly;
  if (!amount) return '₹0';
  return interval.value === 'annual'
    ? `₹${amount.toLocaleString('en-IN')}/yr`
    : `₹${amount.toLocaleString('en-IN')}/mo`;
}

function onSelect(planId) {
  if (planId === 'free') return;
  if (planId === 'student' && !offers.value.student) return;
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
      <p class="pricing-page__brand">Gyanwire</p>
      <h1>Simple plans for deeper research</h1>
      <p>Start free. Upgrade when the daily limit gets in the way. Checkout asks for a GST invoice with place of supply India.</p>
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
          v-if="offers.annual"
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

    <PageSkeleton v-if="loading" variant="pricing" label="Loading plans" />
    <div v-else class="pricing-grid">
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
          v-if="plan.id !== 'free'"
          type="button"
          class="btn btn--primary"
          :disabled="isBusy || (plan.id === 'student' && !offers.student)"
          @click="onSelect(plan.id)"
        >
          {{ plan.id === 'student' && !offers.student ? 'Checkout disabled' : `Choose ${plan.name}` }}
        </button>
        <RouterLink v-else class="btn btn--ghost" to="/">
          Continue free
        </RouterLink>
      </article>
    </div>
  </main>
</template>

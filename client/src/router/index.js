import { createRouter, createWebHistory } from 'vue-router';
import ResearchPage from '../pages/ResearchPage.vue';
import PricingPage from '../pages/PricingPage.vue';
import BillingSuccessPage from '../pages/BillingSuccessPage.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'research', component: ResearchPage },
    { path: '/pricing', name: 'pricing', component: PricingPage },
    { path: '/billing/success', name: 'billing-success', component: BillingSuccessPage },
  ],
  scrollBehavior() {
    return { top: 0 };
  },
});

import { createRouter, createWebHistory } from 'vue-router';
import ResearchPage from '../pages/ResearchPage.vue';
import PricingPage from '../pages/PricingPage.vue';
import BillingSuccessPage from '../pages/BillingSuccessPage.vue';
import ProfilePage from '../pages/ProfilePage.vue';
import PlanPage from '../pages/PlanPage.vue';
import RoadmapPage from '../pages/RoadmapPage.vue';
import OutlinePage from '../pages/OutlinePage.vue';
import ProjectsPage from '../pages/ProjectsPage.vue';
import LegalPage from '../pages/LegalPage.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'research', component: ResearchPage },
    { path: '/pricing', name: 'pricing', component: PricingPage },
    { path: '/billing/success', name: 'billing-success', component: BillingSuccessPage },
    { path: '/profile', name: 'profile', component: ProfilePage },
    { path: '/plan', name: 'plan', component: PlanPage },
    { path: '/roadmap', name: 'roadmap', component: RoadmapPage },
    { path: '/outline', name: 'outline', component: OutlinePage },
    { path: '/projects', name: 'projects', component: ProjectsPage },
    { path: '/terms', name: 'terms', component: LegalPage, meta: { page: 'terms' } },
    { path: '/privacy', name: 'privacy', component: LegalPage, meta: { page: 'privacy' } },
    { path: '/refund', name: 'refund', component: LegalPage, meta: { page: 'refund' } },
    { path: '/contact', name: 'contact', component: LegalPage, meta: { page: 'contact' } },
  ],
  scrollBehavior() {
    return { top: 0 };
  },
});

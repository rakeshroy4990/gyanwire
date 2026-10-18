import { createRouter, createWebHistory } from 'vue-router';
import { hasResearchReturn } from '../services/researchWorkspace.js';
import ResearchPage from '../pages/ResearchPage.vue';
import PricingPage from '../pages/PricingPage.vue';
import BillingSuccessPage from '../pages/BillingSuccessPage.vue';
import ProfilePage from '../pages/ProfilePage.vue';
import IdeaPage from '../pages/IdeaPage.vue';
import NewsIdeasPage from '../pages/NewsIdeasPage.vue';
import PlanPage from '../pages/PlanPage.vue';
import ActionPlanPage from '../pages/ActionPlanPage.vue';
import StepDetailPage from '../pages/StepDetailPage.vue';
import RoadmapPage from '../pages/RoadmapPage.vue';
import OutlinePage from '../pages/OutlinePage.vue';
import ProjectsPage from '../pages/ProjectsPage.vue';
import LlmSpendPage from '../pages/LlmSpendPage.vue';
import LegalPage from '../pages/LegalPage.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'research', component: ResearchPage },
    { path: '/research', redirect: { name: 'research' } },
    { path: '/pricing', name: 'pricing', component: PricingPage },
    { path: '/billing/success', name: 'billing-success', component: BillingSuccessPage },
    { path: '/profile', name: 'profile', component: ProfilePage },
    { path: '/idea', name: 'idea', component: IdeaPage },
    { path: '/news/:id/ideas', name: 'news-ideas', component: NewsIdeasPage },
    { path: '/plan', name: 'plan', component: PlanPage },
    { path: '/action-plan/:id', name: 'action-plan', component: ActionPlanPage },
    { path: '/action-plan/:id/steps/:stepKey', name: 'action-plan-step', component: StepDetailPage },
    { path: '/roadmap', name: 'roadmap', component: RoadmapPage },
    { path: '/outline', name: 'outline', component: OutlinePage },
    { path: '/projects', name: 'projects', component: ProjectsPage },
    { path: '/admin/llm', name: 'llm-spend', component: LlmSpendPage },
    { path: '/terms', name: 'terms', component: LegalPage, meta: { page: 'terms' } },
    { path: '/privacy', name: 'privacy', component: LegalPage, meta: { page: 'privacy' } },
    { path: '/refund', name: 'refund', component: LegalPage, meta: { page: 'refund' } },
    { path: '/contact', name: 'contact', component: LegalPage, meta: { page: 'contact' } },
  ],
  scrollBehavior(to, from, savedPosition) {
    if (to.name === 'research' && hasResearchReturn()) return false;
    if (savedPosition) return savedPosition;
    return { top: 0 };
  },
});

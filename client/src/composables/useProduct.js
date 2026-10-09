import { ref } from 'vue';
import {
  createProject,
  listProjects,
  requestClaimCheck,
  requestIndustryMode,
  requestOutline,
  requestSkillPlan,
  requestWeeklyPlan,
  sendFindingFeedback,
} from '../services/product.service.js';

export function useProduct() {
  const error = ref('');
  const loading = ref(false);

  async function run(fn) {
    loading.value = true;
    error.value = '';
    try {
      return await fn();
    } catch (err) {
      error.value = err.message;
      throw err;
    } finally {
      loading.value = false;
    }
  }

  return {
    error,
    loading,
    skillPlan: (ideaId) => run(() => requestSkillPlan(ideaId)),
    weeklyPlan: (ideaId) => run(() => requestWeeklyPlan(ideaId)),
    outline: (ideaId) => run(() => requestOutline(ideaId)),
    projects: () => run(() => listProjects()),
    addProject: (name) => run(() => createProject(name)),
    feedback: (body) => sendFindingFeedback(body),
    claim: (sentence) => requestClaimCheck(sentence),
    industryMode: (body) => requestIndustryMode(body),
  };
}

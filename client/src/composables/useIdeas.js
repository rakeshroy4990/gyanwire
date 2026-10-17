import { nextTick, ref } from 'vue';
import { requestIdeas, requestLaunchPlan, saveIdea } from '../services/product.service.js';

const STAGE_LABELS = {
  signals: 'Reading the story',
  gate: 'Checking for a real buyer',
  angles: 'Finding angles',
  'idea-build': 'Building offers',
  critic: 'Reviewing offers',
};

function anglePriority(row) {
  const angle = String(row?.angleType || row?.format || '').toLowerCase();
  if (angle === 'comparison_content' || angle.includes('social')) return 0;
  if (angle === 'education_or_training' || angle.includes('education')) return 1;
  return 2;
}

function sortByScore(rows) {
  return [...(rows || [])].sort((a, b) => {
    const priority = anglePriority(a) - anglePriority(b);
    if (priority !== 0) return priority;
    return Number(b.score || 0) - Number(a.score || 0);
  });
}

export function useIdeas() {
  const ideas = ref([]);
  const idea = ref(null);
  const loading = ref(false);
  const error = ref('');
  const limitReached = ref(false);
  const gate = ref(null);
  const removed = ref([]);
  const pipeline = ref(false);
  const emptyReason = ref('');
  const stageLabel = ref('');
  const launchPlan = ref(null);
  const planError = ref('');
  const planLoadingId = ref('');
  const savingId = ref('');
  const savedIds = ref([]);

  function applyIdeaFrame(data) {
    gate.value = data?.gate || null;
    removed.value = Array.isArray(data?.removed) ? data.removed : [];
    pipeline.value = String(data?.pipeline || '').startsWith('idea-pipeline.v');
    emptyReason.value = data?.emptyReason || '';
  }

  async function runFromNews(body) {
    loading.value = true;
    error.value = '';
    limitReached.value = false;
    ideas.value = [];
    idea.value = null;
    gate.value = null;
    removed.value = [];
    // Keep last pipeline mode until meta arrives so the page title does not flash.
    emptyReason.value = '';
    stageLabel.value = 'Scoring ideas';
    launchPlan.value = null;
    planError.value = '';
    try {
      const data = await requestIdeas(body, async (event) => {
        if (event.type === 'status' && event.stage) {
          stageLabel.value = STAGE_LABELS[event.stage] || 'Scoring ideas';
        } else if (event.type === 'meta') {
          applyIdeaFrame(event.data);
        } else if (event.type === 'item' && event.item) {
          const row = event.item;
          const key = row.id || row.title;
          if (key && ideas.value.some((item) => (item.id || item.title) === key)) return;
          ideas.value = sortByScore([...ideas.value, row]);
          idea.value = ideas.value[0] || null;
          await nextTick();
        }
      });
      applyIdeaFrame(data);
      const rows = sortByScore(data?.ideas);
      ideas.value = rows;
      idea.value = rows[0] || null;
      const blocked = gate.value && gate.value.proceed === false;
      if (!rows.length && !blocked && !pipeline.value && !emptyReason.value) {
        error.value = 'No idea fit this profile.';
      }
    } catch (err) {
      if (err.status === 402) limitReached.value = true;
      error.value = err.message || 'Could not build ideas.';
      // Keep any cards that already arrived on the stream.
    } finally {
      loading.value = false;
      stageLabel.value = '';
    }
  }

  async function buildPlan(ideaId) {
    planError.value = '';
    planLoadingId.value = ideaId;
    try {
      launchPlan.value = await requestLaunchPlan(ideaId);
    } catch (err) {
      planError.value = err.message || 'Could not build that plan.';
      launchPlan.value = null;
    } finally {
      planLoadingId.value = '';
    }
  }

  function closePlan() {
    launchPlan.value = null;
    planError.value = '';
  }

  async function save(ideaId) {
    savingId.value = ideaId;
    try {
      await saveIdea(ideaId);
      savedIds.value = [...savedIds.value, ideaId];
    } catch (err) {
      error.value = err.message || 'Could not save that idea.';
    } finally {
      savingId.value = '';
    }
  }

  return {
    idea,
    ideas,
    loading,
    error,
    limitReached,
    gate,
    removed,
    pipeline,
    emptyReason,
    stageLabel,
    launchPlan,
    planError,
    planLoadingId,
    savingId,
    savedIds,
    runFromNews,
    buildPlan,
    closePlan,
    save,
  };
}

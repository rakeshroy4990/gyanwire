const KICKERS = {
  start: 'How you start',
  process: 'The process',
  done: 'Done when',
};

const PHASE_LABELS = {
  learn: 'Learn',
  build: 'Build',
  prove: 'Prove',
  decide: 'Decide',
};

/** Short label for the week rail. Empty when the prior week already named the same tool. */
export function weekRailLabel(week, prevWeek) {
  if (!week) return '';
  const toolKey = week.toolId || week.toolName || '';
  const prevKey = prevWeek ? (prevWeek.toolId || prevWeek.toolName || '') : '';
  if (prevWeek && toolKey && toolKey === prevKey) {
    return '';
  }
  if (week.toolName) return week.toolName;
  return PHASE_LABELS[week.phase] || '';
}

export function phaseLabel(phase) {
  return PHASE_LABELS[phase] || '';
}

export const SCENE_MS = {
  start: 1600,
  process: 1500,
  done: 2200,
};

/** Detail block for one week card on the accumulating tree. */
export function weekDetail(week, allWeeks = [], lineById = null) {
  if (!week) {
    return {
      kicker: '',
      title: '',
      body: '',
      tasks: [],
      toolName: '',
      toolId: '',
      toolUrl: '',
      toolBlurb: '',
      metric: '',
      phase: '',
    };
  }
  const tasks = Array.isArray(week.tasks) ? week.tasks.filter(Boolean) : [];
  const last = allWeeks.length
    ? allWeeks[allWeeks.length - 1]?.weekNo === week.weekNo
    : false;
  const phase = week.phase || '';
  let kicker = PHASE_LABELS[phase] || 'Week';
  if (week.weekNo === 1) kicker = KICKERS.start;
  else if (last) kicker = KICKERS.done;
  const line = lineById?.get?.(week.toolId) || null;
  const toolUrl = week.toolUrl || line?.url || '';
  const toolBlurb = week.toolBlurb || line?.blurb || '';
  return {
    kicker,
    title: week.goal || `Week ${week.weekNo}`,
    body: tasks[0] || '',
    tasks,
    toolName: week.toolName || '',
    toolId: week.toolId || '',
    toolUrl,
    toolBlurb,
    metric: week.metric || '',
    phase,
  };
}

function asWeeks(plan) {
  return [...(plan?.weeks || [])]
    .filter((week) => week && week.weekNo != null)
    .sort((a, b) => a.weekNo - b.weekNo);
}

function sceneFor(week, act, metrics = []) {
  const tasks = Array.isArray(week.tasks) ? week.tasks : [];
  return {
    act,
    kicker: KICKERS[act],
    weekNo: week.weekNo,
    title: act === 'done' ? (week.metric || '') : (week.goal || ''),
    body: act === 'done' ? (week.goal || '') : (tasks[0] || ''),
    toolName: week.toolName || '',
    toolId: week.toolId || '',
    metric: week.metric || '',
    phase: week.phase || '',
    metrics,
  };
}

/**
 * Turns a skill plan into an ordered story: how you start, the middle weeks, then the done-when ending.
 * One-week plans still get a done scene. Empty plans return no scenes.
 */
export function buildPlanStory(plan) {
  const weeks = asWeeks(plan);
  if (!weeks.length) return [];

  const metrics = weeks.map((week) => ({
    weekNo: week.weekNo,
    metric: week.metric || '',
    toolName: week.toolName || '',
  }));

  const scenes = [sceneFor(weeks[0], 'start')];
  if (weeks.length === 1) {
    scenes.push(sceneFor(weeks[0], 'done', metrics));
    return scenes;
  }
  for (const week of weeks.slice(1, -1)) {
    scenes.push(sceneFor(week, 'process'));
  }
  scenes.push(sceneFor(weeks[weeks.length - 1], 'done', metrics));
  return scenes;
}

/** Scene index for a week dot. Prefers the start or process beat over the done card. */
export function sceneIndexForWeek(scenes, weekNo) {
  const list = scenes || [];
  const open = list.findIndex((scene) => scene.weekNo === weekNo && scene.act !== 'done');
  if (open >= 0) return open;
  return list.findIndex((scene) => scene.weekNo === weekNo);
}

/** Tool chips for the strip. Catalog lines win; otherwise the first time each week tool appears. */
export function storyTools(plan) {
  const lines = Array.isArray(plan?.lines) ? plan.lines : [];
  if (lines.length) {
    return lines
      .filter((line) => line && (line.name || line.id))
      .map((line) => ({
        id: line.id || line.name,
        name: line.name || line.id,
      }));
  }
  const seen = new Set();
  const tools = [];
  for (const week of asWeeks(plan)) {
    const id = week.toolId || week.toolName;
    if (!id || seen.has(id)) continue;
    seen.add(id);
    tools.push({ id, name: week.toolName || id });
  }
  return tools;
}

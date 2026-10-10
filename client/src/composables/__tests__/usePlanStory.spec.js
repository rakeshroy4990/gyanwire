import { describe, expect, it } from 'vitest';
import {
  buildPlanStory,
  sceneIndexForWeek,
  storyTools,
  weekDetail,
  weekRailLabel,
} from '../usePlanStory.js';

const weeks = [
  {
    weekNo: 1,
    goal: 'Write the offer',
    tasks: ['Read the story and write who pays'],
    metric: 'Offer written in one sentence',
    toolName: 'Notebook',
    toolId: 'notes',
    phase: 'learn',
  },
  {
    weekNo: 2,
    goal: 'Ship a small piece',
    tasks: ['Spend two focused sittings'],
    metric: 'One artifact saved',
    toolName: 'VS Code',
    toolId: 'vscode',
    phase: 'build',
  },
  {
    weekNo: 3,
    goal: 'Show it to one person',
    tasks: ['Ask one person if they would use it'],
    metric: 'One person has seen it',
    toolName: 'Email',
    toolId: 'email',
    phase: 'prove',
  },
  {
    weekNo: 4,
    goal: 'Decide continue or stop',
    tasks: ['Write the reason'],
    metric: 'Continue or stop is written down',
    toolName: 'Community',
    toolId: 'forum',
    phase: 'decide',
  },
];

describe('buildPlanStory', () => {
  it('opens on start, walks the middle weeks, and ends on done', () => {
    const scenes = buildPlanStory({ weeks });

    expect(scenes.map((scene) => scene.act)).toEqual(['start', 'process', 'process', 'done']);
    expect(scenes[0]).toMatchObject({
      kicker: 'How you start',
      weekNo: 1,
      title: 'Write the offer',
      body: 'Read the story and write who pays',
      toolName: 'Notebook',
      toolId: 'notes',
      metric: 'Offer written in one sentence',
    });
    expect(scenes[1]).toMatchObject({
      kicker: 'The process',
      weekNo: 2,
      toolName: 'VS Code',
      metric: 'One artifact saved',
    });
    expect(scenes[2]).toMatchObject({
      weekNo: 3,
      toolName: 'Email',
      metric: 'One person has seen it',
    });
    expect(scenes[3]).toMatchObject({
      kicker: 'Done when',
      weekNo: 4,
      title: 'Continue or stop is written down',
      body: 'Decide continue or stop',
      toolName: 'Community',
      metric: 'Continue or stop is written down',
    });
    expect(scenes[3].metrics).toEqual([
      { weekNo: 1, metric: 'Offer written in one sentence', toolName: 'Notebook' },
      { weekNo: 2, metric: 'One artifact saved', toolName: 'VS Code' },
      { weekNo: 3, metric: 'One person has seen it', toolName: 'Email' },
      { weekNo: 4, metric: 'Continue or stop is written down', toolName: 'Community' },
    ]);
  });

  it('still adds a done scene for a one-week plan', () => {
    const scenes = buildPlanStory({ weeks: [weeks[0]] });

    expect(scenes).toHaveLength(2);
    expect(scenes[0].act).toBe('start');
    expect(scenes[1].act).toBe('done');
    expect(scenes[1].weekNo).toBe(1);
    expect(scenes[1].title).toBe('Offer written in one sentence');
    expect(scenes[1].toolName).toBe('Notebook');
    expect(scenes[1].metrics).toEqual([
      { weekNo: 1, metric: 'Offer written in one sentence', toolName: 'Notebook' },
    ]);
  });

  it('returns no scenes when the plan has no weeks', () => {
    expect(buildPlanStory(null)).toEqual([]);
    expect(buildPlanStory({})).toEqual([]);
    expect(buildPlanStory({ weeks: [] })).toEqual([]);
  });

  it('orders weeks by week number before building scenes', () => {
    const scenes = buildPlanStory({ weeks: [weeks[2], weeks[0], weeks[1]] });
    expect(scenes.map((scene) => [scene.act, scene.weekNo])).toEqual([
      ['start', 1],
      ['process', 2],
      ['done', 3],
    ]);
  });
});

describe('sceneIndexForWeek', () => {
  it('jumps to the start or process beat when a week has both', () => {
    const scenes = buildPlanStory({ weeks: [weeks[0]] });
    expect(sceneIndexForWeek(scenes, 1)).toBe(0);
  });

  it('jumps to the done scene for the last week', () => {
    const scenes = buildPlanStory({ weeks });
    expect(sceneIndexForWeek(scenes, 4)).toBe(3);
    expect(sceneIndexForWeek(scenes, 2)).toBe(1);
  });
});

describe('storyTools', () => {
  it('uses plan lines when they exist', () => {
    expect(storyTools({
      lines: [{ id: 'vscode', name: 'VS Code' }],
      weeks,
    })).toEqual([{ id: 'vscode', name: 'VS Code' }]);
  });

  it('falls back to the first tool on each week', () => {
    expect(storyTools({
      weeks: [weeks[0], { ...weeks[1], toolId: 'notes', toolName: 'Notebook' }, weeks[2]],
    })).toEqual([
      { id: 'notes', name: 'Notebook' },
      { id: 'email', name: 'Email' },
    ]);
  });
});

describe('weekRailLabel', () => {
  it('names a tool once, then stays blank while the same tool continues', () => {
    const buildA = { weekNo: 2, toolId: 'vscode', toolName: 'VS Code', phase: 'build' };
    const buildB = { weekNo: 3, toolId: 'vscode', toolName: 'VS Code', phase: 'build' };
    expect(weekRailLabel(weeks[0], null)).toBe('Notebook');
    expect(weekRailLabel(buildA, weeks[0])).toBe('VS Code');
    expect(weekRailLabel(buildB, buildA)).toBe('');
    expect(weekRailLabel(weeks[3], buildB)).toBe('Community');
  });
});

describe('weekDetail', () => {
  it('keeps the week goal and tasks on an open card', () => {
    expect(weekDetail(weeks[1], weeks)).toMatchObject({
      kicker: 'Build',
      title: 'Ship a small piece',
      tasks: ['Spend two focused sittings'],
      toolName: 'VS Code',
      metric: 'One artifact saved',
    });
    expect(weekDetail(weeks[0], weeks).kicker).toBe('How you start');
    expect(weekDetail(weeks[3], weeks).kicker).toBe('Done when');
  });
});

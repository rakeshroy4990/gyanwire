import { describe, expect, it } from 'vitest';
import {
  bestValueSwap,
  simulatePlan,
  weekEstimate,
} from '../usePlanSimulator.js';

const weeks = [
  { weekNo: 1, toolId: 'vscode', toolName: 'VS Code', taskType: 'coding', baseHours: 10, billing: 'free', costInr: 0 },
  { weekNo: 2, toolId: 'vscode', toolName: 'VS Code', taskType: 'coding', baseHours: 10, billing: 'free', costInr: 0 },
];

const catalog = [
  {
    id: 'vscode',
    name: 'VS Code',
    billing: 'free',
    costInr: 0,
    monthlyInr: 0,
    taskFit: { coding: { speedup: [1, 1], confidence: 'baseline' } },
  },
  {
    id: 'paid-assist',
    name: 'Paid assist',
    billing: 'monthly',
    costInr: 500,
    monthlyInr: 500,
    taskFit: { coding: { speedup: [1.25, 1.5], confidence: 'placeholder', source: 'test' } },
  },
  {
    id: 'useless-paid',
    name: 'Useless paid',
    billing: 'monthly',
    costInr: 900,
    monthlyInr: 900,
    taskFit: { coding: { speedup: [1, 1], confidence: 'placeholder' } },
  },
];

describe('usePlanSimulator', () => {
  it('free→free is zero change range', () => {
    const est = weekEstimate({
      baseHours: 10,
      option: catalog[0],
      taskType: 'coding',
    });
    expect(est.noChange).toBe(true);
    expect(est.savedRange).toEqual([0, 0]);
    expect(est.hoursRange).toHaveLength(2);
  });

  it('free→paid with known multipliers returns a range', () => {
    const sim = simulatePlan({
      weeks,
      weekOptions: { 1: 'paid-assist', 2: 'vscode' },
      catalog,
      monthsToGoal: 1,
      weeklyCapacityHours: 10,
    });
    expect(sim.savedHours[0]).toBeLessThan(sim.savedHours[1]);
    expect(sim.savedHours[0]).toBeCloseTo(10 - 10 / 1.25, 5);
    expect(sim.savedHours[1]).toBeCloseTo(10 - 10 / 1.5, 5);
    expect(sim.costInr).toBe(500);
    expect(sim.costPerHourSaved).toHaveLength(2);
  });

  it('counts unique tools once for cost', () => {
    const sim = simulatePlan({
      weeks,
      weekOptions: { 1: 'paid-assist', 2: 'paid-assist' },
      catalog,
      monthsToGoal: 2,
    });
    expect(sim.costInr).toBe(1000);
  });

  it('missing task types fall back to [1,1]', () => {
    const est = weekEstimate({
      baseHours: 8,
      option: catalog[1],
      taskType: 'outreach',
    });
    expect(est.noChange).toBe(true);
    expect(est.savedRange).toEqual([0, 0]);
  });

  it('flags not clearly worth it and can recommend staying free', () => {
    const sim = simulatePlan({
      weeks,
      weekOptions: { 1: 'useless-paid', 2: 'vscode' },
      catalog,
      monthsToGoal: 1,
    });
    expect(sim.unclearWorth).toBe(true);

    const nudge = bestValueSwap({
      weeks,
      weekOptions: { 1: 'vscode', 2: 'vscode' },
      catalog,
      monthsToGoal: 1,
    });
    // paid-assist should win; useless-paid should not
    if (nudge.suggestion) {
      expect(nudge.suggestion.optionId).toBe('paid-assist');
    }
  });
});

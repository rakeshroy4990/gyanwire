/**
 * Pure What-If calculator. Estimates are always [lo, hi] ranges.
 */

export const HOURS_PER_SITTING = 5;

export function formatInr(n) {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(Math.round(Number(n) || 0));
}

export function speedupFor(option, taskType) {
  const fit = option?.taskFit?.[taskType];
  const raw = fit?.speedup;
  if (!Array.isArray(raw) || raw.length < 2) {
    return { lo: 1, hi: 1, confidence: 'baseline', source: 'missing task fit → [1,1]' };
  }
  let lo = Number(raw[0]);
  let hi = Number(raw[1]);
  if (!Number.isFinite(lo) || !Number.isFinite(hi) || lo <= 0 || hi <= 0) {
    return { lo: 1, hi: 1, confidence: 'baseline', source: 'invalid speedup → [1,1]' };
  }
  if (lo > hi) {
    const t = lo;
    lo = hi;
    hi = t;
  }
  return {
    lo,
    hi,
    confidence: fit.confidence || 'placeholder',
    source: fit.source || '',
    sampleCount: Number(fit.sampleCount || option?.sampleCount || 0),
  };
}

export function weekEstimate({ baseHours, option, taskType }) {
  const base = Math.max(0, Number(baseHours) || 0);
  const { lo, hi, confidence, source, sampleCount } = speedupFor(option, taskType);
  const hoursRange = [base / hi, base / lo];
  const savedRange = [base - hoursRange[1], base - hoursRange[0]];
  const noChange = Math.abs(lo - 1) < 1e-9 && Math.abs(hi - 1) < 1e-9;
  return {
    baseHours: base,
    speedup: [lo, hi],
    hoursRange,
    savedRange,
    confidence,
    source,
    sampleCount,
    noChange,
    message: noChange ? 'No estimated change for this task.' : null,
  };
}

function optionCostForPlan(option, monthsToGoal) {
  if (!option) return 0;
  const billing = option.billing || 'free';
  const cost = Number(option.costInr || option.monthlyInr || 0);
  if (billing === 'free' || cost <= 0) return 0;
  if (billing === 'monthly') {
    const months = Math.max(1, Number(monthsToGoal) || 1);
    return cost * months;
  }
  return cost;
}

/**
 * @param {object} args
 * @param {Array} args.weeks - skill plan weeks with weekNo, toolId, taskType, baseHours, sittings
 * @param {Record<number|string,string>} args.weekOptions - weekNo -> option id
 * @param {Array} args.catalog
 * @param {number} args.monthsToGoal
 * @param {number|null} args.hourlyValueInr
 * @param {number} args.weeklyCapacityHours
 */
export function simulatePlan({
  weeks,
  weekOptions,
  catalog,
  monthsToGoal = 1,
  hourlyValueInr = null,
  weeklyCapacityHours = 5,
}) {
  const byId = new Map((catalog || []).map((o) => [o.id, o]));
  const weekResults = [];
  const usedIds = new Set();
  let savedLo = 0;
  let savedHi = 0;
  const assumptions = [];

  for (const week of weeks || []) {
    const weekNo = week.weekNo;
    const optionId = weekOptions?.[weekNo] || weekOptions?.[String(weekNo)] || week.toolId;
    const option = byId.get(optionId) || {
      id: optionId,
      name: week.toolName || optionId,
      billing: week.billing || 'free',
      costInr: Number(week.costInr || 0),
      monthlyInr: week.billing === 'monthly' ? Number(week.costInr || 0) : 0,
      taskFit: {},
    };
    if (optionId) usedIds.add(optionId);
    const est = weekEstimate({
      baseHours: week.baseHours ?? (week.sittings || 1) * HOURS_PER_SITTING,
      option,
      taskType: week.taskType || 'learning',
    });
    weekResults.push({
      weekNo,
      optionId: option.id,
      optionName: option.name,
      taskType: week.taskType || 'learning',
      ...est,
      stale: Boolean(option.stale),
    });
    savedLo += est.savedRange[0];
    savedHi += est.savedRange[1];
    assumptions.push({
      weekNo,
      optionId: option.id,
      optionName: option.name,
      taskType: week.taskType || 'learning',
      speedup: est.speedup,
      confidence: est.confidence,
      source: est.source,
      sampleCount: est.sampleCount,
    });
  }

  let costInr = 0;
  for (const id of usedIds) {
    const option = byId.get(id);
    if (option) {
      costInr += optionCostForPlan(option, monthsToGoal);
    } else {
      const week = (weeks || []).find((w) => w.toolId === id);
      if (week) {
        costInr += optionCostForPlan({
          billing: week.billing,
          costInr: week.costInr,
          monthlyInr: week.billing === 'monthly' ? week.costInr : 0,
        }, monthsToGoal);
      }
    }
  }

  const savedHours = [savedLo, savedHi];
  const capacity = Math.max(0.5, Number(weeklyCapacityHours) || 5);
  const weeksShortened = [savedLo / capacity, savedHi / capacity];

  let costPerHourSaved = null;
  if (savedHi > 1e-6) {
    const hi = costInr / Math.max(savedLo, 1e-6);
    const lo = costInr / savedHi;
    costPerHourSaved = [Math.min(lo, hi), Math.max(lo, hi)];
  }

  const unclearWorth = costInr > 0 && savedHi < 0.25;
  let payback = null;
  if (hourlyValueInr != null && Number.isFinite(Number(hourlyValueInr))) {
    const rate = Number(hourlyValueInr);
    const lo = savedLo * rate - costInr;
    const hi = savedHi * rate - costInr;
    let state = 'unclear';
    if (lo > 0) state = 'pays_back';
    else if (hi < 0) state = 'does_not_pay_back';
    payback = { rangeInr: [lo, hi], state };
  }

  return {
    weekResults,
    costInr,
    savedHours,
    weeksShortened,
    costPerHourSaved,
    unclearWorth,
    payback,
    assumptions,
    freeUpgrade: costInr === 0 && savedHi > 0.25,
  };
}

export function bestValueSwap({ weeks, weekOptions, catalog, monthsToGoal = 1 }) {
  const baseline = simulatePlan({ weeks, weekOptions, catalog, monthsToGoal });
  let best = null;
  for (const week of weeks || []) {
    const taskType = week.taskType || 'learning';
    const currentId = weekOptions?.[week.weekNo] || weekOptions?.[String(week.weekNo)] || week.toolId;
    const candidates = (catalog || []).filter((o) => o.taskFit && o.taskFit[taskType]);
    for (const option of candidates) {
      if (option.id === currentId) continue;
      const nextOptions = { ...weekOptions, [week.weekNo]: option.id };
      const sim = simulatePlan({ weeks, weekOptions: nextOptions, catalog, monthsToGoal });
      const deltaSaved = sim.savedHours[0] - baseline.savedHours[0];
      const deltaCost = sim.costInr - baseline.costInr;
      if (sim.unclearWorth) continue;
      if (deltaSaved <= 0.05 && deltaCost >= 0) continue;
      const value = deltaCost <= 0
        ? deltaSaved * 1000
        : deltaSaved / deltaCost;
      if (!best || value > best.value) {
        best = {
          value,
          weekNo: week.weekNo,
          optionId: option.id,
          optionName: option.name,
          reason: deltaCost <= 0
            ? `Week ${week.weekNo}: ${option.name} may save ~${fmtRange(sim.savedHours[0] - baseline.savedHours[0], sim.savedHours[1] - baseline.savedHours[1])} hrs without extra cost (estimate).`
            : `Week ${week.weekNo}: ${option.name} has the best estimated hours saved per rupee.`,
          savedHoursDelta: [
            sim.savedHours[0] - baseline.savedHours[0],
            sim.savedHours[1] - baseline.savedHours[1],
          ],
          costDelta: deltaCost,
        };
      }
    }
  }
  if (!best) {
    return { suggestion: null, message: 'No worthwhile upgrade for this plan (estimate).' };
  }
  return { suggestion: best, message: best.reason };
}

function fmtRange(lo, hi) {
  const a = Math.max(0, lo);
  const b = Math.max(0, hi);
  if (Math.abs(b - a) < 0.5) return a.toFixed(0);
  return `${a.toFixed(0)}–${b.toFixed(0)}`;
}

export function savedChipLabel(savedRange) {
  if (!savedRange) return null;
  const lo = savedRange[0];
  const hi = savedRange[1];
  if (hi <= 0.05) return null;
  if (Math.abs(hi - lo) < 0.5) return `−${Math.round(hi)}h`;
  return `−${Math.round(lo)}–${Math.round(hi)}h`;
}

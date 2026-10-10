<script setup>
import { computed, ref } from 'vue';

const props = defineProps({
  assumptions: { type: Array, default: () => [] },
  hoursPerSitting: { type: Number, default: 5 },
});

const open = ref(false);

function confidenceLabel(confidence, sampleCount) {
  if (confidence === 'measured') {
    const n = sampleCount || 0;
    return n > 0 ? `Based on logged hours from ${n} check-ins` : 'Based on logged hours';
  }
  if (confidence === 'baseline') return 'Baseline — the free starting point';
  if (confidence === 'estimated') return 'Rough estimate from published sources';
  return 'Placeholder — a cautious starting guess, not a proven fact';
}

function speedupLabel(speedup) {
  const lo = Number(speedup?.[0] ?? 1);
  const hi = Number(speedup?.[1] ?? 1);
  if (Math.abs(lo - 1) < 1e-9 && Math.abs(hi - 1) < 1e-9) {
    return 'No estimated speed change for this kind of work';
  }
  const pctLo = Math.round((lo - 1) * 100);
  const pctHi = Math.round((hi - 1) * 100);
  if (pctLo === pctHi) {
    return `May finish this kind of work about ${pctLo}% faster (estimate)`;
  }
  return `May finish this kind of work about ${pctLo}–${pctHi}% faster (estimate)`;
}

function formatWeeks(weeks) {
  if (!weeks.length) return '';
  const sorted = [...weeks].sort((a, b) => a - b);
  const runs = [];
  let start = sorted[0];
  let prev = sorted[0];
  for (let i = 1; i < sorted.length; i += 1) {
    if (sorted[i] === prev + 1) {
      prev = sorted[i];
      continue;
    }
    runs.push(start === prev ? `${start}` : `${start}–${prev}`);
    start = sorted[i];
    prev = sorted[i];
  }
  runs.push(start === prev ? `${start}` : `${start}–${prev}`);
  if (runs.length === 1) {
    return runs[0].includes('–') ? `Weeks ${runs[0]}` : `Week ${runs[0]}`;
  }
  return `Weeks ${runs.join(', ')}`;
}

function taskLabel(taskType) {
  const map = {
    learning: 'learning',
    coding: 'coding',
    writing: 'writing',
    design: 'design',
    data: 'data work',
    research: 'research',
    outreach: 'outreach',
  };
  return map[taskType] || taskType || 'this work';
}

const grouped = computed(() => {
  const map = new Map();
  for (const row of props.assumptions || []) {
    const key = `${row.optionId}|${row.taskType}|${row.speedup?.[0]}|${row.speedup?.[1]}|${row.confidence}`;
    if (!map.has(key)) {
      map.set(key, {
        optionName: row.optionName,
        taskType: row.taskType,
        speedup: row.speedup,
        confidence: row.confidence,
        sampleCount: row.sampleCount,
        weeks: [],
      });
    }
    map.get(key).weeks.push(row.weekNo);
  }
  return [...map.values()];
});
</script>

<template>
  <div class="plan-assumptions">
    <button type="button" class="btn btn--ghost" @click="open = !open">
      {{ open ? 'Hide calculation details' : 'How is this calculated?' }}
    </button>
    <div v-if="open" class="plan-assumptions__body">
      <div class="plan-assumptions__intro">
        <p>
          We start from the hours in your plan (focused sittings of about
          {{ hoursPerSitting }} hours each). Then we apply a low-to-high speed range
          for the tool you picked — never a single exact number.
        </p>
        <p>
          These are estimates. Placeholder values are cautious guesses until enough
          people log real hours after each week.
        </p>
      </div>

      <ul v-if="grouped.length" class="plan-assumptions__list">
        <li
          v-for="(row, idx) in grouped"
          :key="`${row.optionName}-${row.taskType}-${idx}`"
          class="plan-assumptions__item"
        >
          <strong>{{ row.optionName }}</strong>
          <p class="plan-assumptions__meta">
            {{ formatWeeks(row.weeks) }} · mostly {{ taskLabel(row.taskType) }}
          </p>
          <p class="plan-assumptions__meta">{{ speedupLabel(row.speedup) }}</p>
          <p class="plan-assumptions__meta">
            {{ confidenceLabel(row.confidence, row.sampleCount) }}
          </p>
        </li>
      </ul>
      <p v-else class="plan-assumptions__meta">No tool assumptions for this variant yet.</p>
    </div>
  </div>
</template>

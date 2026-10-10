<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = defineProps({
  budget: { type: Number, default: 0 },
  spent: { type: Number, default: 0 },
  weeks: { type: Number, default: 0 },
  title: { type: String, default: '' },
});

const displaySpent = ref(0);
const displayBudget = ref(0);
const ringProgress = ref(0);
const litWeeks = ref(0);
const started = ref(false);

const CIRCUMFERENCE = 2 * Math.PI * 54;
const prefersReduced = typeof window !== 'undefined'
  && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const ratio = computed(() => {
  const budget = Math.max(0, Number(props.budget) || 0);
  const spent = Math.max(0, Number(props.spent) || 0);
  if (budget <= 0) return spent > 0 ? 1 : 0;
  return Math.min(1, spent / budget);
});

const weekCount = computed(() => Math.max(0, Math.min(16, Math.round(Number(props.weeks) || 0))));

const ringOffset = computed(() => CIRCUMFERENCE * (1 - ringProgress.value));

const leftover = computed(() => Math.max(0, displayBudget.value - displaySpent.value));

let cancelFns = [];

function easeOutCubic(t) {
  return 1 - (1 - t) ** 3;
}

function runTween(duration, onUpdate, onDone) {
  if (prefersReduced || duration <= 0) {
    onUpdate(1);
    onDone?.();
    return () => {};
  }
  const start = performance.now();
  let frameId = 0;
  const tick = (now) => {
    const t = Math.min(1, (now - start) / duration);
    onUpdate(easeOutCubic(t));
    if (t < 1) {
      frameId = requestAnimationFrame(tick);
    } else {
      onDone?.();
    }
  };
  frameId = requestAnimationFrame(tick);
  return () => cancelAnimationFrame(frameId);
}

function stopAll() {
  cancelFns.forEach((fn) => fn());
  cancelFns = [];
}

function play() {
  stopAll();
  started.value = true;
  displaySpent.value = 0;
  displayBudget.value = 0;
  ringProgress.value = 0;
  litWeeks.value = 0;

  const targetSpent = Math.max(0, Number(props.spent) || 0);
  const targetBudget = Math.max(0, Number(props.budget) || 0);
  const weeks = weekCount.value;
  const targetRatio = ratio.value;

  if (prefersReduced) {
    displaySpent.value = targetSpent;
    displayBudget.value = targetBudget;
    ringProgress.value = targetRatio;
    litWeeks.value = weeks;
    return;
  }

  cancelFns.push(
    runTween(900, (t) => {
      displaySpent.value = Math.round(targetSpent * t);
      displayBudget.value = Math.round(targetBudget * t);
      ringProgress.value = targetRatio * t;
    }),
  );

  const weekDuration = Math.max(280, weeks * 120);
  cancelFns.push(
    runTween(weekDuration, (t) => {
      litWeeks.value = Math.round(weeks * t);
    }),
  );
}

onMounted(play);
watch(
  () => [props.budget, props.spent, props.weeks].join('|'),
  () => play(),
);
onBeforeUnmount(stopAll);

function rupee(n) {
  return `₹${Math.round(Number(n) || 0).toLocaleString('en-IN')}`;
}
</script>

<template>
  <section class="plan-finish-anim" aria-label="Plan totals">
    <p class="plan-finish-anim__eyebrow">Plan to finish</p>
    <p v-if="title" class="plan-finish-anim__title">{{ title }}</p>

    <div class="plan-finish-anim__stage">
      <div class="plan-finish-anim__ring-wrap" :class="{ 'is-on': started }">
        <svg class="plan-finish-anim__ring" viewBox="0 0 120 120" aria-hidden="true">
          <circle class="plan-finish-anim__track" cx="60" cy="60" r="54" />
          <circle
            class="plan-finish-anim__fill"
            cx="60"
            cy="60"
            r="54"
            :stroke-dasharray="CIRCUMFERENCE"
            :stroke-dashoffset="ringOffset"
          />
        </svg>
        <div class="plan-finish-anim__ring-copy">
          <span class="plan-finish-anim__spent">{{ rupee(displaySpent) }}</span>
          <span class="plan-finish-anim__of">of {{ rupee(displayBudget) }}</span>
        </div>
      </div>

      <div class="plan-finish-anim__facts">
        <p class="plan-finish-anim__fact">
          <span class="plan-finish-anim__fact-label">This plan uses</span>
          <span class="plan-finish-anim__fact-value">{{ rupee(displaySpent) }}</span>
        </p>
        <p class="plan-finish-anim__fact">
          <span class="plan-finish-anim__fact-label">Left this month</span>
          <span class="plan-finish-anim__fact-value">{{ rupee(leftover) }}</span>
        </p>
        <p class="plan-finish-anim__fact">
          <span class="plan-finish-anim__fact-label">Weeks to finish</span>
          <span class="plan-finish-anim__fact-value">{{ litWeeks }}</span>
        </p>
      </div>
    </div>

    <ol v-if="weekCount" class="plan-finish-anim__weeks" aria-label="Week path">
      <li
        v-for="n in weekCount"
        :key="n"
        class="plan-finish-anim__week"
        :class="{ 'is-lit': n <= litWeeks }"
        :style="{ transitionDelay: `${Math.min(n, 12) * 40}ms` }"
      >
        {{ n }}
      </li>
    </ol>
    <p class="plan-finish-anim__caption">
      Numbers come from your skill budget and week count — not from the wording below.
    </p>
  </section>
</template>

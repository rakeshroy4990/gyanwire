<script setup>
import { computed } from 'vue';
import ToolChip from './ToolChip.vue';
import GainBadge from './GainBadge.vue';

const props = defineProps({
  step: { type: Object, required: true },
  path: { type: String, default: 'open_source' },
});

const emit = defineEmits(['toggle', 'open']);

const durationMin = computed(() => {
  if (props.path === 'paid' && props.step.paidDurationMin != null) {
    return Number(props.step.paidDurationMin) || 0;
  }
  return Number(props.step.durationMin) || 0;
});

const activeTool = computed(() => {
  if (props.path === 'paid' && props.step.paidTool) return props.step.paidTool;
  return props.step.tool;
});

const gain = computed(() => props.step.gain || null);

function formatStart(min) {
  const n = Number(min) || 0;
  const h = Math.floor(n / 60);
  const m = n % 60;
  return `${h}:${String(m).padStart(2, '0')}`;
}

function onRowActivate() {
  emit('open', props.step.id);
}

function onCheck(e) {
  e.stopPropagation();
  emit('toggle', props.step.id, e.target.checked);
}

function onKey(e) {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault();
    onRowActivate();
  }
}
</script>

<template>
  <article
    class="step-card step-card--clickable"
    :class="{ 'is-done': step.done }"
    role="button"
    tabindex="0"
    @click="onRowActivate"
    @keydown="onKey"
  >
    <header class="step-card__head">
      <label class="step-card__check" @click.stop>
        <input
          type="checkbox"
          :checked="!!step.done"
          @change="onCheck"
        />
        <span>{{ step.order }}. {{ step.title }}</span>
      </label>
      <div class="step-card__meta">
        <GainBadge
          v-if="gain && path === 'paid'"
          :pct="gain.pct"
          :basis="gain.basis || gain.label || 'estimate'"
        />
        <p class="step-card__time">{{ formatStart(step.startMin) }} · {{ durationMin }} min</p>
      </div>
    </header>

    <p class="step-card__label">Do this</p>
    <p>{{ step.action }}</p>

    <p class="step-card__label">Input → Output</p>
    <p>{{ step.input }} → {{ step.output }}</p>

    <p class="step-card__label">Done when</p>
    <p>{{ step.doneWhen }}</p>

    <p class="step-card__label">Tool</p>
    <div class="step-card__tools">
      <ToolChip :tool="activeTool" />
      <ToolChip v-if="path !== 'paid' && step.altTool" :tool="step.altTool" alt />
      <ToolChip v-if="path === 'mix' && step.paidTool" :tool="step.paidTool" alt />
    </div>

    <p v-if="step.ifStuck" class="step-card__stuck"><strong>If stuck:</strong> {{ step.ifStuck }}</p>
  </article>
</template>

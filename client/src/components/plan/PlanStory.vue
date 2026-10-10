<script setup>
import { computed, ref, watch } from 'vue';
import { catalogExplain } from '../../composables/catalogExplain.js';
import {
  phaseLabel,
  storyTools,
  weekDetail,
  weekRailLabel,
} from '../../composables/usePlanStory.js';

const props = defineProps({
  plan: { type: Object, default: null },
});

const activeWeek = ref(0);

const weeks = computed(() => [...(props.plan?.weeks || [])]
  .filter((week) => week && week.weekNo != null)
  .sort((a, b) => a.weekNo - b.weekNo));

const tools = computed(() => storyTools(props.plan));
const newsUrl = computed(() => String(props.plan?.newsUrl || '').trim());
const newsTitle = computed(() => String(props.plan?.newsTitle || '').trim());

const lineById = computed(() => {
  const map = new Map();
  for (const line of props.plan?.lines || []) {
    if (line?.id) map.set(line.id, line);
  }
  return map;
});

const nodes = computed(() => weeks.value.map((week, i) => {
  const prev = i > 0 ? weeks.value[i - 1] : null;
  const detail = weekDetail(week, weeks.value, lineById.value);
  const explain = catalogExplain(week.toolId, {
    url: detail.toolUrl,
    blurb: detail.toolBlurb,
  }, {
    newsUrl: newsUrl.value,
    newsTitle: newsTitle.value,
  });
  return {
    week,
    rail: weekRailLabel(week, prev) || phaseLabel(week.phase) || '',
    detail: {
      ...detail,
      toolUrl: explain.url,
      toolBlurb: explain.blurb,
      opensNews: explain.opensNews,
    },
    last: i === weeks.value.length - 1,
  };
}));

function selectWeek(weekNo) {
  activeWeek.value = weekNo;
}

function toolOn(tool) {
  const week = weeks.value.find((row) => row.weekNo === activeWeek.value);
  if (!week || !tool) return false;
  if (week.toolId && tool.id === week.toolId) return true;
  return Boolean(week.toolName) && tool.name === week.toolName;
}

watch(weeks, (list) => {
  activeWeek.value = list[0]?.weekNo || 0;
}, { immediate: true });
</script>

<template>
  <section v-if="weeks.length" class="plan-story" aria-label="How this plan finishes">
    <div class="plan-story__head">
      <h2>How this plan finishes</h2>
    </div>

    <ol class="plan-story__tree" aria-label="Week path">
      <li
        v-for="node in nodes"
        :key="node.week.weekNo"
        class="plan-story__node is-lit is-open"
        :class="{
          'is-active': node.week.weekNo === activeWeek,
          'is-last': node.last,
        }"
      >
        <button
          type="button"
          class="plan-story__branch"
          aria-expanded="true"
          :aria-current="node.week.weekNo === activeWeek ? 'step' : undefined"
          :aria-label="`Week ${node.week.weekNo}: ${node.week.goal || 'Details'}`"
          @click="selectWeek(node.week.weekNo)"
        >
          <span class="plan-story__dot" aria-hidden="true">{{ node.week.weekNo }}</span>
          <span class="plan-story__branch-copy">
            <span class="plan-story__branch-week">Week {{ node.week.weekNo }}</span>
            <span v-if="node.rail" class="plan-story__week-label">{{ node.rail }}</span>
          </span>
        </button>

        <article
          :id="`plan-week-card-${node.week.weekNo}`"
          class="plan-story__card"
          :class="{ 'is-active': node.week.weekNo === activeWeek }"
        >
          <p class="plan-story__kicker">{{ node.detail.kicker }}</p>
          <h3>{{ node.detail.title }}</h3>
          <ul v-if="node.detail.tasks.length" class="plan-story__tasks">
            <li v-for="(task, ti) in node.detail.tasks" :key="ti">{{ task }}</li>
          </ul>
          <p v-else-if="node.detail.body" class="plan-story__body">{{ node.detail.body }}</p>
          <p v-if="node.detail.toolName" class="plan-story__tool">
            <a
              v-if="node.detail.toolUrl"
              :href="node.detail.toolUrl"
              target="_blank"
              rel="noopener noreferrer"
              :title="node.detail.opensNews ? (newsTitle || 'Open source news') : node.detail.toolName"
            >{{ node.detail.toolName }}</a>
            <template v-else>{{ node.detail.toolName }}</template>
          </p>
          <p v-if="node.detail.toolBlurb" class="plan-story__blurb">{{ node.detail.toolBlurb }}</p>
          <p v-if="node.detail.metric" class="plan-story__metric">
            Done when: {{ node.detail.metric }}.
          </p>
        </article>
      </li>
    </ol>

    <ul v-if="tools.length" class="plan-story__tools" aria-label="Tools in this plan">
      <li
        v-for="tool in tools"
        :key="tool.id"
        class="plan-story__chip"
        :class="{ 'is-on': toolOn(tool) }"
      >
        {{ tool.name }}
      </li>
    </ul>
  </section>
</template>

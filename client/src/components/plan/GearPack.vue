<script setup>
import { computed } from 'vue';
import { catalogExplain } from '../../composables/catalogExplain.js';
import { formatInr } from '../../composables/usePlanSimulator.js';

const props = defineProps({
  lines: { type: Array, default: () => [] },
  newsUrl: { type: String, default: '' },
  newsTitle: { type: String, default: '' },
});

const buckets = [
  { id: 'learning', label: 'Learn with' },
  { id: 'tools', label: 'Make it with' },
  { id: 'proof', label: 'Prove it with' },
  { id: 'community', label: 'Talk it through with' },
];

function billingLabel(value) {
  if (value === 'free') return 'free';
  if (value === 'once') return 'one-time';
  if (value === 'monthly') return 'monthly';
  return value || '';
}

const grouped = computed(() => buckets.map((b) => ({
  ...b,
  items: (props.lines || []).map((line) => ({
    line,
    info: catalogExplain(line?.id, line, {
      newsUrl: props.newsUrl,
      newsTitle: props.newsTitle,
    }),
  })).filter(({ line }) => line.bucket === b.id),
})).filter((b) => b.items.length));
</script>

<template>
  <div class="plan-gear">
    <div v-for="bucket in grouped" :key="bucket.id" class="plan-gear__bucket">
      <h3>{{ bucket.label }}</h3>
      <ul class="plan-gear__chips">
        <li
          v-for="{ line, info } in bucket.items"
          :key="line.id"
          class="plan-gear__chip"
          :class="{ 'has-explain': info.hasExplain }"
        >
          <strong>
            <a
              v-if="info.url"
              :href="info.url"
              target="_blank"
              rel="noopener noreferrer"
              :title="info.opensNews ? (newsTitle || 'Open source news') : line.name"
            >{{ line.name }}</a>
            <template v-else>{{ line.name }}</template>
          </strong>
          <span>{{ formatInr(line.costInr) }} · {{ billingLabel(line.billing) }}</span>
          <p v-if="info.blurb" class="plan-gear__blurb">{{ info.blurb }}</p>
        </li>
      </ul>
    </div>
  </div>
</template>

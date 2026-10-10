<script setup>
import { ref, watch } from 'vue';
import { useProduct } from '../../composables/useProduct.js';

const props = defineProps({
  industry: { type: String, default: '' },
  results: { type: Array, default: () => [] },
});
const mode = ref(null);
const { industryMode } = useProduct();

function linkable(url) {
  return typeof url === 'string' && /^https?:\/\//i.test(url);
}

watch(() => [props.industry, props.results.length], async () => {
  if (!props.industry || props.industry === 'All' || !props.results.length) {
    mode.value = null;
    return;
  }
  try {
    mode.value = await industryMode({
      industry: props.industry,
      results: props.results.slice(0, 6).map((item) => ({
        title: item.title,
        url: item.url,
        description: item.description || item.why || '',
      })),
    });
  } catch {
    mode.value = null;
  }
}, { immediate: true });
</script>

<template>
  <section v-if="mode?.items?.length" class="brief">
    <h3>{{ mode.mode }}</h3>
    <p v-if="mode.disclaimer" class="disclaimer">{{ mode.disclaimer }}</p>
    <ul class="brief__list">
      <li v-for="item in mode.items" :key="item.url || item.title">
        <a
          v-if="linkable(item.url)"
          class="brief__item"
          :href="item.url"
          target="_blank"
          rel="noopener noreferrer"
        >{{ item.title }}</a>
        <span v-else>{{ item.title }}</span>
        <span class="brief__tag"> · {{ item.tag }}</span>
      </li>
    </ul>
  </section>
</template>

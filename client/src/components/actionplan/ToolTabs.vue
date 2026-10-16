<script setup>
import { computed, ref, watch } from 'vue';
import ToolChip from './ToolChip.vue';

const props = defineProps({
  openSourceTools: { type: Array, default: () => [] },
  paidTools: { type: Array, default: () => [] },
  freeTools: { type: Array, default: () => [] },
  initialTab: { type: String, default: 'open_source' },
});

const emit = defineEmits(['update:tab']);

const tabs = [
  { id: 'open_source', label: 'Open Source' },
  { id: 'paid', label: 'Paid' },
  { id: 'free', label: 'Free (not open source)' },
];

const active = ref(normalizeTab(props.initialTab));

watch(
  () => props.initialTab,
  (tab) => {
    active.value = normalizeTab(tab);
  },
);

watch(active, (tab) => emit('update:tab', tab));

const tools = computed(() => {
  if (active.value === 'paid') return props.paidTools || [];
  if (active.value === 'free') return props.freeTools || [];
  return props.openSourceTools || [];
});

function normalizeTab(tab) {
  const t = String(tab || 'open_source').toLowerCase();
  if (t === 'paid') return 'paid';
  if (t === 'free' || t === 'free_not_os' || t === 'freemium') return 'free';
  return 'open_source';
}

function licenceLabel(tool) {
  const license = String(tool?.license || '').trim();
  if (license) return license;
  const tier = String(tool?.tier || '').toLowerCase();
  if (tier === 'open_source') return 'see project';
  if (tier === 'freemium') return 'free tier · proprietary';
  if (tier === 'free') return 'free · proprietary';
  return 'proprietary';
}

function tierSafe(tool) {
  const tier = String(tool?.tier || '').toLowerCase();
  if (tier === 'freemium' || tier === 'free') {
    return { ...tool, tier: tier === 'freemium' ? 'freemium' : 'free', openSource: false };
  }
  return tool;
}
</script>

<template>
  <div class="tool-tabs">
    <div class="tool-tabs__list" role="tablist" aria-label="Tools by path">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        type="button"
        role="tab"
        class="tool-tabs__tab"
        :aria-selected="active === tab.id"
        :class="{ 'is-active': active === tab.id }"
        @click="active = tab.id"
      >
        {{ tab.label }}
      </button>
    </div>
    <div class="tool-tabs__panel" role="tabpanel">
      <ul v-if="tools.length" class="tool-tabs__items">
        <li v-for="tool in tools" :key="tool.id || tool.name">
          <ToolChip :tool="tierSafe(tool)" />
          <span class="tool-tabs__licence">{{ licenceLabel(tool) }}</span>
        </li>
      </ul>
      <p v-else class="tool-tabs__empty">No tools listed for this tab.</p>
    </div>
  </div>
</template>

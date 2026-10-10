import { computed, ref, watch } from 'vue';
import {
  loadVariants,
  meterVariantCount,
  saveVariants,
} from '../services/planVariants.service.js';

function uid() {
  return `v_${Math.random().toString(36).slice(2, 10)}`;
}

function weekOptionsFromPlan(plan) {
  const map = {};
  for (const week of plan?.weeks || []) {
    map[week.weekNo] = week.toolId;
  }
  return map;
}

export function usePlanVariants(ideaIdRef, planRef) {
  const variants = ref([]);
  const activeId = ref(null);
  const ready = ref(false);
  const limitError = ref('');
  const upgradeUrl = ref('/pricing');

  const active = computed(() => variants.value.find((v) => v.id === activeId.value) || variants.value[0] || null);

  async function persist() {
    const ideaId = typeof ideaIdRef === 'function' ? ideaIdRef() : ideaIdRef.value;
    if (!ideaId) return;
    await saveVariants(ideaId, variants.value, activeId.value);
  }

  async function ensureBaseline() {
    const ideaId = typeof ideaIdRef === 'function' ? ideaIdRef() : ideaIdRef.value;
    const plan = typeof planRef === 'function' ? planRef() : planRef.value;
    if (!ideaId || !plan?.weeks?.length) return;

    const stored = await loadVariants(ideaId);
    if (stored.variants.length) {
      variants.value = stored.variants;
      activeId.value = stored.activeId || stored.variants[0].id;
    } else {
      const base = {
        id: uid(),
        name: 'A',
        createdAt: new Date().toISOString(),
        ideaId,
        weekOptions: weekOptionsFromPlan(plan),
        hourlyValueInr: null,
      };
      variants.value = [base];
      activeId.value = base.id;
      await persist();
    }
    ready.value = true;
  }

  async function select(id) {
    activeId.value = id;
    await persist();
  }

  async function duplicate(name) {
    limitError.value = '';
    const nextCount = variants.value.length + 1;
    try {
      await meterVariantCount(nextCount);
    } catch (err) {
      limitError.value = err?.message || 'Upgrade to compare more variants.';
      upgradeUrl.value = err?.data?.upgradeUrl || '/pricing';
      throw err;
    }
    const src = active.value;
    if (!src) return null;
    const labels = ['A', 'B', 'C', 'D'];
    const copy = {
      id: uid(),
      name: name || labels[variants.value.length] || `V${variants.value.length + 1}`,
      createdAt: new Date().toISOString(),
      ideaId: src.ideaId,
      weekOptions: { ...src.weekOptions },
      hourlyValueInr: src.hourlyValueInr,
    };
    variants.value = [...variants.value, copy];
    activeId.value = copy.id;
    await persist();
    return copy;
  }

  async function rename(id, name) {
    variants.value = variants.value.map((v) => (v.id === id ? { ...v, name } : v));
    await persist();
  }

  async function remove(id) {
    if (variants.value.length <= 1) return;
    variants.value = variants.value.filter((v) => v.id !== id);
    if (activeId.value === id) {
      activeId.value = variants.value[0].id;
    }
    await persist();
  }

  async function setWeekOption(weekNo, optionId) {
    const cur = active.value;
    if (!cur) return;
    const next = {
      ...cur,
      weekOptions: { ...cur.weekOptions, [weekNo]: optionId },
    };
    variants.value = variants.value.map((v) => (v.id === cur.id ? next : v));
    await persist();
  }

  async function setHourlyValue(value) {
    const cur = active.value;
    if (!cur) return;
    const hourlyValueInr = value === '' || value == null ? null : Number(value);
    const next = { ...cur, hourlyValueInr: Number.isFinite(hourlyValueInr) ? hourlyValueInr : null };
    variants.value = variants.value.map((v) => (v.id === cur.id ? next : v));
    await persist();
  }

  watch(
    () => {
      const ideaId = typeof ideaIdRef === 'function' ? ideaIdRef() : ideaIdRef.value;
      const plan = typeof planRef === 'function' ? planRef() : planRef.value;
      return `${ideaId || ''}:${plan?.weeks?.length || 0}`;
    },
    () => {
      ready.value = false;
      ensureBaseline();
    },
    { immediate: true },
  );

  return {
    variants,
    activeId,
    active,
    ready,
    limitError,
    upgradeUrl,
    select,
    duplicate,
    rename,
    remove,
    setWeekOption,
    setHourlyValue,
    ensureBaseline,
  };
}

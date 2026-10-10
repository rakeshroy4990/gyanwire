<script setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useProduct } from '../composables/useProduct.js';
import { submitPlanActual } from '../services/planActuals.service.js';
import PageSkeleton from '../components/ui/PageSkeleton.vue';

const route = useRoute();
const weeks = ref([]);
const loading = ref(true);
const hoursDraft = ref({});
const saveMsg = ref('');
const { weeklyPlan, error } = useProduct();

onMounted(async () => {
  loading.value = true;
  try {
    const data = await weeklyPlan(route.query.ideaId || null);
    weeks.value = data.weeks || [];
  } catch {
    weeks.value = [];
  } finally {
    loading.value = false;
  }
});

async function saveHours(week) {
  saveMsg.value = '';
  const hours = Number(hoursDraft.value[week.weekNo]);
  if (!Number.isFinite(hours) || hours < 0) {
    saveMsg.value = 'Enter hours as a number.';
    return;
  }
  try {
    await submitPlanActual({
      ideaId: route.query.ideaId,
      weekNo: week.weekNo,
      optionId: week.toolId || week.toolName || 'unknown',
      taskType: week.taskType || 'learning',
      hoursActual: hours,
      baselineHours: week.baseHours || null,
    });
    saveMsg.value = `Saved actual hours for week ${week.weekNo}.`;
  } catch (err) {
    saveMsg.value = err?.message || 'Could not save hours.';
  }
}
</script>

<template>
  <main class="account-page">
    <header class="account-page__hero">
      <p class="account-page__kicker">Weekly check-in</p>
      <h1>12-week plan</h1>
    </header>
    <PageSkeleton v-if="loading" variant="cards" :rows="4" label="Loading weekly plan" />
    <template v-else>
      <p v-if="error" class="form-error">{{ error }}</p>
      <p v-if="saveMsg" class="account-notice">{{ saveMsg }}</p>
      <article v-for="week in weeks" :key="week.weekNo" class="account-card">
        <h2>Week {{ week.weekNo }} · {{ week.outcome }}</h2>
        <p v-if="week.toolName">Tool · {{ week.toolName }} · ₹{{ week.costInr }} · {{ week.billing }}</p>
        <ul>
          <li v-for="task in week.tasks" :key="task"><label><input type="checkbox"> {{ task }}</label></li>
        </ul>
        <p>Done when: {{ week.metric }}.</p>
        <label>
          How many hours did this actually take?
          <input
            v-model="hoursDraft[week.weekNo]"
            type="number"
            min="0"
            step="0.5"
            placeholder="e.g. 4"
          >
        </label>
        <button type="button" class="btn btn--ghost" @click="saveHours(week)">Save hours</button>
      </article>
      <RouterLink v-if="route.query.ideaId" :to="`/outline?ideaId=${route.query.ideaId}`">Business outline</RouterLink>
    </template>
  </main>
</template>

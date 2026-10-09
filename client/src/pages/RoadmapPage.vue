<script setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useProduct } from '../composables/useProduct.js';
import PageSkeleton from '../components/ui/PageSkeleton.vue';

const route = useRoute();
const weeks = ref([]);
const loading = ref(true);
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
      <article v-for="week in weeks" :key="week.weekNo" class="account-card">
        <h2>Week {{ week.weekNo }} · {{ week.outcome }}</h2>
        <p v-if="week.toolName">Tool · {{ week.toolName }} · ₹{{ week.costInr }} · {{ week.billing }}</p>
        <ul>
          <li v-for="task in week.tasks" :key="task"><label><input type="checkbox"> {{ task }}</label></li>
        </ul>
        <p>Done when: {{ week.metric }}.</p>
      </article>
      <RouterLink v-if="route.query.ideaId" :to="`/outline?ideaId=${route.query.ideaId}`">Business outline</RouterLink>
    </template>
  </main>
</template>

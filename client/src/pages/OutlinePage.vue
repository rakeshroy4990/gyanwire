<script setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useProduct } from '../composables/useProduct.js';
import { outlineDownloadUrl } from '../services/product.service.js';
import PageSkeleton from '../components/ui/PageSkeleton.vue';

const route = useRoute();
const outline = ref(null);
const loading = ref(true);
const { outline: loadOutline, error } = useProduct();

onMounted(async () => {
  loading.value = true;
  try {
    outline.value = await loadOutline(route.query.ideaId || null);
  } catch {
    outline.value = null;
  } finally {
    loading.value = false;
  }
});

function download() {
  if (!route.query.ideaId) return;
  window.location.href = outlineDownloadUrl(route.query.ideaId);
}
</script>

<template>
  <main class="page legal-page">
    <h1>Business outline</h1>
    <PageSkeleton v-if="loading" variant="facts" :rows="8" label="Loading outline" />
    <template v-else>
      <p v-if="error" class="form-error">{{ error }}</p>
      <dl v-if="outline">
        <template v-for="(value, key) in outline" :key="key">
          <dt>{{ key }}</dt>
          <dd>{{ value }}</dd>
        </template>
      </dl>
      <button type="button" class="btn btn--primary" @click="download">Download Markdown</button>
    </template>
  </main>
</template>

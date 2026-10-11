<script setup>
defineProps({
  gate: { type: Object, required: true },
});
</script>

<template>
  <section class="no-opportunity">
    <h2>No strong opportunity in this news</h2>
    <p>{{ gate.reason }}</p>
    <div v-if="gate.suggestions?.length">
      <p class="idea-card__label">News that usually works</p>
      <ul>
        <li v-for="suggestion in gate.suggestions" :key="suggestion">{{ suggestion }}</li>
      </ul>
    </div>
  </section>
</template>

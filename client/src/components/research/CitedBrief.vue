<script setup>
defineProps({
  brief: { type: Object, required: true },
});
const emit = defineEmits(['claim']);

function cite(id) {
  document.getElementById(`finding-r-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}
</script>

<template>
  <section class="brief">
    <h3>Cited brief</h3>
    <p>{{ brief.summary }}</p>
    <p v-if="brief.limited" class="disclaimer">Daily brief limit reached.</p>
    <ul v-if="brief.agreements?.length">
      <li v-for="(row, index) in brief.agreements" :key="`a-${index}`">
        {{ row.text }}
        <button
          v-for="id in row.citations || []"
          :key="id"
          type="button"
          class="cite"
          @click="cite(id)"
        >[{{ id }}]</button>
        <button type="button" class="btn btn--ghost" @click="emit('claim', row.text)">Check</button>
      </li>
    </ul>
    <div v-if="brief.followUps?.length" class="chips">
      <span v-for="question in brief.followUps" :key="question" class="chip">{{ question }}</span>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const pages = {
  terms: {
    title: 'Terms',
    body: 'REVIEW WITH LAWYER. Gyanwire is a research aid. It does not give investment advice, medical advice, or predictions.',
  },
  privacy: {
    title: 'Privacy',
    body: 'REVIEW WITH LAWYER. We store account data and the profile bands you consent to, for research personalization. You can delete profile data from the profile page. Grievance contact is on the contact page. This notice is a draft for India\'s DPDP Act and is not legal advice.',
  },
  refund: {
    title: 'Refunds',
    body: 'REVIEW WITH LAWYER. Subscriptions cancel at period end. Refund requests go through the contact page.',
  },
  contact: {
    title: 'Contact',
    body: 'REVIEW WITH LAWYER. Grievance contact: privacy@gyanwire.local. Replace this address before launch.',
  },
};
const page = computed(() => pages[route.meta.page] || pages.terms);
</script>

<template>
  <main class="page legal-page">
    <p class="disclaimer">REVIEW WITH LAWYER</p>
    <h1>{{ page.title }}</h1>
    <p>{{ page.body }}</p>
  </main>
</template>

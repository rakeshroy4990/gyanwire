<script setup>
import { onMounted, onUnmounted, watch } from 'vue';
import AppHeader from './components/layout/AppHeader.vue';
import LoginModal from './components/auth/LoginModal.vue';
import RegisterModal from './components/auth/RegisterModal.vue';
import { useAuth } from './composables/useAuth.js';

const { isAnyPopupOpen, closePopup } = useAuth();

watch(isAnyPopupOpen, (open) => {
  document.body.classList.toggle('auth-modal-open', open);
}, { immediate: true });

function onKeydown(event) {
  if (event.key === 'Escape' && isAnyPopupOpen.value) {
    closePopup();
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown));
onUnmounted(() => window.removeEventListener('keydown', onKeydown));
</script>

<template>
  <AppHeader />
  <RouterView />
  <LoginModal />
  <RegisterModal />
</template>

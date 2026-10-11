<script setup>
import { computed, onMounted, onUnmounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import AppHeader from './components/layout/AppHeader.vue';
import ThemeSwitch from './components/layout/ThemeSwitch.vue';
import LoginModal from './components/auth/LoginModal.vue';
import RegisterModal from './components/auth/RegisterModal.vue';
import { useAuth } from './composables/useAuth.js';

const route = useRoute();
const showChrome = computed(() => route.meta.bare !== true);
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
  <AppHeader v-if="showChrome" />
  <ThemeSwitch v-else class="theme-switch--landing" />
  <RouterView v-slot="{ Component }">
    <KeepAlive include="ResearchPage">
      <component :is="Component" />
    </KeepAlive>
  </RouterView>
  <footer v-if="showChrome" class="site-footer">
    <RouterLink to="/terms">Terms</RouterLink>
    <RouterLink to="/privacy">Privacy</RouterLink>
    <RouterLink to="/refund">Refunds</RouterLink>
    <RouterLink to="/contact">Contact</RouterLink>
  </footer>
  <LoginModal />
  <RegisterModal />
</template>

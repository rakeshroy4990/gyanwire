<script setup>
import { ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { useAuth } from '../../composables/useAuth.js';
import { useBilling } from '../../composables/useBilling.js';
import { useUiStore } from '../../stores/ui.store.js';

const {
  isAuthenticated,
  shortName,
  email,
  role,
  profilePic,
  openLoginPopup,
  logoutUser,
} = useAuth();

const router = useRouter();
const { cancel, loadStatus, status, isBusy, error } = useBilling();
const menuOpen = ref(false);
const ui = useUiStore();

async function goHome() {
  menuOpen.value = false;
  ui.requestHome();
  if (router.currentRoute.value.path !== '/') {
    await router.push({ name: 'research' });
  }
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function toggleMenu() {
  menuOpen.value = !menuOpen.value;
  if (menuOpen.value) {
    try {
      await loadStatus();
    } catch {
      // ignore
    }
  }
}

async function onCancel() {
  await cancel();
  menuOpen.value = false;
}
</script>

<template>
  <header class="site-header">
    <div class="site-header__inner">
      <div class="site-header__leading">
        <RouterLink
          class="brand"
          to="/"
          aria-label="Gyanwire home"
          @click.prevent="goHome"
        >
          <span class="brand__mark" aria-hidden="true" />
          <span class="brand__text">
            <span class="brand__name">Gyanwire</span>
            <span class="brand__tag">Live research</span>
          </span>
        </RouterLink>

        <span class="site-header__rule" aria-hidden="true" />

        <nav class="site-header__nav" aria-label="Primary">
          <RouterLink class="site-header__nav-link" to="/pricing">Pricing</RouterLink>
        </nav>
      </div>

      <div class="site-header__actions">
        <button
          v-if="!isAuthenticated"
          type="button"
          class="btn btn--ghost auth-chrome__signin"
          @click="openLoginPopup()"
        >
          Sign in
        </button>

        <div v-else class="auth-chrome__user">
          <img
            v-if="profilePic"
            class="auth-chrome__avatar"
            :src="profilePic"
            alt=""
            referrerpolicy="no-referrer"
          >
          <div class="auth-chrome__meta">
            <span class="auth-chrome__name" :title="email">{{ shortName }}</span>
            <span v-if="role" class="auth-chrome__role">{{ role }}</span>
          </div>

          <div class="account-menu">
            <button type="button" class="btn btn--ghost" @click="toggleMenu">
              Account
            </button>
            <div v-if="menuOpen" class="account-menu__panel">
              <p v-if="status" class="account-menu__line">
                Plan: {{ status.planName }}
                <span v-if="status.cancelAtPeriodEnd"> (cancels at period end)</span>
              </p>
              <p v-if="error" class="form-error">{{ error }}</p>
              <RouterLink class="account-menu__link" to="/profile" @click="menuOpen = false">
                <span class="account-menu__title">Profile</span>
                <span class="account-menu__hint">Persona, goals, and referral</span>
              </RouterLink>
              <RouterLink class="account-menu__link" to="/projects" @click="menuOpen = false">
                <span class="account-menu__title">Projects</span>
                <span class="account-menu__hint">Saved research and exports</span>
              </RouterLink>
              <RouterLink class="account-menu__link" to="/pricing" @click="menuOpen = false">
                <span class="account-menu__title">Manage subscription</span>
              </RouterLink>
              <button
                v-if="status?.providerSubscriptionId && status.planId !== 'free' && !status.cancelAtPeriodEnd"
                type="button"
                class="account-menu__link"
                :disabled="isBusy"
                @click="onCancel"
              >
                Cancel at period end
              </button>
              <button type="button" class="account-menu__link" @click="logoutUser(); menuOpen = false">
                Sign out
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </header>
</template>

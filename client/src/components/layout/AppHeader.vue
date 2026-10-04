<script setup>
import { ref } from 'vue';
import { RouterLink } from 'vue-router';
import { storeToRefs } from 'pinia';
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

const { cancel, loadStatus, status, isBusy, error } = useBilling();
const menuOpen = ref(false);
const ui = useUiStore();
const { statusLine } = storeToRefs(ui);

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
      <RouterLink class="brand" to="/" aria-label="Gyanwire home">
        <span class="brand__mark" aria-hidden="true" />
        <span class="brand__text">
          <span class="brand__name">Gyanwire</span>
          <span class="brand__tag">R&D Research</span>
        </span>
      </RouterLink>

      <p class="site-header__status" aria-live="polite">{{ statusLine }}</p>

      <div class="auth-chrome">
        <RouterLink class="btn btn--ghost" to="/pricing">Pricing</RouterLink>

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
              <RouterLink class="account-menu__link" to="/pricing" @click="menuOpen = false">
                Manage subscription
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

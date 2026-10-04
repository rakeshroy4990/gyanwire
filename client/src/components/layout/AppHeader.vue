<script setup>
import { storeToRefs } from 'pinia';
import { useAuth } from '../../composables/useAuth.js';
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

const ui = useUiStore();
const { statusLine } = storeToRefs(ui);
</script>

<template>
  <header class="site-header">
    <div class="site-header__inner">
      <a class="brand" href="/" aria-label="Gyanwire home">
        <span class="brand__mark" aria-hidden="true" />
        <span class="brand__text">
          <span class="brand__name">Gyanwire</span>
          <span class="brand__tag">R&D Research</span>
        </span>
      </a>

      <p class="site-header__status" aria-live="polite">{{ statusLine }}</p>

      <div class="auth-chrome">
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
          <button type="button" class="btn btn--ghost" @click="logoutUser()">
            Sign out
          </button>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue';
import { useAuth } from '../../composables/useAuth.js';

const {
  isLoginOpen,
  identity,
  password,
  emailError,
  authError,
  loginInfoMessage,
  isSubmitting,
  isGoogleSubmitting,
  canSubmitLogin,
  setIdentity,
  setPassword,
  closePopup,
  openRegisterPopup,
  loginWithPassword,
  loginWithGoogle,
} = useAuth();

const emailInput = ref(null);
const showPassword = ref(false);

watch(isLoginOpen, async (open) => {
  if (!open) {
    showPassword.value = false;
    return;
  }
  await nextTick();
  emailInput.value?.focus();
});
</script>

<template>
  <div
    v-if="isLoginOpen"
    class="auth-modal"
    role="presentation"
  >
    <div class="auth-modal__backdrop" @click="closePopup()" />
    <div
      class="auth-modal__panel"
      role="dialog"
      aria-modal="true"
      aria-labelledby="loginTitle"
      aria-describedby="loginLede"
    >
      <button type="button" class="auth-modal__close" aria-label="Close" @click="closePopup()">
        <span aria-hidden="true">×</span>
      </button>

      <div class="auth-modal__brand" aria-hidden="true">
        <span class="auth-modal__mark" />
      </div>

      <header class="auth-modal__intro">
        <h2 id="loginTitle">Welcome back</h2>
        <p id="loginLede" class="auth-modal__lede">
          Sign in to keep your research tied to your account.
        </p>
      </header>

      <p v-if="loginInfoMessage" class="auth-modal__info" role="status">{{ loginInfoMessage }}</p>

      <button
        type="button"
        class="btn btn--google"
        :class="{ 'is-loading': isGoogleSubmitting }"
        :disabled="isSubmitting || isGoogleSubmitting"
        @click="loginWithGoogle()"
      >
        <svg class="btn--google__logo" viewBox="0 0 24 24" aria-hidden="true">
          <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
          <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
          <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
          <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
        </svg>
        Continue with Google
      </button>

      <div class="auth-divider" role="separator"><span>or continue with email</span></div>

      <form class="auth-form" novalidate @submit.prevent="loginWithPassword()">
        <label class="auth-field">
          <span class="auth-field__label">Email</span>
          <input
            ref="emailInput"
            id="login-email"
            name="email"
            :value="identity"
            type="email"
            inputmode="email"
            autocomplete="username"
            placeholder="you@example.com"
            required
            :aria-invalid="Boolean(emailError)"
            :aria-describedby="emailError ? 'login-email-error' : undefined"
            @input="setIdentity($event.target.value)"
          >
        </label>
        <p v-if="emailError" id="login-email-error" class="auth-form__error">{{ emailError }}</p>

        <label class="auth-field">
          <span class="auth-field__label">Password</span>
          <span class="auth-field__control">
            <input
              id="current-password"
              name="password"
              :value="password"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="Enter your password"
              required
              :aria-invalid="Boolean(authError)"
              :aria-describedby="authError ? 'login-auth-error' : undefined"
              @input="setPassword($event.target.value)"
            >
            <button
              type="button"
              class="auth-field__reveal"
              :aria-pressed="showPassword"
              :aria-label="showPassword ? 'Hide password' : 'Show password'"
              @click="showPassword = !showPassword"
            >
              {{ showPassword ? 'Hide' : 'Show' }}
            </button>
          </span>
        </label>
        <p v-if="authError" id="login-auth-error" class="auth-form__error" role="alert">{{ authError }}</p>

        <button
          type="submit"
          class="btn btn--primary btn--block"
          :class="{ 'is-loading': isSubmitting }"
          :disabled="!canSubmitLogin || isSubmitting || isGoogleSubmitting"
          :aria-busy="isSubmitting"
        >
          <span class="btn__label">Sign in</span>
          <span class="btn__spinner" aria-hidden="true" />
        </button>
      </form>

      <p class="auth-modal__footer">
        New to Gyanwire?
        <button type="button" class="auth-link" @click="openRegisterPopup()">Create an account</button>
      </p>
    </div>
  </div>
</template>

import { defineStore } from 'pinia';

/**
 * Hospital-style AuthForm: login/register field state + inline errors.
 */
export const useAuthFormStore = defineStore('authForm', {
  state: () => ({
    identity: '',
    password: '',
    emailError: '',
    authError: '',
    loginInfoMessage: '',
    registerFirstName: '',
    registerLastName: '',
    registerEmail: '',
    registerPassword: '',
    registerError: '',
    isSubmitting: false,
    isGoogleSubmitting: false,
  }),

  getters: {
    canSubmitLogin: (state) => {
      const identity = state.identity.trim();
      const password = state.password.trim();
      return Boolean(identity && password && !state.emailError);
    },
  },

  actions: {
    setIdentity(value) {
      this.identity = String(value ?? '');
      const email = this.identity.trim();
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      this.emailError = email.length === 0 || emailRegex.test(email)
        ? ''
        : 'Please enter a valid email address.';
      this.authError = '';
      this.loginInfoMessage = '';
    },

    setPassword(value) {
      this.password = String(value ?? '');
      this.authError = '';
      this.loginInfoMessage = '';
    },

    resetLoginForm({ keepIdentity = false, infoMessage = '' } = {}) {
      if (!keepIdentity) this.identity = '';
      this.password = '';
      this.emailError = '';
      this.authError = '';
      this.loginInfoMessage = infoMessage;
      this.isSubmitting = false;
      this.isGoogleSubmitting = false;
    },

    resetRegisterForm() {
      this.registerFirstName = '';
      this.registerLastName = '';
      this.registerEmail = '';
      this.registerPassword = '';
      this.registerError = '';
      this.isSubmitting = false;
    },

    prepareLoginAfterRegister(email, message) {
      this.resetRegisterForm();
      this.identity = String(email || '').trim();
      this.password = '';
      this.emailError = '';
      this.authError = '';
      this.loginInfoMessage = message || 'Account created. Sign in to continue.';
    },
  },
});

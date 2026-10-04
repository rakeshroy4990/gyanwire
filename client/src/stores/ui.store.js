import { defineStore } from 'pinia';

export const useUiStore = defineStore('ui', {
  state: () => ({
    activePopup: null, // 'login' | 'register' | null
    statusLine: '',
  }),

  getters: {
    isLoginOpen: (state) => state.activePopup === 'login',
    isRegisterOpen: (state) => state.activePopup === 'register',
    isAnyPopupOpen: (state) => Boolean(state.activePopup),
  },

  actions: {
    openLoginPopup() {
      this.activePopup = 'login';
    },
    openRegisterPopup() {
      this.activePopup = 'register';
    },
    closePopup() {
      this.activePopup = null;
    },
    setStatus(message) {
      this.statusLine = String(message || '');
    },
  },
});

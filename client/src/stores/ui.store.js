import { defineStore } from 'pinia';

export const useUiStore = defineStore('ui', {
  state: () => ({
    activePopup: null, // 'login' | 'register' | null
    /** Bumped when the header brand asks to return to the home workspace. */
    homeNonce: 0,
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
    requestHome() {
      this.closePopup();
      this.homeNonce += 1;
    },
  },
});

import { defineStore } from 'pinia';
import { markResearchHome } from '../services/researchWorkspace.js';

const THEME_KEY = 'gyanwire-theme';

export function readStoredTheme() {
  try {
    return localStorage.getItem(THEME_KEY) === 'light' ? 'light' : 'dark';
  } catch {
    return 'dark';
  }
}

export function applyTheme(theme) {
  const next = theme === 'light' ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  try {
    localStorage.setItem(THEME_KEY, next);
  } catch {
    // Preference stays for this visit when storage is blocked.
  }
  return next;
}

export const useUiStore = defineStore('ui', {
  state: () => ({
    activePopup: null, // 'login' | 'register' | null
    /** Bumped when the header brand asks to return to the home workspace. */
    homeNonce: 0,
    theme: 'dark',
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
      markResearchHome();
      this.homeNonce += 1;
    },
    hydrateTheme() {
      const fromDom = document.documentElement.dataset.theme;
      this.theme = fromDom === 'light' || fromDom === 'dark' ? fromDom : readStoredTheme();
      applyTheme(this.theme);
    },
    setTheme(theme) {
      this.theme = applyTheme(theme);
    },
  },
});

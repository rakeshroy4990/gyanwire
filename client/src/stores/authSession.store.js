import { defineStore } from 'pinia';

const AUTH_SESSION_KEY = 'gyanwire_auth_session_profile';

function toStringSafe(value) {
  return String(value ?? '').trim();
}

function emptySession() {
  return {
    userId: '',
    userDisplayName: '',
    fullName: '',
    loginDisplayName: 'Sign in',
    email: '',
    firstName: '',
    lastName: '',
    role: '',
    roleStatus: '',
    profilePic: '',
    preferredLocale: '',
    authMethod: '',
  };
}

/**
 * Hospital-style AuthSession: profile in Pinia + sessionStorage.
 * Access/refresh JWTs stay in httpOnly cookies — never localStorage.
 */
export const useAuthSessionStore = defineStore('authSession', {
  state: () => emptySession(),

  getters: {
    isAuthenticated: (state) => Boolean(state.userId),
    shortName: (state) => state.userDisplayName || state.email.split('@')[0] || '',
    greetingName: (state) => state.firstName || state.userDisplayName || state.email.split('@')[0] || '',
  },

  actions: {
    hydrateFromStorage() {
      try {
        const raw = sessionStorage.getItem(AUTH_SESSION_KEY);
        if (!raw) return false;
        const parsed = JSON.parse(raw);
        if (!parsed?.userId) return false;
        this.applyProfile(parsed, { persist: false });
        return true;
      } catch {
        return false;
      }
    },

    persistProfile() {
      try {
        sessionStorage.setItem(
          AUTH_SESSION_KEY,
          JSON.stringify({
            userId: this.userId,
            userDisplayName: this.userDisplayName,
            fullName: this.fullName,
            loginDisplayName: this.loginDisplayName,
            email: this.email,
            firstName: this.firstName,
            lastName: this.lastName,
            role: this.role,
            roleStatus: this.roleStatus,
            profilePic: this.profilePic,
            preferredLocale: this.preferredLocale,
            authMethod: this.authMethod,
          }),
        );
      } catch {
        // ignore quota / private mode
      }
    },

    applyProfile(userData = {}, { identityFallback = '', authMethod = '', persist = true } = {}) {
      const email = toStringSafe(userData.email || userData.Email || identityFallback);
      const firstName = toStringSafe(userData.firstName || userData.FirstName);
      const lastName = toStringSafe(userData.lastName || userData.LastName);
      const username = toStringSafe(userData.username || userData.Username);
      const fullFromNames = [firstName, lastName].filter(Boolean).join(' ');
      const displayName = toStringSafe(
        userData.userDisplayName
          || fullFromNames
          || (username && !username.includes('@') ? username : '')
          || email.split('@')[0]
          || 'User',
      );

      this.userId = toStringSafe(userData.userId || userData.UserId);
      this.email = email;
      this.firstName = firstName;
      this.lastName = lastName;
      this.fullName = fullFromNames || displayName;
      this.userDisplayName = displayName;
      this.loginDisplayName = displayName || 'Account';
      this.role = toStringSafe(userData.role || userData.Role || 'PATIENT').toUpperCase();
      this.roleStatus = toStringSafe(userData.roleStatus || userData.RoleStatus || 'ACTIVE').toUpperCase();
      this.profilePic = toStringSafe(userData.profilePic || userData.ProfilePic);
      this.preferredLocale = toStringSafe(userData.preferredLocale || userData.PreferredLocale);
      if (authMethod) this.authMethod = authMethod;

      if (persist) this.persistProfile();
    },

    clearSession() {
      Object.assign(this, emptySession());
      try {
        sessionStorage.removeItem(AUTH_SESSION_KEY);
      } catch {
        // ignore
      }
    },
  },
});

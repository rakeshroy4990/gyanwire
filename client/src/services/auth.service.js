import { useAuthSessionStore } from '../stores/authSession.store.js';
import { useAuthFormStore } from '../stores/authForm.store.js';
import { useUiStore } from '../stores/ui.store.js';
import { requestGoogleSignInAccessToken } from './googleSignIn.service.js';

async function parseJson(res) {
  try {
    return await res.json();
  } catch {
    return null;
  }
}

async function apiPost(path, body) {
  const res = await fetch(path, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body ?? {}),
  });
  return { res, payload: await parseJson(res) };
}

async function apiGet(path) {
  const res = await fetch(path, {
    method: 'GET',
    credentials: 'include',
  });
  return { res, payload: await parseJson(res) };
}

export function finalizeLoginSession(userData, identityFallback = '', { authMethod = 'password' } = {}) {
  const authSession = useAuthSessionStore();
  authSession.applyProfile(userData, { identityFallback, authMethod, persist: true });
}

export async function bootstrapAuthSession() {
  const authSession = useAuthSessionStore();
  if (!authSession.userId) return false;

  let me = await apiGet('/api/auth/me');
  if (!me.res.ok) {
    const refreshed = await apiPost('/api/auth/refresh', {});
    if (refreshed.res.ok && refreshed.payload?.data) {
      finalizeLoginSession(refreshed.payload.data, refreshed.payload.data.email, {
        authMethod: authSession.authMethod || 'password',
      });
      me = await apiGet('/api/auth/me');
    }
  }

  if (me.res.ok && me.payload?.data) {
    finalizeLoginSession(me.payload.data, me.payload.data.email, {
      authMethod: authSession.authMethod || 'password',
    });
    return true;
  }

  authSession.clearSession();
  return false;
}

export async function loginWithPassword() {
  const authForm = useAuthFormStore();
  const ui = useUiStore();
  const identity = authForm.identity.trim();
  const password = authForm.password;

  if (!identity || !password) {
    authForm.authError = 'Email and password are required.';
    return false;
  }
  if (authForm.emailError) return false;

  authForm.isSubmitting = true;
  authForm.authError = '';
  try {
    const { res, payload } = await apiPost('/api/auth/login', {
      EmailId: identity,
      Password: password,
    });
    if (!res.ok || !payload?.success) {
      authForm.authError = payload?.message || 'Invalid email or password';
      return false;
    }
    finalizeLoginSession(payload.data || {}, identity, { authMethod: 'password' });
    authForm.resetLoginForm();
    ui.closePopup();
    return true;
  } catch {
    authForm.authError = 'Unable to login right now. Please try again.';
    return false;
  } finally {
    authForm.isSubmitting = false;
  }
}

export async function loginWithGoogle() {
  const authForm = useAuthFormStore();
  const ui = useUiStore();
  authForm.isGoogleSubmitting = true;
  authForm.authError = '';
  try {
    const accessToken = await requestGoogleSignInAccessToken();
    const { res, payload } = await apiPost('/api/auth/google-login', {
      AccessToken: accessToken,
    });
    if (!res.ok || !payload?.success) {
      authForm.authError = payload?.message || 'Google sign-in failed.';
      return false;
    }
    finalizeLoginSession(payload.data || {}, payload.data?.email || '', { authMethod: 'google' });
    authForm.resetLoginForm();
    ui.closePopup();
    return true;
  } catch (err) {
    const msg = err instanceof Error ? err.message : 'Google sign-in failed.';
    if (!/cancel|popup|closed|denied|access_denied/i.test(msg)) {
      authForm.authError = msg;
    }
    return false;
  } finally {
    authForm.isGoogleSubmitting = false;
  }
}

export async function registerAccount() {
  const authForm = useAuthFormStore();
  const ui = useUiStore();
  const email = authForm.registerEmail.trim();
  const password = authForm.registerPassword;
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  if (!emailRegex.test(email) || password.length < 8) {
    authForm.registerError = 'Enter a valid email and a password with at least 8 characters.';
    return false;
  }

  authForm.isSubmitting = true;
  authForm.registerError = '';
  try {
    const { res, payload } = await apiPost('/api/auth/register', {
      EmailId: email,
      Password: password,
      FirstName: authForm.registerFirstName.trim(),
      LastName: authForm.registerLastName.trim(),
    });
    if (!res.ok || !payload?.success) {
      authForm.registerError = payload?.message || 'Registration failed.';
      return false;
    }
    authForm.prepareLoginAfterRegister(email, payload.message);
    ui.openLoginPopup();
    return true;
  } catch {
    authForm.registerError = 'Unable to register right now. Please try again.';
    return false;
  } finally {
    authForm.isSubmitting = false;
  }
}

export async function logoutUser() {
  const authSession = useAuthSessionStore();
  try {
    await apiPost('/api/auth/logout', {});
  } catch {
    // still clear local session
  }
  authSession.clearSession();
}

export function openLoginPopup(infoMessage = '') {
  const authForm = useAuthFormStore();
  const ui = useUiStore();
  authForm.resetLoginForm({
    keepIdentity: Boolean(authForm.identity),
    infoMessage,
  });
  ui.openLoginPopup();
}

export function openRegisterPopup() {
  const authForm = useAuthFormStore();
  const ui = useUiStore();
  authForm.resetRegisterForm();
  ui.openRegisterPopup();
}

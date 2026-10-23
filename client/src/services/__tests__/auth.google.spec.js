import { beforeEach, describe, expect, it, vi } from 'vitest';

const requestGoogleSignInAccessToken = vi.fn();
const applyProfile = vi.fn();
const resetLoginForm = vi.fn();
const closePopup = vi.fn();
const authFormState = {
  isGoogleSubmitting: false,
  authError: '',
  resetLoginForm,
};

vi.mock('../googleSignIn.service.js', () => ({
  requestGoogleSignInAccessToken: (...args) => requestGoogleSignInAccessToken(...args),
}));

vi.mock('../../stores/authSession.store.js', () => ({
  useAuthSessionStore: () => ({ applyProfile }),
}));

vi.mock('../../stores/authForm.store.js', () => ({
  useAuthFormStore: () => authFormState,
}));

vi.mock('../../stores/ui.store.js', () => ({
  useUiStore: () => ({ closePopup }),
}));

vi.mock('../../stores/profile.store.js', () => ({
  useProfileStore: () => ({ setProfile: vi.fn() }),
}));

vi.mock('../apiBase.js', () => ({
  apiUrl: (path) => `http://api.test${path}`,
}));

describe('loginWithGoogle', () => {
  beforeEach(() => {
    vi.resetModules();
    requestGoogleSignInAccessToken.mockReset();
    applyProfile.mockReset();
    resetLoginForm.mockReset();
    closePopup.mockReset();
    authFormState.isGoogleSubmitting = false;
    authFormState.authError = '';
    globalThis.fetch = vi.fn();
  });

  it('posts AccessToken and finalizes the session', async () => {
    requestGoogleSignInAccessToken.mockResolvedValue('ya29.ok');
    globalThis.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        success: true,
        data: { email: 'g@example.com', userId: 'u1' },
      }),
    });

    const { loginWithGoogle } = await import('../auth.service.js');
    await expect(loginWithGoogle()).resolves.toBe(true);

    expect(fetch).toHaveBeenCalledWith(
      'http://api.test/api/auth/google-login',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        body: JSON.stringify({ AccessToken: 'ya29.ok' }),
      }),
    );
    expect(applyProfile).toHaveBeenCalledWith(
      { email: 'g@example.com', userId: 'u1' },
      expect.objectContaining({ authMethod: 'google' }),
    );
    expect(closePopup).toHaveBeenCalled();
  });

  it('shows API error message when google-login fails', async () => {
    requestGoogleSignInAccessToken.mockResolvedValue('ya29.ok');
    globalThis.fetch.mockResolvedValue({
      ok: false,
      json: async () => ({
        success: false,
        message: 'Google sign-in failed.',
        errorCode: 'AUTH_GOOGLE_FAILED',
      }),
    });

    const { loginWithGoogle } = await import('../auth.service.js');
    await expect(loginWithGoogle()).resolves.toBe(false);
    expect(authFormState.authError).toBe('Google sign-in failed.');
    expect(applyProfile).not.toHaveBeenCalled();
  });

  it('swallows cancel-like GIS errors without a form error', async () => {
    requestGoogleSignInAccessToken.mockRejectedValue(new Error('access_denied: User closed popup'));
    const { loginWithGoogle } = await import('../auth.service.js');
    await expect(loginWithGoogle()).resolves.toBe(false);
    expect(authFormState.authError).toBe('');
  });
});

import { Router } from 'express';
import {
  AuthError,
  getCurrentUser,
  loginWithGoogleAccessToken,
  loginWithPassword,
  logout,
  refreshSession,
  register,
} from '../services/auth/authService.js';
import {
  clearAuthCookies,
  readAccessToken,
  readRefreshToken,
  setAuthCookies,
} from '../services/auth/cookies.js';

export const authRouter = Router();

function envelope(success, { data = null, message = '', errorCode = null } = {}) {
  return {
    success,
    data,
    message,
    errorCode,
    timestamp: new Date().toISOString(),
  };
}

function publicSession(session) {
  const {
    accessToken: _a,
    refreshToken: _r,
    ...user
  } = session;
  return user;
}

function handleAuthError(res, err) {
  if (err instanceof AuthError) {
    return res.status(err.status || 401).json(
      envelope(false, { message: err.message, errorCode: err.errorCode }),
    );
  }
  console.error(err);
  return res.status(500).json(
    envelope(false, {
      message: 'Unable to complete authentication right now.',
      errorCode: 'AUTH_INTERNAL_ERROR',
    }),
  );
}

// Simple per-IP login rate limit (hospital uses Bucket4j; keep lightweight here).
const loginAttempts = new Map();
function rateLimitLogin(req, res, next) {
  const ip = req.ip || req.socket.remoteAddress || 'unknown';
  const now = Date.now();
  const windowMs = 60_000;
  const max = 5;
  const entry = loginAttempts.get(ip) || { count: 0, start: now };
  if (now - entry.start > windowMs) {
    entry.count = 0;
    entry.start = now;
  }
  entry.count += 1;
  loginAttempts.set(ip, entry);
  if (entry.count > max) {
    return res.status(429).json(
      envelope(false, {
        message: 'Too many sign-in attempts. Please wait a minute and try again.',
        errorCode: 'AUTH_RATE_LIMITED',
      }),
    );
  }
  return next();
}

authRouter.post('/login', rateLimitLogin, async (req, res) => {
  try {
    const emailId = req.body?.EmailId ?? req.body?.emailId ?? req.body?.email;
    const password = req.body?.Password ?? req.body?.password;
    const session = await loginWithPassword(emailId, password);
    setAuthCookies(res, session);
    return res.json(envelope(true, { data: publicSession(session), message: 'Signed in successfully.' }));
  } catch (err) {
    return handleAuthError(res, err);
  }
});

authRouter.post('/google-login', rateLimitLogin, async (req, res) => {
  try {
    const accessToken = req.body?.AccessToken ?? req.body?.accessToken;
    const idToken = req.body?.IdToken ?? req.body?.idToken;
    if (!accessToken && !idToken) {
      return res.status(400).json(
        envelope(false, {
          message: 'Google access token is required.',
          errorCode: 'AUTH_GOOGLE_TOKEN_MISSING',
        }),
      );
    }
    // Hospital supports idToken or accessToken; GIS token client uses accessToken.
    if (!accessToken) {
      return res.status(400).json(
        envelope(false, {
          message: 'Google ID token login is not enabled. Use AccessToken from Google Sign-In.',
          errorCode: 'AUTH_GOOGLE_TOKEN_MISSING',
        }),
      );
    }
    const session = await loginWithGoogleAccessToken(accessToken);
    setAuthCookies(res, session);
    return res.json(envelope(true, { data: publicSession(session), message: 'Signed in with Google.' }));
  } catch (err) {
    return handleAuthError(res, err);
  }
});

authRouter.post('/register', async (req, res) => {
  try {
    const payload = {
      emailId: req.body?.EmailId ?? req.body?.emailId ?? req.body?.email,
      password: req.body?.Password ?? req.body?.password,
      firstName: req.body?.FirstName ?? req.body?.firstName,
      lastName: req.body?.LastName ?? req.body?.lastName,
    };
    const data = await register(payload);
    return res.status(201).json(envelope(true, { data, message: 'Account created. You can sign in now.' }));
  } catch (err) {
    return handleAuthError(res, err);
  }
});

authRouter.post('/refresh', async (req, res) => {
  try {
    const refreshToken = req.body?.RefreshToken ?? req.body?.refreshToken ?? readRefreshToken(req);
    const session = await refreshSession(refreshToken);
    setAuthCookies(res, session);
    return res.json(envelope(true, { data: publicSession(session), message: 'Session refreshed.' }));
  } catch (err) {
    clearAuthCookies(res);
    return handleAuthError(res, err);
  }
});

authRouter.post('/logout', async (req, res) => {
  try {
    const refreshToken = req.body?.RefreshToken ?? req.body?.refreshToken ?? readRefreshToken(req);
    await logout(refreshToken);
    clearAuthCookies(res);
    return res.json(envelope(true, { message: 'Signed out.' }));
  } catch (err) {
    clearAuthCookies(res);
    return handleAuthError(res, err);
  }
});

authRouter.get('/me', async (req, res) => {
  try {
    const user = await getCurrentUser(readAccessToken(req));
    return res.json(envelope(true, { data: user, message: 'OK' }));
  } catch (err) {
    // Try silent refresh path is client-side; here just report unauthenticated.
    return handleAuthError(res, err);
  }
});

import { fetchVerifiedGoogleProfile } from './google.js';
import {
  generateAccessToken,
  generateRefreshToken,
  getAccessExpirationSeconds,
  getRefreshExpirationSeconds,
  parseAndValidate,
} from './jwt.js';
import { hashPassword, validatePasswordPolicy, verifyPassword } from './password.js';
import * as refreshTokens from './refreshTokenRepository.js';
import * as users from './userRepository.js';

export class AuthError extends Error {
  constructor(message, errorCode, status = 401) {
    super(message);
    this.name = 'AuthError';
    this.errorCode = errorCode;
    this.status = status;
  }
}

function displayName(user) {
  const full = [user.firstName, user.lastName].filter(Boolean).join(' ').trim();
  if (full) return full;
  const local = String(user.email || '').split('@')[0];
  return local || 'User';
}

function toPublicUser(user) {
  return {
    userId: user.externalId || user.id,
    email: user.email,
    firstName: user.firstName || '',
    lastName: user.lastName || '',
    username: displayName(user),
    role: user.role || 'PATIENT',
    roleStatus: user.roleStatus || 'ACTIVE',
    active: Boolean(user.active),
    profilePic: user.profilePic || '',
  };
}

function assertEligible(user) {
  if (!user || !user.active || user.roleStatus === 'INACTIVE') {
    throw new AuthError(
      'Your account has been deactivated. You cannot sign in until an administrator reactivates your account.',
      'AUTH_ACCOUNT_DEACTIVATED',
      403,
    );
  }
  if (user.roleStatus === 'PENDING_APPROVAL') {
    throw new AuthError(
      'Your request is pending for approval. Please wait for an admin to approve your request.',
      'AUTH_ROLE_PENDING_APPROVAL',
      403,
    );
  }
  if (user.roleStatus !== 'ACTIVE') {
    throw new AuthError('Unable to sign in with this account.', 'AUTH_ROLE_BLOCKED', 403);
  }
}

async function issueSession(user) {
  assertEligible(user);
  const accessToken = generateAccessToken({
    userId: user.id,
    role: user.role,
    tokenVersion: user.tokenVersion,
  });
  const refreshToken = generateRefreshToken({
    userId: user.id,
    tokenVersion: user.tokenVersion,
  });
  const expiry = new Date(Date.now() + getRefreshExpirationSeconds() * 1000);
  await refreshTokens.saveRefreshToken({
    token: refreshToken,
    userId: user.id,
    expiry,
  });

  return {
    accessToken,
    refreshToken,
    expiresInSeconds: getAccessExpirationSeconds(),
    refreshExpiresInSeconds: getRefreshExpirationSeconds(),
    ...toPublicUser(user),
  };
}

export async function loginWithPassword(emailId, password) {
  const identity = String(emailId || '').trim().toLowerCase();
  const rawPassword = String(password || '');
  if (!identity || !rawPassword) {
    throw new AuthError('Email and password are required.', 'AUTH_VALIDATION_FAILED', 400);
  }

  const user = await users.findByEmail(identity);
  if (!user) {
    throw new AuthError('Invalid email or password', 'AUTH_INVALID_CREDENTIALS');
  }
  assertEligible(user);

  const ok = await verifyPassword(rawPassword, user.passwordHash);
  if (!ok) {
    throw new AuthError('Invalid email or password', 'AUTH_INVALID_CREDENTIALS');
  }

  return issueSession(user);
}

export async function loginWithGoogleAccessToken(accessToken) {
  const profile = await fetchVerifiedGoogleProfile(accessToken);
  if (!profile) {
    throw new AuthError('Google sign-in failed.', 'AUTH_GOOGLE_FAILED');
  }

  let user = await users.findByEmail(profile.email);
  if (user) {
    user = await users.enrichMissingProfile(user, {
      firstName: profile.givenName,
      lastName: profile.familyName,
      profilePic: profile.picture,
    });
  } else {
    user = await users.createGoogleUser({
      email: profile.email,
      firstName: profile.givenName,
      lastName: profile.familyName,
      profilePic: profile.picture,
    });
  }

  return issueSession(user);
}

export async function register({ emailId, password, firstName, lastName }) {
  const email = String(emailId || '').trim().toLowerCase();
  const rawPassword = String(password || '');
  if (!email || !rawPassword) {
    throw new AuthError('Email and password are required.', 'AUTH_VALIDATION_FAILED', 400);
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new AuthError('Please enter a valid email address.', 'AUTH_VALIDATION_FAILED', 400);
  }
  const policyError = validatePasswordPolicy(rawPassword);
  if (policyError) {
    throw new AuthError(policyError, 'AUTH_PASSWORD_POLICY', 400);
  }

  const existing = await users.findByEmail(email);
  if (existing) {
    throw new AuthError('An account with this email already exists.', 'AUTH_ACCOUNT_EXISTS', 409);
  }

  const passwordHash = await hashPassword(rawPassword);
  const user = await users.createPasswordUser({
    email,
    firstName,
    lastName,
    passwordHash,
  });

  return {
    userId: user.externalId || user.id,
    email: user.email,
    firstName: user.firstName || '',
    lastName: user.lastName || '',
    role: user.role,
    roleStatus: user.roleStatus,
  };
}

export async function refreshSession(refreshTokenValue) {
  const supplied = String(refreshTokenValue || '').trim();
  if (!supplied) {
    throw new AuthError('Refresh token is required.', 'AUTH_REFRESH_INVALID');
  }

  const stored = await refreshTokens.findByToken(supplied);
  if (!stored) {
    throw new AuthError('Refresh token is invalid.', 'AUTH_REFRESH_INVALID');
  }
  if (new Date(stored.expiry).getTime() <= Date.now()) {
    await refreshTokens.softDeleteByToken(supplied);
    throw new AuthError('Refresh token has expired.', 'AUTH_REFRESH_INVALID');
  }

  let claims;
  try {
    claims = parseAndValidate(supplied);
  } catch {
    await refreshTokens.softDeleteByToken(supplied);
    throw new AuthError('Refresh token is invalid.', 'AUTH_REFRESH_INVALID');
  }

  if (String(claims.tokenType || '').toLowerCase() !== 'refresh') {
    throw new AuthError('Refresh token is invalid.', 'AUTH_REFRESH_INVALID');
  }

  const user = await users.findById(stored.userId);
  if (!user || String(claims.sub) !== user.id) {
    throw new AuthError('Refresh token is invalid.', 'AUTH_REFRESH_INVALID');
  }
  if (Number(claims.tokenVersion || 0) !== Number(user.tokenVersion || 1)) {
    throw new AuthError('Refresh token is invalid.', 'AUTH_REFRESH_INVALID');
  }

  await refreshTokens.softDeleteByToken(supplied);
  return issueSession(user);
}

export async function logout(refreshTokenValue) {
  const supplied = String(refreshTokenValue || '').trim();
  if (supplied) {
    await refreshTokens.softDeleteByToken(supplied);
  }
}

export async function getCurrentUser(accessTokenValue) {
  const token = String(accessTokenValue || '').trim();
  if (!token) {
    throw new AuthError('Not authenticated.', 'AUTH_UNAUTHORIZED');
  }
  let claims;
  try {
    claims = parseAndValidate(token);
  } catch {
    throw new AuthError('Not authenticated.', 'AUTH_UNAUTHORIZED');
  }
  if (String(claims.tokenType || '').toLowerCase() !== 'access') {
    throw new AuthError('Not authenticated.', 'AUTH_UNAUTHORIZED');
  }
  const user = await users.findById(claims.sub);
  if (!user) {
    throw new AuthError('Not authenticated.', 'AUTH_UNAUTHORIZED');
  }
  assertEligible(user);
  if (Number(claims.tokenVersion || 0) !== Number(user.tokenVersion || 1)) {
    throw new AuthError('Not authenticated.', 'AUTH_UNAUTHORIZED');
  }
  return toPublicUser(user);
}

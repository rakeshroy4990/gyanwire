import crypto from 'node:crypto';
import jwt from 'jsonwebtoken';

function requireSecret() {
  const secret = process.env.JWT_SECRET;
  if (!secret || secret.length < 16) {
    throw new Error('JWT_SECRET must be set to a strong value (16+ characters).');
  }
  return secret;
}

export function getAccessExpirationSeconds() {
  return Number(process.env.JWT_ACCESS_TTL_SECONDS || 12 * 60 * 60);
}

export function getRefreshExpirationSeconds() {
  return Number(process.env.JWT_REFRESH_TTL_SECONDS || 30 * 24 * 60 * 60);
}

export function generateAccessToken({ userId, role, tokenVersion }) {
  return jwt.sign(
    {
      tokenType: 'access',
      role: role || 'PATIENT',
      tokenVersion: Number(tokenVersion || 1),
      aud: 'web',
    },
    requireSecret(),
    {
      subject: String(userId),
      expiresIn: getAccessExpirationSeconds(),
    },
  );
}

export function generateRefreshToken({ userId, tokenVersion, deviceId = 'browser' }) {
  return jwt.sign(
    {
      tokenType: 'refresh',
      tokenVersion: Number(tokenVersion || 1),
      deviceId,
      jti: crypto.randomUUID(),
      aud: 'web',
    },
    requireSecret(),
    {
      subject: String(userId),
      expiresIn: getRefreshExpirationSeconds(),
    },
  );
}

export function parseAndValidate(token) {
  return jwt.verify(String(token), requireSecret());
}

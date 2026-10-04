const ACCESS_COOKIE = 'access_token';
const REFRESH_COOKIE = 'refresh_token';
const REFRESH_PATH = '/api/auth';

function cookiePolicy() {
  const secure = String(process.env.AUTH_COOKIE_SECURE || '').toLowerCase() === 'true';
  const sameSiteRaw = String(process.env.AUTH_COOKIE_SAME_SITE || 'Lax').trim();
  const sameSite = sameSiteRaw.toLowerCase() === 'none' ? 'none' : sameSiteRaw.toLowerCase() === 'strict' ? 'strict' : 'lax';
  const domain = String(process.env.AUTH_COOKIE_DOMAIN || '').trim() || undefined;
  return {
    secure: sameSite === 'none' ? true : secure,
    sameSite,
    domain,
  };
}

function appendCookie(res, name, value, { maxAgeSeconds, path }) {
  const policy = cookiePolicy();
  const parts = [
    `${name}=${encodeURIComponent(value || '')}`,
    `Path=${path}`,
    'HttpOnly',
    `SameSite=${policy.sameSite.charAt(0).toUpperCase()}${policy.sameSite.slice(1)}`,
    `Max-Age=${Math.max(0, Number(maxAgeSeconds) || 0)}`,
  ];
  if (policy.secure) parts.push('Secure');
  if (policy.domain) parts.push(`Domain=${policy.domain}`);
  res.append('Set-Cookie', parts.join('; '));
}

export function setAuthCookies(res, { accessToken, refreshToken, expiresInSeconds, refreshExpiresInSeconds }) {
  appendCookie(res, ACCESS_COOKIE, accessToken, {
    maxAgeSeconds: expiresInSeconds,
    path: '/',
  });
  appendCookie(res, REFRESH_COOKIE, refreshToken, {
    maxAgeSeconds: refreshExpiresInSeconds,
    path: REFRESH_PATH,
  });
}

export function clearAuthCookies(res) {
  appendCookie(res, ACCESS_COOKIE, '', { maxAgeSeconds: 0, path: '/' });
  appendCookie(res, REFRESH_COOKIE, '', { maxAgeSeconds: 0, path: REFRESH_PATH });
}

export function readAccessToken(req) {
  return req.cookies?.[ACCESS_COOKIE] || null;
}

export function readRefreshToken(req) {
  return req.cookies?.[REFRESH_COOKIE] || null;
}

export { ACCESS_COOKIE, REFRESH_COOKIE };

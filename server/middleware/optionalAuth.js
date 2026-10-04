import { readAccessToken } from '../services/auth/cookies.js';
import { parseAndValidate } from '../services/auth/jwt.js';
import * as users from '../services/auth/userRepository.js';

/**
 * Attaches req.authUser when a valid access cookie is present.
 * Never fails the request — anonymous callers continue without a user.
 */
export async function optionalAuth(req, _res, next) {
  req.authUser = null;
  try {
    const token = readAccessToken(req);
    if (!token) return next();
    const claims = parseAndValidate(token);
    if (String(claims.tokenType || '').toLowerCase() !== 'access') return next();
    const user = await users.findById(claims.sub);
    if (!user) return next();
    if (Number(claims.tokenVersion || 0) !== Number(user.tokenVersion || 1)) return next();
    req.authUser = user;
  } catch {
    // ignore invalid/expired tokens for optional auth
  }
  return next();
}

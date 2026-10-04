import { AuthError, getCurrentUser } from '../services/auth/authService.js';
import { readAccessToken } from '../services/auth/cookies.js';
import * as users from '../services/auth/userRepository.js';

export async function requireAuth(req, res, next) {
  try {
    const publicUser = await getCurrentUser(readAccessToken(req));
    const user = await users.findById(publicUser.userId);
    if (!user) {
      throw new AuthError('Not authenticated.', 'AUTH_UNAUTHORIZED');
    }
    req.authUser = user;
    req.publicUser = publicUser;
    return next();
  } catch (err) {
    if (err instanceof AuthError) {
      return res.status(err.status || 401).json({
        success: false,
        message: err.message,
        errorCode: err.errorCode,
        timestamp: new Date().toISOString(),
      });
    }
    return next(err);
  }
}

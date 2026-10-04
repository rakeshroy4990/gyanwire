import { Router } from 'express';
import { optionalAuth } from '../middleware/optionalAuth.js';
import { getUsageSnapshot, hashIp } from '../services/billing/usageService.js';

export const meRouter = Router();

meRouter.get('/usage', optionalAuth, async (req, res, next) => {
  try {
    const userId = req.authUser?.id || null;
    const ipHash = hashIp(req.ip || req.socket?.remoteAddress);
    const usage = await getUsageSnapshot({ userId, ipHash });
    return res.json({
      success: true,
      data: usage,
      message: 'OK',
      timestamp: new Date().toISOString(),
    });
  } catch (err) {
    return next(err);
  }
});

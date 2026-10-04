import {
  evaluateSearchLimit,
  hashIp,
  resolveUserPlan,
  countTodaySearches,
} from '../services/billing/usageService.js';

/**
 * Gate a metered action by the caller's plan daily limit.
 * Currently supports kind === 'search'.
 */
export function requirePlanLimit(kind = 'search') {
  return async function planLimitMiddleware(req, res, next) {
    try {
      if (kind !== 'search') {
        return res.status(500).json({
          success: false,
          message: 'Unknown usage limit kind.',
          errorCode: 'USAGE_KIND_UNSUPPORTED',
          timestamp: new Date().toISOString(),
        });
      }

      const userId = req.authUser?.id || null;
      const ipHash = hashIp(req.ip || req.socket?.remoteAddress);
      req.usageContext = { userId, ipHash, kind };

      const plan = await resolveUserPlan(userId);
      const used = await countTodaySearches({ userId, ipHash });
      const evaluation = evaluateSearchLimit({
        used,
        limit: plan.dailySearchLimit,
      });

      req.usageContext.plan = plan;
      req.usageContext.evaluation = evaluation;

      if (!evaluation.allowed) {
        return res.status(402).json({
          success: false,
          message: userId
            ? `You've used all ${evaluation.limit} searches for today on the ${plan.name} plan.`
            : `Anonymous visitors get ${evaluation.limit} searches per day. Sign up for more.`,
          errorCode: 'LIMIT_REACHED',
          data: {
            code: 'LIMIT_REACHED',
            upgradeUrl: '/pricing',
            plan: { id: plan.id, name: plan.name },
            searchesToday: evaluation.used,
            searchLimit: evaluation.limit,
            searchesRemaining: 0,
          },
          timestamp: new Date().toISOString(),
        });
      }

      return next();
    } catch (err) {
      return next(err);
    }
  };
}

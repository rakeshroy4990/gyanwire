import { createHash } from 'node:crypto';
import { query } from '../../db/pool.js';

export const ANON_DAILY_SEARCH_LIMIT = 2;
export const DEFAULT_PLAN_ID = 'free';

const FREE_PLAN = {
  id: 'free',
  name: 'Free',
  dailySearchLimit: 5,
  canSave: false,
  canExport: false,
  canAlert: false,
  seats: 1,
};

export function hashIp(ip) {
  const raw = String(ip || 'unknown').trim() || 'unknown';
  return createHash('sha256').update(raw).digest('hex');
}

function mapPlan(row) {
  if (!row) return { ...FREE_PLAN };
  return {
    id: row.id,
    name: row.name,
    dailySearchLimit: Number(row.daily_search_limit),
    canSave: Boolean(row.can_save),
    canExport: Boolean(row.can_export),
    canAlert: Boolean(row.can_alert),
    seats: Number(row.seats || 1),
  };
}

export async function getPlanById(planId) {
  const result = await query(
    `SELECT id, name, daily_search_limit, can_save, can_export, can_alert, seats
     FROM plans
     WHERE id = $1
     LIMIT 1`,
    [planId || DEFAULT_PLAN_ID],
  );
  return mapPlan(result.rows[0]);
}

export async function resolveUserPlan(userId) {
  if (!userId) {
    return {
      ...FREE_PLAN,
      dailySearchLimit: ANON_DAILY_SEARCH_LIMIT,
      name: 'Anonymous',
      id: 'anonymous',
    };
  }

  const result = await query(
    `SELECT p.id, p.name, p.daily_search_limit, p.can_save, p.can_export, p.can_alert, p.seats
     FROM subscriptions s
     INNER JOIN plans p ON p.id = s.plan_id
     WHERE s.user_id = $1
       AND s.status IN ('active', 'trialing', 'past_due')
       AND (s.current_period_end IS NULL OR s.current_period_end > NOW())
     ORDER BY s.updated_at DESC
     LIMIT 1`,
    [userId],
  );

  if (result.rows[0]) return mapPlan(result.rows[0]);
  return getPlanById(DEFAULT_PLAN_ID);
}

export function utcDayBounds(now = new Date()) {
  const start = new Date(Date.UTC(
    now.getUTCFullYear(),
    now.getUTCMonth(),
    now.getUTCDate(),
    0, 0, 0, 0,
  ));
  const end = new Date(start);
  end.setUTCDate(end.getUTCDate() + 1);
  return { start, end };
}

export async function countTodaySearches({ userId = null, ipHash = null } = {}) {
  const { start, end } = utcDayBounds();
  if (userId) {
    const result = await query(
      `SELECT COUNT(*)::int AS count
       FROM usage_events
       WHERE kind = 'search'
         AND user_id = $1
         AND created_at >= $2
         AND created_at < $3`,
      [userId, start.toISOString(), end.toISOString()],
    );
    return Number(result.rows[0]?.count || 0);
  }

  if (!ipHash) return 0;
  const result = await query(
    `SELECT COUNT(*)::int AS count
     FROM usage_events
     WHERE kind = 'search'
       AND user_id IS NULL
       AND ip_hash = $1
       AND created_at >= $2
       AND created_at < $3`,
    [ipHash, start.toISOString(), end.toISOString()],
  );
  return Number(result.rows[0]?.count || 0);
}

/**
 * Pure helper for tests and middleware.
 * @returns {{ allowed: boolean, used: number, limit: number, remaining: number }}
 */
export function evaluateSearchLimit({ used, limit }) {
  const safeUsed = Math.max(0, Number(used) || 0);
  const safeLimit = Math.max(0, Number(limit) || 0);
  const remaining = Math.max(0, safeLimit - safeUsed);
  return {
    allowed: safeUsed < safeLimit,
    used: safeUsed,
    limit: safeLimit,
    remaining,
  };
}

export async function getUsageSnapshot({ userId = null, ipHash = null } = {}) {
  const plan = await resolveUserPlan(userId);
  const used = await countTodaySearches({ userId, ipHash });
  const evaluation = evaluateSearchLimit({ used, limit: plan.dailySearchLimit });
  return {
    plan: {
      id: plan.id,
      name: plan.name,
      dailySearchLimit: plan.dailySearchLimit,
      canSave: plan.canSave,
      canExport: plan.canExport,
      canAlert: plan.canAlert,
      seats: plan.seats,
    },
    searchesToday: evaluation.used,
    searchesRemaining: evaluation.remaining,
    searchLimit: evaluation.limit,
  };
}

export async function recordSearchUsage({ userId = null, ipHash = null, costInrEstimate = 0 } = {}) {
  await query(
    `INSERT INTO usage_events (user_id, ip_hash, kind, cost_inr_estimate, created_at)
     VALUES ($1, $2, 'search', $3, NOW())`,
    [userId || null, userId ? null : ipHash, Number(costInrEstimate) || 0],
  );
}

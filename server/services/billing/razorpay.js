import crypto from 'node:crypto';
import Razorpay from 'razorpay';
import { query } from '../../db/pool.js';

const PLAN_ENV = {
  pro: {
    monthly: 'RAZORPAY_PLAN_PRO_MONTHLY',
    annual: 'RAZORPAY_PLAN_PRO_ANNUAL',
  },
  team: {
    monthly: 'RAZORPAY_PLAN_TEAM_MONTHLY',
    annual: 'RAZORPAY_PLAN_TEAM_ANNUAL',
  },
};

export function getRazorpayKeyId() {
  return String(process.env.RAZORPAY_KEY_ID || '').trim();
}

function getRazorpaySecret() {
  return String(process.env.RAZORPAY_KEY_SECRET || '').trim();
}

export function isRazorpayConfigured() {
  return Boolean(getRazorpayKeyId() && getRazorpaySecret());
}

export function getRazorpayClient() {
  if (!isRazorpayConfigured()) {
    throw new Error('Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET.');
  }
  return new Razorpay({
    key_id: getRazorpayKeyId(),
    key_secret: getRazorpaySecret(),
  });
}

export function resolveRazorpayPlanId(planId, interval) {
  const plan = String(planId || '').trim().toLowerCase();
  const intv = String(interval || 'monthly').trim().toLowerCase();
  if (plan === 'free') {
    throw Object.assign(new Error('Free plan does not require checkout.'), {
      status: 400,
      errorCode: 'BILLING_FREE_PLAN',
    });
  }
  const envKey = PLAN_ENV[plan]?.[intv];
  if (!envKey) {
    throw Object.assign(new Error('Unsupported plan or billing interval.'), {
      status: 400,
      errorCode: 'BILLING_PLAN_INVALID',
    });
  }
  const razorpayPlanId = String(process.env[envKey] || '').trim();
  if (!razorpayPlanId) {
    throw Object.assign(new Error(`Missing Razorpay plan id env: ${envKey}`), {
      status: 503,
      errorCode: 'BILLING_PLAN_UNCONFIGURED',
    });
  }
  return { planId: plan, interval: intv, razorpayPlanId };
}

export function verifyWebhookSignature(rawBody, signature) {
  const secret = String(process.env.RAZORPAY_WEBHOOK_SECRET || '').trim();
  if (!secret) {
    throw Object.assign(new Error('RAZORPAY_WEBHOOK_SECRET is not set.'), {
      status: 503,
      errorCode: 'BILLING_WEBHOOK_UNCONFIGURED',
    });
  }
  const expected = crypto
    .createHmac('sha256', secret)
    .update(rawBody)
    .digest('hex');
  const a = Buffer.from(expected);
  const b = Buffer.from(String(signature || ''));
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    throw Object.assign(new Error('Invalid Razorpay webhook signature.'), {
      status: 400,
      errorCode: 'BILLING_WEBHOOK_INVALID',
    });
  }
}

async function upsertSubscription({
  userId,
  planId,
  status,
  providerSubscriptionId,
  currentPeriodEnd = null,
  cancelAtPeriodEnd = false,
}) {
  const existing = await query(
    `SELECT id FROM subscriptions
     WHERE provider = 'razorpay' AND provider_subscription_id = $1
     LIMIT 1`,
    [providerSubscriptionId],
  );

  if (existing.rows[0]) {
    await query(
      `UPDATE subscriptions
       SET user_id = COALESCE($2, user_id),
           plan_id = $3,
           status = $4,
           current_period_end = $5,
           cancel_at_period_end = $6,
           updated_at = NOW()
       WHERE id = $1`,
      [
        existing.rows[0].id,
        userId || null,
        planId,
        status,
        currentPeriodEnd,
        cancelAtPeriodEnd,
      ],
    );
    return existing.rows[0].id;
  }

  // Clear other active rows for this user so the unique partial index holds.
  if (userId) {
    await query(
      `UPDATE subscriptions
       SET status = 'replaced', updated_at = NOW()
       WHERE user_id = $1
         AND status IN ('active', 'trialing', 'past_due', 'created', 'authenticated')
         AND (provider_subscription_id IS DISTINCT FROM $2)`,
      [userId, providerSubscriptionId],
    );
  }

  const inserted = await query(
    `INSERT INTO subscriptions (
       user_id, plan_id, status, provider, provider_subscription_id,
       current_period_end, cancel_at_period_end, created_at, updated_at
     ) VALUES ($1, $2, $3, 'razorpay', $4, $5, $6, NOW(), NOW())
     RETURNING id`,
    [userId, planId, status, providerSubscriptionId, currentPeriodEnd, cancelAtPeriodEnd],
  );
  return inserted.rows[0].id;
}

export async function createCheckoutSubscription({ userId, email, planId, interval }) {
  const { planId: normalizedPlan, razorpayPlanId } = resolveRazorpayPlanId(planId, interval);
  const client = getRazorpayClient();

  const subscription = await client.subscriptions.create({
    plan_id: razorpayPlanId,
    total_count: interval === 'annual' ? 10 : 120,
    customer_notify: 1,
    notes: {
      gyanwire_user_id: String(userId),
      gyanwire_plan_id: normalizedPlan,
      gyanwire_interval: String(interval || 'monthly'),
    },
  });

  await upsertSubscription({
    userId,
    planId: normalizedPlan,
    status: subscription.status || 'created',
    providerSubscriptionId: subscription.id,
    currentPeriodEnd: subscription.current_end
      ? new Date(subscription.current_end * 1000)
      : null,
  });

  return {
    subscriptionId: subscription.id,
    keyId: getRazorpayKeyId(),
    planId: normalizedPlan,
    interval: interval || 'monthly',
    email,
  };
}

export async function getBillingStatus(userId) {
  const result = await query(
    `SELECT s.plan_id, s.status, s.provider, s.provider_subscription_id,
            s.current_period_end, s.cancel_at_period_end, s.updated_at,
            p.name AS plan_name
     FROM subscriptions s
     INNER JOIN plans p ON p.id = s.plan_id
     WHERE s.user_id = $1
     ORDER BY s.updated_at DESC
     LIMIT 1`,
    [userId],
  );
  const row = result.rows[0];
  if (!row) {
    return {
      planId: 'free',
      planName: 'Free',
      status: 'none',
      provider: null,
      providerSubscriptionId: null,
      currentPeriodEnd: null,
      cancelAtPeriodEnd: false,
    };
  }
  return {
    planId: row.plan_id,
    planName: row.plan_name,
    status: row.status,
    provider: row.provider,
    providerSubscriptionId: row.provider_subscription_id,
    currentPeriodEnd: row.current_period_end,
    cancelAtPeriodEnd: Boolean(row.cancel_at_period_end),
  };
}

export async function cancelSubscription(userId) {
  const status = await getBillingStatus(userId);
  if (!status.providerSubscriptionId || status.planId === 'free') {
    throw Object.assign(new Error('No active paid subscription to cancel.'), {
      status: 400,
      errorCode: 'BILLING_NO_SUBSCRIPTION',
    });
  }

  const client = getRazorpayClient();
  await client.subscriptions.cancel(status.providerSubscriptionId, {
    cancel_at_cycle_end: 1,
  });

  await query(
    `UPDATE subscriptions
     SET cancel_at_period_end = true,
         status = CASE WHEN status = 'active' THEN 'active' ELSE status END,
         updated_at = NOW()
     WHERE provider_subscription_id = $1`,
    [status.providerSubscriptionId],
  );

  return getBillingStatus(userId);
}

function notesPlanId(entity) {
  const notes = entity?.notes || {};
  const fromNotes = String(notes.gyanwire_plan_id || '').trim().toLowerCase();
  if (fromNotes === 'pro' || fromNotes === 'team') return fromNotes;
  return 'pro';
}

function notesUserId(entity) {
  const notes = entity?.notes || {};
  const id = String(notes.gyanwire_user_id || '').trim();
  return id || null;
}

async function markEventProcessed(eventId, eventType, payload) {
  const result = await query(
    `INSERT INTO billing_events (event_id, event_type, payload_json)
     VALUES ($1, $2, $3::jsonb)
     ON CONFLICT (event_id) DO NOTHING
     RETURNING id`,
    [eventId, eventType, JSON.stringify(payload)],
  );
  return Boolean(result.rows[0]);
}

export async function handleWebhookEvent(event) {
  const eventId = String(event?.id || '').trim();
  const eventType = String(event?.event || '').trim();
  if (!eventId || !eventType) {
    throw Object.assign(new Error('Webhook payload missing id or event.'), {
      status: 400,
      errorCode: 'BILLING_WEBHOOK_MALFORMED',
    });
  }

  const inserted = await markEventProcessed(eventId, eventType, event);
  if (!inserted) {
    return { duplicate: true, eventType };
  }

  const entity = event?.payload?.subscription?.entity
    || event?.payload?.payment?.entity
    || null;

  // Prefer subscription entity; for charged events Razorpay nests subscription separately.
  const subscriptionEntity = event?.payload?.subscription?.entity || entity;
  if (!subscriptionEntity?.id && !eventType.startsWith('subscription.')) {
    return { duplicate: false, eventType, ignored: true };
  }

  const providerSubscriptionId = subscriptionEntity?.id
    || event?.payload?.subscription?.entity?.id;
  if (!providerSubscriptionId) {
    return { duplicate: false, eventType, ignored: true };
  }

  const userId = notesUserId(subscriptionEntity);
  const planId = notesPlanId(subscriptionEntity);
  const periodEnd = subscriptionEntity.current_end
    ? new Date(subscriptionEntity.current_end * 1000)
    : null;

  switch (eventType) {
    case 'subscription.activated':
    case 'subscription.charged':
      await upsertSubscription({
        userId,
        planId,
        status: 'active',
        providerSubscriptionId,
        currentPeriodEnd: periodEnd,
        cancelAtPeriodEnd: false,
      });
      break;
    case 'subscription.halted':
      await upsertSubscription({
        userId,
        planId,
        status: 'halted',
        providerSubscriptionId,
        currentPeriodEnd: periodEnd,
      });
      break;
    case 'subscription.cancelled':
      await upsertSubscription({
        userId,
        planId,
        status: 'cancelled',
        providerSubscriptionId,
        currentPeriodEnd: periodEnd,
        cancelAtPeriodEnd: false,
      });
      break;
    case 'subscription.completed':
      await upsertSubscription({
        userId,
        planId,
        status: 'completed',
        providerSubscriptionId,
        currentPeriodEnd: periodEnd,
      });
      break;
    default:
      return { duplicate: false, eventType, ignored: true };
  }

  return { duplicate: false, eventType, ignored: false };
}

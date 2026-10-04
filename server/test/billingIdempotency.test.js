import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { migrate } from '../db/migrate.js';
import { isPostgresPersistenceEnabled, query } from '../db/pool.js';
import { handleWebhookEvent } from '../services/billing/razorpay.js';

const dbUrl = process.env.GYANWIRE_DATABASE_URL || process.env.DATABASE_URL;
const canRun = Boolean(dbUrl);

describe.runIf(canRun)('handleWebhookEvent idempotency', () => {
  const eventId = `evt_test_${Date.now()}`;
  const subId = `sub_test_${Date.now()}`;
  let userId = '';

  beforeAll(async () => {
    process.env.APP_PERSISTENCE_PROVIDER = 'postgres';
    await migrate();
    const user = await query(
      `INSERT INTO users (email, password_hash, role)
       VALUES ($1, 'x', 'user')
       RETURNING id`,
      [`billing_test_${Date.now()}@example.com`],
    );
    userId = user.rows[0].id;
  });

  afterAll(async () => {
    await query('DELETE FROM billing_events WHERE event_id = $1', [eventId]);
    await query('DELETE FROM subscriptions WHERE provider_subscription_id = $1', [subId]);
    if (userId) await query('DELETE FROM users WHERE id = $1', [userId]);
  });

  it('processing the same webhook twice only applies once', async () => {
    const event = {
      id: eventId,
      event: 'subscription.activated',
      payload: {
        subscription: {
          entity: {
            id: subId,
            status: 'active',
            current_end: Math.floor(Date.now() / 1000) + 86400,
            notes: {
              gyanwire_user_id: String(userId),
              gyanwire_plan_id: 'pro',
            },
          },
        },
      },
    };

    const first = await handleWebhookEvent(event);
    const second = await handleWebhookEvent(event);
    expect(first.duplicate).toBe(false);
    expect(second.duplicate).toBe(true);

    const rows = await query(
      'SELECT COUNT(*)::int AS count FROM billing_events WHERE event_id = $1',
      [eventId],
    );
    expect(rows.rows[0].count).toBe(1);

    const subs = await query(
      `SELECT plan_id, status FROM subscriptions WHERE provider_subscription_id = $1`,
      [subId],
    );
    expect(subs.rows[0].plan_id).toBe('pro');
    expect(subs.rows[0].status).toBe('active');
  });
});

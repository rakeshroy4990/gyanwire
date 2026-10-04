import crypto from 'node:crypto';
import { afterEach, describe, expect, it } from 'vitest';
import { verifyWebhookSignature } from '../services/billing/razorpay.js';

const ORIGINAL = { ...process.env };

afterEach(() => {
  for (const key of Object.keys(process.env)) {
    if (!(key in ORIGINAL)) delete process.env[key];
  }
  Object.assign(process.env, ORIGINAL);
});

describe('verifyWebhookSignature', () => {
  it('accepts a valid HMAC and rejects a replay with wrong signature', () => {
    process.env.RAZORPAY_WEBHOOK_SECRET = 'whsec_test';
    const body = Buffer.from(JSON.stringify({ id: 'evt_1', event: 'subscription.activated' }));
    const good = crypto.createHmac('sha256', 'whsec_test').update(body).digest('hex');
    expect(() => verifyWebhookSignature(body, good)).not.toThrow();
    expect(() => verifyWebhookSignature(body, 'deadbeef')).toThrow(/Invalid Razorpay webhook signature/);
  });
});

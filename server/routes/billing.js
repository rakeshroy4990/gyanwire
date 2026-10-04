import { Router } from 'express';
import { z } from 'zod';
import { requireAuth } from '../middleware/requireAuth.js';
import {
  cancelSubscription,
  createCheckoutSubscription,
  getBillingStatus,
  handleWebhookEvent,
  isRazorpayConfigured,
  verifyWebhookSignature,
} from '../services/billing/razorpay.js';

export const billingRouter = Router();

const checkoutSchema = z.object({
  planId: z.enum(['pro', 'team']),
  interval: z.enum(['monthly', 'annual']).default('monthly'),
});

function envelope(success, { data = null, message = '', errorCode = null } = {}) {
  return {
    success,
    data,
    message,
    errorCode,
    timestamp: new Date().toISOString(),
  };
}

function handleBillingError(res, err) {
  const status = err.status || 500;
  return res.status(status).json(
    envelope(false, {
      message: err.message || 'Billing request failed.',
      errorCode: err.errorCode || 'BILLING_ERROR',
    }),
  );
}

billingRouter.post('/checkout', requireAuth, async (req, res) => {
  try {
    if (!isRazorpayConfigured()) {
      return res.status(503).json(
        envelope(false, {
          message: 'Billing is not configured yet.',
          errorCode: 'BILLING_UNCONFIGURED',
        }),
      );
    }
    const parsed = checkoutSchema.safeParse(req.body || {});
    if (!parsed.success) {
      return res.status(400).json(
        envelope(false, {
          message: 'Choose Pro or Team and a billing interval.',
          errorCode: 'VALIDATION_ERROR',
          data: parsed.error.flatten(),
        }),
      );
    }
    const data = await createCheckoutSubscription({
      userId: req.authUser.id,
      email: req.authUser.email,
      planId: parsed.data.planId,
      interval: parsed.data.interval,
    });
    return res.json(envelope(true, { data, message: 'Checkout ready.' }));
  } catch (err) {
    return handleBillingError(res, err);
  }
});

billingRouter.post('/cancel', requireAuth, async (req, res) => {
  try {
    const data = await cancelSubscription(req.authUser.id);
    return res.json(envelope(true, {
      data,
      message: 'Subscription will end at the current period.',
    }));
  } catch (err) {
    return handleBillingError(res, err);
  }
});

billingRouter.get('/status', requireAuth, async (req, res) => {
  try {
    const data = await getBillingStatus(req.authUser.id);
    return res.json(envelope(true, { data, message: 'OK' }));
  } catch (err) {
    return handleBillingError(res, err);
  }
});

/**
 * Mount with express.raw({ type: 'application/json' }) before express.json().
 */
export async function billingWebhookHandler(req, res) {
  try {
    const signature = req.get('X-Razorpay-Signature');
    const rawBody = Buffer.isBuffer(req.body)
      ? req.body
      : Buffer.from(typeof req.body === 'string' ? req.body : JSON.stringify(req.body || {}));

    verifyWebhookSignature(rawBody, signature);
    const event = JSON.parse(rawBody.toString('utf8'));
    const result = await handleWebhookEvent(event);
    return res.json({ success: true, ...result });
  } catch (err) {
    return handleBillingError(res, err);
  }
}

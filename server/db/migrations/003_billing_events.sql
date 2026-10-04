-- Idempotent Razorpay webhook event storage.

CREATE TABLE IF NOT EXISTS billing_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id     TEXT NOT NULL UNIQUE,
    event_type   TEXT NOT NULL,
    payload_json JSONB,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_billing_events_type ON billing_events (event_type);

ALTER TABLE subscriptions
  ADD COLUMN IF NOT EXISTS cancel_at_period_end BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS plans (
    id                   TEXT PRIMARY KEY,
    name                 TEXT NOT NULL,
    daily_search_limit   INTEGER NOT NULL,
    can_save             BOOLEAN NOT NULL DEFAULT false,
    can_export           BOOLEAN NOT NULL DEFAULT false,
    can_alert            BOOLEAN NOT NULL DEFAULT false,
    seats                INTEGER NOT NULL DEFAULT 1
);

INSERT INTO plans (id, name, daily_search_limit, can_save, can_export, can_alert, seats)
VALUES
    ('free', 'Free', 5, false, false, false, 1),
    ('pro', 'Pro', 100, true, true, true, 1),
    ('team', 'Team', 500, true, true, true, 5)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS subscriptions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID NOT NULL REFERENCES users (id),
    plan_id                  TEXT NOT NULL REFERENCES plans (id),
    status                   TEXT NOT NULL DEFAULT 'active',
    provider                 TEXT,
    provider_subscription_id TEXT,
    current_period_end       TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id ON subscriptions (user_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_active_user
    ON subscriptions (user_id)
    WHERE status IN ('active', 'trialing', 'past_due');

CREATE TABLE IF NOT EXISTS usage_events (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID REFERENCES users (id),
    ip_hash            TEXT,
    kind               TEXT NOT NULL,
    cost_inr_estimate  NUMERIC(10, 2) NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_usage_events_user_created
    ON usage_events (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_usage_events_ip_created
    ON usage_events (ip_hash, created_at DESC)
    WHERE ip_hash IS NOT NULL;

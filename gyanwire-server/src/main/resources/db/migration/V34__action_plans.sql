CREATE TABLE IF NOT EXISTS news_briefs (
    id UUID PRIMARY KEY,
    url_hash TEXT NOT NULL UNIQUE,
    headline TEXT,
    publisher_url TEXT,
    brief_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS action_plans (
    id UUID PRIMARY KEY,
    user_id UUID NULL REFERENCES users (id),
    news_url_hash TEXT,
    type TEXT NOT NULL,
    format TEXT NOT NULL,
    label TEXT,
    plan_json JSONB NOT NULL,
    lint_passed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE IF NOT EXISTS action_plan_steps (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES action_plans (id) ON DELETE CASCADE,
    step_key TEXT NOT NULL,
    done_at TIMESTAMPTZ NULL,
    UNIQUE (plan_id, step_key)
);

CREATE INDEX IF NOT EXISTS action_plans_user_created_idx ON action_plans (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS news_briefs_url_hash_idx ON news_briefs (url_hash);

ALTER TABLE plans ADD COLUMN IF NOT EXISTS daily_action_plan_limit INTEGER NOT NULL DEFAULT 5;
UPDATE plans SET daily_action_plan_limit = daily_search_limit WHERE daily_action_plan_limit IS NULL OR daily_action_plan_limit = 5;
UPDATE plans SET daily_action_plan_limit = daily_search_limit;

SELECT gyanwire_lock('news_briefs');
SELECT gyanwire_lock('action_plans');
SELECT gyanwire_lock('action_plan_steps');

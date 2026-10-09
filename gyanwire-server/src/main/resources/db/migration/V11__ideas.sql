CREATE TABLE IF NOT EXISTS idea_runs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    url TEXT NOT NULL,
    url_hash TEXT NOT NULL,
    title TEXT,
    industry TEXT,
    signals JSONB,
    prompt_version TEXT NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS ideas (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES idea_runs (id),
    user_id UUID NOT NULL REFERENCES users (id),
    score NUMERIC NOT NULL,
    breakdown JSONB,
    title TEXT,
    why TEXT,
    body JSONB,
    pattern_ids TEXT[],
    status TEXT,
    feedback SMALLINT,
    tried BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE plans ADD COLUMN IF NOT EXISTS daily_idea_limit INTEGER NOT NULL DEFAULT 3;
ALTER TABLE plans ADD COLUMN IF NOT EXISTS can_roadmap BOOLEAN NOT NULL DEFAULT false;
UPDATE plans SET daily_idea_limit = 3, can_roadmap = false WHERE id = 'free';
UPDATE plans SET daily_idea_limit = 30, can_roadmap = true WHERE id = 'pro';
UPDATE plans SET daily_idea_limit = 150, can_roadmap = true WHERE id = 'team';
SELECT gyanwire_lock('idea_runs');
SELECT gyanwire_lock('ideas');

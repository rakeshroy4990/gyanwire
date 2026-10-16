-- N2 ratings, N5 tool step effects, N6 step timings.

CREATE TABLE IF NOT EXISTS idea_ratings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    idea_key TEXT NOT NULL,
    rating TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, idea_key)
);

CREATE TABLE IF NOT EXISTS tool_step_effects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tool_id TEXT NOT NULL,
    step_key TEXT NOT NULL,
    format TEXT NOT NULL DEFAULT 'short_vertical',
    minutes INT NOT NULL,
    basis TEXT NOT NULL DEFAULT 'estimate',
    n INT NOT NULL DEFAULT 0,
    confidence NUMERIC(4,2) NULL,
    notes TEXT NULL,
    UNIQUE (tool_id, step_key, format)
);

CREATE TABLE IF NOT EXISTS step_timings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan_id UUID NOT NULL REFERENCES action_plans (id) ON DELETE CASCADE,
    step_key TEXT NOT NULL,
    tool_id TEXT NULL,
    minutes_actual INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_step_timings_tool_step
    ON step_timings (tool_id, step_key, created_at DESC);

SELECT gyanwire_lock('idea_ratings');
SELECT gyanwire_lock('tool_step_effects');
SELECT gyanwire_lock('step_timings');

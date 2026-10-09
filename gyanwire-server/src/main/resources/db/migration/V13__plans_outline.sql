CREATE TABLE IF NOT EXISTS weekly_plans (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    idea_id UUID,
    hours_per_week INTEGER,
    lighter BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS weekly_tasks (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES weekly_plans (id),
    week_no INTEGER NOT NULL,
    outcome TEXT,
    tasks JSONB,
    metric TEXT,
    done_state TEXT
);
CREATE TABLE IF NOT EXISTS business_outlines (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    idea_id UUID,
    content JSONB NOT NULL,
    version TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
SELECT gyanwire_lock('weekly_plans');
SELECT gyanwire_lock('weekly_tasks');
SELECT gyanwire_lock('business_outlines');

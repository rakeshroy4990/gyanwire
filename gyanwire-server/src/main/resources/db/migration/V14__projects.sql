CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS project_items (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    kind TEXT NOT NULL,
    ref JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE plans ADD COLUMN IF NOT EXISTS project_limit INTEGER;
UPDATE plans SET project_limit = 1 WHERE id = 'free';
UPDATE plans SET project_limit = 20 WHERE id = 'pro';
UPDATE plans SET project_limit = NULL WHERE id = 'team';
SELECT gyanwire_lock('projects');
SELECT gyanwire_lock('project_items');

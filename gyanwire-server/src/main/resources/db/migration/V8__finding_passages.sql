CREATE TABLE IF NOT EXISTS finding_passages (
    id UUID PRIMARY KEY,
    search_key TEXT,
    finding_id TEXT,
    url TEXT,
    passage_text TEXT,
    citation_no INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS finding_feedback (
    id UUID PRIMARY KEY,
    user_id UUID,
    url TEXT NOT NULL,
    vote SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE plans ADD COLUMN IF NOT EXISTS daily_brief_limit INTEGER NOT NULL DEFAULT 1;
UPDATE plans SET daily_brief_limit = 100 WHERE id = 'pro';
UPDATE plans SET daily_brief_limit = 500 WHERE id = 'team';
SELECT gyanwire_lock('finding_passages');
SELECT gyanwire_lock('finding_feedback');

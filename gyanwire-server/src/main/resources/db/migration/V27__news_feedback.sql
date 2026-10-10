CREATE TABLE IF NOT EXISTS news_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID NULL REFERENCES news_items (id),
    user_id UUID NULL,
    anon_hash TEXT NULL,
    vote SMALLINT NOT NULL,
    reason TEXT NULL,
    industry TEXT NULL,
    signal_type TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_news_feedback_industry
    ON news_feedback (industry, signal_type, created_at DESC);

SELECT gyanwire_lock('news_feedback');

-- Per-viewer news dismissals so used items leave the feed.
-- AI query packs widen to 30 days so daily launches can still fill from the month.

CREATE TABLE IF NOT EXISTS news_dismissals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID NULL REFERENCES news_items (id),
    url TEXT NULL,
    user_id UUID NULL,
    anon_hash TEXT NULL,
    industry TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT news_dismissals_viewer CHECK (user_id IS NOT NULL OR anon_hash IS NOT NULL),
    CONSTRAINT news_dismissals_target CHECK (item_id IS NOT NULL OR (url IS NOT NULL AND btrim(url) <> ''))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_news_dismissals_user_item
    ON news_dismissals (user_id, item_id)
    WHERE user_id IS NOT NULL AND item_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_news_dismissals_anon_item
    ON news_dismissals (anon_hash, item_id)
    WHERE anon_hash IS NOT NULL AND item_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_news_dismissals_user_url
    ON news_dismissals (user_id, url)
    WHERE user_id IS NOT NULL AND item_id IS NULL AND url IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_news_dismissals_anon_url
    ON news_dismissals (anon_hash, url)
    WHERE anon_hash IS NOT NULL AND item_id IS NULL AND url IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_news_dismissals_user
    ON news_dismissals (user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_news_dismissals_anon
    ON news_dismissals (anon_hash, created_at DESC)
    WHERE anon_hash IS NOT NULL;

UPDATE news_query_packs
SET query = replace(query, 'when:7d', 'when:30d'),
    window_days = 30
WHERE industry = 'AI'
  AND enabled = TRUE
  AND query LIKE '%when:7d%';

SELECT gyanwire_lock('news_dismissals');

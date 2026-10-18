CREATE TABLE IF NOT EXISTS news_idea_cache (
    id UUID PRIMARY KEY,
    url_hash TEXT NOT NULL UNIQUE,
    ideas_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS news_idea_cache_url_hash_idx ON news_idea_cache (url_hash);

SELECT gyanwire_lock('news_idea_cache');

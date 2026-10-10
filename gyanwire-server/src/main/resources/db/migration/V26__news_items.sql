CREATE TABLE IF NOT EXISTS news_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    industry TEXT NOT NULL,
    sub TEXT NULL,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    domain TEXT NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT TRUE,
    source_id UUID REFERENCES news_sources (id),
    published_at TIMESTAMPTZ NOT NULL,
    date_estimated BOOLEAN NOT NULL DEFAULT FALSE,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    summary TEXT NULL,
    signal_type TEXT NULL,
    india_relevance SMALLINT NULL,
    specificity SMALLINT NULL,
    opportunity_score SMALLINT NULL,
    why_idea TEXT NULL,
    title_hash TEXT NOT NULL,
    simhash BIGINT NULL,
    also_covered_by TEXT[] NULL,
    sample BOOLEAN NOT NULL DEFAULT FALSE,
    scored_by TEXT NULL,
    language TEXT NULL,
    UNIQUE (url)
);

CREATE INDEX IF NOT EXISTS idx_news_items_feed
    ON news_items (industry, published_at DESC);

CREATE INDEX IF NOT EXISTS idx_news_items_title
    ON news_items (industry, title_hash, published_at DESC);

CREATE TABLE IF NOT EXISTS news_ingest_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID REFERENCES news_sources (id),
    industry TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    fetched INT NOT NULL DEFAULT 0,
    inserted INT NOT NULL DEFAULT 0,
    deduped INT NOT NULL DEFAULT 0,
    dropped INT NOT NULL DEFAULT 0,
    drop_reasons TEXT NULL,
    error TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_news_ingest_runs_source
    ON news_ingest_runs (source_id, started_at DESC);

SELECT gyanwire_lock('news_items');
SELECT gyanwire_lock('news_ingest_runs');

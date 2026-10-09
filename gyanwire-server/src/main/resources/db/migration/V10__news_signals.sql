CREATE TABLE IF NOT EXISTS news_signals (
    id UUID PRIMARY KEY,
    url_hash TEXT NOT NULL UNIQUE,
    signal JSONB NOT NULL,
    extracted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
SELECT gyanwire_lock('news_signals');

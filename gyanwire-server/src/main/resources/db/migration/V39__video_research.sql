CREATE TABLE IF NOT EXISTS video_research (
    id UUID PRIMARY KEY,
    url_hash TEXT NOT NULL UNIQUE,
    news_id UUID NULL,
    status TEXT NOT NULL,
    summary_json JSONB NULL,
    error TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT video_research_status_chk CHECK (status IN ('running', 'ready', 'failed'))
);

CREATE INDEX IF NOT EXISTS video_research_url_hash_idx ON video_research (url_hash);
CREATE INDEX IF NOT EXISTS video_research_news_id_idx ON video_research (news_id);

SELECT gyanwire_lock('video_research');

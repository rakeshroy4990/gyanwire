-- Relevance gate: pack signals on items, hide source per user, stop Google News browse.

ALTER TABLE news_items
    ADD COLUMN IF NOT EXISTS relevance_score SMALLINT NULL,
    ADD COLUMN IF NOT EXISTS relevance_reason TEXT NULL,
    ADD COLUMN IF NOT EXISTS signals JSONB NULL;

CREATE TABLE IF NOT EXISTS user_hidden_sources (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_id UUID NOT NULL REFERENCES news_sources (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, source_id)
);

CREATE TABLE IF NOT EXISTS news_relevance_cache (
    url_hash TEXT PRIMARY KEY,
    verdict TEXT NOT NULL,
    reason TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Allowlist-only browse: disable Google News search_feed for product feed.
UPDATE news_sources
SET enabled = FALSE,
    last_error = COALESCE(last_error, 'Disabled: allowlist-only feed (N1)')
WHERE kind = 'search_feed'
  AND enabled = TRUE;

-- Thin Astrology pack: lifestyle horoscope RSS is often junk — keep society/trend only if named.
UPDATE news_sources
SET enabled = FALSE,
    last_error = COALESCE(last_error, 'Disabled: thin astrology pack (N1)')
WHERE industry = 'Astrology'
  AND enabled = TRUE
  AND (
    name ILIKE '%Astrology%'
    OR name ILIKE '%horoscope%'
  );

-- AI / Real Estate had only Google News; seed allowlisted RSS so browse is not empty.
INSERT INTO news_sources (industry, sub, kind, name, url_template, region, weight, enabled, last_error)
SELECT v.industry, NULL, v.kind, v.name, v.url_template, v.region, v.weight, TRUE, NULL
FROM (VALUES
    ('AI', 'rss', 'TechCrunch AI', 'https://techcrunch.com/category/artificial-intelligence/feed/', 'GLOBAL', 1.30),
    ('AI', 'rss', 'The Verge AI', 'https://www.theverge.com/rss/ai-artificial-intelligence/index.xml', 'GLOBAL', 1.20),
    ('AI', 'rss', 'Inc42', 'https://inc42.com/feed/', 'IN', 1.10),
    ('AI', 'rss', 'Mint Technology', 'https://www.livemint.com/rss/technology', 'IN', 1.05),
    ('Real Estate', 'rss', 'Economic Times Realty', 'https://economictimes.indiatimes.com/industry/services/property-/cstruction/rssfeeds/2647163.cms', 'IN', 1.30),
    ('Real Estate', 'rss', 'Mint', 'https://www.livemint.com/rss/money', 'IN', 1.20),
    ('Real Estate', 'rss', 'The Hindu Business', 'https://www.thehindu.com/business/feeder/default.rss', 'IN', 1.15),
    ('Real Estate', 'rss', 'Inc42', 'https://inc42.com/feed/', 'IN', 1.05)
) AS v(industry, kind, name, url_template, region, weight)
WHERE NOT EXISTS (
    SELECT 1 FROM news_sources s
    WHERE s.industry = v.industry AND s.name = v.name AND s.kind = v.kind
);

SELECT gyanwire_lock('user_hidden_sources');
SELECT gyanwire_lock('news_relevance_cache');

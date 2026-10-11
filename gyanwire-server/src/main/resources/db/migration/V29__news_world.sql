-- Worldwide findings, with India query packs left in place.
-- Search feeds for the world edition use region GLOBAL so they do not consume India queries.

ALTER TABLE news_items ADD COLUMN IF NOT EXISTS country TEXT NULL;

ALTER TABLE news_query_packs ADD COLUMN IF NOT EXISTS region TEXT NOT NULL DEFAULT 'IN';

CREATE INDEX IF NOT EXISTS idx_news_query_packs_region
    ON news_query_packs (industry, region, enabled);

INSERT INTO news_sources (industry, sub, kind, name, url_template, region, weight, enabled, last_error)
VALUES
    ('Share Market', NULL, 'search_feed', 'Google News World', 'https://news.google.com/rss/search?q={query}&hl=en-US&gl=US&ceid=US:en', 'GLOBAL', 1.15, TRUE, NULL),
    ('Share Market', NULL, 'rss', 'BBC Business', 'https://feeds.bbci.co.uk/news/business/rss.xml', 'GLOBAL', 0.90, TRUE, NULL),
    ('IT', NULL, 'search_feed', 'Google News World', 'https://news.google.com/rss/search?q={query}&hl=en-US&gl=US&ceid=US:en', 'GLOBAL', 1.15, TRUE, NULL),
    ('IT', NULL, 'rss', 'BBC Technology', 'https://feeds.bbci.co.uk/news/technology/rss.xml', 'GLOBAL', 0.90, TRUE, NULL),
    ('Medical', NULL, 'search_feed', 'Google News World', 'https://news.google.com/rss/search?q={query}&hl=en-US&gl=US&ceid=US:en', 'GLOBAL', 1.15, TRUE, NULL),
    ('Medical', NULL, 'rss', 'BBC Health', 'https://feeds.bbci.co.uk/news/health/rss.xml', 'GLOBAL', 0.90, TRUE, NULL),
    ('Space', NULL, 'search_feed', 'Google News World', 'https://news.google.com/rss/search?q={query}&hl=en-US&gl=US&ceid=US:en', 'GLOBAL', 1.15, TRUE, NULL),
    ('Social Media', NULL, 'search_feed', 'Google News World', 'https://news.google.com/rss/search?q={query}&hl=en-US&gl=US&ceid=US:en', 'GLOBAL', 1.15, TRUE, NULL),
    ('Gaming', NULL, 'search_feed', 'Google News World', 'https://news.google.com/rss/search?q={query}&hl=en-US&gl=US&ceid=US:en', 'GLOBAL', 1.15, TRUE, NULL),
    ('Astrology', NULL, 'search_feed', 'Google News World', 'https://news.google.com/rss/search?q={query}&hl=en-US&gl=US&ceid=US:en', 'GLOBAL', 1.15, TRUE, NULL);

INSERT INTO news_query_packs (industry, sub, signal_type, query, window_days, region)
VALUES
    ('Share Market', NULL, 'launch', 'stock exchange (launches OR unveils OR "rolls out") product when:7d', 7, 'GLOBAL'),
    ('Share Market', NULL, 'funding', 'fintech (raises OR funding OR "series a") when:7d', 7, 'GLOBAL'),
    ('Share Market', NULL, 'approval', '(SEC OR FCA OR ESMA) (approves OR guidelines OR rules) when:7d', 7, 'GLOBAL'),
    ('Share Market', NULL, 'pricing', 'brokerage ("price cut" OR fee OR commission) when:7d', 7, 'GLOBAL'),

    ('IT', NULL, 'launch', 'startup (launches OR unveils OR introduces OR "rolls out") software when:7d', 7, 'GLOBAL'),
    ('IT', NULL, 'funding', 'startup (raises OR funding OR seed OR "series a") when:7d', 7, 'GLOBAL'),
    ('IT', NULL, 'launch', 'AI (launches OR unveils OR introduces) model OR product when:7d', 7, 'GLOBAL'),
    ('IT', NULL, 'pricing', 'cloud OR software ("price cut" OR pricing) when:7d', 7, 'GLOBAL'),

    ('Medical', NULL, 'launch', 'medical (device OR drug) (launches OR unveils OR approved OR FDA OR EMA) when:7d', 7, 'GLOBAL'),
    ('Medical', NULL, 'funding', 'healthtech (raises OR funding OR "series a") when:7d', 7, 'GLOBAL'),
    ('Medical', NULL, 'approval', '(FDA OR EMA) (approved OR approval OR clearance) (device OR drug) when:7d', 7, 'GLOBAL'),
    ('Medical', NULL, 'pricing', '(drug OR medicine) (shortage OR recall OR "price cut") when:7d', 7, 'GLOBAL'),

    ('Space', NULL, 'launch', '(NASA OR SpaceX OR ESA OR satellite OR rocket) (launches OR launch) when:7d', 7, 'GLOBAL'),
    ('Space', NULL, 'funding', 'space startup (raises OR funding) when:7d', 7, 'GLOBAL'),

    ('Social Media', NULL, 'launch', '(Instagram OR YouTube OR TikTok OR WhatsApp) (launches OR unveils OR "rolls out") when:7d', 7, 'GLOBAL'),
    ('Social Media', NULL, 'approval', 'social media (regulation OR ban OR guidelines) when:7d', 7, 'GLOBAL'),
    ('Social Media', NULL, 'funding', 'creator OR social startup (raises OR funding) when:7d', 7, 'GLOBAL'),

    ('Gaming', NULL, 'launch', '(game OR gaming) (launches OR unveils OR "rolls out") when:7d', 7, 'GLOBAL'),
    ('Gaming', NULL, 'funding', 'gaming (studio OR startup) (raises OR funding OR "series a") when:7d', 7, 'GLOBAL'),
    ('Gaming', NULL, 'pricing', 'game (pricing OR "price cut" OR ban) when:7d', 7, 'GLOBAL'),

    ('Astrology', NULL, 'launch', '(astrology OR horoscope) (app OR launches OR unveils) when:7d', 7, 'GLOBAL');

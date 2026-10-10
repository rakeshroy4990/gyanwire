-- News source registry and signal query packs. Feeds were checked on 2026-10-10.
-- A row stays disabled when the URL did not return a parseable feed.

CREATE TABLE IF NOT EXISTS news_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    industry TEXT NOT NULL,
    sub TEXT NULL,
    kind TEXT NOT NULL,
    name TEXT NOT NULL,
    url_template TEXT NOT NULL,
    region TEXT NOT NULL DEFAULT 'IN',
    weight NUMERIC(4,2) NOT NULL DEFAULT 1.0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    poll_minutes INT NOT NULL DEFAULT 60,
    last_ok_at TIMESTAMPTZ NULL,
    last_error TEXT NULL,
    etag TEXT NULL,
    last_modified TEXT NULL,
    consecutive_failures INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_news_sources_industry
    ON news_sources (industry, enabled, weight DESC);

CREATE TABLE IF NOT EXISTS news_query_packs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    industry TEXT NOT NULL,
    sub TEXT NULL,
    signal_type TEXT NOT NULL,
    query TEXT NOT NULL,
    window_days INT NOT NULL DEFAULT 7,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_news_query_packs_pick
    ON news_query_packs (industry, enabled, last_used_at NULLS FIRST);

INSERT INTO news_sources (industry, sub, kind, name, url_template, region, weight, enabled, last_error)
VALUES
    ('Share Market', NULL, 'rss', 'SEBI', 'https://www.sebi.gov.in/sebirss.xml', 'IN', 1.50, TRUE, NULL),
    ('Share Market', NULL, 'rss', 'RBI', 'https://www.rbi.org.in/pressreleases_rss.xml', 'IN', 1.40, TRUE, NULL),
    ('Share Market', NULL, 'rss', 'Economic Times Markets', 'https://economictimes.indiatimes.com/markets/rssfeeds/1977021501.cms', 'IN', 1.30, TRUE, NULL),
    ('Share Market', NULL, 'rss', 'Mint Markets', 'https://www.livemint.com/rss/markets', 'IN', 1.25, TRUE, NULL),
    ('Share Market', NULL, 'rss', 'The Hindu Markets', 'https://www.thehindu.com/business/markets/feeder/default.rss', 'IN', 1.20, TRUE, NULL),
    ('Share Market', NULL, 'rss', 'Business Line Markets', 'https://www.thehindubusinessline.com/markets/feeder/default.rss', 'IN', 1.15, TRUE, NULL),
    ('Share Market', NULL, 'search_feed', 'Google News', 'https://news.google.com/rss/search?q={query}&hl=en-IN&gl=IN&ceid=IN:en', 'IN', 1.00, TRUE, NULL),
    ('Share Market', NULL, 'rss', 'Moneycontrol', 'https://www.moneycontrol.com/rss/marketreports.xml', 'IN', 1.00, FALSE, 'HTTP 403 on 2026-10-10'),
    ('Share Market', NULL, 'rss', 'Business Standard', 'https://www.business-standard.com/rss/markets-106.rss', 'IN', 1.00, FALSE, 'HTTP 403 on 2026-10-10'),

    ('IT', NULL, 'rss', 'Inc42', 'https://inc42.com/feed/', 'IN', 1.40, TRUE, NULL),
    ('IT', NULL, 'rss', 'YourStory', 'https://yourstory.com/feed', 'IN', 1.35, TRUE, NULL),
    ('IT', NULL, 'rss', 'Mint Technology', 'https://www.livemint.com/rss/technology', 'IN', 1.30, TRUE, NULL),
    ('IT', NULL, 'rss', 'The Hindu Technology', 'https://www.thehindu.com/sci-tech/technology/feeder/default.rss', 'IN', 1.25, TRUE, NULL),
    ('IT', NULL, 'rss', 'Business Line Info-tech', 'https://www.thehindubusinessline.com/info-tech/feeder/default.rss', 'IN', 1.20, TRUE, NULL),
    ('IT', NULL, 'rss', 'Indian Express Technology', 'https://indianexpress.com/section/technology/feed/', 'IN', 1.15, TRUE, NULL),
    ('IT', NULL, 'rss', 'MediaNama', 'https://www.medianama.com/feed/', 'IN', 1.10, TRUE, NULL),
    ('IT', NULL, 'search_feed', 'Google News', 'https://news.google.com/rss/search?q={query}&hl=en-IN&gl=IN&ceid=IN:en', 'IN', 1.00, TRUE, NULL),
    ('IT', NULL, 'rss', 'TechCrunch', 'https://techcrunch.com/feed/', 'GLOBAL', 0.80, TRUE, NULL),

    ('Medical', NULL, 'rss', 'Economic Times Healthcare', 'https://economictimes.indiatimes.com/industry/healthcare/biotech/healthcare/rssfeeds/13358050.cms', 'IN', 1.40, TRUE, NULL),
    ('Medical', NULL, 'rss', 'The Hindu Health', 'https://www.thehindu.com/sci-tech/health/feeder/default.rss', 'IN', 1.35, TRUE, NULL),
    ('Medical', NULL, 'rss', 'Indian Express Health', 'https://indianexpress.com/section/lifestyle/health/feed/', 'IN', 1.30, TRUE, NULL),
    ('Medical', NULL, 'rss', 'Times of India Health', 'https://timesofindia.indiatimes.com/rssfeeds/3908999.cms', 'IN', 1.20, TRUE, NULL),
    ('Medical', NULL, 'search_feed', 'Google News', 'https://news.google.com/rss/search?q={query}&hl=en-IN&gl=IN&ceid=IN:en', 'IN', 1.10, TRUE, NULL),
    ('Medical', NULL, 'rss', 'WHO News', 'https://www.who.int/rss-feeds/news-english.xml', 'GLOBAL', 0.90, TRUE, NULL),
    ('Medical', NULL, 'rss', 'MedicalXpress', 'https://medicalxpress.com/rss-feed/', 'GLOBAL', 0.85, TRUE, NULL),
    ('Medical', NULL, 'rss', 'CDSCO', 'https://cdsco.gov.in/opencms/opencms/en/Notifications/', 'IN', 1.00, FALSE, 'No public RSS on 2026-10-10'),

    ('Space', NULL, 'rss', 'The Hindu Science', 'https://www.thehindu.com/sci-tech/science/feeder/default.rss', 'IN', 1.40, TRUE, NULL),
    ('Space', NULL, 'rss', 'Indian Express Science', 'https://indianexpress.com/section/technology/science/feed/', 'IN', 1.30, TRUE, NULL),
    ('Space', NULL, 'search_feed', 'Google News', 'https://news.google.com/rss/search?q={query}&hl=en-IN&gl=IN&ceid=IN:en', 'IN', 1.20, TRUE, NULL),
    ('Space', NULL, 'rss', 'NASA', 'https://www.nasa.gov/feed/', 'GLOBAL', 1.10, TRUE, NULL),
    ('Space', NULL, 'rss', 'ESA Space News', 'https://www.esa.int/rssfeed/Our_Activities/Space_News', 'GLOBAL', 1.00, TRUE, NULL),
    ('Space', NULL, 'atom', 'Space.com', 'https://www.space.com/feeds/all', 'GLOBAL', 0.95, TRUE, NULL),
    ('Space', NULL, 'rss', 'ISRO', 'https://www.isro.gov.in/rss/news.xml', 'IN', 1.50, FALSE, 'HTTP 404 on 2026-10-10'),

    ('Social Media', NULL, 'rss', 'MediaNama', 'https://www.medianama.com/feed/', 'IN', 1.40, TRUE, NULL),
    ('Social Media', NULL, 'rss', 'Inc42', 'https://inc42.com/feed/', 'IN', 1.30, TRUE, NULL),
    ('Social Media', NULL, 'rss', 'Mint Technology', 'https://www.livemint.com/rss/technology', 'IN', 1.25, TRUE, NULL),
    ('Social Media', NULL, 'search_feed', 'Google News', 'https://news.google.com/rss/search?q={query}&hl=en-IN&gl=IN&ceid=IN:en', 'IN', 1.10, TRUE, NULL),
    ('Social Media', NULL, 'rss', 'The Verge', 'https://www.theverge.com/rss/index.xml', 'GLOBAL', 0.90, TRUE, NULL),
    ('Social Media', NULL, 'rss', 'TechCrunch', 'https://techcrunch.com/feed/', 'GLOBAL', 0.85, TRUE, NULL),

    ('Gaming', NULL, 'search_feed', 'Google News', 'https://news.google.com/rss/search?q={query}&hl=en-IN&gl=IN&ceid=IN:en', 'IN', 1.40, TRUE, NULL),
    ('Gaming', NULL, 'rss', 'The Hindu Technology', 'https://www.thehindu.com/sci-tech/technology/feeder/default.rss', 'IN', 1.25, TRUE, NULL),
    ('Gaming', NULL, 'rss', 'Polygon', 'https://www.polygon.com/rss/index.xml', 'GLOBAL', 1.20, TRUE, NULL),
    ('Gaming', NULL, 'rss', 'GameSpot', 'https://www.gamespot.com/feeds/mashup/', 'GLOBAL', 1.15, TRUE, NULL),
    ('Gaming', NULL, 'rss', 'GamesIndustry.biz', 'https://www.gamesindustry.biz/feed', 'GLOBAL', 1.10, TRUE, NULL),
    ('Gaming', NULL, 'rss', 'PC Gamer', 'https://www.pcgamer.com/rss/', 'GLOBAL', 1.05, TRUE, NULL),

    ('Astrology', NULL, 'rss', 'Hindustan Times Astrology', 'https://www.hindustantimes.com/feeds/rss/astrology/rssfeed.xml', 'IN', 1.40, TRUE, NULL),
    ('Astrology', NULL, 'rss', 'Times of India Astrology', 'https://timesofindia.indiatimes.com/rssfeeds/65857041.cms', 'IN', 1.35, TRUE, NULL),
    ('Astrology', NULL, 'rss', 'The Hindu Society', 'https://www.thehindu.com/society/feeder/default.rss', 'IN', 1.20, TRUE, NULL),
    ('Astrology', NULL, 'rss', 'The Hindu Lifestyle', 'https://www.thehindu.com/life-and-style/feeder/default.rss', 'IN', 1.15, TRUE, NULL),
    ('Astrology', NULL, 'rss', 'Times of India Lifestyle', 'https://timesofindia.indiatimes.com/rssfeeds/2886704.cms', 'IN', 1.10, TRUE, NULL),
    ('Astrology', NULL, 'search_feed', 'Google News', 'https://news.google.com/rss/search?q={query}&hl=en-IN&gl=IN&ceid=IN:en', 'IN', 1.00, TRUE, NULL);

INSERT INTO news_query_packs (industry, sub, signal_type, query, window_days)
VALUES
    ('Share Market', NULL, 'launch', 'stock exchange launches OR unveils OR "rolls out" product India when:7d', 7),
    ('Share Market', NULL, 'funding', 'fintech raises OR funding OR "series a" India when:7d', 7),
    ('Share Market', NULL, 'approval', 'SEBI (circular OR approves OR guidelines OR notification) when:7d', 7),
    ('Share Market', NULL, 'pricing', 'brokerage ("price cut" OR fee OR shortage) India when:7d', 7),
    ('Share Market', NULL, 'india', 'SEBI OR NSE OR BSE (launch OR circular OR product) India when:7d', 7),
    ('Share Market', 'Banking', 'launch', 'bank launches OR unveils (app OR card OR UPI) India when:7d', 7),
    ('Share Market', 'Banking', 'approval', 'RBI (circular OR notification OR approves) bank when:7d', 7),
    ('Share Market', 'IT', 'funding', 'IT stock OR software (raises OR funding OR "order win") India when:7d', 7),

    ('IT', NULL, 'launch', 'startup (launches OR unveils OR introduces OR "rolls out") India software when:7d', 7),
    ('IT', NULL, 'funding', 'startup (raises OR funding OR seed OR "series a") India when:7d', 7),
    ('IT', NULL, 'approval', 'MeitY OR CERT-In (notification OR guidelines OR approves) when:7d', 7),
    ('IT', NULL, 'pricing', 'cloud OR software ("price cut" OR pricing OR shortage) India when:7d', 7),
    ('IT', NULL, 'india', 'Indian startup (launch OR raises) Bengaluru OR Hyderabad when:7d', 7),
    ('IT', 'AI', 'launch', 'AI (launches OR unveils OR introduces) model OR product India when:7d', 7),
    ('IT', 'AI', 'funding', 'AI startup (raises OR funding OR "series a") India when:7d', 7),

    ('Medical', NULL, 'launch', 'medical (device OR drug) (launches OR unveils OR "rolls out") India when:7d', 7),
    ('Medical', NULL, 'funding', 'healthtech (raises OR funding OR "series a") India when:7d', 7),
    ('Medical', NULL, 'approval', 'CDSCO OR "drug controller" (approved OR approval OR clearance) India when:7d', 7),
    ('Medical', NULL, 'pricing', '(drug OR medicine) (shortage OR recall OR "price cut") India when:7d', 7),
    ('Medical', NULL, 'india', 'CDSCO OR ICMR OR AIIMS (approval OR launch OR device) India when:7d', 7),
    ('Medical', 'Paediatrics', 'launch', '(paediatric OR pediatric) (launches OR unveils OR introduces OR "rolls out") (vaccine OR device OR drug) when:7d', 7),
    ('Medical', 'Paediatrics', 'funding', '(paediatric OR pediatric) (raises OR funding OR seed OR "series a") when:7d', 7),
    ('Medical', 'Paediatrics', 'approval', '(paediatric OR pediatric) (approved OR approval OR CDSCO OR "drug controller") when:7d', 7),
    ('Medical', 'Paediatrics', 'pricing', '(paediatric OR pediatric) (shortage OR recall OR "price cut" OR ban) when:7d', 7),
    ('Medical', 'Paediatrics', 'india', '(paediatric OR pediatric) (India OR CDSCO OR ICMR) (vaccine OR device OR drug) when:7d', 7),
    ('Medical', 'Cardiology', 'launch', 'cardiology (stent OR device OR drug) (launches OR unveils OR approved) India when:7d', 7),
    ('Medical', 'Cardiology', 'approval', 'cardiac (CDSCO OR approved OR clearance) device India when:7d', 7),

    ('Space', NULL, 'launch', '(ISRO OR satellite OR rocket) (launches OR launch) India when:7d', 7),
    ('Space', NULL, 'funding', 'space startup (raises OR funding) India when:7d', 7),
    ('Space', NULL, 'approval', 'IN-SPACe OR ISRO (approves OR authorization OR guidelines) when:7d', 7),
    ('Space', NULL, 'pricing', 'satellite (pricing OR "price cut" OR shortage) launch India when:7d', 7),
    ('Space', NULL, 'india', 'ISRO OR "Indian space" (launch OR satellite OR startup) when:7d', 7),

    ('Social Media', NULL, 'launch', '(Instagram OR YouTube OR WhatsApp) (launches OR unveils OR "rolls out") India when:7d', 7),
    ('Social Media', NULL, 'funding', 'creator OR social startup (raises OR funding) India when:7d', 7),
    ('Social Media', NULL, 'approval', 'IT rules OR MeitY (social media OR intermediary) (notification OR guidelines) when:7d', 7),
    ('Social Media', NULL, 'pricing', 'social media (advertising OR "price cut" OR ban) India when:7d', 7),
    ('Social Media', NULL, 'india', 'Indian creators (launch OR monetisation OR platform) when:7d', 7),

    ('Gaming', NULL, 'launch', '(game OR gaming) (launches OR unveils OR "rolls out") India mobile when:7d', 7),
    ('Gaming', NULL, 'funding', 'gaming startup (raises OR funding OR "series a") India when:7d', 7),
    ('Gaming', NULL, 'approval', 'gaming (MeitY OR "online gaming" OR guidelines OR notification) India when:7d', 7),
    ('Gaming', NULL, 'pricing', 'game (pricing OR "price cut" OR ban) India when:7d', 7),
    ('Gaming', NULL, 'india', 'Indian (gaming OR esports) (launch OR studio OR raises) when:7d', 7),

    ('Astrology', NULL, 'launch', '(astrology OR horoscope OR vastu) (app OR launches OR unveils) India when:7d', 7),
    ('Astrology', NULL, 'funding', 'astrology (startup OR app) (raises OR funding) India when:7d', 7),
    ('Astrology', NULL, 'approval', 'astrology (guidelines OR advertising OR notification) India when:7d', 7),
    ('Astrology', NULL, 'pricing', 'astrology (pricing OR subscription OR "price cut") app India when:7d', 7),
    ('Astrology', NULL, 'india', 'Indian (astrology OR vastu OR horoscope) (app OR launch) when:7d', 7);

SELECT gyanwire_lock('news_sources');
SELECT gyanwire_lock('news_query_packs');

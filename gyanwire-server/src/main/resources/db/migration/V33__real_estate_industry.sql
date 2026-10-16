-- Real Estate research section: housing, commercial, PropTech, construction, and RERA.

INSERT INTO research_industries (name, label, sort_order)
VALUES ('Real Estate', 'Real Estate products', 15)
ON CONFLICT (name) DO UPDATE
SET label = EXCLUDED.label,
    sort_order = EXCLUDED.sort_order,
    deleted = FALSE,
    updated_at = NOW();

INSERT INTO research_industry_subs (industry_id, name, label, sort_order)
SELECT i.id, s.name, s.label, s.sort_order
FROM research_industries i
JOIN (
    VALUES
        ('Real Estate', 'Residential', 'Residential products', 10),
        ('Real Estate', 'Commercial', 'Commercial products', 20),
        ('Real Estate', 'PropTech', 'PropTech products', 30),
        ('Real Estate', 'Construction', 'Construction products', 40),
        ('Real Estate', 'RERA', 'RERA products', 50)
) AS s(industry_name, name, label, sort_order)
  ON i.name = s.industry_name
ON CONFLICT (industry_id, name) DO UPDATE
SET label = EXCLUDED.label,
    sort_order = EXCLUDED.sort_order,
    deleted = FALSE,
    updated_at = NOW();

INSERT INTO news_sources (industry, sub, kind, name, url_template, region, weight, enabled, last_error)
VALUES
    ('Real Estate', NULL, 'search_feed', 'Google News', 'https://news.google.com/rss/search?q={query}&hl=en-IN&gl=IN&ceid=IN:en', 'IN', 1.20, TRUE, NULL),
    ('Real Estate', NULL, 'search_feed', 'Google News World', 'https://news.google.com/rss/search?q={query}&hl=en-US&gl=US&ceid=US:en', 'GLOBAL', 1.15, TRUE, NULL);

INSERT INTO news_query_packs (industry, sub, signal_type, query, window_days, region)
VALUES
    ('Real Estate', NULL, 'launch', '(housing OR "real estate" OR apartment OR villa) (launches OR unveils OR "rolls out" OR "opens booking") when:30d', 30, 'IN'),
    ('Real Estate', NULL, 'funding', '(PropTech OR "real estate" OR housing) (raises OR funding OR "series a" OR investment) India when:30d', 30, 'IN'),
    ('Real Estate', NULL, 'approval', '(RERA OR "housing board" OR "town planning") (approves OR clearance OR notification OR guidelines) India when:30d', 30, 'IN'),
    ('Real Estate', NULL, 'pricing', '(home loan OR housing OR "real estate") (rate OR "price cut" OR shortage OR stamp duty) India when:30d', 30, 'IN'),
    ('Real Estate', 'Residential', 'launch', '(residential OR apartment OR villa OR "housing project") (launches OR unveils OR "opens booking") India when:30d', 30, 'IN'),
    ('Real Estate', 'Residential', 'pricing', '(home loan OR EMI OR "stamp duty" OR "circle rate") (cut OR hike OR change) India when:30d', 30, 'IN'),
    ('Real Estate', 'Commercial', 'launch', '(commercial OR office OR warehouse OR mall) (launches OR leasing OR "rolls out") India when:30d', 30, 'IN'),
    ('Real Estate', 'Commercial', 'funding', '(REIT OR "commercial real estate" OR warehouse) (raises OR funding OR listing) India when:30d', 30, 'IN'),
    ('Real Estate', 'PropTech', 'launch', '(PropTech OR "property app" OR "home search" OR brokerage) (launches OR unveils OR "rolls out") India when:30d', 30, 'IN'),
    ('Real Estate', 'PropTech', 'funding', 'PropTech (raises OR funding OR "series a") India when:30d', 30, 'IN'),
    ('Real Estate', 'Construction', 'launch', '(construction OR cement OR "building material" OR prefab) (launches OR unveils OR product) India when:30d', 30, 'IN'),
    ('Real Estate', 'Construction', 'pricing', '(cement OR steel OR "building material") (price OR shortage OR "price cut") India when:30d', 30, 'IN'),
    ('Real Estate', 'RERA', 'approval', 'RERA (notification OR guidelines OR order OR approves OR penalty) India when:30d', 30, 'IN'),
    ('Real Estate', 'RERA', 'regulation', '(RERA OR "real estate regulator") (rule OR circular OR registration) India when:30d', 30, 'IN'),

    ('Real Estate', NULL, 'launch', '(housing OR "real estate" OR apartment) (launches OR unveils OR "rolls out") when:30d', 30, 'GLOBAL'),
    ('Real Estate', NULL, 'funding', '(PropTech OR "real estate") (raises OR funding OR "series a") when:30d', 30, 'GLOBAL'),
    ('Real Estate', NULL, 'approval', '(zoning OR housing OR "real estate") (approves OR guidelines OR regulation) when:30d', 30, 'GLOBAL'),
    ('Real Estate', 'Residential', 'launch', '(residential OR apartment OR housing) (launches OR "opens booking") when:30d', 30, 'GLOBAL'),
    ('Real Estate', 'Commercial', 'launch', '(commercial OR office OR warehouse OR REIT) (launches OR leasing OR listing) when:30d', 30, 'GLOBAL'),
    ('Real Estate', 'PropTech', 'launch', '(PropTech OR "property tech" OR brokerage) (launches OR unveils OR "rolls out") when:30d', 30, 'GLOBAL'),
    ('Real Estate', 'Construction', 'launch', '(construction OR prefab OR "building material") (launches OR product) when:30d', 30, 'GLOBAL'),
    ('Real Estate', 'RERA', 'approval', '(housing regulator OR zoning OR "planning authority") (approves OR guidelines OR rule) when:30d', 30, 'GLOBAL');

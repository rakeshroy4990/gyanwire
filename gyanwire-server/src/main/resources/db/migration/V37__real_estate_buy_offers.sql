-- Real Estate browse: drop general business RSS bleed, restore Google search packs
-- aimed at city buy offers and new project launches (not stock/startup noise).

-- Broad money / startup feeds flooded Real Estate with non-property news.
UPDATE news_sources
SET enabled = FALSE,
    last_error = COALESCE(last_error, 'Disabled: not property-specific (V37)')
WHERE industry = 'Real Estate'
  AND kind = 'rss'
  AND name IN ('Mint', 'The Hindu Business', 'Inc42');

-- Keep Economic Times Realty; fix common path typo if present.
UPDATE news_sources
SET url_template = 'https://economictimes.indiatimes.com/industry/services/property-/-cstruction/rssfeeds/2647163.cms',
    enabled = TRUE,
    last_error = NULL
WHERE industry = 'Real Estate'
  AND kind = 'rss'
  AND name = 'Economic Times Realty';

-- Allowlisted-only mode (V35) turned off Google News for every industry.
-- Real Estate needs search packs for city launches and buy offers.
UPDATE news_sources
SET enabled = TRUE,
    last_error = NULL
WHERE industry = 'Real Estate'
  AND kind = 'search_feed'
  AND name IN ('Google News', 'Google News World');

-- Replace Real Estate query packs with launch + buy-offer focus.
DELETE FROM news_query_packs WHERE industry = 'Real Estate';

INSERT INTO news_query_packs (industry, sub, signal_type, query, window_days, region)
VALUES
    -- India: new launches and booking opens
    ('Real Estate', NULL, 'launch',
     '("real estate" OR housing OR apartment OR villa OR flat OR "housing project") (launch OR launches OR unveils OR "opens booking" OR "new project" OR "soft launch") (Mumbai OR Delhi OR NCR OR Bengaluru OR Bangalore OR Hyderabad OR Pune OR Chennai OR Kolkata OR Ahmedabad OR India) when:7d',
     7, 'IN'),
    ('Real Estate', NULL, 'launch',
     '(housing OR apartment OR "real estate") (launch OR "opens booking" OR "new project") India when:30d',
     30, 'IN'),
    -- India: buy offers / festive deals / stamp duty / price cuts by city
    ('Real Estate', NULL, 'pricing',
     '("real estate" OR housing OR apartment OR flat OR home) ("price cut" OR offer OR discount OR "festive offer" OR "buy offer" OR "limited offer" OR "best deal" OR subsidy OR "stamp duty") (Mumbai OR Delhi OR NCR OR Bengaluru OR Bangalore OR Hyderabad OR Pune OR Chennai OR Kolkata OR Ahmedabad OR India) when:7d',
     7, 'IN'),
    ('Real Estate', NULL, 'pricing',
     '(housing OR apartment OR "home loan" OR "stamp duty") (offer OR "price cut" OR subsidy OR EMI) India when:30d',
     30, 'IN'),
    ('Real Estate', 'Residential', 'launch',
     '(residential OR apartment OR villa OR flat OR "housing project") (launch OR launches OR "opens booking" OR unveils) (Mumbai OR Delhi OR Bengaluru OR Hyderabad OR Pune OR Chennai OR India) when:7d',
     7, 'IN'),
    ('Real Estate', 'Residential', 'pricing',
     '(apartment OR flat OR villa OR residential) ("price cut" OR offer OR discount OR "festive offer" OR "stamp duty" OR subsidy) (Mumbai OR Delhi OR Bengaluru OR Hyderabad OR Pune OR Chennai OR India) when:7d',
     7, 'IN'),
    ('Real Estate', 'Commercial', 'launch',
     '(commercial OR office OR warehouse OR mall OR REIT) (launch OR leasing OR "opens booking" OR unveils) India when:7d',
     7, 'IN'),
    ('Real Estate', 'PropTech', 'launch',
     '(PropTech OR "property app" OR Magicbricks OR "Housing.com" OR 99acres OR brokerage) (launch OR unveils OR "rolls out" OR offer) India when:7d',
     7, 'IN'),
    ('Real Estate', 'Construction', 'launch',
     '(construction OR prefab OR "building material") (housing OR apartment OR "real estate") (launch OR product OR offer) India when:7d',
     7, 'IN'),
    ('Real Estate', 'RERA', 'approval',
     'RERA (notification OR guidelines OR registration OR clearance OR approves) (housing OR project OR "real estate") India when:7d',
     7, 'IN'),

    -- Global: launches and buy deals
    ('Real Estate', NULL, 'launch',
     '("real estate" OR housing OR apartment OR "new project") (launch OR launches OR "opens booking" OR unveils) when:7d',
     7, 'GLOBAL'),
    ('Real Estate', NULL, 'pricing',
     '("real estate" OR housing OR apartment OR home) ("price cut" OR offer OR discount OR "buyer incentive" OR subsidy) when:7d',
     7, 'GLOBAL'),
    ('Real Estate', 'Residential', 'launch',
     '(residential OR apartment OR housing) (launch OR "opens booking" OR "new project") when:7d',
     7, 'GLOBAL'),
    ('Real Estate', 'Commercial', 'launch',
     '(commercial OR office OR warehouse OR REIT) (launch OR leasing OR listing) when:7d',
     7, 'GLOBAL'),
    ('Real Estate', 'PropTech', 'launch',
     '(PropTech OR "property tech" OR brokerage OR "listing platform") (launch OR unveils OR offer) when:7d',
     7, 'GLOBAL');

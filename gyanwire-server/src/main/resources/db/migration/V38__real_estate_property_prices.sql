-- Real Estate: chase flat/villa/apartment prices and project launches — not pay-commission
-- or home-loan interest rate roundups.

DELETE FROM news_query_packs WHERE industry = 'Real Estate';

INSERT INTO news_query_packs (industry, sub, signal_type, query, window_days, region)
VALUES
    -- India: property prices (flats / villas / PSF)
    ('Real Estate', NULL, 'pricing',
     '("flat prices" OR "apartment prices" OR "villa prices" OR "property prices" OR "property price" OR "per sq ft" OR "per sq.ft" OR psf OR "circle rate") (Mumbai OR Delhi OR NCR OR Bengaluru OR Bangalore OR Hyderabad OR Pune OR Chennai OR Kolkata OR Ahmedabad OR India) when:7d',
     7, 'IN'),
    ('Real Estate', NULL, 'pricing',
     '(flat OR apartment OR villa OR "real estate") (prices OR "price rise" OR "price fall" OR "price surge" OR "capital values") India -"home loan" -"pay commission" -"interest rate" when:7d',
     7, 'IN'),
    ('Real Estate', NULL, 'pricing',
     '("flat prices" OR "apartment prices" OR "villa prices" OR "property prices") India when:30d',
     30, 'IN'),
    -- India: new launches / booking
    ('Real Estate', NULL, 'launch',
     '(apartment OR villa OR flat OR "housing project" OR "residential project") (launch OR launches OR "opens booking" OR "new project" OR "soft launch") (Mumbai OR Delhi OR Bengaluru OR Hyderabad OR Pune OR Chennai OR India) when:7d',
     7, 'IN'),
    ('Real Estate', 'Residential', 'pricing',
     '(apartment OR flat OR villa) (prices OR "per sq ft" OR psf OR "price cut" OR "festive offer") (Mumbai OR Delhi OR Bengaluru OR Hyderabad OR Pune OR Chennai OR India) -"home loan interest" -"pay commission" when:7d',
     7, 'IN'),
    ('Real Estate', 'Residential', 'launch',
     '(residential OR apartment OR villa OR flat) (launch OR "opens booking" OR "new project") India -"home loan" -"pay commission" when:7d',
     7, 'IN'),
    ('Real Estate', 'Commercial', 'pricing',
     '(office OR warehouse OR "commercial property" OR REIT) (prices OR leasing OR rental OR "capital values") India when:7d',
     7, 'IN'),
    ('Real Estate', 'PropTech', 'launch',
     '(PropTech OR Magicbricks OR "Housing.com" OR 99acres OR NoBroker) (prices OR listing OR launch OR offer) India -"home loan interest" when:7d',
     7, 'IN'),
    ('Real Estate', 'RERA', 'approval',
     'RERA (notification OR registration OR clearance) (housing OR apartment OR project OR "real estate") India when:7d',
     7, 'IN'),

    -- Global
    ('Real Estate', NULL, 'pricing',
     '("home prices" OR "house prices" OR "apartment prices" OR "property prices" OR "per sq ft") (rise OR fall OR surge OR drop) when:7d',
     7, 'GLOBAL'),
    ('Real Estate', NULL, 'launch',
     '(apartment OR villa OR housing OR "real estate") (launch OR "opens booking" OR "new project") when:7d',
     7, 'GLOBAL'),
    ('Real Estate', 'Residential', 'pricing',
     '(apartment OR villa OR flat OR residential) (prices OR "per sq ft" OR "price cut") when:7d',
     7, 'GLOBAL');

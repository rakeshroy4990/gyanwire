-- Research industry catalog for Gyanwire UI + validation.
-- Idempotent so Express migration 005 can coexist on the same database.

CREATE TABLE IF NOT EXISTS research_industries (
    id              BIGSERIAL PRIMARY KEY,
    external_id     UUID NOT NULL DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    label           TEXT NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT research_industries_external_id_key UNIQUE (external_id),
    CONSTRAINT research_industries_name_key UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS research_industry_subs (
    id              BIGSERIAL PRIMARY KEY,
    external_id     UUID NOT NULL DEFAULT gen_random_uuid(),
    industry_id     BIGINT NOT NULL REFERENCES research_industries (id),
    name            TEXT NOT NULL,
    label           TEXT NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT research_industry_subs_external_id_key UNIQUE (external_id),
    CONSTRAINT research_industry_subs_industry_name_key UNIQUE (industry_id, name)
);

CREATE INDEX IF NOT EXISTS idx_research_industries_sort
    ON research_industries (sort_order)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_research_industry_subs_industry
    ON research_industry_subs (industry_id)
    WHERE deleted = FALSE;

INSERT INTO research_industries (name, label, sort_order)
VALUES
    ('Share Market', 'Share products', 10),
    ('IT', 'IT products', 20),
    ('Medical', 'Medical products', 30),
    ('Space', 'Space products', 40),
    ('Social Media', 'Social products', 50),
    ('Gaming', 'Gaming products', 60),
    ('Astrology', 'Astrology products', 70)
ON CONFLICT (name) DO NOTHING;

INSERT INTO research_industry_subs (industry_id, name, label, sort_order)
SELECT i.id, s.name, s.label, s.sort_order
FROM research_industries i
JOIN (
    VALUES
        ('Share Market', 'IT', 'IT shares', 10),
        ('Share Market', 'EV', 'EV shares', 20),
        ('Share Market', 'Space', 'Space shares', 30),
        ('Share Market', 'Pharma', 'Pharma shares', 40),
        ('Share Market', 'Banking', 'Banking shares', 50),
        ('Share Market', 'Energy', 'Energy shares', 60),
        ('IT', 'AI', 'AI products', 10),
        ('IT', 'Semiconductors', 'Chip products', 20),
        ('IT', 'Cybersecurity', 'Security products', 30),
        ('IT', 'Cloud', 'Cloud products', 40),
        ('Medical', 'Paediatrics', 'Paediatrics products', 10),
        ('Medical', 'Cardiology', 'Cardiology products', 20),
        ('Medical', 'Gynecology', 'Gynecology products', 30),
        ('Medical', 'Oncology', 'Oncology products', 40),
        ('Medical', 'Mental Health', 'Mental health products', 50),
        ('Medical', 'Dermatology', 'Dermatology products', 60),
        ('Medical', 'Autism', 'Autism products', 70),
        ('Space', 'Satellites', 'Satellite products', 10),
        ('Space', 'Launch', 'Launch products', 20),
        ('Space', 'Commercial Space', 'Commercial space products', 30),
        ('Space', 'Lunar', 'Lunar products', 40),
        ('Social Media', 'Short Video', 'Short video products', 10),
        ('Social Media', 'Social Commerce', 'Social commerce products', 20),
        ('Social Media', 'Messaging', 'Messaging products', 30),
        ('Social Media', 'Creators', 'Creator products', 40),
        ('Gaming', 'Mobile', 'Mobile game products', 10),
        ('Gaming', 'Esports', 'Esports products', 20),
        ('Gaming', 'Console', 'Console products', 30),
        ('Gaming', 'PC Gaming', 'PC gaming products', 40),
        ('Gaming', 'Game Dev', 'Game development products', 50),
        ('Astrology', 'Horoscope', 'Horoscope products', 10),
        ('Astrology', 'Vedic', 'Vedic astrology products', 20),
        ('Astrology', 'Tarot', 'Tarot products', 30),
        ('Astrology', 'Numerology', 'Numerology products', 40),
        ('Astrology', 'Vastu', 'Vastu products', 50)
) AS s(industry_name, name, label, sort_order)
  ON i.name = s.industry_name
ON CONFLICT (industry_id, name) DO NOTHING;

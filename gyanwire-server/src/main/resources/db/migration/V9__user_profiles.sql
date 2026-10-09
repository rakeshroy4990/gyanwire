CREATE TABLE IF NOT EXISTS user_profiles (
    user_id UUID PRIMARY KEY REFERENCES users (id),
    persona TEXT,
    goal_90d TEXT,
    capital_band TEXT,
    income_band TEXT,
    invest_pct INTEGER NOT NULL DEFAULT 10,
    hours_per_week INTEGER,
    location_tier TEXT,
    state TEXT,
    city TEXT,
    languages TEXT[],
    industries TEXT[],
    assets TEXT[],
    risk_appetite TEXT,
    constraints TEXT[],
    education TEXT,
    consent_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS user_skills (
    user_id UUID NOT NULL REFERENCES users (id),
    skill_tag TEXT NOT NULL,
    level INTEGER NOT NULL,
    PRIMARY KEY (user_id, skill_tag)
);
SELECT gyanwire_lock('user_profiles');
SELECT gyanwire_lock('user_skills');

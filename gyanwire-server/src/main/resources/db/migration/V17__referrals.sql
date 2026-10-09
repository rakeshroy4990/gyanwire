CREATE TABLE IF NOT EXISTS referral_redemptions (
    id UUID PRIMARY KEY,
    code TEXT NOT NULL,
    referrer_id UUID NOT NULL REFERENCES users (id),
    redeemed_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (redeemed_by)
);
CREATE TABLE IF NOT EXISTS referral_bonus (
    user_id UUID PRIMARY KEY REFERENCES users (id),
    extra_searches INTEGER NOT NULL DEFAULT 0
);
INSERT INTO plans (id, name, daily_search_limit, can_save, can_export, can_alert, seats, daily_brief_limit, daily_idea_limit, can_roadmap, project_limit)
VALUES ('student', 'Student', 20, true, false, false, 1, 5, 10, false, 3)
ON CONFLICT (id) DO NOTHING;
SELECT gyanwire_lock('referral_redemptions');
SELECT gyanwire_lock('referral_bonus');

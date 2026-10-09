
CREATE TABLE IF NOT EXISTS tools (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    category TEXT,
    cost_inr INTEGER NOT NULL,
    billing TEXT NOT NULL,
    free_alternative_id TEXT,
    unlocks_skills TEXT[],
    weeks_saved INTEGER NOT NULL DEFAULT 0,
    priority INTEGER NOT NULL DEFAULT 0,
    region TEXT,
    affiliate BOOLEAN NOT NULL DEFAULT false,
    last_verified_at DATE NOT NULL,
    bucket TEXT NOT NULL,
    stale BOOLEAN NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS courses (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    category TEXT,
    cost_inr INTEGER NOT NULL,
    billing TEXT NOT NULL,
    free_alternative_id TEXT,
    unlocks_skills TEXT[],
    weeks_saved INTEGER NOT NULL DEFAULT 0,
    priority INTEGER NOT NULL DEFAULT 0,
    region TEXT,
    affiliate BOOLEAN NOT NULL DEFAULT false,
    last_verified_at DATE NOT NULL,
    bucket TEXT NOT NULL,
    stale BOOLEAN NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS skill_graph (
    skill TEXT NOT NULL,
    prerequisite_skill TEXT NOT NULL,
    est_hours INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (skill, prerequisite_skill)
);
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('vscode', 'VS Code', 'tools', 0, 'free', NULL, ARRAY['ide'], 0, 5, 'IN', false, DATE '2026-10-09', 'tools') ON CONFLICT (id) DO NOTHING;
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('notion-free', 'Notion free', 'tools', 0, 'free', NULL, ARRAY['notes'], 0, 4, 'IN', false, DATE '2026-10-09', 'tools') ON CONFLICT (id) DO NOTHING;
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('canva-free', 'Canva free', 'tools', 0, 'free', NULL, ARRAY['design'], 0, 4, 'IN', false, DATE '2026-10-09', 'tools') ON CONFLICT (id) DO NOTHING;
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('github-free', 'GitHub', 'tools', 0, 'free', NULL, ARRAY['code'], 0, 5, 'IN', false, DATE '2026-10-09', 'tools') ON CONFLICT (id) DO NOTHING;
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('google-analytics', 'Google Analytics', 'tools', 0, 'free', NULL, ARRAY['analytics'], 0, 3, 'IN', false, DATE '2026-10-09', 'tools') ON CONFLICT (id) DO NOTHING;
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('wordpress', 'WordPress.com free', 'tools', 0, 'free', NULL, ARRAY['site'], 0, 3, 'IN', false, DATE '2026-10-09', 'tools') ON CONFLICT (id) DO NOTHING;
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('razorpay-test', 'Razorpay test mode', 'tools', 0, 'free', NULL, ARRAY['payments'], 0, 4, 'IN', false, DATE '2026-10-09', 'tools') ON CONFLICT (id) DO NOTHING;
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('figma', 'Figma starter', 'tools', 0, 'free', NULL, ARRAY['design'], 0, 4, 'IN', false, DATE '2026-10-09', 'tools') ON CONFLICT (id) DO NOTHING;
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('chatgpt-free', 'Chat assistant free tier', 'tools', 0, 'free', NULL, ARRAY['writing'], 0, 3, 'IN', false, DATE '2026-10-09', 'tools') ON CONFLICT (id) DO NOTHING;
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('sheets', 'Google Sheets', 'tools', 0, 'free', NULL, ARRAY['ops'], 0, 5, 'IN', false, DATE '2026-10-09', 'tools') ON CONFLICT (id) DO NOTHING;
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('domain', 'Domain name', 'proof', 800, 'once', NULL, ARRAY['site'], 6, 4, 'IN', false, DATE '2026-10-09', 'proof') ON CONFLICT (id) DO NOTHING;
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('hosting', 'Basic hosting', 'tools', 200, 'monthly', 'wordpress', ARRAY['site'], 4, 3, 'IN', false, DATE '2026-10-09', 'tools') ON CONFLICT (id) DO NOTHING;
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('canva-pro', 'Canva Pro', 'tools', 500, 'monthly', 'canva-free', ARRAY['design'], 4, 2, 'IN', false, DATE '2026-10-09', 'tools') ON CONFLICT (id) DO NOTHING;
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('notion-plus', 'Notion Plus', 'tools', 400, 'monthly', 'notion-free', ARRAY['notes'], 4, 2, 'IN', false, DATE '2026-10-09', 'tools') ON CONFLICT (id) DO NOTHING;
INSERT INTO tools (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('analytics-paid', 'Plausible', 'tools', 700, 'monthly', 'google-analytics', ARRAY['analytics'], 4, 1, 'IN', false, DATE '2026-10-09', 'tools') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('yt-python', 'Python for beginners (free videos)', 'learning', 0, 'free', NULL, ARRAY['python'], 0, 5, 'IN', false, DATE '2026-10-09', 'learning') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('yt-sales', 'Sales conversations playlist', 'learning', 0, 'free', NULL, ARRAY['sales'], 0, 4, 'IN', false, DATE '2026-10-09', 'learning') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('yt-excel', 'Spreadsheets for operators', 'learning', 0, 'free', NULL, ARRAY['sheets'], 0, 4, 'IN', false, DATE '2026-10-09', 'learning') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('nptel-python', 'NPTEL programming', 'learning', 0, 'free', NULL, ARRAY['python'], 0, 5, 'IN', false, DATE '2026-10-09', 'learning') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('swayam', 'SWAYAM starter course', 'learning', 0, 'free', NULL, ARRAY['learning'], 0, 4, 'IN', false, DATE '2026-10-09', 'learning') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('yt-design', 'Design basics playlist', 'learning', 0, 'free', NULL, ARRAY['design'], 0, 3, 'IN', false, DATE '2026-10-09', 'learning') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('yt-writing', 'Clear writing practice', 'learning', 0, 'free', NULL, ARRAY['writing'], 0, 3, 'IN', false, DATE '2026-10-09', 'learning') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('community-free', 'Local founder meetup', 'community', 0, 'free', NULL, ARRAY['network'], 0, 3, 'IN', false, DATE '2026-10-09', 'community') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('library', 'Public library card', 'community', 0, 'free', NULL, ARRAY['reading'], 0, 2, 'IN', false, DATE '2026-10-09', 'community') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('paid-python', 'Structured Python course', 'learning', 1200, 'once', 'yt-python', ARRAY['python'], 6, 3, 'IN', false, DATE '2026-10-09', 'learning') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('paid-sales', 'Sales sprint', 'learning', 1500, 'once', 'yt-sales', ARRAY['sales'], 5, 3, 'IN', false, DATE '2026-10-09', 'learning') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('paid-design', 'Portfolio design workshop', 'proof', 900, 'once', 'yt-design', ARRAY['design'], 4, 2, 'IN', false, DATE '2026-10-09', 'proof') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('cert', 'Low-cost certificate exam', 'proof', 600, 'once', NULL, ARRAY['credential'], 4, 2, 'IN', false, DATE '2026-10-09', 'proof') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('book', 'One practice book', 'community', 300, 'once', 'library', ARRAY['reading'], 4, 1, 'IN', false, DATE '2026-10-09', 'community') ON CONFLICT (id) DO NOTHING;
INSERT INTO courses (id, name, category, cost_inr, billing, free_alternative_id, unlocks_skills, weeks_saved, priority, region, affiliate, last_verified_at, bucket) VALUES ('mentor', 'One mentor call pack', 'community', 500, 'once', 'community-free', ARRAY['network'], 4, 1, 'IN', false, DATE '2026-10-09', 'community') ON CONFLICT (id) DO NOTHING;

INSERT INTO skill_graph (skill, prerequisite_skill, est_hours) VALUES
('python', 'sheets', 20),
('sales', 'writing', 15),
('design', 'writing', 12)
ON CONFLICT DO NOTHING;

SELECT gyanwire_lock('tools');
SELECT gyanwire_lock('courses');
SELECT gyanwire_lock('skill_graph');

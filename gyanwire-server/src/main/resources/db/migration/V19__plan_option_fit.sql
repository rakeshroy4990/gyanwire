-- What-If option fit: extend V12 skill catalog (CURSOR_PLAN_CREATIVE_UI.md Step 1)

ALTER TABLE tools ADD COLUMN IF NOT EXISTS kind TEXT NOT NULL DEFAULT 'tool';
ALTER TABLE tools ADD COLUMN IF NOT EXISTS task_fit JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE tools ADD COLUMN IF NOT EXISTS priced_at DATE;
ALTER TABLE tools ADD COLUMN IF NOT EXISTS sample_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE courses ADD COLUMN IF NOT EXISTS kind TEXT NOT NULL DEFAULT 'course';
ALTER TABLE courses ADD COLUMN IF NOT EXISTS task_fit JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS priced_at DATE;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS sample_count INTEGER NOT NULL DEFAULT 0;

UPDATE tools SET priced_at = last_verified_at WHERE priced_at IS NULL;
UPDATE courses SET priced_at = last_verified_at WHERE priced_at IS NULL;

-- Placeholder speedups only — UI must show confidence=placeholder
UPDATE tools SET kind = 'tool', task_fit = '{"coding":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"},"writing":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"}}'::jsonb
WHERE id = 'vscode';
UPDATE tools SET kind = 'tool', task_fit = '{"coding":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"},"data":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"}}'::jsonb
WHERE id = 'github-free';
UPDATE tools SET kind = 'tool', task_fit = '{"design":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"}}'::jsonb
WHERE id = 'canva-free';
UPDATE tools SET kind = 'tool', task_fit = '{"design":{"speedup":[1.05,1.25],"confidence":"placeholder","source":"seed placeholder — not measured"},"writing":{"speedup":[1.0,1.1],"confidence":"placeholder","source":"seed placeholder — not measured"}}'::jsonb
WHERE id = 'canva-pro';
UPDATE tools SET kind = 'tool', task_fit = '{"writing":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"},"learning":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"},"data":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"}}'::jsonb
WHERE id = 'notion-free';
UPDATE tools SET kind = 'tool', task_fit = '{"writing":{"speedup":[1.05,1.2],"confidence":"placeholder","source":"seed placeholder — not measured"},"learning":{"speedup":[1.05,1.15],"confidence":"placeholder","source":"seed placeholder — not measured"},"data":{"speedup":[1.05,1.2],"confidence":"placeholder","source":"seed placeholder — not measured"}}'::jsonb
WHERE id = 'notion-plus';
UPDATE tools SET kind = 'model', task_fit = '{"writing":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"},"coding":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"},"research":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"}}'::jsonb
WHERE id = 'chatgpt-free';
UPDATE tools SET kind = 'tool', task_fit = '{"design":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"}}'::jsonb
WHERE id = 'figma';
UPDATE tools SET kind = 'tool', task_fit = '{"data":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"}}'::jsonb
WHERE id = 'sheets';
UPDATE tools SET kind = 'tool', task_fit = '{"data":{"speedup":[1.05,1.2],"confidence":"placeholder","source":"seed placeholder — not measured"}}'::jsonb
WHERE id = 'analytics-paid';
UPDATE tools SET kind = 'tool', task_fit = '{"data":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"}}'::jsonb
WHERE id = 'google-analytics';

UPDATE courses SET kind = 'course', task_fit = '{"learning":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"},"coding":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"}}'::jsonb
WHERE id = 'yt-python';
UPDATE courses SET kind = 'course', task_fit = '{"learning":{"speedup":[1.1,1.35],"confidence":"placeholder","source":"seed placeholder — not measured"},"coding":{"speedup":[1.05,1.25],"confidence":"placeholder","source":"seed placeholder — not measured"}}'::jsonb
WHERE id = 'paid-python';
UPDATE courses SET kind = 'course', task_fit = '{"learning":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"},"writing":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"}}'::jsonb
WHERE id = 'yt-writing';
UPDATE courses SET kind = 'course', task_fit = '{"learning":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"},"design":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"}}'::jsonb
WHERE id = 'yt-design';
UPDATE courses SET kind = 'course', task_fit = '{"learning":{"speedup":[1.05,1.2],"confidence":"placeholder","source":"seed placeholder — not measured"},"design":{"speedup":[1.1,1.3],"confidence":"placeholder","source":"seed placeholder — not measured"}}'::jsonb
WHERE id = 'paid-design';
UPDATE courses SET kind = 'course', task_fit = '{"outreach":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"},"learning":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"}}'::jsonb
WHERE id = 'community-free';
UPDATE courses SET kind = 'course', task_fit = '{"outreach":{"speedup":[1.05,1.2],"confidence":"placeholder","source":"seed placeholder — not measured"},"learning":{"speedup":[1.05,1.15],"confidence":"placeholder","source":"seed placeholder — not measured"}}'::jsonb
WHERE id = 'mentor';
UPDATE courses SET kind = 'course', task_fit = '{"learning":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"},"data":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"}}'::jsonb
WHERE id = 'yt-excel';
UPDATE courses SET kind = 'course', task_fit = '{"outreach":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"},"learning":{"speedup":[1.0,1.0],"confidence":"baseline","source":"catalog baseline"}}'::jsonb
WHERE id = 'yt-sales';
UPDATE courses SET kind = 'course', task_fit = '{"outreach":{"speedup":[1.1,1.3],"confidence":"placeholder","source":"seed placeholder — not measured"}}'::jsonb
WHERE id = 'paid-sales';

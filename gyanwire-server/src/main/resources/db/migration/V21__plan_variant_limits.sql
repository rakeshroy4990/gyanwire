-- What-If multi-variant gate (CURSOR_PLAN_CREATIVE_UI.md Step 9)

ALTER TABLE plans ADD COLUMN IF NOT EXISTS max_plan_variants INTEGER NOT NULL DEFAULT 1;

UPDATE plans SET max_plan_variants = 1 WHERE id = 'free';
UPDATE plans SET max_plan_variants = 3 WHERE id IN ('pro', 'team');

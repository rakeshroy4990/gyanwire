-- P2: paid plans may use the high-thinking model. Free, student, and anonymous stay on the low model.

ALTER TABLE plans ADD COLUMN IF NOT EXISTS allows_high_model BOOLEAN NOT NULL DEFAULT false;

UPDATE plans SET allows_high_model = true WHERE id IN ('pro', 'team');

ALTER TABLE llm_calls ADD COLUMN IF NOT EXISTS model TEXT;

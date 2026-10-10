-- What-If calibration: logged actual hours (CURSOR_PLAN_CREATIVE_UI.md Step 8)

CREATE TABLE IF NOT EXISTS plan_week_actuals (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    idea_id UUID,
    week_no INTEGER NOT NULL,
    option_id TEXT NOT NULL,
    task_type TEXT NOT NULL,
    hours_actual NUMERIC(8, 2) NOT NULL CHECK (hours_actual >= 0 AND hours_actual <= 168),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS plan_week_actuals_option_task_idx
    ON plan_week_actuals (option_id, task_type);

CREATE INDEX IF NOT EXISTS plan_week_actuals_user_idx
    ON plan_week_actuals (user_id, created_at DESC);

SELECT gyanwire_lock('plan_week_actuals');

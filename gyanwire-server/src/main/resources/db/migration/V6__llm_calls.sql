CREATE TABLE IF NOT EXISTS llm_calls (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users (id),
    feature         TEXT NOT NULL,
    prompt_version  TEXT NOT NULL,
    tokens_in       INTEGER,
    tokens_out      INTEGER,
    latency_ms      INTEGER,
    ok              BOOLEAN NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_llm_calls_created_at ON llm_calls (created_at);

DO $$
DECLARE
  role_name text;
BEGIN
  IF to_regclass('public.llm_calls') IS NULL THEN
    RETURN;
  END IF;

  EXECUTE 'ALTER TABLE public.llm_calls ENABLE ROW LEVEL SECURITY';
  EXECUTE 'REVOKE ALL ON TABLE public.llm_calls FROM PUBLIC';

  FOREACH role_name IN ARRAY ARRAY['anon', 'authenticated', 'authenticator']
  LOOP
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
      EXECUTE format('REVOKE ALL ON TABLE public.llm_calls FROM %I', role_name);
    END IF;
  END LOOP;
END $$;

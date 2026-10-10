CREATE TABLE IF NOT EXISTS plan_cache (
    key text PRIMARY KEY,
    stage text NOT NULL,
    payload jsonb NOT NULL,
    prompt_version text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    hits int NOT NULL DEFAULT 0
);

DO $$
DECLARE
  role_name text;
BEGIN
  IF to_regclass('public.plan_cache') IS NULL THEN
    RETURN;
  END IF;
  EXECUTE 'ALTER TABLE public.plan_cache ENABLE ROW LEVEL SECURITY';
  EXECUTE 'REVOKE ALL ON TABLE public.plan_cache FROM PUBLIC';
  FOREACH role_name IN ARRAY ARRAY['anon', 'authenticated', 'authenticator']
  LOOP
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
      EXECUTE format('REVOKE ALL ON TABLE public.plan_cache FROM %I', role_name);
    END IF;
  END LOOP;
END $$;

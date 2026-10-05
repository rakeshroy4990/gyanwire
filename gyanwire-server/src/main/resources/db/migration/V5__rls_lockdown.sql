-- Lock tables against Supabase Data API roles when present.

DO $$
DECLARE
  tbl text;
  role_name text;
BEGIN
  FOREACH tbl IN ARRAY ARRAY[
    'users',
    'refresh_tokens',
    'plans',
    'subscriptions',
    'usage_events',
    'billing_events',
    'research_industries',
    'research_industry_subs'
  ]
  LOOP
    IF to_regclass(format('public.%I', tbl)) IS NULL THEN
      CONTINUE;
    END IF;

    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', tbl);
    EXECUTE format('REVOKE ALL ON TABLE public.%I FROM PUBLIC', tbl);

    FOREACH role_name IN ARRAY ARRAY['anon', 'authenticated', 'authenticator']
    LOOP
      IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
        EXECUTE format('REVOKE ALL ON TABLE public.%I FROM %I', tbl, role_name);
      END IF;
    END LOOP;
  END LOOP;
END $$;

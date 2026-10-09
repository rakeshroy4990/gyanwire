CREATE TABLE IF NOT EXISTS page_cache (
    url_hash     TEXT PRIMARY KEY,
    url          TEXT,
    snippet      TEXT,
    text         TEXT,
    search_vector tsvector,
    fetched_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_page_cache_fetched_at ON page_cache (fetched_at);

CREATE OR REPLACE FUNCTION gyanwire_page_cache_tsv() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('english', coalesce(NEW.text, ''));
    RETURN NEW;
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_page_cache_tsv ON page_cache;
CREATE TRIGGER trg_page_cache_tsv
    BEFORE INSERT OR UPDATE OF text ON page_cache
    FOR EACH ROW EXECUTE FUNCTION gyanwire_page_cache_tsv();

CREATE TABLE IF NOT EXISTS query_cache (
    cache_key   TEXT PRIMARY KEY,
    payload     JSONB NOT NULL,
    created_on  DATE NOT NULL
);

CREATE OR REPLACE FUNCTION gyanwire_lock(tbl text) RETURNS void AS $$
DECLARE
    role_name text;
BEGIN
    IF to_regclass(format('public.%I', tbl)) IS NULL THEN
        RETURN;
    END IF;
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', tbl);
    EXECUTE format('REVOKE ALL ON TABLE public.%I FROM PUBLIC', tbl);
    FOREACH role_name IN ARRAY ARRAY['anon', 'authenticated', 'authenticator']
    LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
            EXECUTE format('REVOKE ALL ON TABLE public.%I FROM %I', tbl, role_name);
        END IF;
    END LOOP;
END $$ LANGUAGE plpgsql;

SELECT gyanwire_lock('page_cache');
SELECT gyanwire_lock('query_cache');

CREATE TABLE IF NOT EXISTS saved_queries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    query TEXT NOT NULL,
    industry TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS digests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    url_hash TEXT NOT NULL,
    sent_at TIMESTAMPTZ,
    UNIQUE (user_id, url_hash)
);
CREATE TABLE IF NOT EXISTS job_locks (
    name TEXT PRIMARY KEY,
    locked_until TIMESTAMPTZ,
    owner TEXT
);
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS digest_opt_in BOOLEAN NOT NULL DEFAULT false;
SELECT gyanwire_lock('saved_queries');
SELECT gyanwire_lock('digests');
SELECT gyanwire_lock('job_locks');

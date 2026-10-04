-- Gyanwire auth schema (mirrors hospital users / refresh_tokens shape, trimmed for this app)

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id              TEXT PRIMARY KEY,
    external_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    email           TEXT NOT NULL,
    first_name      TEXT,
    last_name       TEXT,
    password_hash   TEXT,
    auth_provider   TEXT NOT NULL DEFAULT 'password',
    role            TEXT NOT NULL DEFAULT 'USER',
    role_status     TEXT NOT NULL DEFAULT 'ACTIVE',
    active          BOOLEAN NOT NULL DEFAULT true,
    token_version   BIGINT NOT NULL DEFAULT 1,
    profile_pic     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT false
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower
    ON users (lower(email))
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_users_email_lower ON users (lower(email));

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id           TEXT PRIMARY KEY,
    token        TEXT NOT NULL,
    user_id      TEXT NOT NULL REFERENCES users (id),
    expiry       TIMESTAMPTZ NOT NULL,
    device_id    TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted      BOOLEAN NOT NULL DEFAULT false
);

-- Drop legacy global unique constraint if present (soft-deleted rows would collide).
ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_token_key;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_tokens_token_active
    ON refresh_tokens (token)
    WHERE deleted = false;

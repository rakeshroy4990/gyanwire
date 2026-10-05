-- Auth schema (idempotent; may already exist from legacy Express migrations).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL,
    password_hash   TEXT,
    first_name      TEXT,
    last_name       TEXT,
    profile_pic     TEXT,
    auth_provider   TEXT NOT NULL DEFAULT 'password',
    role            TEXT NOT NULL DEFAULT 'user',
    token_version   BIGINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower
    ON users (lower(email))
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_users_email_lower ON users (lower(email));

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token        TEXT NOT NULL,
    user_id      UUID NOT NULL REFERENCES users (id),
    expiry       TIMESTAMPTZ NOT NULL,
    device_id    TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_tokens_token_active
    ON refresh_tokens (token)
    WHERE deleted_at IS NULL;

#!/usr/bin/env node
/**
 * One-off: copy Gyanwire sign-in users from a legacy shared Postgres into the
 * Gyanwire-owned database.
 *
 * Source:  EXPORT_SOURCE_DATABASE_URL
 * Dest:    GYANWIRE_DATABASE_URL (or DATABASE_URL)
 *
 * Selection: users that have at least one refresh_tokens row with
 * device_id = 'browser' (Gyanwire's cookie sessions). Pass --all to copy every
 * non-deleted source user instead.
 *
 * Usage:
 *   EXPORT_SOURCE_DATABASE_URL=postgresql://... node server/scripts/export-gyanwire-users.js
 *   node server/scripts/export-gyanwire-users.js --all --dry-run
 */
import dotenv from 'dotenv';
import pg from 'pg';

dotenv.config();

const { Pool } = pg;
const args = new Set(process.argv.slice(2));
const dryRun = args.has('--dry-run');
const copyAll = args.has('--all');

function resolveSourceUrl() {
  const exportUrl = String(process.env.EXPORT_SOURCE_DATABASE_URL || '').trim();
  if (!exportUrl) {
    throw new Error('Set EXPORT_SOURCE_DATABASE_URL for the legacy source database.');
  }
  return exportUrl;
}

function resolveDestUrl() {
  const url = String(process.env.GYANWIRE_DATABASE_URL || process.env.DATABASE_URL || '').trim();
  if (!url) {
    throw new Error('Set GYANWIRE_DATABASE_URL (or DATABASE_URL) for the destination DB.');
  }
  return url;
}

function poolFor(connectionString) {
  const useSsl = process.env.DATABASE_SSL !== 'false'
    && (process.env.DATABASE_SSL === 'true'
      || connectionString.includes('supabase.com')
      || connectionString.includes('neon.tech'));
  return new Pool({
    connectionString,
    ssl: useSsl ? { rejectUnauthorized: false } : undefined,
  });
}

async function main() {
  const source = poolFor(resolveSourceUrl());
  const dest = poolFor(resolveDestUrl());

  try {
    const selectSql = copyAll
      ? `SELECT id, email, first_name, last_name, password_hash, profile_pic,
                created_at, updated_at
         FROM users
         WHERE COALESCE(deleted, false) = false
           AND email IS NOT NULL`
      : `SELECT DISTINCT u.id, u.email, u.first_name, u.last_name, u.password_hash,
                u.profile_pic, u.created_at, u.updated_at
         FROM users u
         INNER JOIN refresh_tokens rt ON rt.user_id = u.id
         WHERE COALESCE(u.deleted, false) = false
           AND COALESCE(rt.deleted, false) = false
           AND rt.device_id = 'browser'
           AND u.email IS NOT NULL`;

    const { rows } = await source.query(selectSql);
    console.log(`Found ${rows.length} candidate user(s)${copyAll ? ' (--all)' : ' (Gyanwire browser sessions)'}.`);

    let inserted = 0;
    let skipped = 0;

    for (const row of rows) {
      const email = String(row.email || '').trim().toLowerCase();
      if (!email) {
        skipped += 1;
        continue;
      }

      if (dryRun) {
        console.log(`[dry-run] would copy ${email}`);
        inserted += 1;
        continue;
      }

      const existing = await dest.query(
        `SELECT id FROM users WHERE lower(email) = $1 AND deleted_at IS NULL LIMIT 1`,
        [email],
      );
      if (existing.rowCount > 0) {
        skipped += 1;
        continue;
      }

      await dest.query(
        `INSERT INTO users (
           email, password_hash, first_name, last_name, profile_pic,
           auth_provider, role, token_version, created_at, updated_at
         ) VALUES (
           $1, $2, $3, $4, $5,
           'password', 'user', 1, COALESCE($6, NOW()), COALESCE($7, NOW())
         )`,
        [
          email,
          row.password_hash || null,
          row.first_name || null,
          row.last_name || null,
          row.profile_pic || null,
          row.created_at || null,
          row.updated_at || null,
        ],
      );
      inserted += 1;
    }

    console.log(`Done. inserted=${inserted} skipped=${skipped} dryRun=${dryRun}`);
  } finally {
    await source.end();
    await dest.end();
  }
}

main().catch((err) => {
  console.error('Export failed:', err.message);
  process.exit(1);
});

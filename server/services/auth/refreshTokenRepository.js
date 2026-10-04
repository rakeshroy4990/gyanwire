import { randomUUID } from 'node:crypto';
import { query } from '../../db/pool.js';

/**
 * Uses shared Supabase `refresh_tokens` table (same as hospital backend).
 * Hospital hard-deletes on rotation; we match that to respect the unique(token) constraint.
 */
export async function saveRefreshToken({ token, userId, expiry, deviceId = 'browser' }) {
  const id = randomUUID();
  await query(
    `INSERT INTO refresh_tokens (id, token, user_id, expiry, device_id, created_at, deleted)
     VALUES ($1, $2, $3, $4, $5, NOW(), false)`,
    [id, token, userId, expiry, deviceId],
  );
}

export async function findByToken(token) {
  const result = await query(
    `SELECT id, token, user_id, expiry, device_id
     FROM refresh_tokens
     WHERE token = $1 AND deleted = false
     LIMIT 1`,
    [String(token)],
  );
  const row = result.rows[0];
  if (!row) return null;
  return {
    id: row.id,
    token: row.token,
    userId: row.user_id,
    expiry: row.expiry,
    deviceId: row.device_id,
  };
}

export async function softDeleteByToken(token) {
  await query(
    `DELETE FROM refresh_tokens
     WHERE token = $1`,
    [String(token)],
  );
}

export async function softDeleteByUserId(userId) {
  await query(
    `DELETE FROM refresh_tokens
     WHERE user_id = $1`,
    [String(userId)],
  );
}

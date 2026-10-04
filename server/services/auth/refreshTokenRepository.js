import { query } from '../../db/pool.js';

export async function saveRefreshToken({ token, userId, expiry, deviceId = 'browser' }) {
  await query(
    `INSERT INTO refresh_tokens (token, user_id, expiry, device_id, created_at)
     VALUES ($1, $2, $3, $4, NOW())`,
    [token, userId, expiry, deviceId],
  );
}

export async function findByToken(token) {
  const result = await query(
    `SELECT id, token, user_id, expiry, device_id
     FROM refresh_tokens
     WHERE token = $1 AND deleted_at IS NULL
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
    `UPDATE refresh_tokens
     SET deleted_at = NOW()
     WHERE token = $1 AND deleted_at IS NULL`,
    [String(token)],
  );
}

export async function softDeleteByUserId(userId) {
  await query(
    `UPDATE refresh_tokens
     SET deleted_at = NOW()
     WHERE user_id = $1 AND deleted_at IS NULL`,
    [String(userId)],
  );
}

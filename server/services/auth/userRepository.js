import { query } from '../../db/pool.js';
import { hashPassword } from './password.js';
import { randomUUID } from 'node:crypto';

const USER_COLUMNS = `
  id, email, first_name, last_name, password_hash, auth_provider,
  role, token_version, profile_pic, created_at, updated_at, deleted_at
`;

function mapUser(row) {
  if (!row) return null;
  return {
    id: row.id,
    email: row.email,
    firstName: row.first_name,
    lastName: row.last_name,
    passwordHash: row.password_hash,
    authProvider: row.auth_provider,
    role: row.role,
    tokenVersion: Number(row.token_version || 1),
    profilePic: row.profile_pic,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    deletedAt: row.deleted_at,
  };
}

export async function findByEmail(email) {
  const normalized = String(email || '').trim().toLowerCase();
  if (!normalized) return null;
  const result = await query(
    `SELECT ${USER_COLUMNS}
     FROM users
     WHERE lower(email) = $1 AND deleted_at IS NULL
     LIMIT 1`,
    [normalized],
  );
  return mapUser(result.rows[0]);
}

export async function findById(id) {
  const result = await query(
    `SELECT ${USER_COLUMNS}
     FROM users
     WHERE id = $1 AND deleted_at IS NULL
     LIMIT 1`,
    [String(id)],
  );
  return mapUser(result.rows[0]);
}

export async function createPasswordUser({ email, firstName, lastName, passwordHash }) {
  const result = await query(
    `INSERT INTO users (
       email, first_name, last_name, password_hash,
       auth_provider, role, token_version, created_at, updated_at
     ) VALUES (
       $1, $2, $3, $4,
       'password', 'user', 1, NOW(), NOW()
     )
     RETURNING ${USER_COLUMNS}`,
    [
      String(email).trim().toLowerCase(),
      String(firstName || '').trim() || null,
      String(lastName || '').trim() || null,
      passwordHash,
    ],
  );
  return mapUser(result.rows[0]);
}

export async function createGoogleUser({ email, firstName, lastName, profilePic }) {
  const placeholderHash = await hashPassword(randomUUID());
  const result = await query(
    `INSERT INTO users (
       email, first_name, last_name, password_hash, profile_pic,
       auth_provider, role, token_version, created_at, updated_at
     ) VALUES (
       $1, $2, $3, $4, $5,
       'google', 'user', 1, NOW(), NOW()
     )
     RETURNING ${USER_COLUMNS}`,
    [
      String(email).trim().toLowerCase(),
      String(firstName || '').trim() || null,
      String(lastName || '').trim() || null,
      placeholderHash,
      profilePic || null,
    ],
  );
  return mapUser(result.rows[0]);
}

export async function enrichMissingProfile(user, { firstName, lastName, profilePic }) {
  const nextFirst = user.firstName || firstName || null;
  const nextLast = user.lastName || lastName || null;
  const nextPic = user.profilePic || profilePic || null;
  if (nextFirst === user.firstName && nextLast === user.lastName && nextPic === user.profilePic) {
    return user;
  }
  const result = await query(
    `UPDATE users
     SET first_name = $2,
         last_name = $3,
         profile_pic = $4,
         updated_at = NOW()
     WHERE id = $1 AND deleted_at IS NULL
     RETURNING ${USER_COLUMNS}`,
    [user.id, nextFirst, nextLast, nextPic],
  );
  return mapUser(result.rows[0]) || user;
}

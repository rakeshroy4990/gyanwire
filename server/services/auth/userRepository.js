import { randomUUID } from 'node:crypto';
import { query } from '../../db/pool.js';
import { hashPassword } from './password.js';

/**
 * Maps the shared Supabase hospital `users` table (project xbefsiwpieeznbkbahab).
 * Do not invent a parallel users schema for Gyanwire.
 */
function mapUser(row) {
  if (!row) return null;
  return {
    id: row.id,
    externalId: row.external_id,
    email: row.email,
    firstName: row.first_name,
    lastName: row.last_name,
    passwordHash: row.password_hash,
    role: row.role,
    roleStatus: row.role_status,
    active: row.active,
    tokenVersion: Number(row.token_version || 1),
    profilePic: row.profile_pic,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

export async function findByEmail(email) {
  const normalized = String(email || '').trim().toLowerCase();
  if (!normalized) return null;
  const result = await query(
    `SELECT id, external_id, email, first_name, last_name, password_hash,
            role, role_status, active, token_version, profile_pic, created_at, updated_at
     FROM users
     WHERE lower(email) = $1 AND deleted = false
     LIMIT 1`,
    [normalized],
  );
  return mapUser(result.rows[0]);
}

export async function findById(id) {
  const result = await query(
    `SELECT id, external_id, email, first_name, last_name, password_hash,
            role, role_status, active, token_version, profile_pic, created_at, updated_at
     FROM users
     WHERE id = $1 AND deleted = false
     LIMIT 1`,
    [String(id)],
  );
  return mapUser(result.rows[0]);
}

export async function createPasswordUser({ email, firstName, lastName, passwordHash }) {
  const id = randomUUID();
  const result = await query(
    `INSERT INTO users (
       id, email, first_name, last_name, password_hash,
       role, role_status, active, token_version, deleted, created_at, updated_at
     ) VALUES (
       $1, $2, $3, $4, $5,
       'PATIENT', 'ACTIVE', true, 1, false, NOW(), NOW()
     )
     RETURNING id, external_id, email, first_name, last_name, password_hash,
               role, role_status, active, token_version, profile_pic, created_at, updated_at`,
    [
      id,
      String(email).trim().toLowerCase(),
      String(firstName || '').trim() || null,
      String(lastName || '').trim() || null,
      passwordHash,
    ],
  );
  return mapUser(result.rows[0]);
}

export async function createGoogleUser({ email, firstName, lastName, profilePic }) {
  const id = randomUUID();
  const placeholderHash = await hashPassword(randomUUID());
  const result = await query(
    `INSERT INTO users (
       id, email, first_name, last_name, password_hash, profile_pic,
       role, role_status, active, token_version, deleted, created_at, updated_at
     ) VALUES (
       $1, $2, $3, $4, $5, $6,
       'PATIENT', 'ACTIVE', true, 1, false, NOW(), NOW()
     )
     RETURNING id, external_id, email, first_name, last_name, password_hash,
               role, role_status, active, token_version, profile_pic, created_at, updated_at`,
    [
      id,
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
     WHERE id = $1 AND deleted = false
     RETURNING id, external_id, email, first_name, last_name, password_hash,
               role, role_status, active, token_version, profile_pic, created_at, updated_at`,
    [user.id, nextFirst, nextLast, nextPic],
  );
  return mapUser(result.rows[0]) || user;
}

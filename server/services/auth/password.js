import bcrypt from 'bcryptjs';

const ROUNDS = 12;

export async function hashPassword(rawPassword) {
  return bcrypt.hash(String(rawPassword), ROUNDS);
}

export async function verifyPassword(rawPassword, passwordHash) {
  if (!passwordHash) return false;
  return bcrypt.compare(String(rawPassword), passwordHash);
}

export function validatePasswordPolicy(rawPassword) {
  const password = String(rawPassword ?? '');
  if (password.length < 8) {
    return 'Password must be at least 8 characters.';
  }
  if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) {
    return 'Password must include at least one letter and one number.';
  }
  return null;
}

import pg from 'pg';

const { Pool } = pg;

let pool;

/**
 * Datasource resolution (Gyanwire-owned DB):
 *   Prefer GYANWIRE_DATABASE_URL (postgresql://...)
 *   Fallback: DATABASE_URL (local docker-compose)
 */
export function isPostgresPersistenceEnabled() {
  const provider = String(process.env.APP_PERSISTENCE_PROVIDER || '').trim().toLowerCase();
  if (provider && provider !== 'postgres') return false;
  return Boolean(resolveDatasourceConfig());
}

export function resolveDatasourceConfig() {
  const gyanwireUrl = String(process.env.GYANWIRE_DATABASE_URL || '').trim();
  if (gyanwireUrl) {
    return { connectionUrl: new URL(gyanwireUrl), source: 'gyanwire' };
  }

  const databaseUrl = String(process.env.DATABASE_URL || '').trim();
  if (databaseUrl) {
    return { connectionUrl: new URL(databaseUrl), source: 'database_url' };
  }

  return null;
}

function buildPoolConfig() {
  const resolved = resolveDatasourceConfig();
  if (!resolved) {
    throw new Error(
      'Postgres is not configured. Set GYANWIRE_DATABASE_URL '
      + '(or DATABASE_URL for local docker).',
    );
  }

  const url = resolved.connectionUrl;
  url.searchParams.delete('sslmode');
  url.searchParams.delete('ssl');

  const useSsl = process.env.DATABASE_SSL !== 'false'
    && (process.env.DATABASE_SSL === 'true'
      || url.hostname.includes('supabase.com')
      || url.hostname.includes('pooler.supabase.com')
      || url.hostname.includes('neon.tech'));

  return {
    connectionString: url.toString(),
    ssl: useSsl ? { rejectUnauthorized: false } : undefined,
    max: Number(process.env.DATABASE_POOL_MAX || 5),
  };
}

export function getPool() {
  if (pool) return pool;
  pool = new Pool(buildPoolConfig());
  pool.on('error', (err) => {
    console.error('Unexpected PostgreSQL pool error', err.message);
  });
  return pool;
}

/** Test helper — clears the cached pool between vitest cases. */
export function resetPoolForTests() {
  pool = undefined;
}

export async function query(text, params = []) {
  return getPool().query(text, params);
}

import pg from 'pg';

const { Pool } = pg;

let pool;

/**
 * Hospital-compatible datasource resolution:
 *   APP_PERSISTENCE_PROVIDER=postgres
 *   SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/postgres
 *   SPRING_DATASOURCE_USERNAME=...
 *   SPRING_DATASOURCE_PASSWORD=...
 * Optional fallback: DATABASE_URL=postgresql://...
 */
export function isPostgresPersistenceEnabled() {
  const provider = String(process.env.APP_PERSISTENCE_PROVIDER || '').trim().toLowerCase();
  if (provider && provider !== 'postgres') return false;
  return Boolean(resolveDatasourceConfig());
}

function jdbcToPostgresUrl(jdbcUrl) {
  const raw = String(jdbcUrl || '').trim();
  if (!raw) return '';
  if (raw.startsWith('postgresql://') || raw.startsWith('postgres://')) return raw;
  if (raw.startsWith('jdbc:postgresql://')) {
    return `postgresql://${raw.slice('jdbc:postgresql://'.length)}`;
  }
  throw new Error(
    'SPRING_DATASOURCE_URL must be jdbc:postgresql://... or postgresql://...',
  );
}

export function resolveDatasourceConfig() {
  const springUrl = String(process.env.SPRING_DATASOURCE_URL || '').trim();
  const springUser = String(process.env.SPRING_DATASOURCE_USERNAME || '').trim();
  const springPassword = String(process.env.SPRING_DATASOURCE_PASSWORD || '');
  const legacyUrl = String(process.env.DATABASE_URL || '').trim();

  if (springUrl) {
    const url = new URL(jdbcToPostgresUrl(springUrl));
    if (springUser) url.username = springUser;
    if (springPassword !== '') url.password = springPassword;
    return { connectionUrl: url, source: 'spring' };
  }

  if (legacyUrl) {
    return { connectionUrl: new URL(legacyUrl), source: 'database_url' };
  }

  return null;
}

function buildPoolConfig() {
  const resolved = resolveDatasourceConfig();
  if (!resolved) {
    throw new Error(
      'Postgres is not configured. Set APP_PERSISTENCE_PROVIDER=postgres and '
      + 'SPRING_DATASOURCE_URL / SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD '
      + '(same variables as hospital).',
    );
  }

  const url = resolved.connectionUrl;
  url.searchParams.delete('sslmode');
  url.searchParams.delete('ssl');

  const useSsl = process.env.DATABASE_SSL !== 'false'
    && (process.env.DATABASE_SSL === 'true'
      || url.hostname.includes('supabase.com')
      || url.hostname.includes('pooler.supabase.com')
      || resolved.source === 'spring');

  return {
    connectionString: url.toString(),
    ssl: useSsl ? { rejectUnauthorized: false } : undefined,
    max: Number(process.env.DATABASE_POOL_MAX || process.env.SPRING_DATASOURCE_MAX_POOL_SIZE || 5),
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

export async function query(text, params = []) {
  return getPool().query(text, params);
}

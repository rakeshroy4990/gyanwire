import { afterEach, describe, expect, it } from 'vitest';
import { resetPoolForTests, resolveDatasourceConfig } from '../db/pool.js';

const ORIGINAL = { ...process.env };

afterEach(() => {
  for (const key of Object.keys(process.env)) {
    if (!(key in ORIGINAL)) delete process.env[key];
  }
  Object.assign(process.env, ORIGINAL);
  resetPoolForTests();
});

describe('resolveDatasourceConfig', () => {
  it('prefers GYANWIRE_DATABASE_URL over DATABASE_URL', () => {
    process.env.GYANWIRE_DATABASE_URL = 'postgresql://u:p@localhost:5432/gyanwire';
    process.env.DATABASE_URL = 'postgresql://other@localhost:5432/other';

    const resolved = resolveDatasourceConfig();
    expect(resolved.source).toBe('gyanwire');
    expect(resolved.connectionUrl.pathname).toBe('/gyanwire');
  });

  it('falls back to DATABASE_URL', () => {
    delete process.env.GYANWIRE_DATABASE_URL;
    process.env.DATABASE_URL = 'postgresql://u:p@localhost:5433/gyanwire';
    const resolved = resolveDatasourceConfig();
    expect(resolved.source).toBe('database_url');
    expect(resolved.connectionUrl.port).toBe('5433');
  });

  it('returns null when unset', () => {
    delete process.env.GYANWIRE_DATABASE_URL;
    delete process.env.DATABASE_URL;
    expect(resolveDatasourceConfig()).toBeNull();
  });
});

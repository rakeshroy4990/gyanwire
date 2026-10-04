import { describe, expect, it } from 'vitest';
import { evaluateSearchLimit, ANON_DAILY_SEARCH_LIMIT } from '../services/billing/usageService.js';

describe('evaluateSearchLimit', () => {
  it('allows free users for the first 5 searches and blocks the 6th', () => {
    expect(evaluateSearchLimit({ used: 0, limit: 5 })).toEqual({
      allowed: true,
      used: 0,
      limit: 5,
      remaining: 5,
    });
    expect(evaluateSearchLimit({ used: 4, limit: 5 }).allowed).toBe(true);
    expect(evaluateSearchLimit({ used: 5, limit: 5 })).toEqual({
      allowed: false,
      used: 5,
      limit: 5,
      remaining: 0,
    });
  });

  it('uses the anonymous daily cap of 2', () => {
    expect(ANON_DAILY_SEARCH_LIMIT).toBe(2);
    expect(evaluateSearchLimit({ used: 1, limit: ANON_DAILY_SEARCH_LIMIT }).remaining).toBe(1);
    expect(evaluateSearchLimit({ used: 2, limit: ANON_DAILY_SEARCH_LIMIT }).allowed).toBe(false);
  });
});

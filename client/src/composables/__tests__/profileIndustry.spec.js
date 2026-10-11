import { describe, expect, it } from 'vitest';
import { profileInterestIndustry } from '../profileIndustry.js';

const catalog = ['Share Market', 'IT', 'Medical', 'Space'];

describe('profileInterestIndustry', () => {
  it('uses the first saved industry that exists in the catalog', () => {
    expect(profileInterestIndustry(['Medical', 'IT'], catalog)).toBe('Medical');
  });

  it('skips a blank or All value and keeps the next real industry', () => {
    expect(profileInterestIndustry(['', 'All', 'Space'], catalog)).toBe('Space');
  });

  it('returns null when the saved industry is not in the catalog', () => {
    expect(profileInterestIndustry(['Farming'], catalog)).toBeNull();
  });

  it('returns null when the profile has no industries', () => {
    expect(profileInterestIndustry(undefined, catalog)).toBeNull();
    expect(profileInterestIndustry([], catalog)).toBeNull();
  });
});

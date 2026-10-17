import { describe, expect, it } from 'vitest';
import {
  homeFeedIndustry,
  needsProfileForHomeFeed,
  profileInterestIndustry,
} from '../profileIndustry.js';

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

describe('needsProfileForHomeFeed', () => {
  it('skips the profile fetch for signed-out visitors', () => {
    expect(needsProfileForHomeFeed(false, null)).toBe(false);
  });

  it('fetches when signed in and the store is empty', () => {
    expect(needsProfileForHomeFeed(true, null)).toBe(true);
  });

  it('skips the fetch when the store already has a profile', () => {
    expect(needsProfileForHomeFeed(true, { industries: ['IT'] })).toBe(false);
  });
});

describe('homeFeedIndustry', () => {
  it('uses the profile industry when it matches the catalog', () => {
    expect(homeFeedIndustry(['IT'], catalog)).toBe('IT');
  });

  it('falls back to AI when the profile has no catalog match', () => {
    expect(homeFeedIndustry(['Farming'], catalog)).toBe('AI');
    expect(homeFeedIndustry([], catalog)).toBe('AI');
    expect(homeFeedIndustry(undefined, catalog)).toBe('AI');
  });
});

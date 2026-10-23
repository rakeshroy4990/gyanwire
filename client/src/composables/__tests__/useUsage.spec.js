import { expect, it } from 'vitest';
import { formatIdeaButtonLabel, UNLIMITED_IDEA_CAP } from '../useUsage.js';

it('does not print the unlimited idea cap on the button', () => {
  expect(formatIdeaButtonLabel({
    ready: true,
    loading: false,
    limitReached: false,
    remaining: UNLIMITED_IDEA_CAP,
    limit: UNLIMITED_IDEA_CAP,
  })).toBe('Get an idea');
});

it('shows a real remaining count', () => {
  expect(formatIdeaButtonLabel({
    ready: true,
    loading: false,
    limitReached: false,
    remaining: 2,
    limit: 3,
  })).toBe('Get an idea (2 left)');
});

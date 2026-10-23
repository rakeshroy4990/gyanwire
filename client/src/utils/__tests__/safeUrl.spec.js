import { describe, expect, it } from 'vitest';
import { safeHttpUrl } from '../safeUrl.js';

describe('safeHttpUrl', () => {
  it('allows http and https', () => {
    expect(safeHttpUrl('https://inc42.com/a')).toContain('https://inc42.com/a');
    expect(safeHttpUrl('http://example.com')).toContain('http://example.com');
  });

  it('blocks javascript and data urls', () => {
    expect(safeHttpUrl('javascript:alert(1)')).toBe('');
    expect(safeHttpUrl('data:text/html,hi')).toBe('');
    expect(safeHttpUrl('')).toBe('');
  });
});

import { describe, expect, it } from 'vitest';
import { catalogExplain } from '../catalogExplain.js';

describe('catalogExplain', () => {
  it('explains SWAYAM with a public link and meaning', () => {
    const row = catalogExplain('swayam');
    expect(row.url).toBe('https://swayam.gov.in');
    expect(row.blurb).toMatch(/government learning platform/i);
    expect(row.blurb).toMatch(/not a SEBI/i);
    expect(row.hasExplain).toBe(true);
    expect(row.opensNews).toBe(false);
  });

  it('points SWAYAM at the plan source news when newsUrl is present', () => {
    const row = catalogExplain('swayam', null, {
      newsUrl: 'https://news.example/cas',
      newsTitle: 'Sebi’s CAS guidelines likely within a week',
    });
    expect(row.url).toBe('https://news.example/cas');
    expect(row.opensNews).toBe(true);
    expect(row.blurb).toMatch(/source news/i);
    expect(row.blurb).toMatch(/CAS guidelines/i);
  });

  it('prefers API fields when present and there is no news override', () => {
    const row = catalogExplain('vscode', {
      url: 'https://example.com/vscode',
      blurb: 'Custom blurb',
    });
    expect(row.url).toBe('https://example.com/vscode');
    expect(row.blurb).toBe('Custom blurb');
  });
});

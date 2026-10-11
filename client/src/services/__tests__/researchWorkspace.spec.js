import { beforeEach, describe, expect, it } from 'vitest';
import {
  clearResearchReturn,
  consumeResearchHome,
  findingSelector,
  hasResearchReturn,
  loadResearchReturn,
  loadResearchWorkspace,
  markResearchHome,
  rememberResearchReturn,
  saveResearchWorkspace,
} from '../researchWorkspace.js';

const store = new Map();

beforeEach(() => {
  store.clear();
  globalThis.sessionStorage = {
    getItem: (key) => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => store.set(key, String(value)),
    removeItem: (key) => store.delete(key),
  };
});

describe('researchWorkspace', () => {
  it('round-trips the industry, subcategory, and findings', () => {
    saveResearchWorkspace({
      industry: 'Medical',
      subcategory: 'Cardiology',
      thoughts: 'heart devices',
      thread: [],
      browseResults: [{ url: 'https://news.example/a', title: 'Stent' }],
      browseSub: 'Medical · Cardiology',
      browseFreshness: 'last 14 days',
      browseNote: '',
      emptyMessage: '',
      showEmpty: false,
    });

    expect(loadResearchWorkspace()).toMatchObject({
      industry: 'Medical',
      subcategory: 'Cardiology',
      thoughts: 'heart devices',
      browseSub: 'Medical · Cardiology',
      showEmpty: false,
      browseResults: [{ url: 'https://news.example/a', title: 'Stent' }],
      followProfile: true,
      country: null,
      browseCountries: [],
    });
  });

  it('keeps the country filter with the findings', () => {
    saveResearchWorkspace({
      industry: 'IT',
      country: 'US',
      browseCountries: [{ code: 'IN', name: 'India' }, { code: 'US', name: 'United States' }],
      showEmpty: false,
    });
    expect(loadResearchWorkspace()).toMatchObject({
      country: 'US',
      browseCountries: [{ code: 'IN', name: 'India' }, { code: 'US', name: 'United States' }],
    });
  });

  it('remembers a manual industry pick separately from the profile feed', () => {
    saveResearchWorkspace({
      industry: 'Gaming',
      followProfile: false,
      showEmpty: true,
    });
    expect(loadResearchWorkspace()).toMatchObject({
      industry: 'Gaming',
      followProfile: false,
    });
  });

  it('treats a logo-home request as a reason to skip the saved selection', () => {
    markResearchHome();
    expect(consumeResearchHome()).toBe(true);
    expect(consumeResearchHome()).toBe(false);
  });

  it('settles an in-flight search so Back does not restore a spinner', () => {
    saveResearchWorkspace({
      industry: 'IT',
      subcategory: null,
      thread: [{ id: 't1', status: 'streaming', results: [], error: '' }],
      browseResults: [],
      showEmpty: false,
    });

    const saved = loadResearchWorkspace();
    expect(saved.thread[0].status).toBe('error');
    expect(saved.thread[0].error).toMatch(/interrupted/);
  });

  it('remembers the finding opened from Get an idea', () => {
    rememberResearchReturn({ id: 'n1', url: 'https://news.example/a' }, 480);
    expect(hasResearchReturn()).toBe(true);
    expect(loadResearchReturn()).toEqual({
      id: 'n1',
      url: 'https://news.example/a',
      scrollY: 480,
    });
    expect(findingSelector(loadResearchReturn())).toBe('[data-finding-id="n1"]');
    clearResearchReturn();
    expect(hasResearchReturn()).toBe(false);
    expect(loadResearchReturn()).toBeNull();
  });
});

import { describe, expect, it } from 'vitest';
import { presentGitHubFinding } from '../githubFinding.js';

describe('presentGitHubFinding', () => {
  it('shows what the repository is instead of signal labels', () => {
    const card = presentGitHubFinding({
      title: 'deepseek-ai/deepseek-harness',
      url: 'https://github.com/deepseek-ai/deepseek-harness',
      domain: 'github.com',
      industry: 'GitHub',
      source: 'GitHub Search',
      countryName: 'India',
      signals: ['stars', 'tool', 'tech'],
      relevanceReason: 'Signals: Stars · Tool · Tech — opportunity to build or cover.',
      description: 'DeepSeek Harness: Everything is a Plugin. Open-source GitHub project with 253.0k stars and 30.4k forks. Language: TypeScript. Licence: MIT. Last push: 2026-10-17. Famous library/framework/tool developers build products on — use it as a seed for India-first business ideas.',
    });

    expect(card.title).toBe('deepseek-ai/deepseek-harness — DeepSeek Harness: Everything is a Plugin.');
    expect(card.host).toBe('github.com/deepseek-ai/deepseek-harness');
    expect(card.why).toContain('253.0k stars');
    expect(card.why).toContain('TypeScript');
    expect(card.why).toContain('MIT');
    expect(card.why).not.toMatch(/india/i);
    expect(card.why).not.toMatch(/Signals:/);
    expect(card.signals).toEqual([]);
    expect(card.countryName).toBe('');
  });

  it('does not repeat a description already stored on the title', () => {
    const card = presentGitHubFinding({
      title: 'n8n-io/n8n — Fair-code workflow automation platform with native AI capabilities. Combine visual building with custom code, self-host…',
      url: 'https://github.com/n8n-io/n8n',
      domain: 'github.com',
      description: 'Fair-code workflow automation platform with native AI capabilities. Combine visual building with custom code, self-host or cloud, 400+ integrations. Open-source GitHub project with 207.9k stars and 61.2k forks. Language: TypeScript. Licence: Other. Last push: 2026-10-18.',
    });

    expect(card.title).toBe('n8n-io/n8n — Fair-code workflow automation platform with native AI capabilities. Combine visual building with custom code, self-host or cloud, 400+ integrations.');
    expect(card.title.match(/Fair-code/g)).toHaveLength(1);
  });
});
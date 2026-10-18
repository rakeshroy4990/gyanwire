import { afterEach, expect, it, vi } from 'vitest';
import { fetchIndustryNewsChunks, INDUSTRY_WINDOWS, WARMING_RETRY_MS } from '../news.service.js';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.useRealTimers();
});

function jsonResponse(data) {
  return new Response(JSON.stringify({ success: true, data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function ndjsonResponse(lines) {
  return new Response(`${lines.join('\n')}\n`, {
    status: 200,
    headers: { 'Content-Type': 'application/x-ndjson' },
  });
}

it('loads industry findings in one 30d request', async () => {
  const urls = [];
  vi.stubGlobal('fetch', vi.fn(async (url) => {
    urls.push(String(url));
    return jsonResponse({
      results: [{ title: 'Week launch', url: 'https://a.example/7' }],
      freshness: 'last 7 days',
      windowDays: 7,
      total: 1,
      returned: 1,
      page: 1,
      label: 'AI products',
    });
  }));

  const chunks = [];
  const data = await fetchIndustryNewsChunks({
    industry: 'AI',
    onChunk: async (chunk, windowDays) => {
      chunks.push({ windowDays, title: chunk.results[0].title, freshness: chunk.freshness });
    },
  });

  expect(INDUSTRY_WINDOWS).toEqual([1, 7, 30]);
  expect(urls).toHaveLength(1);
  expect(urls[0]).toContain('window=30d');
  expect(chunks).toEqual([
    { windowDays: 7, title: 'Week launch', freshness: 'last 7 days' },
  ]);
  expect(data.windowDays).toBe(7);
});

it('paints the first streamed item before the response finishes', async () => {
  const painted = [];
  vi.stubGlobal('fetch', vi.fn(async () => ndjsonResponse([
    JSON.stringify({ type: 'open', engine: 'gyanwire' }),
    JSON.stringify({
      type: 'meta',
      success: true,
      freshness: 'fresh',
      windowDays: 1,
      paintFirst: 1,
      results: [
        { title: 'Today launch', url: 'https://a.example/1' },
        { title: 'Week launch', url: 'https://a.example/7' },
      ],
    }),
    JSON.stringify({ type: 'item', item: { title: 'Today launch', url: 'https://a.example/1' } }),
    JSON.stringify({ type: 'item', item: { title: 'Week launch', url: 'https://a.example/7' } }),
    JSON.stringify({ type: 'done', success: true, returned: 2 }),
  ])));

  const data = await fetchIndustryNewsChunks({
    industry: 'AI',
    onItem: async (item) => {
      painted.push(item.title);
    },
  });

  expect(painted[0]).toBe('Today launch');
  expect(painted).toEqual(['Today launch', 'Week launch']);
  expect(data.freshness).toBe('fresh');
  expect(data.returned).toBe(2);
});

it('retries once when the first response is warming_up with no rows', async () => {
  vi.useFakeTimers();
  const urls = [];
  vi.stubGlobal('fetch', vi.fn(async (url) => {
    urls.push(String(url));
    if (urls.length === 1) {
      return jsonResponse({
        results: [],
        freshness: 'warming_up',
        windowDays: 30,
        returned: 0,
        page: 1,
      });
    }
    return jsonResponse({
      results: [{ title: 'Late launch', url: 'https://a.example/late' }],
      freshness: 'fresh',
      windowDays: 1,
      returned: 1,
      page: 1,
    });
  }));

  const pending = fetchIndustryNewsChunks({ industry: 'AI' });
  await vi.advanceTimersByTimeAsync(WARMING_RETRY_MS);
  const data = await pending;

  expect(urls).toHaveLength(2);
  expect(urls.every((url) => url.includes('window=30d'))).toBe(true);
  expect(data.results[0].title).toBe('Late launch');
  expect(data.freshness).toBe('fresh');
});

it('settles empty after warming_up retry still has no rows', async () => {
  vi.useFakeTimers();
  const urls = [];
  const chunks = [];
  vi.stubGlobal('fetch', vi.fn(async (url) => {
    urls.push(String(url));
    return jsonResponse({
      results: [],
      freshness: 'warming_up',
      windowDays: 30,
      returned: 0,
      page: 1,
      message: 'No business-relevant news from pack sources yet.',
    });
  }));

  const pending = fetchIndustryNewsChunks({
    industry: 'AI',
    sub: 'Anthropic',
    onChunk: async (chunk) => {
      chunks.push(chunk.freshness);
    },
  });
  await vi.advanceTimersByTimeAsync(WARMING_RETRY_MS);
  const data = await pending;

  expect(urls).toHaveLength(2);
  expect(urls.every((url) => url.includes('sub=Anthropic'))).toBe(true);
  expect(chunks).toEqual(['warming_up', 'last 30 days']);
  expect(data.freshness).toBe('last 30 days');
  expect(data.results).toEqual([]);
  expect(data.message).toContain('No business-relevant news');
});

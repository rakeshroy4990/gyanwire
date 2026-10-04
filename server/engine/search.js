import { discoverPages } from './discover.js';
import { sortIndiaFirst } from './india.js';
import { scoreWithPointers } from './pointers.js';
import { scrapeMany } from './scrape.js';

/**
 * Custom Gyanwire search engine.
 * Discover → scrape → score with your pointers.
 * India results are preferred before global ones.
 */
export async function searchWeb({
  query,
  categories = [],
  thoughts = '',
  limit = 6,
}) {
  const discoverLimit = Math.min(Math.max(limit * 2, 8), 16);
  const discovered = await discoverPages(query, discoverLimit, { categories });

  const scraped = await scrapeMany(
    discovered.map((item) => item.url),
    { concurrency: 4, timeoutMs: 8000 },
  );

  const pages = discovered.map((item, index) => {
    const page = scraped.get(item.url);
    return {
      id: `r-${index + 1}`,
      title: page?.title || item.title || 'Untitled page',
      url: item.url,
      description: page?.description || item.description || page?.snippet || 'No summary available.',
      snippet: page?.snippet || item.description || '',
      text: page?.text || '',
      source: item.source,
      position: index + 1,
      scraped: Boolean(page?.ok),
    };
  });

  const ranked = sortIndiaFirst(
    pages.map((page, index) => {
      const pointer = scoreWithPointers({
        page,
        categories,
        thoughts,
        query,
        discoveryIndex: index,
      });

      return {
        ...page,
        score: pointer.score,
        why: pointer.why,
        pointers: pointer.breakdown,
        usedLlm: false,
      };
    }),
  )
    .slice(0, limit)
    .map((item, index) => ({
      ...item,
      id: `r-${index + 1}`,
    }));

  return ranked;
}

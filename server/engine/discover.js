import * as cheerio from 'cheerio';
import { indiaFirstQueries, sortIndiaFirst, withIndiaBias } from './india.js';

const BROWSER_UA =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36';

const BOT_UA =
  'GyanwireBot/1.0 (+local research tool; respectful fetch; contact: local)';

/**
 * Discover candidate pages for a query.
 * India-first: search Indian context first, then broader web.
 */
export async function discoverPages(query, limit = 12, { categories = [] } = {}) {
  const seen = new Set();
  const results = [];

  const push = (item) => {
    if (!item?.url || seen.has(item.url)) return;
    if (!isHttpUrl(item.url) || isBlockedDiscoveryUrl(item.url)) return;
    seen.add(item.url);
    results.push({
      title: cleanText(item.title) || 'Untitled page',
      url: item.url,
      description: cleanText(item.description) || '',
      source: item.source || 'web',
    });
  };

  const queryVariants = indiaFirstQueries(query);

  for (const variant of queryVariants) {
    if (results.length >= limit) break;

    const sources = [
      () => searchDuckDuckGoHtml(variant, limit),
      () => searchWikipedia(shortQuery(variant), Math.max(3, Math.ceil(limit / 3))),
      () => searchSearxng(variant, limit),
    ];

    for (const source of sources) {
      if (results.length >= limit) break;
      try {
        const batch = await source();
        batch.forEach(push);
      } catch (error) {
        console.error('Discovery source failed:', error.message);
      }
    }
  }

  if (results.length < Math.min(4, limit)) {
    try {
      const seeds = categorySeedPages(categories, withIndiaBias(query));
      seeds.forEach(push);
    } catch (error) {
      console.error('Seed discovery failed:', error.message);
    }
  }

  if (!results.length) {
    const error = new Error(
      'Could not discover pages right now. Check your network and try again.',
    );
    error.status = 502;
    error.code = 'DISCOVERY_EMPTY';
    throw error;
  }

  return sortIndiaFirst(results).slice(0, limit);
}

async function searchDuckDuckGoHtml(query, limit) {
  const response = await fetch('https://html.duckduckgo.com/html/', {
    method: 'POST',
    headers: {
      'User-Agent': BROWSER_UA,
      'Content-Type': 'application/x-www-form-urlencoded',
      Accept: 'text/html,application/xhtml+xml',
      'Accept-Language': 'en-IN,en;q=0.9',
    },
    body: new URLSearchParams({ q: query, b: '', kl: 'in-en' }),
    signal: AbortSignal.timeout(15000),
  });

  if (!response.ok) {
    throw new Error(`DuckDuckGo returned ${response.status}`);
  }

  const html = await response.text();
  if (html.includes('anomaly') && !html.includes('result__a')) {
    throw new Error('DuckDuckGo challenge page returned');
  }

  const $ = cheerio.load(html);
  const results = [];

  $('.result').each((_, element) => {
    if (results.length >= limit) return;

    const anchor = $(element).find('a.result__a').first();
    const href = anchor.attr('href');
    const title = anchor.text();
    const description = $(element).find('.result__snippet').text();
    const resolved = resolveDuckDuckGoUrl(href);

    if (!resolved) return;

    results.push({
      title,
      url: resolved,
      description,
      source: 'duckduckgo',
    });
  });

  return results;
}

async function searchWikipedia(query, limit) {
  if (!query) return [];

  const endpoint =
    `https://en.wikipedia.org/w/api.php?action=opensearch`
    + `&search=${encodeURIComponent(query)}`
    + `&limit=${limit}&namespace=0&format=json`;

  const response = await fetch(endpoint, {
    headers: { 'User-Agent': BOT_UA },
    signal: AbortSignal.timeout(10000),
  });

  if (!response.ok) {
    throw new Error(`Wikipedia returned ${response.status}`);
  }

  const data = await response.json();
  const titles = data[1] || [];
  const descriptions = data[2] || [];
  const urls = data[3] || [];

  return titles.map((title, index) => ({
    title,
    description: descriptions[index] || '',
    url: urls[index],
    source: 'wikipedia',
  })).filter((item) => item.url);
}

async function searchSearxng(query, limit) {
  const base = (process.env.SEARXNG_URL || '').replace(/\/$/, '');
  if (!base || base.includes('your-')) return [];

  const url =
    `${base}/search?q=${encodeURIComponent(query)}`
    + `&format=json&categories=general&language=en`;

  const response = await fetch(url, {
    headers: { 'User-Agent': BOT_UA, Accept: 'application/json' },
    signal: AbortSignal.timeout(12000),
  });

  if (!response.ok) {
    throw new Error(`SearXNG returned ${response.status}`);
  }

  const data = await response.json();
  return (data.results || []).slice(0, limit).map((item) => ({
    title: item.title,
    url: item.url,
    description: item.content || item.description || '',
    source: 'searxng',
  }));
}

function categorySeedPages(categories, query) {
  const seeds = {
    IT: [
      { title: 'arXiv cs', url: 'https://arxiv.org/list/cs/recent', description: 'Computer science research preprints' },
      { title: 'IEEE Xplore', url: 'https://ieeexplore.ieee.org/', description: 'Engineering and computing research' },
      { title: 'ACM Digital Library', url: 'https://dl.acm.org/', description: 'Computing research papers' },
    ],
    Medical: [
      { title: 'PubMed', url: 'https://pubmed.ncbi.nlm.nih.gov/', description: 'Biomedical research literature' },
      { title: 'ClinicalTrials.gov', url: 'https://clinicaltrials.gov/', description: 'Registered clinical studies' },
      { title: 'WHO Health topics', url: 'https://www.who.int/health-topics', description: 'Global health research topics' },
      { title: 'Nature Medicine', url: 'https://www.nature.com/nm/', description: 'Medical research journal' },
    ],
    Space: [
      { title: 'NASA', url: 'https://www.nasa.gov/', description: 'Space research and missions' },
      { title: 'ISRO', url: 'https://www.isro.gov.in/', description: 'Indian space research organisation' },
      { title: 'ESA', url: 'https://www.esa.int/', description: 'European Space Agency research' },
      { title: 'arXiv astro-ph', url: 'https://arxiv.org/list/astro-ph/recent', description: 'Astrophysics research preprints' },
    ],
    'Share Market': [
      { title: 'Moneycontrol Markets', url: 'https://www.moneycontrol.com/stocksmarketsindia/', description: 'Indian share market news and quotes' },
      { title: 'Economic Times Markets', url: 'https://economictimes.indiatimes.com/markets', description: 'Stock market research and analysis' },
      { title: 'LiveMint Markets', url: 'https://www.livemint.com/market', description: 'Latest market research updates' },
      { title: 'NSE India', url: 'https://www.nseindia.com/', description: 'National Stock Exchange of India' },
    ],
    'Social Media': [
      { title: 'Meta Newsroom', url: 'https://about.fb.com/news/', description: 'Instagram, WhatsApp, and Threads product news' },
      { title: 'TikTok Newsroom', url: 'https://newsroom.tiktok.com/', description: 'TikTok product and platform updates' },
      { title: 'X Blog', url: 'https://blog.x.com/', description: 'X product and feature updates' },
      { title: 'LinkedIn Blog', url: 'https://www.linkedin.com/blog/topic/linkedin-news', description: 'LinkedIn product research and launches' },
    ],
  };


  const tokens = shortQuery(query).split(' ').slice(0, 3).join(' ');
  const pages = [];

  for (const category of categories) {
    for (const seed of seeds[category] || []) {
      pages.push({
        ...seed,
        description: tokens
          ? `${seed.description}. Related to: ${tokens}`
          : seed.description,
        source: 'category-seed',
      });
    }
  }

  return pages;
}

function shortQuery(query) {
  const stop = new Set([
    'the', 'and', 'for', 'with', 'from', 'that', 'this', 'into', 'want',
    'need', 'have', 'just', 'about', 'simple', 'really', 'very', 'your',
    'my', 'a', 'an', 'of', 'to', 'in', 'on', 'at', 'by', 'or',
  ]);

  return String(query || '')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s'-]/gu, ' ')
    .split(/\s+/)
    .filter((token) => token.length > 2 && !stop.has(token))
    .slice(0, 6)
    .join(' ');
}

function resolveDuckDuckGoUrl(href) {
  if (!href) return '';
  try {
    const parsed = new URL(href, 'https://duckduckgo.com');
    if (parsed.pathname === '/l/' || parsed.searchParams.has('uddg')) {
      return decodeURIComponent(parsed.searchParams.get('uddg') || '');
    }
    return parsed.toString();
  } catch {
    return '';
  }
}

function isHttpUrl(value) {
  try {
    const parsed = new URL(value);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch {
    return false;
  }
}

function isBlockedDiscoveryUrl(url) {
  try {
    const host = new URL(url).hostname.replace(/^www\./, '');
    return (
      host === 'duckduckgo.com'
      || host.endsWith('.duckduckgo.com')
      || host === 'youtube.com'
      || host === 'youtu.be'
      || host === 'google.com'
      || host.endsWith('.google.com')
    );
  } catch {
    return true;
  }
}

function cleanText(value) {
  return String(value || '').replace(/\s+/g, ' ').trim();
}

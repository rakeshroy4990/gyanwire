import * as cheerio from 'cheerio';

const USER_AGENT =
  'GyanwireBot/1.0 (+local research tool; respectful fetch; contact: local)';

/**
 * Fetch a page and extract main text, similar in spirit to Firecrawl scrape,
 * but owned and tunable here.
 */
export async function scrapePage(url, { timeoutMs = 8000 } = {}) {
  try {
    const response = await fetch(url, {
      headers: {
        'User-Agent': USER_AGENT,
        Accept: 'text/html,application/xhtml+xml',
      },
      redirect: 'follow',
      signal: AbortSignal.timeout(timeoutMs),
    });

    if (!response.ok) {
      return emptyPage(url, `HTTP ${response.status}`);
    }

    const contentType = response.headers.get('content-type') || '';
    if (!contentType.includes('text/html') && !contentType.includes('application/xhtml')) {
      return emptyPage(url, 'Not an HTML page');
    }

    const html = await response.text();
    return extractContent(url, html);
  } catch (error) {
    return emptyPage(url, error.message || 'Fetch failed');
  }
}

export async function scrapeMany(urls, { concurrency = 4, timeoutMs = 8000 } = {}) {
  const queue = [...urls];
  const output = new Map();

  async function worker() {
    while (queue.length) {
      const url = queue.shift();
      output.set(url, await scrapePage(url, { timeoutMs }));
    }
  }

  await Promise.all(
    Array.from({ length: Math.min(concurrency, urls.length) }, () => worker()),
  );

  return output;
}

function extractContent(url, html) {
  const $ = cheerio.load(html);

  $('script, style, noscript, svg, iframe, form, nav, footer, header, aside').remove();
  $('[role="navigation"], [role="banner"], [role="contentinfo"], .cookie, .ads, .advert').remove();

  const title =
    $('meta[property="og:title"]').attr('content')
    || $('title').first().text()
    || $('h1').first().text()
    || '';

  const description =
    $('meta[name="description"]').attr('content')
    || $('meta[property="og:description"]').attr('content')
    || '';

  const root =
    $('article').first().length ? $('article').first()
      : $('main').first().length ? $('main').first()
        : $('[role="main"]').first().length ? $('[role="main"]').first()
          : $('body');

  const blocks = [];
  root.find('h1, h2, h3, p, li').each((_, element) => {
    const text = cleanText($(element).text());
    if (text.length >= 40 || /^(h1|h2|h3)$/i.test(element.tagName)) {
      blocks.push(text);
    }
  });

  let text = blocks.join('\n');
  if (text.length < 180) {
    text = cleanText(root.text());
  }

  text = text.slice(0, 8000);
  const snippet = text.replace(/\s+/g, ' ').trim().slice(0, 420);

  return {
    url,
    title: cleanText(title),
    description: cleanText(description),
    text,
    snippet,
    ok: Boolean(snippet || description || title),
  };
}

function emptyPage(url, reason) {
  return {
    url,
    title: '',
    description: '',
    text: '',
    snippet: '',
    ok: false,
    reason,
  };
}

function cleanText(value) {
  return String(value || '').replace(/\s+/g, ' ').trim();
}

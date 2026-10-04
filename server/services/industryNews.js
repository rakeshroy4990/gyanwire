import * as cheerio from 'cheerio';
import { discoverPages } from '../engine/discover.js';
import { indiaFirstQueries, sortIndiaFirst, withIndiaBias } from '../engine/india.js';

/**
 * Major industries + high-demand sub-combinations people search most.
 * Sub queries stay product / specialty specific so headlines feel sticky.
 */
export const INDUSTRY_NEWS = {
  IT: {
    label: 'IT products',
    why: 'Hot product move in IT.',
    queries: [
      'NVIDIA GPU AI chip product launch OR earnings',
      'ChatGPT OpenAI product update OR API release',
      'Microsoft Copilot product update enterprise',
    ],
    blockGeneric: ['share market live', 'stock market today', 'nifty closes'],
    subs: {
      AI: {
        label: 'AI products',
        why: 'AI product people are watching.',
        queries: [
          'ChatGPT Claude Gemini product update',
          'OpenAI API GPT model launch news',
          'enterprise AI copilot product release',
        ],
      },
      Semiconductors: {
        label: 'Chip products',
        why: 'Semiconductor product catalyst.',
        queries: [
          'NVIDIA AMD Intel AI chip launch',
          'TSMC semiconductor capacity OR node news',
          'GPU accelerator product announcement',
        ],
      },
      Cybersecurity: {
        label: 'Security products',
        why: 'Cybersecurity product move.',
        queries: [
          'CrowdStrike Palo Alto cybersecurity product',
          'zero trust security product launch',
          'ransomware security tool update news',
        ],
      },
      Cloud: {
        label: 'Cloud products',
        why: 'Cloud product people use.',
        queries: [
          'AWS Azure Google Cloud product launch',
          'Kubernetes serverless cloud platform update',
          'SaaS infrastructure cloud product news',
        ],
      },
    },
  },
  Medical: {
    label: 'Medical products',
    why: 'Medical product or therapy breakthrough.',
    queries: [
      'Ozempic Wegovy drug trial OR FDA approval',
      'CRISPR gene therapy clinical trial results',
      'mRNA vaccine cancer therapy research news',
    ],
    blockGeneric: ['share market live', 'stock market today'],
    subs: {
      Paediatrics: {
        label: 'Paediatrics products',
        why: 'Child-health product or vaccine news.',
        queries: [
          'paediatric vaccine childhood drug trial',
          'pediatric rare disease therapy FDA',
          'child health medical device approval news',
        ],
      },
      Cardiology: {
        label: 'Cardiology products',
        why: 'Heart-care product or therapy news.',
        queries: [
          'cardiology stent pacemaker FDA OR trial',
          'heart failure drug clinical trial results',
          'Apple Watch ECG cardiac monitoring product',
        ],
      },
      Gynecology: {
        label: 'Gynecology products',
        why: 'Women’s health product people search.',
        queries: [
          'gynecology fertility IVF product OR trial',
          'OB GYN endometriosis treatment news',
          'women health drug device FDA approval',
        ],
      },
      Oncology: {
        label: 'Oncology products',
        why: 'Cancer therapy product breakthrough.',
        queries: [
          'cancer immunotherapy drug trial results',
          'oncology CAR-T therapy FDA approval',
          'mRNA cancer vaccine clinical trial news',
        ],
      },
      'Mental Health': {
        label: 'Mental health products',
        why: 'Mental health product people seek.',
        queries: [
          'depression anxiety drug clinical trial',
          'digital mental health app therapy product',
          'psychiatry psychedelic therapy research news',
        ],
      },
      Dermatology: {
        label: 'Dermatology products',
        why: 'Skin-care treatment product news.',
        queries: [
          'dermatology eczema psoriasis drug approval',
          'acne biologic skin treatment trial',
          'dermatology medical device laser product news',
        ],
      },
      Autism: {
        label: 'Autism products',
        why: 'Autism therapy, diagnostic, or support product news.',
        queries: [
          'autism therapy drug clinical trial OR FDA',
          'autism diagnosis screening tool digital product',
          'ABA OR speech therapy autism product research news',
          'autism wearable OR AI support product launch',
        ],
      },
    },
  },
  Space: {

    label: 'Space products',
    why: 'Space product or mission update.',
    queries: [
      'SpaceX Starship Falcon 9 launch update',
      'Starlink satellite product news OR expansion',
      'ISRO Chandrayaan OR Gaganyaan mission update',
    ],
    blockGeneric: ['share market live', 'stock market today'],
    subs: {
      Satellites: {
        label: 'Satellite products',
        why: 'Satellite product or constellation news.',
        queries: [
          'Starlink OneWeb satellite constellation news',
          'earth observation satellite product launch',
          'satellite broadband commercial service update',
        ],
      },
      Launch: {
        label: 'Launch products',
        why: 'Rocket / launch product update.',
        queries: [
          'SpaceX Falcon Starship launch update',
          'ISRO LVM3 rocket launch mission news',
          'reusable rocket launch vehicle product',
        ],
      },
      'Commercial Space': {
        label: 'Commercial space products',
        why: 'Commercial space product people follow.',
        queries: [
          'space tourism commercial crew product news',
          'Blue Origin Virgin Galactic flight update',
          'commercial space station product announcement',
        ],
      },
      Lunar: {
        label: 'Lunar products',
        why: 'Moon mission product people track.',
        queries: [
          'NASA Artemis lunar lander mission news',
          'ISRO Chandrayaan lunar mission update',
          'moon rover lander product announcement',
        ],
      },
    },
  },
  'Share Market': {
    label: 'Share products',
    why: 'Share-specific product or company move.',
    queries: [
      'Reliance OR TCS OR Infosys stock earnings OR product',
      'Tesla OR NVIDIA stock price product catalyst',
      'IPO listing OR SME IPO allotment today India',
    ],
    blockGeneric: [
      'share market live',
      'share market today: latest',
      'nifty closes below',
      'indian stock market prediction',
      'things that will decide stock market action',
    ],
    subs: {
      IT: {
        label: 'IT shares',
        why: 'IT stock / product catalyst.',
        queries: [
          'TCS Infosys Wipro stock earnings product',
          'NVIDIA Microsoft IT stock catalyst news',
          'AI software company stock product launch',
        ],
      },
      EV: {
        label: 'EV shares',
        why: 'EV stock / product catalyst.',
        queries: [
          'Tesla EV stock delivery OR product news',
          'Tata Motors Ola Electric EV share news',
          'electric vehicle battery stock catalyst',
        ],
      },
      Space: {
        label: 'Space shares',
        why: 'Space stock / product catalyst.',
        queries: [
          'SpaceX related stock OR satellite company shares',
          'aerospace defence stock product contract news',
          'rocket satellite company IPO OR share news',
        ],
      },
      Pharma: {
        label: 'Pharma shares',
        why: 'Pharma stock / drug catalyst.',
        queries: [
          'Sun Pharma Dr Reddy stock drug approval',
          'pharma stock FDA trial catalyst news',
          'biotech share price clinical trial result',
        ],
      },
      Banking: {
        label: 'Banking shares',
        why: 'Bank stock / product catalyst.',
        queries: [
          'HDFC Bank ICICI SBI stock earnings news',
          'bank share price fintech product catalyst',
          'private bank stock quarterly results India',
        ],
      },
      Energy: {
        label: 'Energy shares',
        why: 'Energy stock / product catalyst.',
        queries: [
          'Reliance ONGC energy stock product news',
          'renewable energy stock solar wind catalyst',
          'oil gas company share earnings update',
        ],
      },
    },
  },
  'Social Media': {
    label: 'Social products',
    why: 'Social product feature or platform move.',
    queries: [
      'Instagram Reels OR Threads product update Meta',
      'TikTok Shop OR TikTok algorithm product news',
      'WhatsApp Channels OR WhatsApp AI feature',
    ],
    blockGeneric: ['share market live', 'stock market today'],
    subs: {
      'Short Video': {
        label: 'Short video products',
        why: 'Short-video product people binge.',
        queries: [
          'Instagram Reels TikTok YouTube Shorts update',
          'short video algorithm product feature news',
          'Reels monetisation creator product update',
        ],
      },
      'Social Commerce': {
        label: 'Social commerce products',
        why: 'Shop-on-social product move.',
        queries: [
          'TikTok Shop Instagram shopping product news',
          'social commerce checkout feature launch',
          'Meta shoppable Reels product update',
        ],
      },
      Messaging: {
        label: 'Messaging products',
        why: 'Messaging product people use daily.',
        queries: [
          'WhatsApp Channels AI feature update',
          'iMessage Telegram Signal product news',
          'messaging app privacy feature launch',
        ],
      },
      Creators: {
        label: 'Creator products',
        why: 'Creator-economy product update.',
        queries: [
          'creator fund monetisation product update',
          'LinkedIn newsletter creator tools news',
          'influencer platform product feature launch',
        ],
      },
    },
  },
};

const DEFAULT_MIX = [
  { category: 'Share Market', sub: 'IT', take: 2 },
  { category: 'Share Market', sub: 'EV', take: 1 },
  { category: 'Medical', sub: 'Cardiology', take: 1 },
  { category: 'Medical', sub: 'Gynecology', take: 1 },
];

export function listIndustries() {
  return Object.keys(INDUSTRY_NEWS);
}

export function getIndustryCatalog() {
  const order = ['Share Market', 'IT', 'Medical', 'Space', 'Social Media'];
  const entries = Object.entries(INDUSTRY_NEWS);
  entries.sort((a, b) => {
    const ai = order.indexOf(a[0]);
    const bi = order.indexOf(b[0]);
    return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi);
  });

  return entries.map(([name, config]) => ({
    name,
    label: config.label,
    subs: Object.keys(config.subs || {}),
  }));
}

export function listSubs(category) {
  return Object.keys(INDUSTRY_NEWS[category]?.subs || {});
}

/**
 * Default home feed: high-demand share + medical sub products.
 */
export async function getDefaultProductNews(limit = 5) {
  const collected = [];

  for (const part of DEFAULT_MIX) {
    const remaining = limit - collected.length;
    if (remaining <= 0) break;
    const take = Math.min(part.take, remaining);
    const batch = await getIndustryProductNews(part.category, take, part.sub);
    collected.push(...batch.results);
  }

  return {
    query: 'share IT/EV + medical cardiology/gynecology products',
    category: 'Default',
    label: 'Top searched product news',
    results: collected.slice(0, limit).map((item, index) => ({
      ...item,
      id: `n-${index + 1}`,
    })),
  };
}

/**
 * Latest product-centric news for one industry, optionally a sub-combination.
 */
export async function getIndustryProductNews(category, limit = 5, sub = null) {
  const config = INDUSTRY_NEWS[category];
  if (!config) {
    const error = new Error(`Unknown research industry: ${category}`);
    error.status = 400;
    error.code = 'UNKNOWN_INDUSTRY';
    throw error;
  }

  let active = {
    label: config.label,
    why: config.why,
    queries: config.queries,
  };
  let activeSub = null;

  if (sub) {
    const subConfig = config.subs?.[sub];
    if (!subConfig) {
      const error = new Error(`Unknown sub-combination: ${sub}`);
      error.status = 400;
      error.code = 'UNKNOWN_SUB';
      throw error;
    }
    active = {
      label: subConfig.label,
      why: subConfig.why,
      queries: subConfig.queries,
    };
    activeSub = sub;
  }

  const seen = new Set();
  const results = [];

  // India-first queries, then broader global variants.
  const queryPlan = active.queries.flatMap((query) => indiaFirstQueries(query));

  for (const query of queryPlan) {
    if (results.length >= limit) break;

    const items = await fetchGoogleNewsRss(query, limit + 4, {
      why: active.why,
      blockGeneric: config.blockGeneric,
    });

    for (const item of items) {
      if (results.length >= Math.max(limit * 2, 8)) break;
      const key = normalizeKey(item.title);
      if (seen.has(key) || isGenericHeadline(item.title, config.blockGeneric)) continue;
      seen.add(key);
      results.push({
        ...item,
        category,
        sub: activeSub,
        why: item.why || active.why,
      });
    }
  }

  if (results.length < limit) {
    const fallbackQuery = withIndiaBias(
      activeSub
        ? `${category} ${activeSub} product launch OR breakthrough OR update`
        : `${category} product launch OR breakthrough OR update research`,
    );
    const discovered = await discoverPages(fallbackQuery, limit + 4, {
      categories: [category],
    });

    for (const item of discovered) {
      if (results.length >= Math.max(limit * 2, 8)) break;
      const key = normalizeKey(item.title);
      if (seen.has(key) || isGenericHeadline(item.title, config.blockGeneric)) continue;
      seen.add(key);
      results.push({
        id: `n-${results.length + 1}`,
        title: item.title,
        url: item.url,
        description: item.description || active.why,
        why: active.why,
        score: Math.max(58, 92 - results.length * 6),
        source: item.source || 'web',
        category,
        sub: activeSub,
      });
    }
  }

  const ordered = sortIndiaFirst(results).slice(0, limit);

  return {
    query: withIndiaBias(active.queries[0]),
    category,
    sub: activeSub,
    label: active.label,
    regionPreference: 'India first',
    results: ordered.map((item, index) => ({
      ...item,
      id: `n-${index + 1}`,
      score: item.score ?? Math.max(58, 94 - index * 6),
    })),
  };
}

async function fetchGoogleNewsRss(query, limit, { why, blockGeneric = [] } = {}) {
  const url =
    `https://news.google.com/rss/search?q=${encodeURIComponent(query)}`
    + '&hl=en-IN&gl=IN&ceid=IN:en&when:7d';

  try {
    const response = await fetch(url, {
      headers: {
        'User-Agent': 'GyanwireBot/1.0 (+local research tool)',
        Accept: 'application/rss+xml, application/xml, text/xml',
      },
      signal: AbortSignal.timeout(12000),
    });

    if (!response.ok) {
      throw new Error(`Google News RSS returned ${response.status}`);
    }

    const xml = await response.text();
    const $ = cheerio.load(xml, { xmlMode: true });
    const items = [];

    $('item').each((_, element) => {
      if (items.length >= limit) return;

      const title = cleanText($(element).find('title').first().text());
      const link = cleanText($(element).find('link').first().text());
      const description = cleanText(
        cheerio.load($(element).find('description').first().text() || '').text(),
      );
      const pubDate = cleanText($(element).find('pubDate').first().text());
      const source = cleanText($(element).find('source').first().text());

      if (!title || !link) return;
      if (isGenericHeadline(title, blockGeneric)) return;

      items.push({
        id: `n-${items.length + 1}`,
        title,
        url: link,
        description: description || why || 'Product news.',
        why: source ? `${why} via ${source}.` : why || 'Product news.',
        score: Math.max(60, 95 - items.length * 5),
        source: 'google-news',
        publishedAt: pubDate || null,
      });
    });

    return items;
  } catch (error) {
    console.error('Industry news RSS failed:', error.message);
    return [];
  }
}

function isGenericHeadline(title, blockGeneric = []) {
  const text = normalizeKey(title);
  const genericPatterns = [
    'share market live',
    'share market today',
    'latest share market news',
    'stock market prediction',
    'things that will decide stock market',
    'market action on',
    'nifty closes',
    'sensex sinks',
    'top buzzing stocks today',
    ...blockGeneric.map(normalizeKey),
  ];
  return genericPatterns.some((pattern) => pattern && text.includes(pattern));
}

function normalizeKey(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function cleanText(value) {
  return String(value || '').replace(/\s+/g, ' ').trim();
}

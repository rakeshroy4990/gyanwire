import { indiaAffinity, INDIA_DOMAIN_HINTS } from './india.js';

/**
 * Gyanwire ranking pointers.
 *
 * These are the scoring rules that make this engine yours.
 * Raise/lower weights, add keywords, or add new pointer functions
 * without touching the rest of the pipeline.
 */

/** Domains that usually publish durable, useful pages. */
export const TRUSTED_DOMAIN_HINTS = [
  ...INDIA_DOMAIN_HINTS.filter((d) => !d.startsWith('.')),
  'edu',
  'gov',
  'wikipedia.org',
  'developer.mozilla.org',
  'github.com',
  'stackoverflow.com',
  'arxiv.org',
  'nih.gov',
  'who.int',
  'nasa.gov',
  'isro.gov.in',
  'esa.int',
  'ieee.org',
  'acm.org',
  'nature.com',
  'sciencedirect.com',
  'pubmed.ncbi.nlm.nih.gov',
  'clinicaltrials.gov',
  'harvard.edu',
  'mit.edu',
  'stanford.edu',
  'reuters.com',
  'bloomberg.com',
  'moneycontrol.com',
  'economictimes.indiatimes.com',
  'livemint.com',
  'cnbc.com',
  'marketwatch.com',
];


/** Domains that are usually thin, spammy, or low-signal. */
export const LOW_QUALITY_DOMAIN_HINTS = [
  'pinterest.com',
  'quora.com',
  'scribd.com',
  'slideplayer.com',
  'coursehero.com',
];

/** Words that mark R&D / research-quality sources. */
export const RESEARCH_TERMS = [
  'research',
  'r&d',
  'study',
  'clinical trial',
  'peer reviewed',
  'journal',
  'preprint',
  'whitepaper',
  'patent',
  'laboratory',
  'experiment',
  'methodology',
  'dataset',
  'findings',
  'innovation',
  'prototype',
];

/** Category → boost terms used when that industry is selected. */
export const CATEGORY_POINTERS = {
  IT: [
    'software',
    'computing',
    'artificial intelligence',
    'semiconductor',
    'cybersecurity',
    'systems',
    'algorithm',
    'engineering',
    'open source',
  ],
  Medical: [
    'clinical',
    'biomedical',
    'pharma',
    'therapy',
    'diagnostics',
    'trial',
    'patient',
    'genomics',
    'healthcare research',
  ],
  Space: [
    'aerospace',
    'satellite',
    'orbital',
    'launch',
    'propulsion',
    'nasa',
    'isro',
    'astronomy',
    'spacecraft',
  ],
  'Share Market': [
    'equity research',
    'stock',
    'share',
    'nifty',
    'sensex',
    'market analysis',
    'ipo',
    'earnings',
    'valuation',
  ],
  'Social Media': [
    'instagram',
    'tiktok',
    'whatsapp',
    'linkedin',
    'threads',
    'reels',
    'creator',
    'platform',
    'engagement',
    'social graph',
  ],
};

/**
 * Weight map for each pointer. Sum does not need to be 100;
 * final scores are normalized to 0–100.
 */
export const POINTER_WEIGHTS = {
  thoughtOverlap: 22,
  titleOverlap: 14,
  categoryAffinity: 14,
  researchSignal: 14,
  indiaSignal: 16,
  contentDepth: 8,
  trustedDomain: 8,
  discoveryRank: 4,
};

/**
 * Build the pointer scorecard for one page.
 * Return { score, why, breakdown }.
 */
export function scoreWithPointers({
  page,
  categories = [],
  thoughts = '',
  query = '',
  discoveryIndex = 0,
}) {
  const haystack = normalize(
    [page.title, page.description, page.snippet, page.text].filter(Boolean).join(' '),
  );
  const title = normalize(page.title || '');
  const thoughtTokens = tokenize(`${thoughts} ${query}`);
  const categoryTerms = categories.flatMap((name) => CATEGORY_POINTERS[name] || [name.toLowerCase()]);

  const breakdown = {
    thoughtOverlap: overlapRatio(thoughtTokens, haystack) * POINTER_WEIGHTS.thoughtOverlap,
    titleOverlap: overlapRatio(thoughtTokens, title) * POINTER_WEIGHTS.titleOverlap,
    categoryAffinity: categoryAffinityScore(categoryTerms, haystack) * POINTER_WEIGHTS.categoryAffinity,
    researchSignal: researchScore(haystack) * POINTER_WEIGHTS.researchSignal,
    indiaSignal: indiaAffinity({
      url: page.url,
      title: page.title,
      description: page.description || page.snippet,
    }) * POINTER_WEIGHTS.indiaSignal,
    contentDepth: contentDepthScore(page) * POINTER_WEIGHTS.contentDepth,
    trustedDomain: domainScore(page.url) * POINTER_WEIGHTS.trustedDomain,
    discoveryRank: discoveryScore(discoveryIndex) * POINTER_WEIGHTS.discoveryRank,
  };

  const raw = Object.values(breakdown).reduce((sum, value) => sum + value, 0);
  const max = Object.values(POINTER_WEIGHTS).reduce((sum, value) => sum + value, 0);
  const score = clamp(Math.round((raw / max) * 100), 0, 100);

  return {
    score,
    why: explainScore({ page, breakdown, categories, score }),
    breakdown,
  };
}

function categoryAffinityScore(terms, haystack) {
  if (!terms.length) return 0.35;
  const hits = terms.filter((term) => haystack.includes(normalize(term))).length;
  return clamp(hits / Math.min(4, terms.length), 0, 1);
}

function researchScore(haystack) {
  const hits = RESEARCH_TERMS.filter((term) => haystack.includes(term)).length;
  return clamp(hits / 4, 0, 1);
}

function contentDepthScore(page) {
  const length = (page.text || page.snippet || page.description || '').length;
  if (length > 2400) return 1;
  if (length > 1200) return 0.8;
  if (length > 500) return 0.55;
  if (length > 160) return 0.35;
  return 0.15;
}

function domainScore(url) {
  let host = '';
  try {
    host = new URL(url).hostname.replace(/^www\./, '').toLowerCase();
  } catch {
    return 0.2;
  }

  if (LOW_QUALITY_DOMAIN_HINTS.some((d) => host === d || host.endsWith(`.${d}`) || host.includes(d))) {
    return 0.05;
  }
  if (TRUSTED_DOMAIN_HINTS.some((d) => host === d || host.endsWith(`.${d}`) || host.endsWith(d))) {
    return 1;
  }
  if (host.endsWith('.edu') || host.endsWith('.gov')) return 1;
  if (host.split('.').length <= 2) return 0.55;
  return 0.4;
}

function discoveryScore(index) {
  // Earlier discovery hits still matter a little, but pointers can overturn them.
  return clamp(1 - index * 0.08, 0.2, 1);
}

function explainScore({ page, breakdown, categories, score }) {
  const ranked = Object.entries(breakdown).sort((a, b) => b[1] - a[1]);
  const top = ranked[0]?.[0];

  switch (top) {
    case 'thoughtOverlap':
      return 'Matches the language in your notes closely.';
    case 'titleOverlap':
      return 'The page title lines up with what you wrote.';
    case 'categoryAffinity':
      return `Strong fit for ${categories.slice(0, 2).join(' / ') || 'your topics'}.`;
    case 'researchSignal':
      return 'Looks like R&D or research-grade material.';
    case 'indiaSignal':
      return 'India-first source or India-focused coverage.';
    case 'contentDepth':
      return 'Has enough depth for research reading.';
    case 'trustedDomain':
      return `Comes from a solid source (${safeHost(page.url)}).`;
    default:
      return score >= 70
        ? 'Strong R&D fit across your research pointers.'
        : 'Partial research match across your pointers.';
  }
}

function overlapRatio(tokens, text) {
  if (!tokens.length || !text) return 0;
  const hits = tokens.filter((token) => text.includes(token)).length;
  return clamp(hits / Math.min(tokens.length, 10), 0, 1);
}

function tokenize(value) {
  return normalize(value)
    .split(' ')
    .filter((token) => token.length > 2)
    .filter((token, index, all) => all.indexOf(token) === index)
    .slice(0, 24);
}

function normalize(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s'-]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function safeHost(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return 'this site';
  }
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

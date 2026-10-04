/**
 * India-first helpers for discovery, news, and ranking.
 * Prefer Indian sources and context, then fall back to global.
 */

export const INDIA_DOMAIN_HINTS = [
  '.in',
  'moneycontrol.com',
  'economictimes.indiatimes.com',
  'livemint.com',
  'business-standard.com',
  'financialexpress.com',
  'hindustantimes.com',
  'timesofindia.indiatimes.com',
  'indianexpress.com',
  'thehindu.com',
  'ndtv.com',
  'cnbctv18.com',
  'zeebiz.com',
  'isro.gov.in',
  'nseindia.com',
  'bseindia.com',
  'rbi.org.in',
  'icmr.gov.in',
  'dbtindia.gov.in',
  'cdsco.gov.in',
  'india.gov.in',
  'pib.gov.in',
  'meity.gov.in',
];

export const INDIA_TERMS = [
  'india',
  'indian',
  'nifty',
  'sensex',
  'mumbai',
  'bengaluru',
  'hyderabad',
  'chennai',
  'delhi',
  'isro',
  'nse',
  'bse',
  'rupee',
  'sebi',
];

/**
 * Build India-first query variants.
 * First query leans India; second is global fallback.
 */
export function indiaFirstQueries(query) {
  const base = String(query || '').replace(/\s+/g, ' ').trim();
  if (!base) return [];

  const alreadyIndia = /\bindia\b|\bindian\b|\bnifty\b|\bsensex\b|\bisro\b/i.test(base);
  const indiaQuery = alreadyIndia ? base : `${base} India`;
  const globalQuery = base;

  return alreadyIndia ? [indiaQuery] : [indiaQuery, globalQuery];
}

export function withIndiaBias(query) {
  const variants = indiaFirstQueries(query);
  return variants[0] || query;
}

export function isIndiaUrl(url) {
  try {
    const host = new URL(url).hostname.replace(/^www\./, '').toLowerCase();
    return INDIA_DOMAIN_HINTS.some((hint) => {
      if (hint.startsWith('.')) return host.endsWith(hint) || host.includes(hint);
      return host === hint || host.endsWith(`.${hint}`);
    });
  } catch {
    return false;
  }
}

export function isIndiaText(value) {
  const text = String(value || '').toLowerCase();
  return INDIA_TERMS.some((term) => text.includes(term));
}

export function indiaAffinity({ url = '', title = '', description = '' } = {}) {
  if (isIndiaUrl(url)) return 1;
  if (isIndiaText(`${title} ${description}`)) return 0.7;
  return 0.15;
}

/**
 * Stable India-first sort: Indian results rise above similar global ones.
 */
export function sortIndiaFirst(items, { scoreKey = 'score' } = {}) {
  return [...items].sort((a, b) => {
    const aIndia = indiaAffinity(a);
    const bIndia = indiaAffinity(b);
    if (aIndia !== bIndia) return bIndia - aIndia;

    const aScore = Number(a?.[scoreKey] ?? 0);
    const bScore = Number(b?.[scoreKey] ?? 0);
    if (aScore !== bScore) return bScore - aScore;

    return (a.position ?? 0) - (b.position ?? 0);
  });
}

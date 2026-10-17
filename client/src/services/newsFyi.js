/**
 * Client-side FYI drop for product-news browse. Mirrors server SignalRules FYI
 * so a restored session cannot keep settlement briefs or market digests on screen.
 */
const FYI_TITLE =
  /\b(fii|fpi|dii)s?\b.{0,40}\b(dump|sell(?:ing|s|off)?|outflow|buying|bought)\b|\b(more )?pain ahead\b|\breversal on the cards\b|\bcheck details\b|\bsettlement (rule|rules|cycle|norms|framework|mechanism|process)\b|\b(notifies|notify|notified|notification)\b.{0,80}\b(settlement|formula|fast[- ]?track)\b|\bunveils? formula\b|\bfast[- ]?track route\b|\b(trading holiday|market holiday|circuit breaker|stock split|bonus issue|dividend declared|record date|ex[- ]date)\b|\b(nifty|sensex|d-street|wall street|dow jones|nasdaq)\b.{0,60}\b(bulls?|bears?|week ahead|today|outlook|momentum|losing run|weekly)\b|\b(bonds?|stocks?|gold|oil|euro|rupee|bitcoin)\b.{0,60}\b(slide|rally|hits?|falls?|rises?|weekly gains?|weekly drops?|weekly slide)\b|\btoday['’]?s?\s+(gold|silver)\s+rate\b|\b(gold|silver)\s+rate\s+in\s+india\b|\b(gold|silver)\s+prices?\s+(steady|unchanged|flat|stable|hold|holding)\b|\b(gold|silver)\s+(rate|price|prices)\s+today\b|\bmarket wrap\b|\bbull market\b|\bmarket commentary\b|\blikely within a week\b|\bwhat changes for\b|\bwar premium\b|\brisk premium\b|\bcommands a\b.{0,40}\bpremium\b|\b(brent|wti)\b.{0,90}\b(premium|crude|oil)\b|\bsupply risks?\b.{0,60}\b(keep|keeps|holding|holds|oil|brent|crude|gold)\b/i;

const INSPECTION_STATUS =
  /\breceiv(?:es|ed)\b.{0,50}\bobservations?\b|\b(gets?|got|issued|issues)\b.{0,40}\bobservations?\b|\bobservations?\b.{0,40}\b(facilit(?:y|ies)|plants?|sites?|units?|inspections?)\b|\b(facilit(?:y|ies)|plants?|sites?|units?)\b.{0,40}\bobservations?\b|\bform[- ]?483\b|\b(zero|no|nil)\s+observations?\b|\b(usfda|fda|cdsco)\b.{0,50}\b(observations?|inspections?)\b|\binspections?\b.{0,40}\b(observations?|complet(?:es|ed)|conclud(?:es|ed)|clos(?:es|ed))\b/i;

const INSPECTION_OVERRIDE =
  /\b(approv(?:ed|es|al)|ban(?:ned)?|recall(?:s|ed)?|launch(?:es|ed)?|shortage|stockout)\b/i;

function inspectionStatus(title) {
  return INSPECTION_STATUS.test(title) && !INSPECTION_OVERRIDE.test(title);
}

export function isFyiNewsItem(item) {
  if (!item || typeof item !== 'object') return false;
  const signal = String(item.signalType || '').trim().toLowerCase();
  if (signal === 'fyi') return true;
  const title = String(item.title || '');
  return FYI_TITLE.test(title) || inspectionStatus(title);
}

export function withoutFyiNews(items) {
  if (!Array.isArray(items)) return [];
  return items.filter((item) => !isFyiNewsItem(item));
}

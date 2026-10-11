const ANGLE_LABELS = {
  comparison_content: 'Social media',
  buying_guide_or_alert: 'Buying guide or alert',
  import_or_reseller: 'Import or reseller',
  accessories_or_addons: 'Accessories or add-ons',
  b2b_service: 'B2B service',
  data_or_tool: 'Data or tool',
  community_or_newsletter: 'Community or newsletter',
  education_or_training: 'Education or training',
};

const REVENUE_LABELS = {
  affiliate: 'Affiliate',
  one_time: 'One-time',
  subscription: 'Subscription',
  service: 'Service',
  ads: 'Ads',
  lead_gen: 'Lead gen',
};

const BAR_KEYS = [
  ['demandEvidence', 'Demand'],
  ['buildEffort', 'Effort'],
  ['competition', 'Competition'],
];

export function isPipelineIdea(row) {
  return Boolean(row && row.angleType);
}

export function angleLabel(row) {
  return ANGLE_LABELS[row?.angleType] || row?.format || 'Idea';
}

export function effortTag(buildEffort) {
  if (buildEffort === 'hours' || buildEffort === 'days') return 'Quick win';
  if (buildEffort === 'weeks') return 'Bigger build';
  return '';
}

export function urgencyChip(urgency) {
  switch (urgency) {
    case 'hours':
      return 'Hours';
    case 'days':
      return 'Days';
    case 'weeks':
      return 'Weeks';
    case 'evergreen':
      return 'Evergreen';
    default:
      return '';
  }
}

export function customerLine(idea) {
  const who = String(idea?.customer?.who || '').trim();
  const where = String(idea?.customer?.where || '').trim();
  if (!who) return '';
  if (!where || who.toLowerCase().includes(where.toLowerCase())) return who;
  return `${who} · ${where}`;
}

export function moneyLine(idea) {
  const model = idea?.revenueModel;
  if (!model || typeof model !== 'object') return String(idea?.business_model || idea?.businessModel || '');
  const type = REVENUE_LABELS[model.type] || model.type || 'Revenue';
  const price = String(model.price || '').trim();
  const rationale = String(model.rationale || '').trim().replace(/^because\s+/i, '');
  if (price && rationale) return `${type}, ${price}, because ${rationale}`;
  if (price) return `${type}, ${price}`;
  if (rationale) return `${type}, because ${rationale}`;
  return type;
}

/** Short face-line for cards: type and price only. */
export function moneyShort(idea) {
  const model = idea?.revenueModel;
  if (!model || typeof model !== 'object') {
    const fallback = String(idea?.business_model || idea?.businessModel || '').trim();
    return fallback.length > 48 ? `${fallback.slice(0, 45).trim()}…` : fallback;
  }
  const type = REVENUE_LABELS[model.type] || model.type || '';
  const price = String(model.price || '').trim();
  if (type && price) return `${type} · ${price}`;
  return type || price;
}

export function scoreBars(idea) {
  const dims = Array.isArray(idea?.scores?.dimensions) ? idea.scores.dimensions : [];
  const byKey = Object.fromEntries(dims.map((dim) => [dim.key, dim]));
  return BAR_KEYS.map(([key, label]) => {
    const dim = byKey[key] || {};
    const score = Number(dim.score);
    if (!Number.isFinite(score) || score <= 0) return null;
    const reason = String(dim.reason || '').trim();
    const assumed = dim.assumed ? ' Assumed.' : '';
    return {
      key,
      label,
      score: Math.max(1, Math.min(5, Math.round(score))),
      tip: `${reason}${assumed}`.trim(),
    };
  }).filter(Boolean);
}

export function testLine(idea) {
  const test = idea?.validationTest;
  if (!test) return '';
  const action = String(test.action || '').trim();
  const metric = String(test.successMetric || '').trim();
  if (action && metric) return `${action}. ${metric}.`;
  return action || metric;
}

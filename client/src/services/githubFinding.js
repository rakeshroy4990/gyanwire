/** A GitHub listing is a repository, not a news article. */
export function isGitHubFinding(item) {
  const domain = String(item?.domain || '').toLowerCase();
  const industry = String(item?.industry || '');
  const url = String(item?.url || '');
  return domain === 'github.com' || industry === 'GitHub' || /github\.com\//i.test(url);
}

/**
 * Drop the stock closer ("Famous library… India-first…") so the card
 * shows the repo description, stars, language, and licence.
 */
export function githubProjectDetail(summary) {
  const raw = String(summary || '').replace(/\s+/g, ' ').trim();
  if (!raw) return '';
  return raw.split(/Famous library\/framework\/tool/i)[0].trim();
}

export function githubHost(item) {
  try {
    const path = new URL(item.url).pathname.replace(/^\//, '').replace(/\/$/, '');
    return path ? `github.com/${path}` : 'github.com';
  } catch {
    return 'github.com';
  }
}

/** What the project is, taken from the sentence before the star counts. */
function projectBlurb(detail) {
  const marker = detail.toLowerCase().indexOf(' open-source github project');
  if (marker <= 0) return '';
  return detail.slice(0, marker).trim();
}

function projectFacts(detail) {
  const marker = detail.toLowerCase().indexOf('open-source github project');
  if (marker < 0) return detail;
  const facts = detail.slice(marker).trim();
  return facts ? facts.charAt(0).toUpperCase() + facts.slice(1) : detail;
}

/**
 * Show the repository on the finding card: name plus what it is,
 * then stars, language, and licence. Signal keywords and a country
 * chip are not a description of the project.
 */
export function presentGitHubFinding(item) {
  if (!item || !isGitHubFinding(item)) return item;
  const detail = githubProjectDetail(item.description || item.summary || '');
  const blurb = projectBlurb(detail);
  const name = String(item.title || '').split(/\s+[—–-]\s+/)[0].trim();
  const title = blurb ? `${name} — ${blurb}` : name;
  const facts = detail ? projectFacts(detail) : '';
  return {
    ...item,
    title: title || name,
    host: githubHost(item),
    description: detail || item.description || '',
    whyIdea: facts || detail || item.whyIdea || '',
    relevanceReason: facts || detail || '',
    why: facts || detail || '',
    signals: [],
    country: null,
    countryName: '',
  };
}

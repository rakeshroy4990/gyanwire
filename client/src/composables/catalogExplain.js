/** Fallback explainers when the catalog API has not shipped url/blurb yet. */
const FALLBACK = {
  swayam: {
    url: 'https://swayam.gov.in',
    blurb: 'India’s free government learning platform (Study Webs of Active-learning for Young Aspiring Minds). University and school courses online — not a SEBI or share-market class.',
  },
  vscode: {
    url: 'https://code.visualstudio.com',
    blurb: 'Free code editor from Microsoft. Only when the idea needs software or coding work.',
  },
  'notion-free': {
    url: 'https://www.notion.so',
    blurb: 'Free notes app. Write the offer, outline, or one-page artifact you can show someone — no coding needed.',
  },
};

/**
 * @param {string} id
 * @param {{ url?: string, blurb?: string } | null} fromApi
 * @param {{ newsUrl?: string, newsTitle?: string } | null} news
 */
export function catalogExplain(id, fromApi = null, news = null) {
  const key = id == null ? '' : String(id);
  const base = FALLBACK[key] || {};
  const newsUrl = (news?.newsUrl || '').trim();
  // Learning gear opens the plan’s source news when we have it.
  if (key === 'swayam' && newsUrl) {
    const title = (news?.newsTitle || '').trim();
    return {
      url: newsUrl,
      blurb: title
        ? `Opens this plan’s source news: “${title}”. SWAYAM itself is India’s free government learning platform — not a market class.`
        : 'Opens this plan’s source news. SWAYAM itself is India’s free government learning platform — not a market class.',
      hasExplain: true,
      opensNews: true,
    };
  }
  const url = (fromApi?.url || base.url || '').trim();
  const blurb = (fromApi?.blurb || base.blurb || '').trim();
  return {
    url: url || '',
    blurb: blurb || '',
    hasExplain: Boolean(url || blurb),
    opensNews: false,
  };
}

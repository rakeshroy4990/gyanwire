const ALL_INDUSTRY = 'All';

/**
 * First saved profile industry that exists in the research catalog.
 * Returns null when the profile has no usable interest, so the home feed
 * can fall back to AI.
 */
export function profileInterestIndustry(industries, catalogNames = []) {
  const known = new Set(
    (catalogNames || []).map((name) => String(name || '').trim()).filter(Boolean),
  );
  const list = Array.isArray(industries) ? industries : [];
  for (const raw of list) {
    const name = String(raw ?? '').trim();
    if (!name || name.toLowerCase() === ALL_INDUSTRY.toLowerCase()) continue;
    if (known.size === 0 || known.has(name)) return name;
  }
  return null;
}

/**
 * Guests skip the profile fetch; signed-in users fetch only when the store is empty.
 */
export function needsProfileForHomeFeed(signedIn, profile) {
  return Boolean(signedIn) && !profile;
}

/**
 * Industry chip for the profile-following home feed.
 * Falls back to the default pack when the profile has no catalog match.
 */
export function homeFeedIndustry(industries, catalogNames = [], fallback = 'AI') {
  return profileInterestIndustry(industries, catalogNames) || fallback;
}

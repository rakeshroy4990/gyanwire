const ALL_INDUSTRY = 'All';

/**
 * First saved profile industry that exists in the research catalog.
 * Returns null when the profile has no usable interest, so the home feed
 * can fall back to the mixed list.
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

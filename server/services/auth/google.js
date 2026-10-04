/**
 * Verifies a Google OAuth access token via userinfo (same path as hospital AuthService).
 */
export async function fetchVerifiedGoogleProfile(accessToken) {
  const token = String(accessToken || '').trim();
  if (!token) return null;

  const res = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!res.ok) {
    return null;
  }

  const body = await res.json();
  const email = String(body.email || '').trim().toLowerCase();
  if (!email) return null;

  const verified = body.email_verified === true || body.email_verified === 'true' || body.verified_email === true;
  if (!verified) return null;

  let givenName = String(body.given_name || '').trim();
  let familyName = String(body.family_name || '').trim();
  const fullName = String(body.name || '').trim();
  if (!givenName && !familyName && fullName) {
    const parts = fullName.split(/\s+/, 2);
    givenName = parts[0] || '';
    familyName = parts[1] || '';
  }

  return {
    email,
    givenName,
    familyName,
    picture: String(body.picture || '').trim(),
  };
}

/**
 * Resolve API paths for local vs cloud.
 *
 * - Local: VITE_BACKEND_URL=http://localhost:8080 (gyanwire-server)
 * - Cloud: VITE_BACKEND_URL=https://gyanwire-r5eanyyt6a-el.a.run.app
 *
 * Fallback: VITE_API_BASE_URL (legacy name).
 * If unset in Vite dev, default to local Spring Boot.
 */
const CLOUD_BACKEND_DEFAULT = 'https://gyanwire-r5eanyyt6a-el.a.run.app';
const LOCAL_BACKEND_DEFAULT = 'http://localhost:8080';

export function backendBaseUrl() {
  const fromEnv = String(
    import.meta.env.VITE_BACKEND_URL
      || import.meta.env.VITE_API_BASE_URL
      || '',
  ).trim().replace(/\/+$/, '');

  if (fromEnv) return fromEnv;

  if (import.meta.env.DEV) {
    return LOCAL_BACKEND_DEFAULT;
  }

  // Production builds should bake VITE_BACKEND_URL; keep Cloud Run as last resort.
  return CLOUD_BACKEND_DEFAULT;
}

export function apiUrl(path) {
  const base = backendBaseUrl();
  const normalized = path.startsWith('/') ? path : `/${path}`;
  return `${base}${normalized}`;
}

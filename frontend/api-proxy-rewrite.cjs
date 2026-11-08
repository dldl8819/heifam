// The browser calls the backend at /api/proxy/... on the site's own address (lib/api.ts). Vercel's CDN
// forwards those calls straight to the backend as an external rewrite, so no Vercel Function runs for
// them: the function that used to forward them was nearly all of the team's Fluid Active CPU.
// next.config.js reads this when the site is built, so a change of backend address needs a new build.

// Used only when no backend address is configured. Every name here must be a backend we run: a hosting
// name nobody holds any more could be taken by someone else, who would then be sent members'
// requests, sign-in tokens included.
const FALLBACK_BACKEND_ORIGIN = 'https://heifam.onrender.com'

function parseCsv(value) {
  if (!value) {
    return []
  }
  return value
    .split(',')
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0)
}

/** The backend's base address without a trailing slash, or null when the value cannot be one. */
function sanitizeBackendBaseUrl(value) {
  let parsed
  try {
    parsed = new URL(value.trim())
  } catch {
    return null
  }
  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
    return null
  }

  const normalizedPath = parsed.pathname.replace(/\/+$/, '')
  // The site's own /api/proxy given as the backend would send every call round in a loop.
  if (normalizedPath.startsWith('/api/proxy')) {
    return null
  }
  // A common slip: the backend's `/api` or `/api/health` given instead of its base address.
  if (normalizedPath === '/api' || normalizedPath === '/api/health') {
    parsed.pathname = '/'
  }

  parsed.search = ''
  parsed.hash = ''
  return parsed.toString().replace(/\/+$/, '')
}

/**
 * The first usable address in BACKEND_API_BASE_URLS, then NEXT_PUBLIC_API_BASE_URL, as the old proxy
 * function tried them; otherwise the backend we run. Only one address is used: a rewrite has a single
 * destination, where the function also retried reads at the later addresses.
 */
function resolveBackendOrigin(env = process.env) {
  const candidates = [
    ...parseCsv(env.BACKEND_API_BASE_URLS),
    ...parseCsv(env.NEXT_PUBLIC_API_BASE_URL),
  ]
  for (const candidate of candidates) {
    const sanitized = sanitizeBackendBaseUrl(candidate)
    if (sanitized) {
      return sanitized
    }
  }
  return FALLBACK_BACKEND_ORIGIN
}

/** /api/proxy/<path>?<query> goes to <backend>/<path>?<query>; Next.js passes the query along. */
function apiProxyRewrites(env = process.env) {
  return [
    {
      source: '/api/proxy/:path*',
      destination: `${resolveBackendOrigin(env)}/:path*`,
    },
  ]
}

module.exports = {
  FALLBACK_BACKEND_ORIGIN,
  apiProxyRewrites,
  resolveBackendOrigin,
  sanitizeBackendBaseUrl,
}

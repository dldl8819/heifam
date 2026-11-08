// How long the browser waits for the backend. On the live site calls go to /api/proxy, which Vercel's
// CDN forwards to the backend (an external rewrite, next.config.js). Vercel gives the backend at most
// 120 seconds and then answers ROUTER_EXTERNAL_TARGET_ERROR (vercel.com/docs/limits, "Proxied request
// timeout"); the browser gives up well before that.
export const VERCEL_PROXIED_REQUEST_TIMEOUT_MS = 120_000
export const PROXY_READ_CLIENT_TIMEOUT_MS = 30_000
export const PROXY_MUTATION_CLIENT_TIMEOUT_MS = 50_000
export const DEFAULT_DIRECT_API_TIMEOUT_MS = 10_000

export function isReadOnlyHttpMethod(method: string): boolean {
  const normalizedMethod = method.trim().toUpperCase()
  return normalizedMethod === 'GET' || normalizedMethod === 'HEAD'
}

export function resolveDefaultApiRequestTimeoutMs({
  method,
  usesProductionProxy,
}: {
  method: string
  usesProductionProxy: boolean
}): number {
  if (!usesProductionProxy) {
    return DEFAULT_DIRECT_API_TIMEOUT_MS
  }

  return isReadOnlyHttpMethod(method)
    ? PROXY_READ_CLIENT_TIMEOUT_MS
    : PROXY_MUTATION_CLIENT_TIMEOUT_MS
}

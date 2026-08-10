export const PROXY_MAX_DURATION_MS = 60_000
export const PROXY_READ_TOTAL_TIMEOUT_MS = 25_000
export const PROXY_READ_CLIENT_TIMEOUT_MS = 30_000
export const PROXY_MUTATION_UPSTREAM_TIMEOUT_MS = 45_000
export const PROXY_MUTATION_CLIENT_TIMEOUT_MS = 50_000
export const DEFAULT_DIRECT_API_TIMEOUT_MS = 10_000
export const DEFAULT_UPSTREAM_ATTEMPT_TIMEOUT_MS = 45_000
export const MIN_UPSTREAM_ATTEMPT_TIMEOUT_MS = 1_000
export const MAX_UPSTREAM_ATTEMPT_TIMEOUT_MS = 60_000
export const MAX_READ_UPSTREAM_CANDIDATES = 3

export function isReadOnlyHttpMethod(method: string): boolean {
  const normalizedMethod = method.trim().toUpperCase()
  return normalizedMethod === 'GET' || normalizedMethod === 'HEAD'
}

export function resolveConfiguredUpstreamTimeoutMs(rawValue: string | undefined): number {
  const normalizedValue = rawValue?.trim()
  if (!normalizedValue) {
    return DEFAULT_UPSTREAM_ATTEMPT_TIMEOUT_MS
  }

  const parsedValue = Number(normalizedValue)
  if (
    !Number.isFinite(parsedValue)
    || parsedValue < MIN_UPSTREAM_ATTEMPT_TIMEOUT_MS
    || parsedValue > MAX_UPSTREAM_ATTEMPT_TIMEOUT_MS
  ) {
    return DEFAULT_UPSTREAM_ATTEMPT_TIMEOUT_MS
  }

  return Math.floor(parsedValue)
}

export function resolveProxyCandidateLimit(method: string): number {
  return isReadOnlyHttpMethod(method) ? MAX_READ_UPSTREAM_CANDIDATES : 1
}

export function resolveProxyAttemptTimeoutMs({
  method,
  configuredTimeoutMs,
  remainingReadBudgetMs,
  remainingCandidateCount,
}: {
  method: string
  configuredTimeoutMs: number
  remainingReadBudgetMs?: number
  remainingCandidateCount: number
}): number {
  if (!isReadOnlyHttpMethod(method)) {
    return Math.min(configuredTimeoutMs, PROXY_MUTATION_UPSTREAM_TIMEOUT_MS)
  }

  if (
    !Number.isFinite(remainingReadBudgetMs)
    || (remainingReadBudgetMs ?? 0) < MIN_UPSTREAM_ATTEMPT_TIMEOUT_MS
    || remainingCandidateCount <= 0
  ) {
    return 0
  }

  const fairShareMs = Math.floor((remainingReadBudgetMs as number) / remainingCandidateCount)
  if (fairShareMs < MIN_UPSTREAM_ATTEMPT_TIMEOUT_MS) {
    return 0
  }

  return Math.min(configuredTimeoutMs, fairShareMs)
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

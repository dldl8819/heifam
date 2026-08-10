import { describe, expect, it } from 'vitest'

import {
  DEFAULT_DIRECT_API_TIMEOUT_MS,
  DEFAULT_UPSTREAM_ATTEMPT_TIMEOUT_MS,
  MAX_READ_UPSTREAM_CANDIDATES,
  PROXY_MAX_DURATION_MS,
  PROXY_MUTATION_CLIENT_TIMEOUT_MS,
  PROXY_MUTATION_UPSTREAM_TIMEOUT_MS,
  PROXY_READ_CLIENT_TIMEOUT_MS,
  PROXY_READ_TOTAL_TIMEOUT_MS,
  isReadOnlyHttpMethod,
  resolveConfiguredUpstreamTimeoutMs,
  resolveDefaultApiRequestTimeoutMs,
  resolveProxyAttemptTimeoutMs,
  resolveProxyCandidateLimit,
} from '@/lib/proxy-timeout'

describe('proxy timeout policy', () => {
  it('keeps client deadlines outside proxy budgets and below the platform limit', () => {
    expect(PROXY_READ_TOTAL_TIMEOUT_MS).toBeLessThan(PROXY_READ_CLIENT_TIMEOUT_MS)
    expect(PROXY_READ_CLIENT_TIMEOUT_MS).toBeLessThan(PROXY_MAX_DURATION_MS)
    expect(PROXY_MUTATION_UPSTREAM_TIMEOUT_MS).toBeLessThan(PROXY_MUTATION_CLIENT_TIMEOUT_MS)
    expect(PROXY_MUTATION_CLIENT_TIMEOUT_MS).toBeLessThan(PROXY_MAX_DURATION_MS)
  })

  it('limits fallback to read-only requests', () => {
    expect(isReadOnlyHttpMethod('get')).toBe(true)
    expect(isReadOnlyHttpMethod('HEAD')).toBe(true)
    expect(isReadOnlyHttpMethod('POST')).toBe(false)
    expect(resolveProxyCandidateLimit('GET')).toBe(MAX_READ_UPSTREAM_CANDIDATES)
    expect(resolveProxyCandidateLimit('PATCH')).toBe(1)
    expect(resolveProxyCandidateLimit('DELETE')).toBe(1)
  })

  it('sanitizes the configured per-attempt ceiling', () => {
    expect(resolveConfiguredUpstreamTimeoutMs(undefined)).toBe(DEFAULT_UPSTREAM_ATTEMPT_TIMEOUT_MS)
    expect(resolveConfiguredUpstreamTimeoutMs('invalid')).toBe(DEFAULT_UPSTREAM_ATTEMPT_TIMEOUT_MS)
    expect(resolveConfiguredUpstreamTimeoutMs('999')).toBe(DEFAULT_UPSTREAM_ATTEMPT_TIMEOUT_MS)
    expect(resolveConfiguredUpstreamTimeoutMs('61000')).toBe(DEFAULT_UPSTREAM_ATTEMPT_TIMEOUT_MS)
    expect(resolveConfiguredUpstreamTimeoutMs('15000.9')).toBe(15000)
  })

  it('shares the read budget across remaining candidates', () => {
    const firstAttempt = resolveProxyAttemptTimeoutMs({
      method: 'GET',
      configuredTimeoutMs: DEFAULT_UPSTREAM_ATTEMPT_TIMEOUT_MS,
      remainingReadBudgetMs: PROXY_READ_TOTAL_TIMEOUT_MS,
      remainingCandidateCount: 3,
    })
    const secondAttempt = resolveProxyAttemptTimeoutMs({
      method: 'GET',
      configuredTimeoutMs: DEFAULT_UPSTREAM_ATTEMPT_TIMEOUT_MS,
      remainingReadBudgetMs: PROXY_READ_TOTAL_TIMEOUT_MS - firstAttempt,
      remainingCandidateCount: 2,
    })
    const thirdAttempt = resolveProxyAttemptTimeoutMs({
      method: 'GET',
      configuredTimeoutMs: DEFAULT_UPSTREAM_ATTEMPT_TIMEOUT_MS,
      remainingReadBudgetMs: PROXY_READ_TOTAL_TIMEOUT_MS - firstAttempt - secondAttempt,
      remainingCandidateCount: 1,
    })

    expect(firstAttempt).toBe(8333)
    expect(firstAttempt + secondAttempt + thirdAttempt).toBeLessThanOrEqual(PROXY_READ_TOTAL_TIMEOUT_MS)
  })

  it('honors the configured ceiling and stops when too little read budget remains', () => {
    expect(resolveProxyAttemptTimeoutMs({
      method: 'GET',
      configuredTimeoutMs: 5000,
      remainingReadBudgetMs: PROXY_READ_TOTAL_TIMEOUT_MS,
      remainingCandidateCount: 3,
    })).toBe(5000)
    expect(resolveProxyAttemptTimeoutMs({
      method: 'GET',
      configuredTimeoutMs: DEFAULT_UPSTREAM_ATTEMPT_TIMEOUT_MS,
      remainingReadBudgetMs: 999,
      remainingCandidateCount: 1,
    })).toBe(0)
  })

  it('keeps mutation requests on one bounded upstream attempt', () => {
    expect(resolveProxyAttemptTimeoutMs({
      method: 'POST',
      configuredTimeoutMs: DEFAULT_UPSTREAM_ATTEMPT_TIMEOUT_MS,
      remainingCandidateCount: 1,
    })).toBe(PROXY_MUTATION_UPSTREAM_TIMEOUT_MS)
    expect(resolveProxyAttemptTimeoutMs({
      method: 'DELETE',
      configuredTimeoutMs: 15000,
      remainingCandidateCount: 1,
    })).toBe(15000)
  })

  it('uses longer defaults only for the production proxy', () => {
    expect(resolveDefaultApiRequestTimeoutMs({ method: 'GET', usesProductionProxy: true }))
      .toBe(PROXY_READ_CLIENT_TIMEOUT_MS)
    expect(resolveDefaultApiRequestTimeoutMs({ method: 'PATCH', usesProductionProxy: true }))
      .toBe(PROXY_MUTATION_CLIENT_TIMEOUT_MS)
    expect(resolveDefaultApiRequestTimeoutMs({ method: 'GET', usesProductionProxy: false }))
      .toBe(DEFAULT_DIRECT_API_TIMEOUT_MS)
  })
})

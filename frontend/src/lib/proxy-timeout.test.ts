import { describe, expect, it } from 'vitest'

import {
  DEFAULT_DIRECT_API_TIMEOUT_MS,
  PROXY_MUTATION_CLIENT_TIMEOUT_MS,
  PROXY_READ_CLIENT_TIMEOUT_MS,
  VERCEL_PROXIED_REQUEST_TIMEOUT_MS,
  isReadOnlyHttpMethod,
  resolveDefaultApiRequestTimeoutMs,
} from '@/lib/proxy-timeout'

describe('proxy timeout policy', () => {
  it('gives up in the browser before Vercel stops waiting for the backend', () => {
    expect(PROXY_READ_CLIENT_TIMEOUT_MS).toBeLessThan(PROXY_MUTATION_CLIENT_TIMEOUT_MS)
    expect(PROXY_MUTATION_CLIENT_TIMEOUT_MS).toBeLessThan(VERCEL_PROXIED_REQUEST_TIMEOUT_MS)
  })

  it('tells reads from writes', () => {
    expect(isReadOnlyHttpMethod('get')).toBe(true)
    expect(isReadOnlyHttpMethod(' HEAD ')).toBe(true)
    expect(isReadOnlyHttpMethod('POST')).toBe(false)
    expect(isReadOnlyHttpMethod('DELETE')).toBe(false)
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

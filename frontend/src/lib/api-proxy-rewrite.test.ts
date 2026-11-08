import { createRequire } from 'node:module'
import { describe, expect, it } from 'vitest'

// next.config.js loads this file with require(), so the test does the same.
const {
  FALLBACK_BACKEND_ORIGIN,
  apiProxyRewrites,
  resolveBackendOrigin,
} = createRequire(import.meta.url)('../../api-proxy-rewrite.cjs') as {
  FALLBACK_BACKEND_ORIGIN: string
  apiProxyRewrites: (env: Record<string, string | undefined>) => { source: string, destination: string }[]
  resolveBackendOrigin: (env: Record<string, string | undefined>) => string
}

describe('API rewrite to the backend', () => {
  it('sends /api/proxy/<path> to the backend, path and query along', () => {
    expect(apiProxyRewrites({ BACKEND_API_BASE_URLS: 'https://backend.invalid' })).toEqual([
      { source: '/api/proxy/:path*', destination: 'https://backend.invalid/:path*' },
    ])
  })

  it('falls back to the backend we run when no address is configured', () => {
    expect(resolveBackendOrigin({})).toBe(FALLBACK_BACKEND_ORIGIN)
    expect(resolveBackendOrigin({ BACKEND_API_BASE_URLS: ' , ', NEXT_PUBLIC_API_BASE_URL: '' })).toBe(FALLBACK_BACKEND_ORIGIN)
    expect(FALLBACK_BACKEND_ORIGIN).toMatch(/^https:\/\/[^/]+$/)
  })

  it('takes the first usable address, BACKEND_API_BASE_URLS before NEXT_PUBLIC_API_BASE_URL', () => {
    expect(resolveBackendOrigin({
      BACKEND_API_BASE_URLS: 'https://first.invalid, https://second.invalid',
      NEXT_PUBLIC_API_BASE_URL: 'https://public.invalid',
    })).toBe('https://first.invalid')
    expect(resolveBackendOrigin({
      BACKEND_API_BASE_URLS: 'not a url,ftp://files.invalid',
      NEXT_PUBLIC_API_BASE_URL: 'https://public.invalid/',
    })).toBe('https://public.invalid')
    // The browser's own relative base is no backend.
    expect(resolveBackendOrigin({ NEXT_PUBLIC_API_BASE_URL: '/api/proxy' })).toBe(FALLBACK_BACKEND_ORIGIN)
  })

  it('never points the rewrite back at the site itself', () => {
    expect(resolveBackendOrigin({
      BACKEND_API_BASE_URLS: 'https://site.invalid/api/proxy,https://backend.invalid',
    })).toBe('https://backend.invalid')
  })

  it('cleans an address given with /api, /api/health, a query or a trailing slash', () => {
    expect(resolveBackendOrigin({ BACKEND_API_BASE_URLS: 'https://backend.invalid/api' })).toBe('https://backend.invalid')
    expect(resolveBackendOrigin({ BACKEND_API_BASE_URLS: 'https://backend.invalid/api/health/' })).toBe('https://backend.invalid')
    expect(resolveBackendOrigin({ BACKEND_API_BASE_URLS: 'https://backend.invalid/?a=1#b' })).toBe('https://backend.invalid')
    expect(resolveBackendOrigin({ BACKEND_API_BASE_URLS: 'https://backend.invalid/base/' })).toBe('https://backend.invalid/base')
  })
})

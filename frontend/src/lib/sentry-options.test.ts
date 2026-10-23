import { afterEach, describe, expect, it, vi } from 'vitest'
import { FRONTEND_SENTRY_DSN, isProductionHost, sharedSentryOptions } from '@/lib/sentry-options'
import { SENTRY_DATA_COLLECTION, scrubBreadcrumb, scrubEvent } from '@/lib/sentry-scrub'

describe('sentry options', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('sends from the live site only', () => {
    expect(isProductionHost('www.heifam.co.kr')).toBe(true)
    expect(isProductionHost('heifam.co.kr')).toBe(true)
    expect(isProductionHost('localhost')).toBe(false)
    expect(isProductionHost('heifam-git-feature-YOUR_USERNAME.vercel.app')).toBe(false)
    expect(isProductionHost(undefined)).toBe(false)
  })

  it('shares the project, the scrubbing and every data collection switch off', () => {
    vi.stubEnv('NEXT_PUBLIC_SENTRY_DSN', '')
    const options = sharedSentryOptions(true, 'production')

    expect(options).toEqual({
      dsn: FRONTEND_SENTRY_DSN,
      enabled: true,
      environment: 'production',
      dataCollection: SENTRY_DATA_COLLECTION,
      beforeSend: scrubEvent,
      beforeBreadcrumb: scrubBreadcrumb,
    })
    expect(SENTRY_DATA_COLLECTION).toEqual({
      userInfo: false,
      cookies: false,
      httpHeaders: { request: { allow: ['user-agent'] }, response: false },
      httpBodies: [],
      urlQueryParams: false,
      graphQL: { document: false, variables: false },
      genAI: { inputs: false, outputs: false },
      databaseQueryData: false,
      queues: false,
      stackFrameVariables: false,
    })
    expect(sharedSentryOptions(false, 'preview').enabled).toBe(false)
  })

  it('takes another DSN from NEXT_PUBLIC_SENTRY_DSN', () => {
    vi.stubEnv('NEXT_PUBLIC_SENTRY_DSN', 'https://YOUR_API_KEY@example.ingest.sentry.io/1')

    expect(sharedSentryOptions(true, 'production').dsn).toBe('https://YOUR_API_KEY@example.ingest.sentry.io/1')
  })
})

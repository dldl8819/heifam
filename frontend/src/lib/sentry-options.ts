import { SENTRY_DATA_COLLECTION, scrubBreadcrumb, scrubEvent } from '@/lib/sentry-scrub'

// The site's Sentry project (hei-m9/heifam-frontend, EU). A browser DSN is public by design: it is
// in the page's script either way, and only lets events be sent. NEXT_PUBLIC_SENTRY_DSN overrides it.
export const FRONTEND_SENTRY_DSN = 'https://566c289e3e6652ec9d5bba13dd91c886@o4512302087405568.ingest.de.sentry.io/4512306046107728'

// The live site. A browser elsewhere (localhost, a Vercel preview) sends nothing.
const PRODUCTION_HOSTS = new Set(['www.heifam.co.kr', 'heifam.co.kr'])

export function isProductionHost(hostname: string | undefined): boolean {
  return hostname !== undefined && PRODUCTION_HOSTS.has(hostname)
}

/**
 * What every Sentry.init of the site shares: errors only (no tracing, no session replay), sent only
 * when `enabled` (the live site), and nothing that names a member (sentry-scrub.ts).
 */
export function sharedSentryOptions(enabled: boolean, environment: string | undefined) {
  return {
    dsn: process.env.NEXT_PUBLIC_SENTRY_DSN || FRONTEND_SENTRY_DSN,
    enabled,
    environment,
    dataCollection: SENTRY_DATA_COLLECTION,
    beforeSend: scrubEvent,
    beforeBreadcrumb: scrubBreadcrumb,
  }
}

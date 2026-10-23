import * as Sentry from '@sentry/nextjs'
import { isProductionHost, sharedSentryOptions } from '@/lib/sentry-options'

// Errors in members' browsers on the live site go to Sentry, through /monitoring on this site
// (withSentryConfig's tunnel), so ad blockers and other sites do not see them. See sentry-options.ts.
Sentry.init(
  sharedSentryOptions(
    typeof window !== 'undefined' && isProductionHost(window.location.hostname),
    process.env.NEXT_PUBLIC_VERCEL_ENV ?? process.env.NODE_ENV,
  ),
)

export const onRouterTransitionStart = Sentry.captureRouterTransitionStart

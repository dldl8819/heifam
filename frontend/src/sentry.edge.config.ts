import * as Sentry from '@sentry/nextjs'
import { sharedSentryOptions } from '@/lib/sentry-options'

// The edge runtime, with the same rules as the server (sentry.server.config.ts).
Sentry.init(sharedSentryOptions(process.env.VERCEL_ENV === 'production', process.env.VERCEL_ENV ?? process.env.NODE_ENV))

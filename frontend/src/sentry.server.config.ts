import * as Sentry from '@sentry/nextjs'
import { sharedSentryOptions } from '@/lib/sentry-options'

// Errors of the site's own server (pages rendered there, the /api/proxy route), from Vercel's
// production deployment only; the same rules as in the browser (sentry-options.ts).
Sentry.init(sharedSentryOptions(process.env.VERCEL_ENV === 'production', process.env.VERCEL_ENV ?? process.env.NODE_ENV))

import * as Sentry from '@sentry/nextjs'

// Starts Sentry in whichever server runtime Next.js runs (Node.js or edge).
export async function register() {
  if (process.env.NEXT_RUNTIME === 'nodejs') {
    await import('./sentry.server.config')
  }
  if (process.env.NEXT_RUNTIME === 'edge') {
    await import('./sentry.edge.config')
  }
}

// Errors thrown while the server handles a request.
export const onRequestError = Sentry.captureRequestError

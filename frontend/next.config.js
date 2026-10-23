const path = require('path')
const { withSentryConfig } = require('@sentry/nextjs/config')

/** @type {import('next').NextConfig} */
const nextConfig = {
  devIndicators: false,
  poweredByHeader: false,
  outputFileTracingRoot: path.join(__dirname, '..'),
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          // No other site may frame the app and trick a signed-in admin into clicking. Nor may a
          // page load plugins, point relative links elsewhere with <base>, or post a form to another
          // site: the app does none of these, so they are refused outright.
          { key: 'X-Frame-Options', value: 'DENY' },
          {
            key: 'Content-Security-Policy',
            value: "frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'",
          },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=()' },
        ],
      },
    ]
  },
}

// Sentry (instrumentation-client.ts, sentry.*.config.ts) sends from the live site only. Events go
// through /monitoring on this site. Source maps are uploaded only where SENTRY_AUTH_TOKEN is set
// (Vercel's build), so stack traces read as the source; without it the build is as before.
module.exports = withSentryConfig(nextConfig, {
  org: process.env.SENTRY_ORG ?? 'hei-m9',
  project: process.env.SENTRY_PROJECT ?? 'heifam-frontend',
  authToken: process.env.SENTRY_AUTH_TOKEN,
  sourcemaps: { disable: !process.env.SENTRY_AUTH_TOKEN },
  widenClientFileUpload: true,
  tunnelRoute: '/monitoring',
  silent: !process.env.CI,
  telemetry: false,
})

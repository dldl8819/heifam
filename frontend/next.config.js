const path = require('path')
const { withSentryConfig } = require('@sentry/nextjs/config')
const { apiProxyRewrites } = require('./api-proxy-rewrite.cjs')

/** @type {import('next').NextConfig} */
const nextConfig = {
  devIndicators: false,
  poweredByHeader: false,
  outputFileTracingRoot: path.join(__dirname, '..'),
  // API calls (/api/proxy/...) go from Vercel's CDN straight to the backend, with no function in
  // between (api-proxy-rewrite.cjs). The backend answers them with "Cache-Control: no-store", or
  // "private" for notice images, so the CDN keeps none of them.
  async rewrites() {
    return apiProxyRewrites(process.env)
  },
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

// Sentry runs in members' browsers on the live site only (instrumentation-client.ts). The site's own
// functions do not start it: each of their start-ups and requests paid for it in Fluid Active CPU, and
// they now only draw the four detail pages. Events go through /monitoring on this site. Source maps
// are uploaded only where SENTRY_AUTH_TOKEN is set (Vercel's build), so stack traces read as the
// source; without it the build is as before.
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

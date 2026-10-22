const path = require('path')

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

module.exports = nextConfig

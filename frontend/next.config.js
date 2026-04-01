const path = require('path')

/** @type {import('next').NextConfig} */
const nextConfig = {
  devIndicators: false,
  outputFileTracingRoot: path.join(__dirname, '..'),
  webpack: (config, { dev, isServer }) => {
    if (dev && !isServer) {
      // Avoid eval-source-map parsing issues on Windows paths with non-ASCII characters.
      config.devtool = 'cheap-module-source-map'
    }
    return config
  },
}

module.exports = nextConfig

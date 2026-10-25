import { defineConfig } from '@playwright/test'

// The accessibility checks (e2e/accessibility.spec.ts) run in a browser against `next start` on a
// production build (`npm run build` first). Every call the pages make to the backend or to Supabase
// is answered inside the browser, so no service, secret or member data is needed. The installed
// Google Chrome is used, so no browser is downloaded.
const PORT = 3100

export default defineConfig({
  testDir: 'e2e',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://127.0.0.1:${PORT}`,
    channel: 'chrome',
    locale: 'ko-KR',
  },
  webServer: {
    command: `npx next start -p ${PORT}`,
    url: `http://127.0.0.1:${PORT}`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})

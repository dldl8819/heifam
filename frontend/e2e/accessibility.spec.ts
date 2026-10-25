import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

/*
  Accessibility checks with axe-core against WCAG 2.2 A and AA. They cover the pages a visitor sees
  and every menu page of a signed-in super admin, who sees every menu, in light and dark mode, each
  with and without high contrast.

  Nothing leaves the browser: the calls to the backend (/api/proxy) and to Supabase are answered
  here. A visitor has no session. A signed-in page gets a session in the browser's storage, where
  Supabase keeps one, and an access profile. Every other call to the backend fails (503), so the
  pages are checked with their frames, menus, forms and error messages, not with rows of data.
*/

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

// /auth is left out: it goes straight on to Google's sign-in.
const VISITOR_PAGES = ['/', '/notices', '/events', '/ads', '/results', '/privacy', '/terms']

const SIGNED_IN_PAGES = [
  '/players',
  '/ranking',
  '/balance',
  '/balance/multi',
  '/tournaments',
  '/captain-draft',
  '/draws',
  '/notices',
  '/boards/free',
  '/boards/anonymous',
  '/boards/video',
  '/boards/nickname',
  '/points',
  '/points/ranking',
  '/points/policy',
  '/points/events',
  '/predictions',
  '/events',
  '/ads',
  '/results',
  '/admin/access/admins',
  '/admin/access/result-editors',
  '/admin/access/allowed',
  '/admin/access/people',
  '/admin/audit',
]

// The same address the build was made with: Supabase names its storage by it.
const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL ?? ''

const EMAIL = 'YOUR_USERNAME@example.com'

const USER = {
  id: '00000000-0000-0000-0000-000000000000',
  aud: 'authenticated',
  role: 'authenticated',
  email: EMAIL,
  app_metadata: { provider: 'google' },
  user_metadata: { name: 'YOUR_USERNAME' },
  created_at: '2026-01-01T00:00:00Z',
}

const SESSION = {
  access_token: 'YOUR_API_KEY',
  refresh_token: 'YOUR_API_KEY',
  token_type: 'bearer',
  expires_in: 3600,
  // Far ahead, so Supabase never tries to refresh it.
  expires_at: 4102444800,
  user: USER,
}

const ACCESS = {
  email: EMAIL,
  nickname: 'YOUR_USERNAME',
  role: 'SUPER_ADMIN',
  admin: true,
  superAdmin: true,
  allowed: true,
  canViewMmr: true,
  preferredRace: 'P',
  matchResultEditor: true,
}

function supabaseStorageKey(): string {
  if (!SUPABASE_URL) {
    throw new Error('Set NEXT_PUBLIC_SUPABASE_URL to the address the build was made with.')
  }
  return `sb-${new URL(SUPABASE_URL).hostname.split('.')[0]}-auth-token`
}

async function answerCalls(page: Page, signedIn: boolean) {
  // Registered first, so the routes added after it win where both match.
  await page.route('**/api/proxy/**', (route) =>
    route.fulfill({ status: 503, json: { message: 'Not available in the accessibility check.' } })
  )
  if (SUPABASE_URL) {
    await page.route(`${SUPABASE_URL.replace(/\/+$/, '')}/**`, (route) =>
      signedIn && route.request().url().includes('/auth/v1/user')
        ? route.fulfill({ json: USER })
        : route.fulfill({ status: 401, json: { message: 'No session.' } })
    )
  }
  if (signedIn) {
    await page.route('**/api/access/me', (route) => route.fulfill({ json: ACCESS }))
    await page.addInitScript(
      ([key, session]) => window.localStorage.setItem(key, session),
      [supabaseStorageKey(), JSON.stringify(SESSION)]
    )
  }
}

async function expectNoViolations(page: Page, path: string, signedIn: boolean) {
  await page.goto(path)
  await page.waitForLoadState('networkidle')
  // The app has drawn the page once the page's own heading is there.
  await expect(page.locator('main h1')).toHaveCount(1)
  if (signedIn) {
    // The session took: the members' menu is there, so the page is the one members see.
    await expect(page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: '선수 관리' })).toBeVisible()
  }

  const { violations } = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze()
  if (violations.length > 0) {
    // In full, for the report: each rule with the elements it failed on and why.
    await test.info().attach('axe-violations', {
      contentType: 'application/json',
      body: JSON.stringify(
        violations.map((violation) => ({
          id: violation.id,
          impact: violation.impact,
          help: violation.help,
          nodes: violation.nodes.map((node) => ({ target: node.target, summary: node.failureSummary })),
        })),
        null,
        2
      ),
    })
  }
  const found = violations.map(
    (violation) =>
      `${violation.id} (${violation.impact}): ${violation.help}\n` +
      violation.nodes
        .slice(0, 5)
        .map((node) => `    ${node.target.join(' ')}`)
        .join('\n')
  )
  expect(found, `axe found ${found.length} kind(s) of problem on ${path}`).toEqual([])
}

const MODES = [
  { name: 'light', colorScheme: 'light', contrast: 'no-preference' },
  { name: 'dark', colorScheme: 'dark', contrast: 'no-preference' },
  { name: 'light, high contrast', colorScheme: 'light', contrast: 'more' },
  { name: 'dark, high contrast', colorScheme: 'dark', contrast: 'more' },
] as const

for (const mode of MODES) {
  test.describe(mode.name, () => {
    // The site follows the device's theme and contrast until a reader picks their own.
    test.use({ colorScheme: mode.colorScheme, contrast: mode.contrast })

    for (const path of VISITOR_PAGES) {
      test(`visitor ${path}`, async ({ page }) => {
        await answerCalls(page, false)
        await expectNoViolations(page, path, false)
      })
    }

    for (const path of SIGNED_IN_PAGES) {
      test(`signed in ${path}`, async ({ page }) => {
        await answerCalls(page, true)
        await expectNoViolations(page, path, true)
      })
    }
  })
}

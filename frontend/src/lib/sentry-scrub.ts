import type { Breadcrumb, Event, init } from '@sentry/nextjs'

// What a Sentry event from the site may carry: the error and where it happened, never who was
// involved. The anonymous board must never give its writer away, and an account deleted here cannot
// be deleted from Sentry. Shared by every Sentry.init (instrumentation-client.ts, sentry.*.config.ts).

type DataCollection = NonNullable<NonNullable<Parameters<typeof init>[0]>['dataCollection']>

const EMAIL = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g
const EMAIL_MASK = '[email]'
// The one request header kept: which browser broke helps, and it names nobody.
const KEPT_HEADERS = new Set(['user-agent'])

/**
 * Everything the SDK would gather on its own, off. Since v11 it gathers user info, cookies, headers,
 * request bodies, query strings and local variable values unless told not to, even when this option
 * is left out.
 */
export const SENTRY_DATA_COLLECTION: DataCollection = {
  userInfo: false,
  cookies: false,
  httpHeaders: { request: { allow: [...KEPT_HEADERS] }, response: false },
  httpBodies: [],
  urlQueryParams: false,
  graphQL: { document: false, variables: false },
  genAI: { inputs: false, outputs: false },
  databaseQueryData: false,
  queues: false,
  stackFrameVariables: false,
}

export function maskEmails<T extends string | undefined>(text: T): T {
  return (typeof text === 'string' ? text.replace(EMAIL, EMAIL_MASK) : text) as T
}

/** A URL without its query string or fragment: search words and tokens stay out. */
export function withoutQuery<T extends string | undefined>(url: T): T {
  if (typeof url !== 'string') {
    return url
  }
  const cut = url.search(/[?#]/)
  return (cut < 0 ? url : url.slice(0, cut)) as T
}

function scrubData(data: Record<string, unknown> | undefined): Record<string, unknown> | undefined {
  if (!data) {
    return data
  }
  return Object.fromEntries(
    Object.entries(data).map(([key, value]) => [key, typeof value === 'string' ? maskEmails(withoutQuery(value)) : value]),
  )
}

export function scrubBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb {
  return { ...breadcrumb, message: maskEmails(breadcrumb.message), data: scrubData(breadcrumb.data) }
}

/** The last word before an event leaves, whatever the SDK gathered: see SENTRY_DATA_COLLECTION. */
export function scrubEvent<T extends Event>(event: T): T {
  delete event.user
  delete event.server_name
  if (event.request) {
    const headers = Object.fromEntries(
      Object.entries(event.request.headers ?? {}).filter(([name]) => KEPT_HEADERS.has(name.toLowerCase())),
    )
    event.request = { method: event.request.method, url: withoutQuery(event.request.url), headers }
  }
  if (event.message) {
    event.message = maskEmails(event.message)
  }
  for (const exception of event.exception?.values ?? []) {
    exception.value = maskEmails(exception.value)
  }
  if (event.breadcrumbs) {
    event.breadcrumbs = event.breadcrumbs.map(scrubBreadcrumb)
  }
  return event
}

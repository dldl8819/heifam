import { describe, expect, it } from 'vitest'
import type { ErrorEvent } from '@sentry/nextjs'
import { maskEmails, scrubBreadcrumb, scrubEvent, withoutQuery } from '@/lib/sentry-scrub'

describe('sentry scrubbing', () => {
  it('keeps the error, the page path and the browser, and nothing that names a member', () => {
    const event: ErrorEvent = {
      type: undefined,
      message: 'Signed in as YOUR_USERNAME@example.com',
      user: { email: 'YOUR_USERNAME@example.com', ip_address: '{{auto}}' },
      server_name: 'YOUR_HOST',
      request: {
        method: 'GET',
        url: 'https://www.heifam.co.kr/boards/search?q=YOUR_SEARCH#top',
        query_string: 'q=YOUR_SEARCH',
        cookies: { session: 'YOUR_API_KEY' },
        data: '{"content":"YOUR_TEXT"}',
        headers: { 'User-Agent': 'YOUR_BROWSER', Authorization: 'Bearer YOUR_API_KEY', 'X-USER-EMAIL': 'YOUR_USERNAME@example.com' },
      },
      exception: { values: [{ type: 'TypeError', value: 'No member YOUR_USERNAME@example.com' }] },
      breadcrumbs: [
        { category: 'fetch', data: { url: '/api/proxy/api/boards/search?q=YOUR_SEARCH', status_code: 500 } },
        { category: 'console', message: 'token for YOUR_USERNAME@example.com' },
      ],
    }

    const sent = scrubEvent(event)

    expect(sent.user).toBeUndefined()
    expect(sent.server_name).toBeUndefined()
    expect(sent.request).toEqual({
      method: 'GET',
      url: 'https://www.heifam.co.kr/boards/search',
      headers: { 'User-Agent': 'YOUR_BROWSER' },
    })
    expect(sent.message).toBe('Signed in as [email]')
    expect(sent.exception?.values?.[0]).toEqual({ type: 'TypeError', value: 'No member [email]' })
    expect(sent.breadcrumbs?.[0].data).toEqual({ url: '/api/proxy/api/boards/search', status_code: 500 })
    expect(sent.breadcrumbs?.[1].message).toBe('token for [email]')
  })

  it('leaves an event without request or text as it is', () => {
    const event: ErrorEvent = { type: undefined, exception: { values: [{ type: 'Error' }] } }

    expect(scrubEvent(event)).toEqual({ type: undefined, exception: { values: [{ type: 'Error', value: undefined }] } })
  })

  it('cuts query strings and fragments, and masks only email addresses', () => {
    expect(withoutQuery('/notices/5?from=push#comments')).toBe('/notices/5')
    expect(withoutQuery('/notices/5')).toBe('/notices/5')
    expect(withoutQuery(undefined)).toBeUndefined()
    expect(maskEmails('Round 2 set 1 already has a result')).toBe('Round 2 set 1 already has a result')
    expect(maskEmails('a@b.co and YOUR_USERNAME@example.com')).toBe('[email] and [email]')
    expect(scrubBreadcrumb({ category: 'navigation', data: { from: '/boards?q=x', to: '/notices/5' } }).data).toEqual({
      from: '/boards',
      to: '/notices/5',
    })
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { NextRequest } from 'next/server'

import { GET, PATCH, maxDuration } from '@/app/api/proxy/[...path]/route'
import { PROXY_MAX_DURATION_MS } from '@/lib/proxy-timeout'

const context = {
  params: Promise.resolve({ path: ['api', 'groups', 'YOUR_GROUP_ID', 'matches', 'recent'] }),
}

beforeEach(() => {
  vi.stubEnv(
    'BACKEND_API_BASE_URLS',
    'https://YOUR_BACKEND_1.invalid,https://YOUR_BACKEND_2.invalid,https://YOUR_BACKEND_3.invalid'
  )
  vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', '')
  vi.stubEnv('BACKEND_PROXY_UPSTREAM_TIMEOUT_MS', '15000')
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
})

describe('API proxy fallback policy', () => {
  it('keeps the route duration aligned with the shared timeout policy', () => {
    expect(maxDuration * 1000).toBe(PROXY_MAX_DURATION_MS)
  })

  it('falls back to the next upstream for a retryable read response', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('', { status: 503 }))
      .mockResolvedValueOnce(new Response('{"ok":true}', {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await GET(
      new NextRequest('https://YOUR_CLIENT.invalid/api/proxy/api/groups/YOUR_GROUP_ID/matches/recent'),
      context
    )

    expect(response.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('does not retry a mutation against another upstream', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('', { status: 503 }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await PATCH(
      new NextRequest('https://YOUR_CLIENT.invalid/api/proxy/api/matches/YOUR_MATCH_ID/result', {
        method: 'PATCH',
        body: '{}',
        headers: { 'content-type': 'application/json' },
      }),
      { params: Promise.resolve({ path: ['api', 'matches', 'YOUR_MATCH_ID', 'result'] }) }
    )

    expect(response.status).toBe(503)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('stops read fallback when the caller aborts', async () => {
    const fetchMock = vi.fn((_: RequestInfo | URL, init?: RequestInit): Promise<Response> => (
      new Promise((_, reject) => {
        const signal = init?.signal
        if (!signal) {
          reject(new Error('Abort signal is required'))
          return
        }
        signal.addEventListener(
          'abort',
          () => reject(new DOMException('Request aborted', 'AbortError')),
          { once: true }
        )
      })
    ))
    vi.stubGlobal('fetch', fetchMock)
    const callerController = new AbortController()
    const request = new NextRequest(
      'https://YOUR_CLIENT.invalid/api/proxy/api/groups/YOUR_GROUP_ID/matches/recent',
      { signal: callerController.signal }
    )

    const responsePromise = GET(request, context)
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    callerController.abort()
    const response = await responsePromise

    expect(response.status).toBe(502)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

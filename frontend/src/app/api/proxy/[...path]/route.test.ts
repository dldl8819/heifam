import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { NextRequest } from 'next/server'

import { GET, PATCH, POST, maxDuration } from '@/app/api/proxy/[...path]/route'
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

  it('asks only the one built-in backend when no upstream is configured', async () => {
    vi.stubEnv('BACKEND_API_BASE_URLS', '')
    const fetchMock = vi.fn().mockResolvedValue(new Response('', { status: 503 }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await GET(
      new NextRequest('https://app.invalid/api/proxy/api/groups/YOUR_GROUP_ID/matches/recent'),
      context
    )

    // A retryable answer is handed back, not passed on to host names kept from older setups.
    expect(response.status).toBe(503)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('hands an image back byte for byte', async () => {
    // Bytes that are not text: read as text they would come back as other bytes.
    const image = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x80, 0xc3, 0x28, 0xfe, 0x0a])
    const fetchMock = vi.fn().mockResolvedValue(new Response(image, {
      status: 200,
      headers: { 'content-type': 'image/jpeg', 'x-internal': 'kept back' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await GET(
      new NextRequest('https://YOUR_CLIENT.invalid/api/proxy/api/groups/YOUR_GROUP_ID/notice-images/9'),
      { params: Promise.resolve({ path: ['api', 'groups', 'YOUR_GROUP_ID', 'notice-images', '9'] }) }
    )

    expect(response.status).toBe(200)
    expect(response.headers.get('content-type')).toBe('image/jpeg')
    expect(response.headers.get('cache-control')).toBe('no-store, max-age=0')
    expect(response.headers.get('x-internal')).toBeNull()
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(image)
  })

  it('sends an uploaded image on as it came, with its type', async () => {
    const image = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0xff, 0x80])
    const fetchMock = vi.fn().mockResolvedValue(new Response('{"id":9}', {
      status: 200,
      headers: { 'content-type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await POST(
      new NextRequest('https://YOUR_CLIENT.invalid/api/proxy/api/groups/YOUR_GROUP_ID/notice-images', {
        method: 'POST',
        body: image,
        headers: { 'content-type': 'image/png', 'x-user-email': 'YOUR_USERNAME' },
      }),
      { params: Promise.resolve({ path: ['api', 'groups', 'YOUR_GROUP_ID', 'notice-images'] }) }
    )

    expect(await response.json()).toEqual({ id: 9 })
    const sent = fetchMock.mock.calls[0][1] as RequestInit
    expect(new Uint8Array(sent.body as ArrayBuffer)).toEqual(image)
    expect(new Headers(sent.headers).get('content-type')).toBe('image/png')
    expect(new Headers(sent.headers).get('x-user-email')).toBeNull()
  })

  describe('what a browser is told about keeping an answer', () => {
    const image = new Uint8Array([0xff, 0xd8, 0xff, 0xe0])
    const imagePath = ['api', 'groups', 'YOUR_GROUP_ID', 'notice-images', '9']

    async function answered(
      path: string[],
      upstream: { status?: number; cacheControl?: string },
      method: 'GET' | 'POST' = 'GET'
    ): Promise<string | null> {
      // A new answer for every attempt: a read that is refused is tried against each backend in turn.
      vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response(upstream.status ? '{}' : image, {
        status: upstream.status ?? 200,
        headers: upstream.cacheControl ? { 'cache-control': upstream.cacheControl } : {},
      })))
      const url = `https://YOUR_CLIENT.invalid/api/proxy/${path.join('/')}`
      const request = method === 'GET'
        ? new NextRequest(url)
        : new NextRequest(url, { method, body: image, headers: { 'content-type': 'image/jpeg' } })
      const response = await (method === 'GET' ? GET : POST)(request, { params: Promise.resolve({ path }) })
      return response.headers.get('cache-control')
    }

    it('passes on that the reader may keep an image the backend marked so', async () => {
      expect(await answered(imagePath, { cacheControl: 'private, max-age=2592000, immutable' }))
        .toBe('private, max-age=2592000, immutable')
    })

    it('keeps an image the backend did not mark out of every cache', async () => {
      expect(await answered(imagePath, { cacheControl: 'no-store, max-age=0' })).toBe('no-store, max-age=0')
      expect(await answered(imagePath, {})).toBe('no-store, max-age=0')
    })

    it('never passes on what a shared cache could keep', async () => {
      for (const cacheControl of ['public, max-age=2592000', 'private, s-maxage=60', 'max-age=2592000', 'private, public']) {
        expect(await answered(imagePath, { cacheControl })).toBe('no-store, max-age=0')
      }
    })

    it('keeps a refusal out of every cache, whatever it says', async () => {
      for (const status of [401, 403, 404, 500]) {
        expect(await answered(imagePath, { status, cacheControl: 'private, max-age=2592000, immutable' }))
          .toBe('no-store, max-age=0')
      }
    })

    it('lets nothing but reading a notice image be kept', async () => {
      const marked = { cacheControl: 'private, max-age=2592000, immutable' }

      expect(await answered(['api', 'groups', 'YOUR_GROUP_ID', 'notices', '5'], marked)).toBe('no-store, max-age=0')
      expect(await answered(['api', 'groups', 'YOUR_GROUP_ID', 'notice-images'], marked)).toBe('no-store, max-age=0')
      expect(await answered([...imagePath, 'extra'], marked)).toBe('no-store, max-age=0')
      expect(await answered(['api', 'points', 'YOUR_GROUP_ID', 'notice-images', '9'], marked)).toBe('no-store, max-age=0')
      expect(await answered(['api', 'groups', 'YOUR_GROUP_ID', 'notice-images'], marked, 'POST')).toBe('no-store, max-age=0')
    })
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

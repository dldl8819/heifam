import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/supabase', () => ({
  supabase: {
    auth: {
      getSession: async () => ({
        data: { session: { access_token: 'YOUR_API_KEY', user: { email: 'YOUR_USERNAME@example.com' } } },
      }),
    },
  },
}))

// api.ts picks its base address when it is loaded, so each test loads it afresh as the live site.
async function loadApi() {
  vi.resetModules()
  vi.stubEnv('NODE_ENV', 'production')
  return import('@/lib/api')
}

function answer(status: number, body: string, headers: Record<string, string> = {}): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(headers),
    text: async () => body,
  } as unknown as Response
}

function sentHeaders(fetchMock: ReturnType<typeof vi.fn>, call: number): Headers {
  const [, init] = fetchMock.mock.calls[call] as unknown as [string, RequestInit]
  return new Headers(init.headers)
}

const LIST = '{"notifications":[],"unreadCount":0}'

afterEach(() => {
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('the bell and the regular draft poll with X-Poll-Tag', () => {
  it('sends the tag under X-Poll-Tag, not If-None-Match, and keeps the list on a 204', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(answer(200, LIST, { 'X-Poll-Tag': '"n-1"', ETag: '"n-1"' }))
      .mockResolvedValueOnce(answer(204, '', { 'X-Poll-Tag': '"n-1"' }))
    vi.stubGlobal('fetch', fetchMock)
    const { apiClient } = await loadApi()

    const first = await apiClient.getNotificationsIfChanged(1, null)
    expect(first.list).toEqual({ notifications: [], unreadCount: 0 })
    expect(first.etag).toBe('"n-1"')
    expect(sentHeaders(fetchMock, 0).has('X-Poll-Tag')).toBe(false)

    const again = await apiClient.getNotificationsIfChanged(1, first.etag)
    expect(again).toEqual({ list: null, etag: '"n-1"' })
    expect(sentHeaders(fetchMock, 1).get('X-Poll-Tag')).toBe('"n-1"')
    expect(sentHeaders(fetchMock, 1).has('If-None-Match')).toBe(false)
  })

  it('keeps the tag it had when an unchanged answer brings none back', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => answer(204, '')))
    const { apiClient } = await loadApi()

    await expect(apiClient.getCaptainDraftIfChanged(1, 7, '"d-7"')).resolves.toEqual({ draft: null, etag: '"d-7"' })
  })

  it('takes the ETag from a backend that does not send X-Poll-Tag yet', async () => {
    const fetchMock = vi.fn(async () => answer(200, '{"draftId":7}', { ETag: '"d-7"' }))
    vi.stubGlobal('fetch', fetchMock)
    const { apiClient } = await loadApi()

    const draft = await apiClient.getCaptainDraftIfChanged(1, 7, '"d-6"')
    expect(draft.etag).toBe('"d-7"')
    expect(draft.draft).toMatchObject({ draftId: 7 })
    expect(sentHeaders(fetchMock, 0).get('X-Poll-Tag')).toBe('"d-6"')
  })
})

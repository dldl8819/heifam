import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/supabase', () => ({
  supabase: {
    auth: {},
  },
}))

// api.ts picks its base address when it is loaded, so each test loads it afresh under NODE_ENV.
async function loadApi(nodeEnv: string) {
  vi.resetModules()
  vi.stubEnv('NODE_ENV', nodeEnv)
  return import('@/lib/api')
}

function answer(status: number, body: string): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(),
    text: async () => body,
  } as unknown as Response
}

const signedIn = {
  adminOnly: true,
  includeUserEmail: true,
  userEmail: 'YOUR_USERNAME@example.com',
  userNickname: 'YOUR_USERNAME',
  accessToken: 'YOUR_API_KEY',
}

afterEach(() => {
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('apiRequest on the live site', () => {
  it('asks /api/proxy with the token only, without the email and nickname headers', async () => {
    const fetchMock = vi.fn(async () => answer(200, '{"ok":true}'))
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadApi('production')

    await expect(apiRequest('/api/groups/YOUR_GROUP_ID/notifications?limit=30', undefined, signedIn))
      .resolves.toEqual({ ok: true })

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    const headers = new Headers(init.headers)
    expect(url).toBe('/api/proxy/api/groups/YOUR_GROUP_ID/notifications?limit=30')
    expect(headers.get('Authorization')).toBe('Bearer YOUR_API_KEY')
    expect(headers.has('X-USER-EMAIL')).toBe(false)
    expect(headers.has('X-USER-NICKNAME')).toBe(false)
  })

  it('shows a plain message when a gateway answers instead of the backend', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => answer(502, 'An error occurred. ROUTER_EXTERNAL_TARGET_ERROR')))
    const { apiRequest } = await loadApi('production')

    await expect(apiRequest('/api/groups/YOUR_GROUP_ID/players', undefined, signedIn))
      .rejects.toMatchObject({ status: 502, message: 'Backend service is temporarily unavailable.' })
  })

  it('keeps the backend’s own message, and other statuses’ text, as before', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(answer(503, '{"message":"점검 중입니다."}'))
      .mockResolvedValueOnce(answer(400, 'plain reason'))
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadApi('production')

    await expect(apiRequest('/api/a', undefined, signedIn)).rejects.toMatchObject({ status: 503, message: '점검 중입니다.' })
    await expect(apiRequest('/api/b', undefined, signedIn)).rejects.toMatchObject({ status: 400, message: 'plain reason' })
  })
})

describe('apiRequest against a local backend', () => {
  it('still sends the email and nickname headers there', async () => {
    const fetchMock = vi.fn(async () => answer(200, '{}'))
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadApi('test')

    await apiRequest('/api/groups/YOUR_GROUP_ID/players', undefined, signedIn)

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    const headers = new Headers(init.headers)
    expect(url.endsWith('/api/groups/YOUR_GROUP_ID/players')).toBe(true)
    expect(url.startsWith('/api/proxy')).toBe(false)
    expect(headers.get('X-USER-EMAIL')).toBe('YOUR_USERNAME@example.com')
    expect(headers.get('X-USER-NICKNAME')).toBe('YOUR_USERNAME')
  })
})

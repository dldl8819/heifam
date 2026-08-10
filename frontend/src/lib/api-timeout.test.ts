import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/supabase', () => ({
  supabase: {
    auth: {},
  },
}))

import { ApiRequestError, apiRequest } from '@/lib/api'

function abortError(): DOMException {
  return new DOMException('Request aborted', 'AbortError')
}

function createPendingFetchMock() {
  return vi.fn((_: RequestInfo | URL, init?: RequestInit): Promise<Response> => (
    new Promise((_, reject) => {
      const signal = init?.signal
      if (!signal) {
        reject(new Error('Abort signal is required'))
        return
      }

      if (signal.aborted) {
        reject(abortError())
        return
      }

      signal.addEventListener('abort', () => reject(abortError()), { once: true })
    })
  ))
}

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('apiRequest timeout handling', () => {
  it('reports its own deadline as a timeout', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', createPendingFetchMock())

    const request = apiRequest('/api/test', undefined, { timeoutMs: 25 })
    const assertion = expect(request).rejects.toMatchObject({ status: 408 })

    await vi.advanceTimersByTimeAsync(25)
    await assertion
  })

  it('preserves a caller abort instead of reporting a timeout', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', createPendingFetchMock())
    const callerController = new AbortController()
    const removeListenerSpy = vi.spyOn(callerController.signal, 'removeEventListener')

    const request = apiRequest('/api/test', { signal: callerController.signal }, { timeoutMs: 1000 })
    callerController.abort()
    const error = await request.catch((caught) => caught)

    expect(error).toBeInstanceOf(DOMException)
    expect(error).not.toBeInstanceOf(ApiRequestError)
    expect((error as DOMException).name).toBe('AbortError')
    expect(removeListenerSpy).toHaveBeenCalled()
  })

  it('keeps the deadline active while reading the response body', async () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn((_: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
      const signal = init?.signal
      const response = {
        ok: true,
        status: 200,
        text: () => new Promise<string>((_, reject) => {
          if (!signal) {
            reject(new Error('Abort signal is required'))
            return
          }
          if (signal.aborted) {
            reject(abortError())
            return
          }
          signal.addEventListener('abort', () => reject(abortError()), { once: true })
        }),
      } as Response
      return Promise.resolve(response)
    })
    vi.stubGlobal('fetch', fetchMock)

    const request = apiRequest('/api/test', undefined, { timeoutMs: 25 })
    const assertion = expect(request).rejects.toMatchObject({ status: 408 })

    await vi.advanceTimersByTimeAsync(25)
    await assertion
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

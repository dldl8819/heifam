import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { startVisiblePolling } from '@/lib/visible-polling'

function fakeDocument(initial: DocumentVisibilityState) {
  const listeners = new Set<() => void>()
  const target = {
    visibilityState: initial,
    addEventListener: (_type: 'visibilitychange', listener: () => void) => {
      listeners.add(listener)
    },
    removeEventListener: (_type: 'visibilitychange', listener: () => void) => {
      listeners.delete(listener)
    },
  }
  const setVisibility = (state: DocumentVisibilityState) => {
    target.visibilityState = state
    listeners.forEach((listener) => listener())
  }
  return { target, setVisibility, listenerCount: () => listeners.size }
}

describe('startVisiblePolling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('polls on every interval while the page is visible', () => {
    const page = fakeDocument('visible')
    const refresh = vi.fn()

    startVisiblePolling(refresh, 3000, page.target)
    vi.advanceTimersByTime(9000)

    expect(refresh).toHaveBeenCalledTimes(3)
  })

  it('skips polls while hidden and catches up when shown again', () => {
    const page = fakeDocument('visible')
    const refresh = vi.fn()

    startVisiblePolling(refresh, 3000, page.target)
    page.setVisibility('hidden')
    vi.advanceTimersByTime(30000)
    expect(refresh).not.toHaveBeenCalled()

    page.setVisibility('visible')
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('stops polling and listening once stopped', () => {
    const page = fakeDocument('visible')
    const refresh = vi.fn()

    const stop = startVisiblePolling(refresh, 3000, page.target)
    stop()
    vi.advanceTimersByTime(9000)
    page.setVisibility('visible')

    expect(refresh).not.toHaveBeenCalled()
    expect(page.listenerCount()).toBe(0)
  })
})

type VisibilityTarget = {
  readonly visibilityState: DocumentVisibilityState
  addEventListener(type: 'visibilitychange', listener: () => void): void
  removeEventListener(type: 'visibilitychange', listener: () => void): void
}

/**
 * Calls `refresh` every `intervalMs` while the page is visible, and once as soon as it is shown
 * again. A hidden tab makes no calls: each one is a backend request that Supabase Auth has to
 * answer too. Returns a function that stops polling.
 */
export function startVisiblePolling(
  refresh: () => void,
  intervalMs: number,
  target: VisibilityTarget = document,
): () => void {
  const refreshIfVisible = () => {
    if (target.visibilityState === 'visible') {
      refresh()
    }
  }

  const intervalId = setInterval(refreshIfVisible, intervalMs)
  target.addEventListener('visibilitychange', refreshIfVisible)

  return () => {
    clearInterval(intervalId)
    target.removeEventListener('visibilitychange', refreshIfVisible)
  }
}

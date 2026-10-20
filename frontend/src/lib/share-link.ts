import { copyTextWithFallback } from '@/lib/clipboard'

export type ShareOutcome = 'shared' | 'copied' | 'cancelled'

// What the browser offers for passing a link on: the system share sheet (where there is one),
// whether to prefer it (on a touch screen, a phone or tablet), and copying.
export type ShareEnvironment = {
  share?: (data: ShareData) => Promise<void>
  prefersShareSheet: boolean
  copy: (text: string) => Promise<void>
}

export function browserShareEnvironment(): ShareEnvironment {
  const nav = typeof navigator === 'undefined' ? undefined : navigator
  return {
    share: nav && typeof nav.share === 'function' ? (data) => nav.share(data) : undefined,
    prefersShareSheet: typeof window !== 'undefined' && window.matchMedia?.('(pointer: coarse)').matches === true,
    copy: copyTextWithFallback,
  }
}

function isAbort(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { name?: unknown }).name === 'AbortError'
}

/**
 * Passes a link on. On a phone or tablet, the system share sheet opens (카카오톡, messages, copying
 * and so on), which matters most in the app added to the home screen, where there is no address bar
 * to copy from. On a computer, or where there is no share sheet or it fails, the link is copied.
 * Closing the share sheet is not a failure.
 */
export async function shareLink(
  link: { title: string; url: string },
  environment: ShareEnvironment = browserShareEnvironment(),
): Promise<ShareOutcome> {
  if (environment.share && environment.prefersShareSheet) {
    try {
      await environment.share({ title: link.title, url: link.url })
      return 'shared'
    } catch (error) {
      if (isAbort(error)) {
        return 'cancelled'
      }
    }
  }
  await environment.copy(link.url)
  return 'copied'
}

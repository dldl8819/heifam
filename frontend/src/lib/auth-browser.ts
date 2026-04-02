const IN_APP_BROWSER_PATTERNS = [
  /KAKAOTALK/i,
  /NAVER/i,
  /DaumApps/i,
  /FBAN/i,
  /FBAV/i,
  /Instagram/i,
  /Line/i,
  /; wv\)/i,
  /\bwv\b/i,
  /WebView/i,
]

export function isInAppBrowser(userAgent?: string): boolean {
  const ua =
    userAgent ??
    (typeof navigator === 'undefined' ? '' : navigator.userAgent)

  if (!ua) {
    return false
  }

  return IN_APP_BROWSER_PATTERNS.some((pattern) => pattern.test(ua))
}

export function buildSupabaseAuthRedirectTo(): string | undefined {
  if (typeof window === 'undefined') {
    return undefined
  }

  const configuredOrigin = process.env.NEXT_PUBLIC_APP_ORIGIN?.trim()
  if (configuredOrigin) {
    try {
      const url = new URL(configuredOrigin)
      if (url.protocol === 'https:' || url.protocol === 'http:') {
        return `${url.origin}/auth/callback`
      }
    } catch {
      // fallback to runtime origin
    }
  }

  return `${window.location.origin}/auth/callback`
}

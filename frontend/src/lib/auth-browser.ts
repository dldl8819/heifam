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

  return `${window.location.origin}/auth/callback`
}

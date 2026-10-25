export const THEME_STORAGE_KEY = 'heifam-theme'

export type ColorTheme = 'light' | 'dark'

export function isColorTheme(value: unknown): value is ColorTheme {
  return value === 'light' || value === 'dark'
}

export function resolveColorTheme(storedTheme: unknown, prefersDark: boolean): ColorTheme {
  if (isColorTheme(storedTheme)) {
    return storedTheme
  }

  return prefersDark ? 'dark' : 'light'
}

export const CONTRAST_STORAGE_KEY = 'heifam-contrast'

/** The class on <html> that turns the high-contrast colours on (globals.css). */
export const HIGH_CONTRAST_CLASS = 'hc'

/** 'more' is the high-contrast mode; 'normal' is the site as drawn. It goes with either theme. */
export type ContrastMode = 'more' | 'normal'

export function isContrastMode(value: unknown): value is ContrastMode {
  return value === 'more' || value === 'normal'
}

/** A choice made on this browser wins; without one, the device's "increase contrast" setting decides. */
export function resolveContrastMode(storedMode: unknown, prefersMore: boolean): ContrastMode {
  if (isContrastMode(storedMode)) {
    return storedMode
  }

  return prefersMore ? 'more' : 'normal'
}

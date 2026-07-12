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

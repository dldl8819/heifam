'use client'

import { useEffect, useState } from 'react'
import { isColorTheme, THEME_STORAGE_KEY, type ColorTheme } from '@/lib/theme'

function getSystemTheme(): ColorTheme {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function getStoredTheme(): ColorTheme | null {
  try {
    const storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
    return isColorTheme(storedTheme) ? storedTheme : null
  } catch {
    return null
  }
}

function applyTheme(theme: ColorTheme) {
  const root = document.documentElement
  root.classList.toggle('dark', theme === 'dark')
  root.style.colorScheme = theme
}

export function ThemeToggle() {
  const [theme, setTheme] = useState<ColorTheme>('light')

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')

    const syncTheme = () => {
      const nextTheme = getStoredTheme() ?? getSystemTheme()
      applyTheme(nextTheme)
      setTheme(nextTheme)
    }

    const handleSystemThemeChange = () => {
      if (getStoredTheme() === null) {
        syncTheme()
      }
    }

    const handleStorageChange = (event: StorageEvent) => {
      if (event.key === THEME_STORAGE_KEY) {
        syncTheme()
      }
    }

    syncTheme()
    mediaQuery.addEventListener('change', handleSystemThemeChange)
    window.addEventListener('storage', handleStorageChange)

    return () => {
      mediaQuery.removeEventListener('change', handleSystemThemeChange)
      window.removeEventListener('storage', handleStorageChange)
    }
  }, [])

  const toggleTheme = () => {
    const nextTheme: ColorTheme = document.documentElement.classList.contains('dark')
      ? 'light'
      : 'dark'

    applyTheme(nextTheme)
    setTheme(nextTheme)

    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, nextTheme)
    } catch {
      // The selected theme still applies for the current page when storage is unavailable.
    }
  }

  const nextThemeLabel = theme === 'dark' ? '\uB77C\uC774\uD2B8 \uBAA8\uB4DC\uB85C \uC804\uD658' : '\uB2E4\uD06C \uBAA8\uB4DC\uB85C \uC804\uD658'

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={nextThemeLabel}
      aria-pressed={theme === 'dark'}
      title={nextThemeLabel}
      data-testid="theme-toggle"
      className="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-slate-700 bg-slate-800/80 text-slate-100 transition-colors hover:bg-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900 md:h-10 md:w-10"
    >
      <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5 dark:hidden" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M20.4 15.5A8.5 8.5 0 018.5 3.6 8.5 8.5 0 1020.4 15.5z" />
      </svg>
      <svg aria-hidden="true" viewBox="0 0 24 24" className="hidden h-5 w-5 dark:block" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
      </svg>
    </button>
  )
}

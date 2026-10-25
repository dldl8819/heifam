'use client'

import { useEffect, useState } from 'react'
import { t } from '@/lib/i18n'
import {
  CONTRAST_STORAGE_KEY,
  HIGH_CONTRAST_CLASS,
  isContrastMode,
  resolveContrastMode,
  type ContrastMode,
} from '@/lib/theme'

const PREFERS_MORE_QUERY = '(prefers-contrast: more)'

function getStoredMode(): ContrastMode | null {
  try {
    const storedMode = window.localStorage.getItem(CONTRAST_STORAGE_KEY)
    return isContrastMode(storedMode) ? storedMode : null
  } catch {
    return null
  }
}

function applyMode(mode: ContrastMode) {
  document.documentElement.classList.toggle(HIGH_CONTRAST_CLASS, mode === 'more')
}

/**
 * Turns the high-contrast colours on and off, beside the light/dark switch and with either theme.
 * The choice stays on this browser only; without one, the device's "increase contrast" decides.
 */
export function ContrastToggle() {
  const [mode, setMode] = useState<ContrastMode>('normal')

  useEffect(() => {
    const mediaQuery = window.matchMedia(PREFERS_MORE_QUERY)

    const syncMode = () => {
      const nextMode = resolveContrastMode(getStoredMode(), mediaQuery.matches)
      applyMode(nextMode)
      setMode(nextMode)
    }

    const handleDeviceChange = () => {
      if (getStoredMode() === null) {
        syncMode()
      }
    }

    const handleStorageChange = (event: StorageEvent) => {
      if (event.key === CONTRAST_STORAGE_KEY) {
        syncMode()
      }
    }

    syncMode()
    mediaQuery.addEventListener('change', handleDeviceChange)
    window.addEventListener('storage', handleStorageChange)

    return () => {
      mediaQuery.removeEventListener('change', handleDeviceChange)
      window.removeEventListener('storage', handleStorageChange)
    }
  }, [])

  const toggleMode = () => {
    const nextMode: ContrastMode = document.documentElement.classList.contains(HIGH_CONTRAST_CLASS) ? 'normal' : 'more'

    applyMode(nextMode)
    setMode(nextMode)

    try {
      window.localStorage.setItem(CONTRAST_STORAGE_KEY, nextMode)
    } catch {
      // The choice still applies to this page when storage is unavailable.
    }
  }

  const label = mode === 'more' ? t('a11y.contrastOff') : t('a11y.contrastOn')

  return (
    <button
      type="button"
      onClick={toggleMode}
      aria-label={label}
      aria-pressed={mode === 'more'}
      title={label}
      data-testid="contrast-toggle"
      className="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-slate-700 bg-slate-800/80 text-slate-100 transition-colors hover:bg-slate-700 md:h-10 md:w-10"
    >
      {/* A circle half filled: the usual sign for contrast. */}
      <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="9" />
        <path d="M12 3a9 9 0 0 1 0 18z" fill="currentColor" stroke="none" />
      </svg>
    </button>
  )
}

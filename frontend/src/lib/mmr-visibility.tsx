'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

const MMR_VISIBILITY_STORAGE_KEY = 'heifam.ui.mmr-visible'

type MmrVisibilityContextValue = {
  mmrVisible: boolean
  setMmrVisible: (nextValue: boolean) => void
  toggleMmrVisible: () => void
}

const MmrVisibilityContext = createContext<MmrVisibilityContextValue | undefined>(undefined)

export function MmrVisibilityProvider({ children }: { children: ReactNode }) {
  const [mmrVisible, setMmrVisibleState] = useState<boolean>(false)

  useEffect(() => {
    if (typeof window === 'undefined') {
      return
    }

    try {
      const stored = window.localStorage.getItem(MMR_VISIBILITY_STORAGE_KEY)
      if (stored === 'true') {
        setMmrVisibleState(true)
      }
    } catch {
      // ignore storage read errors
    }
  }, [])

  const setMmrVisible = useCallback((nextValue: boolean) => {
    setMmrVisibleState(nextValue)

    if (typeof window === 'undefined') {
      return
    }

    try {
      window.localStorage.setItem(MMR_VISIBILITY_STORAGE_KEY, nextValue ? 'true' : 'false')
    } catch {
      // ignore storage write errors
    }
  }, [])

  const toggleMmrVisible = useCallback(() => {
    setMmrVisibleState((prevValue) => {
      const nextValue = !prevValue

      if (typeof window !== 'undefined') {
        try {
          window.localStorage.setItem(MMR_VISIBILITY_STORAGE_KEY, nextValue ? 'true' : 'false')
        } catch {
          // ignore storage write errors
        }
      }

      return nextValue
    })
  }, [])

  const value = useMemo<MmrVisibilityContextValue>(
    () => ({
      mmrVisible,
      setMmrVisible,
      toggleMmrVisible,
    }),
    [mmrVisible, setMmrVisible, toggleMmrVisible]
  )

  return <MmrVisibilityContext.Provider value={value}>{children}</MmrVisibilityContext.Provider>
}

export function useMmrVisibility(): MmrVisibilityContextValue {
  const context = useContext(MmrVisibilityContext)
  if (!context) {
    throw new Error('useMmrVisibility must be used inside MmrVisibilityProvider')
  }

  return context
}

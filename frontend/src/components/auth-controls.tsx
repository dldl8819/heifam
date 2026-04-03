'use client'

import { useEffect, useRef, useState } from 'react'
import { useAuth } from '@/components/auth-session-provider'
import { useAdminAuth } from '@/lib/admin-auth'
import { buildSupabaseAuthRedirectTo, isInAppBrowser } from '@/lib/auth-browser'
import { supabase } from '@/lib/supabase'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'

function buildDisplayName(name?: string | null): string {
  const normalizedName = name?.trim()
  if (normalizedName && normalizedName.length > 0) {
    return normalizedName
  }

  return t('auth.noProfile')
}

export function AuthControls() {
  const { user, loading, signOut } = useAuth()
  const { nickname, isAdmin } = useAdminAuth()
  const { mmrVisible, setMmrVisible } = useMmrVisibility()
  const [warningMessage, setWarningMessage] = useState<string | null>(null)
  const [oauthInProgress, setOauthInProgress] = useState<boolean>(false)
  const [menuOpen, setMenuOpen] = useState<boolean>(false)
  const menuRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (!menuOpen) {
      return
    }

    const handlePointerDown = (event: MouseEvent | TouchEvent) => {
      if (!menuRef.current) {
        return
      }

      const target = event.target
      if (target instanceof Node && !menuRef.current.contains(target)) {
        setMenuOpen(false)
      }
    }

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMenuOpen(false)
      }
    }

    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('touchstart', handlePointerDown)
    document.addEventListener('keydown', handleEscape)

    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('touchstart', handlePointerDown)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [menuOpen])

  useEffect(() => {
    if (!user) {
      setMenuOpen(false)
    }
  }, [user])

  const handleGoogleLogin = async () => {
    if (oauthInProgress) {
      return
    }

    if (isInAppBrowser()) {
      setWarningMessage(t('auth.inAppBrowser.alert'))
      return
    }

    setOauthInProgress(true)
    setWarningMessage(null)
    const redirectTo = buildSupabaseAuthRedirectTo()

    const { error } = await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: {
        redirectTo,
      },
    })

    if (error) {
      setWarningMessage(t('auth.error.default'))
      setOauthInProgress(false)
    }
  }

  const displayName = buildDisplayName(
    nickname ||
      user?.user_metadata?.nickname ||
      user?.user_metadata?.full_name ||
      user?.user_metadata?.name
  )

  if (loading) {
    return (
      <div className="rounded-lg border border-slate-700 bg-slate-800/70 px-3 py-2 text-xs text-slate-300">
        {t('auth.loading')}
      </div>
    )
  }

  if (!user) {
    return (
      <div className="space-y-2">
        <button
          onClick={() => void handleGoogleLogin()}
          disabled={oauthInProgress}
          className="rounded-lg border border-slate-700 bg-slate-800/70 px-3 py-2 text-xs text-slate-300 hover:bg-slate-700 transition-colors"
        >
          {oauthInProgress ? t('auth.loginGooglePending') : t('auth.loginGoogle')}
        </button>
        {warningMessage && (
          <p className="max-w-xs text-xs text-amber-300">
            {warningMessage}
          </p>
        )}
      </div>
    )
  }

  return (
    <div className="flex flex-wrap items-center justify-end gap-2">
      <div ref={menuRef} className="relative">
        <button
          type="button"
          onClick={() => setMenuOpen((prevValue) => !prevValue)}
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          className="inline-flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-800/70 px-3 py-2 text-xs text-slate-300 transition-colors hover:bg-slate-700"
        >
          <span>{displayName}</span>
          <svg
            viewBox="0 0 12 12"
            aria-hidden="true"
            className={`h-3 w-3 transition-transform ${menuOpen ? 'rotate-180' : ''}`}
          >
            <path
              d="M2.25 4.5 6 8.25 9.75 4.5"
              fill="none"
              stroke="currentColor"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth="1.5"
            />
          </svg>
        </button>

        {menuOpen && (
          <div
            role="menu"
            className="absolute right-0 top-full z-40 mt-2 min-w-[11rem] rounded-xl border border-slate-700 bg-slate-900/95 p-2 shadow-2xl backdrop-blur"
          >
            {isAdmin && (
              <label className="flex cursor-pointer items-center justify-between gap-3 rounded-lg px-3 py-2 text-xs text-slate-200 transition-colors hover:bg-slate-800/80">
                <span>{t('auth.mmrToggle')}</span>
                <input
                  type="checkbox"
                  checked={mmrVisible}
                  onChange={(event) => setMmrVisible(event.target.checked)}
                  className="h-3.5 w-3.5 accent-amber-400"
                />
              </label>
            )}
            <button
              type="button"
              onClick={() => {
                setMenuOpen(false)
                void signOut()
              }}
              className="mt-1 w-full rounded-lg px-3 py-2 text-left text-xs text-slate-300 transition-colors hover:bg-slate-800/80"
            >
              {t('auth.logout')}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

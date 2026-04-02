'use client'

import { useState } from 'react'
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

  const handleGoogleLogin = async () => {
    if (isInAppBrowser()) {
      setWarningMessage(t('auth.inAppBrowser.alert'))
      return
    }

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
    }
  }

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
          className="rounded-lg border border-slate-700 bg-slate-800/70 px-3 py-2 text-xs text-slate-300 hover:bg-slate-700 transition-colors"
        >
          {t('auth.loginGoogle')}
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
      {isAdmin && (
        <label className="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-slate-700 bg-slate-800/70 px-3 py-2 text-xs text-slate-300">
          <span>{t('auth.mmrToggle')}</span>
          <input
            type="checkbox"
            checked={mmrVisible}
            onChange={(event) => setMmrVisible(event.target.checked)}
            className="h-3.5 w-3.5 accent-amber-400"
          />
        </label>
      )}
      <div className="rounded-lg border border-slate-700 bg-slate-800/70 px-3 py-2 text-xs text-slate-300">
        {buildDisplayName(
          nickname ||
            user.user_metadata?.nickname ||
            user.user_metadata?.full_name ||
            user.user_metadata?.name
        )}
      </div>
      <button
        type="button"
        onClick={signOut}
        className="rounded-lg border border-slate-700 bg-slate-800/70 px-3 py-2 text-xs text-slate-300 hover:bg-slate-700 transition-colors"
      >
        {t('auth.logout')}
      </button>
    </div>
  )
}

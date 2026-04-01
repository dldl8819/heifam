'use client'

import { useAuth } from '@/components/auth-session-provider'
import { useAdminAuth } from '@/lib/admin-auth'
import { supabase } from '@/lib/supabase'
import { t } from '@/lib/i18n'

function buildDisplayName(name?: string | null): string {
  const normalizedName = name?.trim()
  if (normalizedName && normalizedName.length > 0) {
    return normalizedName
  }

  return t('auth.noProfile')
}

export function AuthControls() {
  const { user, loading, signOut } = useAuth()
  const { nickname } = useAdminAuth()

  const handleGoogleLogin = async () => {
    const redirectTo =
      typeof window === 'undefined'
        ? undefined
        : `${window.location.origin}/auth/callback`

    await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: {
        redirectTo,
      },
    })
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
      <button
        onClick={() => void handleGoogleLogin()}
        className="rounded-lg border border-slate-700 bg-slate-800/70 px-3 py-2 text-xs text-slate-300 hover:bg-slate-700 transition-colors"
      >
        {t('auth.loginGoogle')}
      </button>
    )
  }

  return (
    <div className="flex items-center gap-2">
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

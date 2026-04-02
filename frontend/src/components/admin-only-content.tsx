'use client'

import type { ReactNode } from 'react'
import { useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { buildSupabaseAuthRedirectTo, isInAppBrowser } from '@/lib/auth-browser'
import { supabase } from '@/lib/supabase'
import { t } from '@/lib/i18n'

type AdminOnlyContentProps = {
  children: ReactNode
}

export function AdminOnlyContent({ children }: AdminOnlyContentProps) {
  const { isAdmin, isLoading, isLoggedIn, canAccess } = useAdminAuth()
  const [warningMessage, setWarningMessage] = useState<string | null>(null)
  const [oauthInProgress, setOauthInProgress] = useState<boolean>(false)

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

  if (isLoading) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white px-4 py-4 text-sm text-slate-600 shadow-sm">
        {t('auth.loading')}
      </div>
    )
  }

  if (!isLoggedIn) {
    return (
      <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-4 text-sm text-amber-900 shadow-sm">
        <p className="font-semibold">{t('adminGuard.loginTitle')}</p>
        <p className="mt-1 text-xs">{t('adminGuard.loginDescription')}</p>
        <button
          type="button"
          onClick={() => void handleGoogleLogin()}
          disabled={oauthInProgress}
          className="mt-3 rounded-lg bg-emerald-500 px-3 py-2 text-xs font-semibold text-slate-950 transition-colors hover:bg-emerald-400"
        >
          {oauthInProgress ? t('auth.loginGooglePending') : t('auth.loginGoogle')}
        </button>
        {warningMessage && (
          <p className="mt-2 text-xs text-amber-800">
            {warningMessage}
          </p>
        )}
      </div>
    )
  }

  if (!canAccess) {
    return (
      <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-4 text-sm text-rose-800 shadow-sm">
        <p className="font-semibold">{t('access.blockedTitle')}</p>
        <p className="mt-1 text-xs">{t('access.blockedDescription')}</p>
      </div>
    )
  }

  if (!isAdmin) {
    return (
      <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-4 text-sm text-rose-800 shadow-sm">
        <p className="font-semibold">{t('adminGuard.forbiddenTitle')}</p>
        <p className="mt-1 text-xs">{t('adminGuard.forbiddenDescription')}</p>
      </div>
    )
  }

  return <>{children}</>
}

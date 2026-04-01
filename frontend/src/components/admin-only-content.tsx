'use client'

import type { ReactNode } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { supabase } from '@/lib/supabase'
import { t } from '@/lib/i18n'

type AdminOnlyContentProps = {
  children: ReactNode
}

export function AdminOnlyContent({ children }: AdminOnlyContentProps) {
  const { isAdmin, isLoading, isLoggedIn, canAccess } = useAdminAuth()

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
          className="mt-3 rounded-lg bg-emerald-500 px-3 py-2 text-xs font-semibold text-slate-950 transition-colors hover:bg-emerald-400"
        >
          {t('auth.loginGoogle')}
        </button>
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

'use client'

import { useEffect } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { useAuth } from '@/components/auth-session-provider'
import { buildSupabaseAuthRedirectTo, isInAppBrowser } from '@/lib/auth-browser'
import { t } from '@/lib/i18n'
import { supabase } from '@/lib/supabase'

export default function AuthPage() {
  const { user, loading } = useAuth()
  const router = useRouter()
  const blockedInAppBrowser = typeof window !== 'undefined' && isInAppBrowser()

  useEffect(() => {
    if (loading) {
      return
    }

    if (user) {
      router.replace('/dashboard')
      return
    }

    if (blockedInAppBrowser) {
      return
    }

    const redirectTo = buildSupabaseAuthRedirectTo()

    void supabase.auth.signInWithOAuth({
      provider: 'google',
      options: {
        redirectTo,
      },
    })
  }, [blockedInAppBrowser, loading, router, user])

  if (blockedInAppBrowser) {
    return (
      <section className="space-y-6">
        <header className="rounded-xl border border-amber-200 bg-amber-50 px-5 py-4 shadow-sm">
          <h2 className="text-2xl font-semibold tracking-tight text-amber-900">
            {t('auth.inAppBrowser.title')}
          </h2>
          <p className="mt-1 text-sm text-amber-800">
            {t('auth.inAppBrowser.description')}
          </p>
        </header>

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <ol className="list-decimal space-y-1 pl-5 text-sm text-slate-700">
            <li>{t('auth.inAppBrowser.steps.one')}</li>
            <li>{t('auth.inAppBrowser.steps.two')}</li>
          </ol>
          <Link
            href="/"
            className="mt-3 inline-flex rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800"
          >
            {t('auth.inAppBrowser.goHome')}
          </Link>
        </article>
      </section>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center">
      <LoadingIndicator label="로그인 페이지로 이동 중..." />
    </div>
  )
}

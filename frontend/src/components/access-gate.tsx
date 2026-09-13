'use client'

import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { useAdminAuth } from '@/lib/admin-auth'
import { getRouteAccessDecision } from '@/lib/route-access'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'

/** Application form for members who have not been granted access yet. */
const ACCESS_REQUEST_FORM_URL = 'https://forms.gle/RpgrcQFLNw4ZpdcP7'

type AccessGateProps = {
  children: ReactNode
}

export function AccessGate({ children }: AccessGateProps) {
  const {
    isLoggedIn,
    canAccess,
    isLoading,
    email,
    isAdmin,
    isSuperAdmin,
    accessError,
    refreshAccess,
  } = useAdminAuth()
  const pathname = usePathname()
  const router = useRouter()
  const decision = getRouteAccessDecision(pathname, {
    isLoggedIn,
    canAccess,
    isAdmin,
    isSuperAdmin,
  })

  useEffect(() => {
    if (isLoading || !decision.redirectTo) {
      return
    }

    router.replace(decision.redirectTo)
  }, [decision.redirectTo, isLoading, router])

  useEffect(() => {
    if (!accessError || !isLoggedIn || isLoading) {
      return
    }

    const retryTimer = window.setTimeout(() => {
      void refreshAccess()
    }, 1500)

    return () => window.clearTimeout(retryTimer)
  }, [accessError, isLoading, isLoggedIn, refreshAccess])

  if (isLoading) {
    return <LoadingIndicator label={t('auth.loading')} />
  }

  if (accessError && !canAccess) {
    return <LoadingIndicator label="권한 확인 재시도 중..." />
  }

  if (decision.redirectTo) {
    return null
  }

  if (decision.allowed) {
    return <>{children}</>
  }

  if (!decision.blocked) {
    return null
  }

  return (
    <section className="rounded-xl border border-rose-200 bg-rose-50 px-5 py-4 text-rose-800 shadow-sm dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-200">
      <h2 className="text-lg font-semibold">{t('access.blockedTitle')}</h2>
      <p className="mt-1 text-sm">{t('access.blockedDescription')}</p>
      <a
        href={ACCESS_REQUEST_FORM_URL}
        target="_blank"
        rel="noopener noreferrer"
        className="mt-3 inline-flex items-center rounded-lg bg-rose-700 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-rose-800 dark:bg-rose-600 dark:hover:bg-rose-500"
      >
        {t('access.blockedApply')}
      </a>
      {email && (
        <p className="mt-3 text-xs text-rose-700 dark:text-rose-300">
          {t('access.blockedEmail', { email })}
        </p>
      )}
      <p className="mt-1 text-xs text-rose-700 dark:text-rose-300">
        {t('access.blockedEmailHint')}
      </p>
    </section>
  )
}

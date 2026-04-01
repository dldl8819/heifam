'use client'

import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { useAdminAuth } from '@/lib/admin-auth'
import { useAuth } from '@/components/auth-session-provider'
import { getRouteAccessDecision } from '@/lib/route-access'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'

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
    role,
    accessError,
    refreshAccess,
  } = useAdminAuth()
  const { signOut } = useAuth()
  const [signingOutBlockedUser, setSigningOutBlockedUser] = useState(false)
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
    const isAuthRoute = pathname === '/auth' || pathname.startsWith('/auth/')
    if (
      isAuthRoute ||
      isLoading ||
      !isLoggedIn ||
      canAccess ||
      signingOutBlockedUser ||
      accessError ||
      role !== 'BLOCKED'
    ) {
      return
    }

    setSigningOutBlockedUser(true)
    void signOut().finally(() => {
      router.replace('/')
      setSigningOutBlockedUser(false)
    })
  }, [
    accessError,
    canAccess,
    isLoading,
    isLoggedIn,
    pathname,
    role,
    router,
    signOut,
    signingOutBlockedUser,
  ])

  useEffect(() => {
    if (!accessError || !isLoggedIn || isLoading) {
      return
    }

    const retryTimer = window.setTimeout(() => {
      void refreshAccess()
    }, 1500)

    return () => window.clearTimeout(retryTimer)
  }, [accessError, isLoading, isLoggedIn, refreshAccess])

  if (signingOutBlockedUser) {
    return <LoadingIndicator label={t('auth.loading')} />
  }

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
    <section className="rounded-xl border border-rose-200 bg-rose-50 px-5 py-4 text-rose-800 shadow-sm">
      <h2 className="text-lg font-semibold">{t('access.blockedTitle')}</h2>
      <p className="mt-1 text-sm">{t('access.blockedDescription')}</p>
      {email && (
        <p className="mt-2 text-xs text-rose-700">
          {t('access.blockedEmail', { email })}
        </p>
      )}
    </section>
  )
}

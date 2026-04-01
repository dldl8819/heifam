'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { useAuth } from '@/components/auth-session-provider'
import { supabase } from '@/lib/supabase'

export default function AuthPage() {
  const { user, loading } = useAuth()
  const router = useRouter()

  useEffect(() => {
    if (loading) {
      return
    }

    if (user) {
      router.replace('/dashboard')
      return
    }

    const redirectTo =
      typeof window === 'undefined'
        ? undefined
        : `${window.location.origin}/auth/callback`

    void supabase.auth.signInWithOAuth({
      provider: 'google',
      options: {
        redirectTo,
      },
    })
  }, [loading, router, user])

  return (
    <div className="min-h-screen flex items-center justify-center">
      <LoadingIndicator label="로그인 페이지로 이동 중..." />
    </div>
  )
}


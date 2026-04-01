'use client'

import type { ReactNode } from 'react'
import { supabase } from '@/lib/supabase'

type GoogleLoginButtonProps = {
  className?: string
  children: ReactNode
}

export function GoogleLoginButton({ className, children }: GoogleLoginButtonProps) {
  const handleLogin = async () => {
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

  return (
    <button type="button" onClick={() => void handleLogin()} className={className}>
      {children}
    </button>
  )
}

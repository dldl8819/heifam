'use client'

import type { ReactNode } from 'react'
import { useState } from 'react'
import { t } from '@/lib/i18n'
import { buildSupabaseAuthRedirectTo, isInAppBrowser } from '@/lib/auth-browser'
import { supabase } from '@/lib/supabase'

type GoogleLoginButtonProps = {
  className?: string
  children: ReactNode
}

export function GoogleLoginButton({ className, children }: GoogleLoginButtonProps) {
  const [warningMessage, setWarningMessage] = useState<string | null>(null)
  const [oauthInProgress, setOauthInProgress] = useState<boolean>(false)

  const handleLogin = async () => {
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

  return (
    <div className="space-y-2">
      <button
        type="button"
        onClick={() => void handleLogin()}
        disabled={oauthInProgress}
        className={className}
      >
        {children}
      </button>
      {warningMessage && (
        <p className="max-w-md text-xs text-amber-200">
          {warningMessage}
        </p>
      )}
    </div>
  )
}

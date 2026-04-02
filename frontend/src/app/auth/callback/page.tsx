'use client'

import { useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { ApiRequestError, apiClient } from '@/lib/api'
import { supabase } from '@/lib/supabase'

function resolveSessionNickname(metadata: unknown): string {
  if (!metadata || typeof metadata !== 'object') {
    return ''
  }

  const source = metadata as Record<string, unknown>
  const nickname =
    source.nickname ??
    source.full_name ??
    source.name ??
    source.preferred_username

  return typeof nickname === 'string' ? nickname.trim() : ''
}

function isPkceVerifierMissingError(error: unknown): boolean {
  if (!(error instanceof Error)) {
    return false
  }

  return (
    error.name === 'AuthPKCECodeVerifierMissingError' ||
    /pkce code verifier not found/i.test(error.message)
  )
}

function isFlowStateNotFoundError(error: unknown): boolean {
  if (!(error instanceof Error)) {
    return false
  }

  return (
    /flow_state_not_found/i.test(error.message) ||
    /invalid flow state/i.test(error.message) ||
    /no valid flow state found/i.test(error.message)
  )
}

async function waitForSessionWithRetry(maxAttempts: number = 8, delayMs: number = 250) {
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    const { data } = await supabase.auth.getSession()
    if (data.session) {
      return data.session
    }

    if (attempt < maxAttempts - 1) {
      await new Promise((resolve) => setTimeout(resolve, delayMs))
    }
  }

  return null
}

export default function AuthCallbackPage() {
  const router = useRouter()
  const handledRef = useRef(false)

  useEffect(() => {
    if (handledRef.current) {
      return
    }
    handledRef.current = true

    const handleAuthCallback = async () => {
      const authCode =
        typeof window === 'undefined'
          ? null
          : new URL(window.location.href).searchParams.get('code')
      let session = null

      if (authCode) {
        const { data: exchangeData, error: exchangeError } = await supabase.auth.exchangeCodeForSession(
          authCode
        )
        if (exchangeError) {
          const recoverableFlowError =
            isPkceVerifierMissingError(exchangeError) || isFlowStateNotFoundError(exchangeError)

          if (!recoverableFlowError) {
            console.error('Error exchanging auth code:', exchangeError)
            await supabase.auth.signOut()
            router.replace('/')
            return
          }

          session = await waitForSessionWithRetry()

          if (!session) {
            console.error('Error exchanging auth code:', exchangeError)
            router.replace('/auth?retry=1')
            return
          }
        } else {
          session = exchangeData.session
        }
      }

      if (!session) {
        session = (await supabase.auth.getSession()).data.session
      }

      if (!session) {
        router.replace('/')
        return
      }

      try {
        const sessionEmail = session.user.email?.trim().toLowerCase() ?? ''
        if (sessionEmail.length === 0) {
          await supabase.auth.signOut()
          router.replace('/')
          return
        }

        const access = await apiClient.getMyAccess({
          email: sessionEmail,
          nickname: resolveSessionNickname(session.user.user_metadata),
          accessToken: session.access_token ?? '',
        })
        if (!access.allowed) {
          await supabase.auth.signOut()
          router.replace('/')
          return
        }

        router.replace(access.superAdmin ? '/dashboard' : '/ranking')
      } catch (callbackError) {
        if (
          callbackError instanceof ApiRequestError &&
          (callbackError.status === 401 || callbackError.status === 403)
        ) {
          await supabase.auth.signOut()
          router.replace('/')
          return
        }

        console.error('Error verifying access during auth callback:', callbackError)
        router.replace('/ranking')
      }
    }

    void handleAuthCallback()
  }, [router])

  return (
    <div className="min-h-screen flex items-center justify-center">
      <LoadingIndicator label="로그인 처리 중..." />
    </div>
  )
}

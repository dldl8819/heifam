'use client'

import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '@/components/auth-session-provider'
import { ApiRequestError, apiClient } from '@/lib/api'
import type { AccessMeResponse, AccessRole, PlayerRace } from '@/types/api'

let cachedAccessEmail: string | null = null
let cachedAccessProfile: AccessMeResponse | null = null
const ACCESS_FETCH_RETRY_DELAYS_MS = [400, 900]

export type AdminAuthState = {
  status: 'loading' | 'authenticated' | 'unauthenticated'
  isLoading: boolean
  isLoggedIn: boolean
  isAdmin: boolean
  isSuperAdmin: boolean
  canAccess: boolean
  role: AccessRole
  accessError: boolean
  email: string | null
  nickname: string | null
  preferredRace: PlayerRace | null
  refreshAccess: () => Promise<void>
}

function resolveRole(profile: AccessMeResponse | null): AccessRole {
  if (!profile) {
    return 'BLOCKED'
  }
  return profile.role
}

export function useAdminAuth(): AdminAuthState {
  const { user, loading } = useAuth()
  const email = user?.email?.trim().toLowerCase() ?? null
  const [accessProfile, setAccessProfile] = useState<AccessMeResponse | null>(null)
  const [accessLoading, setAccessLoading] = useState<boolean>(false)
  const [accessError, setAccessError] = useState<boolean>(false)

  const refreshAccess = useCallback(async () => {
    if (!email) {
      cachedAccessEmail = null
      cachedAccessProfile = null
      setAccessProfile(null)
      setAccessLoading(false)
      setAccessError(false)
      return
    }

    setAccessLoading(true)
    setAccessError(false)
    let lastError: unknown = null

    try {
      for (let attempt = 0; attempt <= ACCESS_FETCH_RETRY_DELAYS_MS.length; attempt += 1) {
        try {
          const profile = await apiClient.getMyAccess({ email })
          cachedAccessEmail = email
          cachedAccessProfile = profile
          setAccessProfile(profile)
          setAccessError(false)
          return
        } catch (error) {
          if (
            error instanceof ApiRequestError &&
            (error.status === 401 || error.status === 403)
          ) {
            const blockedProfile: AccessMeResponse = {
              email,
              nickname: null,
              role: 'BLOCKED',
              admin: false,
              superAdmin: false,
              allowed: false,
              preferredRace: null,
            }
            cachedAccessEmail = email
            cachedAccessProfile = blockedProfile
            setAccessProfile(blockedProfile)
            setAccessError(false)
            return
          }

          lastError = error
          if (attempt < ACCESS_FETCH_RETRY_DELAYS_MS.length) {
            const delayMs = ACCESS_FETCH_RETRY_DELAYS_MS[attempt]
            await new Promise((resolve) => setTimeout(resolve, delayMs))
            continue
          }
          break
        }
      }

      if (cachedAccessEmail === email && cachedAccessProfile) {
        setAccessProfile(cachedAccessProfile)
      } else {
        setAccessProfile({
          email,
          nickname: null,
          role: 'MEMBER',
          admin: false,
          superAdmin: false,
          allowed: false,
          preferredRace: null,
        })
      }
      setAccessError(true)
      console.error('Failed to load access profile after retries:', lastError)
    } finally {
      setAccessLoading(false)
    }
  }, [email])

  useEffect(() => {
    if (loading) {
      return
    }

    if (!email) {
      cachedAccessEmail = null
      cachedAccessProfile = null
      setAccessProfile(null)
      setAccessLoading(false)
      setAccessError(false)
      return
    }

    if (cachedAccessEmail === email && cachedAccessProfile !== null) {
      setAccessProfile(cachedAccessProfile)
      setAccessLoading(false)
      setAccessError(false)
      return
    }

    void refreshAccess()
  }, [email, loading, refreshAccess])

  const isLoggedIn = Boolean(user)
  const needsAccessResolution = isLoggedIn && accessProfile === null
  const isLoading = loading || needsAccessResolution || (isLoggedIn && accessLoading)
  const status: AdminAuthState['status'] = isLoading
    ? 'loading'
    : isLoggedIn
      ? 'authenticated'
      : 'unauthenticated'

  return {
    status,
    isLoading,
    isLoggedIn,
    isAdmin: Boolean(accessProfile?.admin),
    isSuperAdmin: Boolean(accessProfile?.superAdmin),
    canAccess: Boolean(accessProfile?.allowed),
    role: resolveRole(accessProfile),
    accessError,
    email,
    nickname: accessProfile?.nickname ?? null,
    preferredRace: accessProfile?.preferredRace ?? null,
    refreshAccess,
  }
}

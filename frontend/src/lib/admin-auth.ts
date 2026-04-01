'use client'

import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '@/components/auth-session-provider'
import { apiClient } from '@/lib/api'
import type { AccessMeResponse, AccessRole, PlayerRace } from '@/types/api'

let cachedAccessEmail: string | null = null
let cachedAccessProfile: AccessMeResponse | null = null

export type AdminAuthState = {
  status: 'loading' | 'authenticated' | 'unauthenticated'
  isLoading: boolean
  isLoggedIn: boolean
  isAdmin: boolean
  isSuperAdmin: boolean
  canAccess: boolean
  role: AccessRole
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

  const refreshAccess = useCallback(async () => {
    if (!email) {
      cachedAccessEmail = null
      cachedAccessProfile = null
      setAccessProfile(null)
      setAccessLoading(false)
      return
    }

    setAccessLoading(true)
    try {
      const profile = await apiClient.getMyAccess({ email })
      cachedAccessEmail = email
      cachedAccessProfile = profile
      setAccessProfile(profile)
    } catch {
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
      return
    }

    if (cachedAccessEmail === email && cachedAccessProfile !== null) {
      setAccessProfile(cachedAccessProfile)
      setAccessLoading(false)
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
    email,
    nickname: accessProfile?.nickname ?? null,
    preferredRace: accessProfile?.preferredRace ?? null,
    refreshAccess,
  }
}

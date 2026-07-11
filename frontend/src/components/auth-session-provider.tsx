'use client'

import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { usePathname } from 'next/navigation'
import type { Session, User } from '@supabase/supabase-js'
import { supabase } from '@/lib/supabase'
import { isPublicRoute } from '@/lib/route-access'
import { apiClient, clearSessionIdentityCache } from '@/lib/api'

export type AuthUser = {
  email: string | null
  nickname: string | null
}

type AuthContextType = {
  user: AuthUser | null
  accessToken: string | null
  loading: boolean
  signOut: () => Promise<void>
  deleteAccount: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)
const ENABLE_SUPABASE_PROFILE_SYNC =
  process.env.NEXT_PUBLIC_ENABLE_SUPABASE_PROFILE_SYNC === 'true'
const INITIAL_SESSION_TIMEOUT_MS = 7000
const SUPABASE_AUTH_STORAGE_KEY_PREFIX = 'sb-'
const SUPABASE_AUTH_STORAGE_KEY_SUFFIX = '-auth-token'

function toSafeNickname(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null
  }

  const normalized = value.trim()
  return normalized.length > 0 ? normalized : null
}

function resolveAuthUser(user: User | null): AuthUser | null {
  if (!user) {
    return null
  }

  const nickname = toSafeNickname(
    user.user_metadata && typeof user.user_metadata === 'object'
      ? (user.user_metadata as Record<string, unknown>).nickname
      : null,
  )

  return {
    email: user.email?.trim() ?? null,
    nickname,
  }
}

function isSupabaseLockContentionError(error: unknown): boolean {
  if (!(error instanceof Error)) {
    return false
  }
  return /lock .* was released because another request stole it/i.test(error.message)
}

function hasStoredSupabaseSession(): boolean {
  if (typeof window === 'undefined') {
    return false
  }

  try {
    for (let index = 0; index < window.localStorage.length; index += 1) {
      const key = window.localStorage.key(index)
      if (
        key?.startsWith(SUPABASE_AUTH_STORAGE_KEY_PREFIX) &&
        key.endsWith(SUPABASE_AUTH_STORAGE_KEY_SUFFIX)
      ) {
        return true
      }
    }
  } catch {
    return true
  }

  return false
}

function removeStoredSupabaseSession(): void {
  if (typeof window === 'undefined') {
    return
  }

  try {
    const authStorageKeys: string[] = []
    for (let index = 0; index < window.localStorage.length; index += 1) {
      const key = window.localStorage.key(index)
      if (
        key?.startsWith(SUPABASE_AUTH_STORAGE_KEY_PREFIX) &&
        key.endsWith(SUPABASE_AUTH_STORAGE_KEY_SUFFIX)
      ) {
        authStorageKeys.push(key)
      }
    }

    authStorageKeys.forEach((key) => window.localStorage.removeItem(key))
  } catch {
    // The in-memory auth state is still cleared below when storage is unavailable.
  }
}

function shouldHydrateAuthSession(pathname: string | null): boolean {
  const currentPath = pathname ?? '/'
  if (currentPath === '/auth' || currentPath.startsWith('/auth/')) {
    return true
  }

  if (!isPublicRoute(currentPath)) {
    return true
  }

  return hasStoredSupabaseSession()
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

type AuthProviderProps = {
  children: ReactNode
}

export function AuthProvider({ children }: AuthProviderProps) {
  const pathname = usePathname()
  const [user, setUser] = useState<AuthUser | null>(null)
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    let subscription: { unsubscribe: () => void } | null = null

    if (!shouldHydrateAuthSession(pathname)) {
      setAccessToken(null)
      setUser(null)
      setLoading(false)
      return () => {
        cancelled = true
      }
    }

    setLoading(true)

    const initialSessionTimeout = window.setTimeout(() => {
      if (!cancelled) {
        setLoading(false)
      }
    }, INITIAL_SESSION_TIMEOUT_MS)

    const applySession = (session: Session | null) => {
      if (cancelled) {
        return
      }
      setAccessToken(session?.access_token ?? null)
      setUser(resolveAuthUser(session?.user ?? null))
      setLoading(false)
    }

    // Get initial session
    void supabase.auth
      .getSession()
      .then(({ data: { session } }) => {
        applySession(session)
      })
      .catch((error) => {
        if (cancelled) {
          return
        }
        if (!isSupabaseLockContentionError(error)) {
          console.error('Failed to load auth session')
        }
        setAccessToken(null)
        setUser(null)
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
        window.clearTimeout(initialSessionTimeout)
      })

    // Listen for auth changes
    const {
      data: { subscription: authSubscription },
    } = supabase.auth.onAuthStateChange((event, session) => {
      applySession(session)

      // If user signed in, save to users table
      if (event === 'SIGNED_IN' && session?.user && ENABLE_SUPABASE_PROFILE_SYNC) {
        void saveUserToDatabase(session.user)
      }
    })
    subscription = authSubscription

    return () => {
      cancelled = true
      window.clearTimeout(initialSessionTimeout)
      subscription?.unsubscribe()
    }
  }, [pathname])

  const signOut = async () => {
    clearSessionIdentityCache()
    await supabase.auth.signOut()
  }

  const deleteAccount = async () => {
    const currentAccessToken = accessToken?.trim() ?? ''
    if (currentAccessToken.length === 0) {
      throw new Error('An active session is required to delete the account')
    }

    await apiClient.deleteMyAccount({
      accessToken: currentAccessToken,
    })

    clearSessionIdentityCache()
    try {
      const { error } = await supabase.auth.signOut({ scope: 'local' })
      if (error) {
        removeStoredSupabaseSession()
      }
    } catch {
      removeStoredSupabaseSession()
    }
    setAccessToken(null)
    setUser(null)
  }

  const value = {
    user,
    accessToken,
    loading,
    signOut,
    deleteAccount,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

async function saveUserToDatabase(user: User) {
  try {
    const { error } = await supabase
      .from('users')
      .upsert({
        id: user.id,
        email: user.email,
        avatar_url: user.user_metadata?.avatar_url,
        created_at: user.created_at,
        updated_at: new Date().toISOString(),
      })

    if (error) {
      console.error('Error saving user to database')
    }
  } catch {
    console.error('Error saving user to database')
  }
}

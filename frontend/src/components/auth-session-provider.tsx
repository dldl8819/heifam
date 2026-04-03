'use client'

import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { User } from '@supabase/supabase-js'
import { supabase } from '@/lib/supabase'

export type AuthUser = {
  email: string | null
  nickname: string | null
}

type AuthContextType = {
  user: AuthUser | null
  accessToken: string | null
  loading: boolean
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)
const ENABLE_SUPABASE_PROFILE_SYNC =
  process.env.NEXT_PUBLIC_ENABLE_SUPABASE_PROFILE_SYNC === 'true'
const INITIAL_SESSION_TIMEOUT_MS = 7000

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
  const [user, setUser] = useState<AuthUser | null>(null)
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    const initialSessionTimeout = window.setTimeout(() => {
      if (!cancelled) {
        setLoading(false)
      }
    }, INITIAL_SESSION_TIMEOUT_MS)

    // Get initial session
    void supabase.auth
      .getSession()
      .then(({ data: { session } }) => {
        if (cancelled) {
          return
        }
        setAccessToken(session?.access_token ?? null)
        setUser(resolveAuthUser(session?.user ?? null))
      })
      .catch((error) => {
        if (cancelled) {
          return
        }
        if (!isSupabaseLockContentionError(error)) {
          console.error('Failed to load auth session:', error)
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
      data: { subscription },
    } = supabase.auth.onAuthStateChange((event, session) => {
      if (cancelled) {
        return
      }
      setAccessToken(session?.access_token ?? null)
      setUser(resolveAuthUser(session?.user ?? null))
      setLoading(false)

      // If user signed in, save to users table
      if (event === 'SIGNED_IN' && session?.user && ENABLE_SUPABASE_PROFILE_SYNC) {
        void saveUserToDatabase(session.user)
      }
    })

    return () => {
      cancelled = true
      window.clearTimeout(initialSessionTimeout)
      subscription.unsubscribe()
    }
  }, [])

  const signOut = async () => {
    await supabase.auth.signOut()
  }

  const value = {
    user,
    accessToken,
    loading,
    signOut,
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
      console.error('Error saving user to database:', error)
    }
  } catch (error) {
    console.error('Error saving user to database:', error)
  }
}

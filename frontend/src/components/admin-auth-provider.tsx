'use client'

import type { ReactNode } from 'react'
import { AdminAuthContext, useAdminAuthState } from '@/lib/admin-auth'

type AdminAuthProviderProps = {
  children: ReactNode
}

/**
 * Fetches /api/access/me exactly once for the whole app and shares the result via
 * context, so every useAdminAuth() consumer (nav, access gate, pages) reads the same
 * state instead of each triggering its own request.
 */
export function AdminAuthProvider({ children }: AdminAuthProviderProps) {
  const value = useAdminAuthState()
  return <AdminAuthContext.Provider value={value}>{children}</AdminAuthContext.Provider>
}

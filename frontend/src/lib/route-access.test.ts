import { describe, expect, it } from 'vitest'
import { getRouteAccessDecision } from '@/lib/route-access'

describe('route access', () => {
  it('redirects admins away from temporarily disabled dashboard', () => {
    const decision = getRouteAccessDecision('/dashboard', {
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: false,
    })

    expect(decision).toEqual({
      allowed: false,
      redirectTo: '/players',
      blocked: false,
    })
  })

  it('keeps super admin routes restricted to super admins', () => {
    const decision = getRouteAccessDecision('/admin/access', {
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: false,
    })

    expect(decision.allowed).toBe(false)
    expect(decision.redirectTo).toBe('/players')
  })
})

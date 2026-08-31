import { describe, expect, it } from 'vitest'
import { getRouteAccessDecision } from '@/lib/route-access'

describe('route access', () => {
  it('allows ads page without login', () => {
    const decision = getRouteAccessDecision('/ads', {
      isLoggedIn: false,
      canAccess: false,
      isAdmin: false,
      isSuperAdmin: false,
    })

    expect(decision).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
  })

  it('allows events archive without login', () => {
    const decision = getRouteAccessDecision('/events', {
      isLoggedIn: false,
      canAccess: false,
      isAdmin: false,
      isSuperAdmin: false,
    })

    expect(decision).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
  })

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

  it('blocks a regular member from viewing notices', () => {
    const decision = getRouteAccessDecision('/notices', {
      isLoggedIn: true,
      canAccess: true,
      isAdmin: false,
      isSuperAdmin: false,
    })

    expect(decision).toEqual({
      allowed: false,
      redirectTo: '/players',
      blocked: false,
    })
  })

  it('blocks a regular admin from viewing notices', () => {
    const decision = getRouteAccessDecision('/notices', {
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

  it('allows a super admin to view notices', () => {
    const decision = getRouteAccessDecision('/notices', {
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: true,
    })

    expect(decision).toEqual({
      allowed: true,
      redirectTo: null,
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
